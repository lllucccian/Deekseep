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
        System.out.println("Host compatibility regression tests passed");
    }

    private static void setV230(boolean enabled) throws Exception {
        Field initialized = HostCompat.class.getDeclaredField("initialized");
        Field v230 = HostCompat.class.getDeclaredField("v230");
        initialized.setAccessible(true);
        v230.setAccessible(true);
        initialized.setBoolean(null, true);
        v230.setBoolean(null, enabled);
    }

    private static void equals(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }
}
