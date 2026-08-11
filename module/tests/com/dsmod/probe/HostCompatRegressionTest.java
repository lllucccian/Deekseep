package com.dsmod.probe;

import java.lang.reflect.Field;

/** Verifies that the 2.3 symbol table never changes the legacy identity path. */
public final class HostCompatRegressionTest {
    private static final class vv {}
    private static final class aq {}

    public static void main(String[] args) throws Exception {
        setV230(false);
        equals("u25", HostCompat.name("u25"), "legacy class identity");
        equals("O", HostCompat.messageMethod("O"), "legacy message method identity");
        equals("z", HostCompat.staticMessageField(new vv(), "z"),
                "legacy message field identity");
        equals("ba1", HostCompat.resumeMessageEventClass(),
                "2.2.x native resume event");
        setBoolean("legacyUnitUsesTi8", false);
        equals("ui8", HostCompat.unitClass(), "2.2.0/2.2.2 Unit mapping");
        setBoolean("legacyUnitUsesTi8", true);
        equals("ti8", HostCompat.unitClass(), "2.2.1 Unit mapping");
        setBoolean("legacyUnitUsesTi8", false);

        setV230(true);
        equals("t55", HostCompat.name("u25"), "2.3 settings class");
        equals("qw0", HostCompat.name("ew0"), "2.3 request class");
        equals("P", HostCompat.messageMethod("O"), "2.3 persistence row method");
        equals("z", HostCompat.instanceMethod(new aq(), "z"),
                "non-message methods must not be shifted");
        equals("B", HostCompat.instanceMethod(new vv(), "z"),
                "2.3 message method shift");
        equals("A", HostCompat.staticMessageField(new vv(), "z"),
                "2.3 message z field shift");
        equals("B", HostCompat.staticMessageField(new vv(), "A"),
                "2.3 message A field shift");
        equals("ab1", HostCompat.resumeMessageEventClass(),
                "2.3.0 native resume event");

        setV234(true, true);
        equals("y08", HostCompat.name("jm7"),
                "2.3.4 Google Play immutable attachment list");
        equals("ef1", HostCompat.name("za1"),
                "2.3.4 Google Play composer view model");
        equals("ika", HostCompat.name("mc"),
                "2.3.4 Google Play sidebar renderer");
        equals("b", HostCompat.method("mc", "e"),
                "2.3.4 Google Play session row");
        equals("c", HostCompat.method("mc", "f"),
                "2.3.4 Google Play session navigator");
        equals("vu8", HostCompat.name("mq5"),
                "2.3.4 Google Play drawer renderer");
        equals("m", HostCompat.method("mq5", "i"),
                "2.3.4 Google Play drawer toggle");
        equals("n", HostCompat.messageMethod("l"),
                "2.3.4 Google Play message fragment list");
        equals("ql8", HostCompat.name("i68"),
                "2.3.4 Google Play Compose text renderer");
        equals("ce1", HostCompat.resumeMessageEventClass(),
                "2.3.4 Google Play native resume event");
        equals("lw8", HostCompat.name("u82"),
                "2.3.4 Google Play runBlocking holder");

        setV234(true, false);
        equals("dx7", HostCompat.name("jm7"),
                "2.3.4 mainland immutable attachment list");
        equals("kd1", HostCompat.name("za1"),
                "2.3.4 mainland composer view model");
        equals("sc", HostCompat.name("mc"),
                "2.3.4 mainland sidebar renderer");
        equals("d", HostCompat.method("mc", "e"),
                "2.3.4 mainland session row");
        equals("e", HostCompat.method("mc", "f"),
                "2.3.4 mainland session navigator");
        equals("gv7", HostCompat.name("mq5"),
                "2.3.4 mainland drawer renderer");
        equals("k", HostCompat.method("mq5", "i"),
                "2.3.4 mainland drawer toggle");
        equals("qh8", HostCompat.name("i68"),
                "2.3.4 mainland Compose text renderer");
        equals("oc1", HostCompat.resumeMessageEventClass(),
                "2.3.4 mainland native resume event");
        equals("tn4", HostCompat.name("u82"),
                "2.3.4 mainland runBlocking holder");
        System.out.println("Host compatibility regression tests passed");
    }

    private static void setV230(boolean enabled) throws Exception {
        Field initialized = HostCompat.class.getDeclaredField("initialized");
        Field v230 = HostCompat.class.getDeclaredField("v230");
        initialized.setAccessible(true);
        v230.setAccessible(true);
        initialized.setBoolean(null, true);
        v230.setBoolean(null, enabled);
        setBoolean("v234", false);
        setBoolean("googlePlay", false);
    }

    private static void setV234(boolean enabled, boolean google) throws Exception {
        setBoolean("initialized", true);
        setBoolean("v230", enabled);
        setBoolean("v234", enabled);
        setBoolean("googlePlay", google);
    }

    private static void setBoolean(String name, boolean value) throws Exception {
        Field field = HostCompat.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(null, value);
    }

    private static void equals(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }
}
