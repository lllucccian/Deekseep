package com.dsmod.probe;

import java.lang.reflect.Member;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public final class LegacyXposedModuleRegressionTest {
    private static final class Module extends LegacyXposedModule {}

    private static final class Target {
        String join(String left, String right) { return left + ":" + right; }
        String fail(RuntimeException signal) { throw signal; }
    }

    public static void main(String[] args) throws Throwable {
        for (int api = 82; api <= 102; api++) {
            runApiContract(api);
        }
        System.out.println("Legacy Xposed API 82-102 matrix OK (21 versions)");
    }

    private static void runApiContract(int api) throws Throwable {
        XposedBridge.XPOSED_BRIDGE_VERSION = api;
        XposedBridge.resetTestState();
        final Method method = Target.class.getDeclaredMethod(
                "join", String.class, String.class);
        method.setAccessible(true);
        XposedBridge.originalMethodInvokerForTest =
                new XposedBridge.OriginalMethodInvoker() {
                    @Override public Object invoke(Member member, Object receiver,
                                                   Object[] invokeArgs) throws Throwable {
                        return ((Method) member).invoke(receiver, invokeArgs);
                    }
                };

        Module module = new Module();
        module.hook(method).intercept(new LegacyXposedModule.Hooker() {
            @Override public Object intercept(LegacyXposedModule.Chain chain) throws Throwable {
                check("left".equals(chain.getArg(0)), "outer hook saw wrong initial args");
                return chain.proceed(new Object[] {"changed", "middle"}) + ":outer";
            }
        });
        module.hook(method).intercept(new LegacyXposedModule.Hooker() {
            @Override public Object intercept(LegacyXposedModule.Chain chain) throws Throwable {
                check("changed".equals(chain.getArg(0)),
                        "inner hook did not receive replaced args");
                Object[] next = chain.getArgs().toArray();
                next[1] = "final";
                return chain.proceed(next) + ":inner";
            }
        });

        check(XposedBridge.hookCountForTest == 1,
                "the same Member registered more than one traditional callback");
        check(XposedBridge.callbackForTest != null, "traditional callback was not registered");
        check(XposedBridge.callbackForTest.priority == Integer.MIN_VALUE,
                "adapter should run after ordinary before callbacks");

        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
        param.method = method;
        param.thisObject = new Target();
        param.args = new Object[] {"left", "right"};
        XposedBridge.callbackForTest.dispatchBeforeForTest(param);

        check(!param.hasThrowable(), "adapter returned an unexpected throwable");
        check("changed:final:inner:outer".equals(param.getResult()),
                "around-hook order or return transforms changed: " + param.getResult());
        check("changed".equals(param.args[0]) && "final".equals(param.args[1]),
                "final arguments were not exposed to traditional after callbacks");

        XposedBridge.resetTestState();
        final Method failing = Target.class.getDeclaredMethod("fail", RuntimeException.class);
        failing.setAccessible(true);
        XposedBridge.originalMethodInvokerForTest =
                new XposedBridge.OriginalMethodInvoker() {
                    @Override public Object invoke(Member member, Object receiver,
                                                   Object[] invokeArgs) throws Throwable {
                        return ((Method) member).invoke(receiver, invokeArgs);
                    }
                };
        Module exceptionModule = new Module();
        exceptionModule.hook(failing).intercept(new LegacyXposedModule.Hooker() {
            @Override public Object intercept(LegacyXposedModule.Chain chain) throws Throwable {
                return chain.proceed();
            }
        });
        RuntimeException cancellation = new RuntimeException("flow finished");
        XC_MethodHook.MethodHookParam failingParam = new XC_MethodHook.MethodHookParam();
        failingParam.method = failing;
        failingParam.thisObject = new Target();
        failingParam.args = new Object[] {cancellation};
        XposedBridge.callbackForTest.dispatchBeforeForTest(failingParam);
        check(failingParam.getThrowable() == cancellation,
                "reflection wrapper changed the host exception contract: "
                        + failingParam.getThrowable());

        XposedBridge.resetTestState();
        XposedBridge.originalMethodInvokerForTest =
                new XposedBridge.OriginalMethodInvoker() {
                    @Override public Object invoke(Member member, Object receiver,
                                                   Object[] invokeArgs) throws Throwable {
                        return ((Method) member).invoke(receiver, invokeArgs);
                    }
                };
        Module failOpenModule = new Module();
        failOpenModule.hook(method).intercept(new LegacyXposedModule.Hooker() {
            @Override public Object intercept(LegacyXposedModule.Chain chain) throws Throwable {
                return chain.proceed(new Object[] {"invalid", Integer.valueOf(7)});
            }
        });
        XC_MethodHook.MethodHookParam failOpenParam = new XC_MethodHook.MethodHookParam();
        failOpenParam.method = method;
        failOpenParam.thisObject = new Target();
        failOpenParam.args = new Object[] {"safe", "original"};
        XposedBridge.callbackForTest.dispatchBeforeForTest(failOpenParam);
        check(!failOpenParam.hasThrowable(),
                "API " + api
                        + " incompatible replacement should fail open to the original arguments");
        check("safe:original".equals(failOpenParam.getResult()),
                "API " + api + " fail-open invocation did not preserve the original arguments");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
