package com.dsmod.probe;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Compatibility adapter that runs the stable core's around-hook contract on traditional Xposed.
 *
 * <p>The domestic and Google Play universal APKs compile the same Main.java. Traditional Xposed
 * supplies before/after callbacks through this adapter. This
 * bridge invokes the original member from a before callback and publishes the interceptor result,
 * preserving the core's fail-open and argument-replacement behavior without maintaining a second
 * several-thousand-line fork.</p>
 */
abstract class LegacyXposedModule {

    private final ConcurrentHashMap<Member, HookTarget> targets =
            new ConcurrentHashMap<Member, HookTarget>();

    interface Hooker {
        Object intercept(Chain chain) throws Throwable;
    }

    static final class Chain {
        private final Member executable;
        private final XC_MethodHook.MethodHookParam param;
        private final List<Hooker> hookers;
        private final int nextHooker;
        private final Object[] args;
        private final Object[] originalArgs;
        private boolean proceeded;

        Chain(Member executable, XC_MethodHook.MethodHookParam param,
              List<Hooker> hookers, int nextHooker, Object[] args,
              Object[] originalArgs) {
            this.executable = executable;
            this.param = param;
            this.hookers = hookers;
            this.nextHooker = nextHooker;
            this.args = args == null ? new Object[0] : args;
            this.originalArgs = originalArgs == null ? new Object[0] : originalArgs;
        }

        Object getThisObject() { return param.thisObject; }

        Object getArg(int index) { return args[index]; }

        List<Object> getArgs() { return Arrays.asList(args); }

        Member getExecutable() { return executable; }

        Object proceed() throws Throwable { return proceed(args); }

        Object proceed(Object... args) throws Throwable {
            if (proceeded) {
                throw new IllegalStateException("Xposed chain proceeded more than once");
            }
            proceeded = true;
            Object[] actual = args == null ? new Object[0] : args;
            if (nextHooker < hookers.size()) {
                return hookers.get(nextHooker).intercept(new Chain(
                        executable, param, hookers, nextHooker + 1, actual, originalArgs));
            }
            // Keep MethodHookParam consistent for traditional after callbacks registered by
            // other modules. invokeOriginalMethod bypasses hook dispatch but uses these exact
            // arguments and thisObject, including for constructors.
            Object[] invokeArgs = actual;
            if (!argumentsCompatible(executable, actual)
                    && argumentsCompatible(executable, originalArgs)) {
                // A few traditional bridges expose synthetic Compose parameters in a different
                // order when an around hook supplies a replacement array. Never crash the host
                // for such a bridge mismatch: preserve the unmodified invocation and emit enough
                // framework-log detail to diagnose the affected member.
                XposedBridge.log("Deekseep universal: rejected incompatible replacement for "
                        + executable + " actual=" + argumentTypes(actual)
                        + " original=" + argumentTypes(originalArgs));
                invokeArgs = originalArgs.clone();
            }
            param.args = invokeArgs;
            try {
                return XposedBridge.invokeOriginalMethod(
                        executable, param.thisObject, invokeArgs);
            } catch (InvocationTargetException wrapped) {
                // Some traditional bridges implement invokeOriginalMethod with Method.invoke().
                // Preserve the exception contract seen by the host instead of leaking the
                // reflection wrapper. This matters for Kotlin coroutine cancellation signals.
                Throwable cause = wrapped.getCause();
                if (cause != null) throw cause;
                throw wrapped;
            }
        }

        private static boolean argumentsCompatible(Member member, Object[] values) {
            Class<?>[] types;
            if (member instanceof Method) {
                types = ((Method) member).getParameterTypes();
            } else if (member instanceof Constructor) {
                types = ((Constructor<?>) member).getParameterTypes();
            } else {
                return true;
            }
            if (values == null || types.length != values.length) return false;
            for (int i = 0; i < types.length; i++) {
                Object value = values[i];
                if (value == null) {
                    if (types[i].isPrimitive()) return false;
                    continue;
                }
                Class<?> wanted = boxed(types[i]);
                if (!wanted.isInstance(value)) return false;
            }
            return true;
        }

        private static Class<?> boxed(Class<?> type) {
            if (!type.isPrimitive()) return type;
            if (type == boolean.class) return Boolean.class;
            if (type == byte.class) return Byte.class;
            if (type == char.class) return Character.class;
            if (type == short.class) return Short.class;
            if (type == int.class) return Integer.class;
            if (type == long.class) return Long.class;
            if (type == float.class) return Float.class;
            if (type == double.class) return Double.class;
            return Void.class;
        }

        private static String argumentTypes(Object[] values) {
            if (values == null) return "null";
            StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < values.length; i++) {
                if (i > 0) out.append(',');
                out.append(values[i] == null ? "null" : values[i].getClass().getName());
            }
            return out.append(']').toString();
        }
    }

    final class HookTarget {
        private final Member member;
        private final CopyOnWriteArrayList<Hooker> hookers =
                new CopyOnWriteArrayList<Hooker>();
        private boolean registered;

        HookTarget(Member member) { this.member = member; }

        synchronized void intercept(final Hooker hooker) {
            if (hooker == null) throw new NullPointerException("hooker");
            hookers.add(hooker);
            if (registered) return;
            try {
                // Run after ordinary before callbacks, then publish our result so their matching
                // after callbacks still execute. A highest-priority replacement would silently
                // suppress every other module hooked to the same host member.
                XposedBridge.hookMethod(member, new XC_MethodHook(Integer.MIN_VALUE) {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            List<Hooker> snapshot = Arrays.asList(
                                    hookers.toArray(new Hooker[hookers.size()]));
                            Object[] original = param.args == null
                                    ? new Object[0] : param.args.clone();
                            Chain chain = new Chain(
                                    member, param, snapshot, 0, param.args, original);
                            param.setResult(chain.proceed());
                        } catch (Throwable error) {
                            param.setThrowable(error);
                        }
                    }
                });
                registered = true;
            } catch (RuntimeException error) {
                hookers.remove(hooker);
                throw error;
            }
        }
    }

    HookTarget hook(Member member) {
        if (member == null) throw new NullPointerException("member");
        HookTarget existing = targets.get(member);
        if (existing != null) return existing;
        HookTarget created = new HookTarget(member);
        existing = targets.putIfAbsent(member, created);
        return existing == null ? created : existing;
    }

    boolean deoptimize(Member member) {
        try {
            Method method = XposedBridge.class.getDeclaredMethod("deoptimizeMethod", Member.class);
            method.setAccessible(true);
            method.invoke(null, member);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    void log(int priority, String tag, String message) {
        XposedBridge.log(tag + " [" + priority + "] " + message);
    }
}
