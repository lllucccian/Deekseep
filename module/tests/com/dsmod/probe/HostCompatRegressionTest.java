package com.dsmod.probe;

import java.lang.reflect.Field;

/** Verifies that the 2.3 symbol table never changes the legacy identity path. */
public final class HostCompatRegressionTest {
    private static final class vv {}
    private static final class aq {}
    private static final class gw {}
    private static final class ew {}
    private static final class kw {}
    private static final class iw {}

    public static void main(String[] args) throws Exception {
        check(HostCompat.hasV236ChatViewModel(
                        HostCompatRegressionTest.class.getClassLoader()),
                "2.3.6 td1 contract detected even when kd1 also exists");
        setV230(false);
        equals("u25", HostCompat.name("u25"), "legacy class identity");
        equals("O", HostCompat.messageMethod("O"), "legacy message method identity");
        equals("z", HostCompat.staticMessageField(new vv(), "z"),
                "legacy message field identity");
        equals("ba1", HostCompat.resumeMessageEventClass(),
                "2.2.x native resume event");
        setBoolean("legacyUnitUsesTi8", false);
        equals("ui8", HostCompat.unitClass(), "2.2.0/2.2.2 Unit mapping");
        check(HostCompat.supportsHostVersionName("2.2.0"), "2.2.0 supported");
        check(HostCompat.supportsHostVersionName("2.2.2"), "2.2.2 supported");
        check(HostCompat.supportsHostVersionName("2.3.0"), "2.3.0 supported");
        check(HostCompat.supportsHostVersionName("2.3.4"), "2.3.4 supported");
        check(HostCompat.supportsHostVersionName("2.3.6"), "2.3.6 supported");
        check(!HostCompat.supportsHostVersionName("2.3.3"), "2.3.3 rejected");
        check(!HostCompat.supportsHostVersionName("2.4.0"), "future host rejected");
        equals("id0", HostCompat.localApiAuthInterceptorClass(),
                "2.2.x account auth interceptor");
        equals("jk3", HostCompat.localApiHeaderBuilderClass(),
                "2.2.x account header builder");
        equals("l0", HostCompat.localApiHeaderSetterMethod(),
                "2.2.x account header setter");
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
        equals("td0", HostCompat.localApiAuthInterceptorClass(),
                "2.3.0 account auth interceptor");
        equals("tm3", HostCompat.localApiHeaderBuilderClass(),
                "2.3.0 account header builder");
        equals("k0", HostCompat.localApiHeaderSetterMethod(),
                "2.3.0 account header setter");

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
        equals("a", HostCompat.localApiSessionCreateMethod(),
                "2.3.4 Google Play local API session create");
        equals("c", HostCompat.localApiSessionDeleteMethod(),
                "2.3.4 Google Play local API session delete");
        equals("of1", HostCompat.localApiSessionDeleteRequestClass(),
                "2.3.4 Google Play local API session delete request");
        equals("lw8", HostCompat.name("u82"),
                "2.3.4 Google Play runBlocking holder");
        equals("eg0", HostCompat.localApiAuthInterceptorClass(),
                "2.3.4 GP account auth interceptor");
        equals("gs3", HostCompat.localApiHeaderBuilderClass(),
                "2.3.4 GP account header builder");
        equals("sd7", HostCompat.name("p68"),
                "2.3.4 Google Play cloud-directory transaction");
        equals("g2a", HostCompat.name("aw"),
                "2.3.4 Google Play chat-session directory DAO");
        equals("d71", HostCompat.name("k31"),
                "2.3.4 Google Play attachment composer");
        equals("d", HostCompat.method("p68", "a"),
                "2.3.4 Google Play cloud-directory transaction method");
        equals("s", HostCompat.method("aw", "a"),
                "2.3.4 Google Play chat-session directory reader");
        equals("B", HostCompat.instanceMethod(new kw(), "z"),
                "2.3.4 Google Play dynamic message accessor shift");
        equals("A", HostCompat.staticMessageField(new iw(), "z"),
                "2.3.4 Google Play static message field shift");

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
        equals("u", HostCompat.localApiSessionCreateMethod(),
                "2.3.4 mainland local API session create");
        equals("w", HostCompat.localApiSessionDeleteMethod(),
                "2.3.4 mainland local API session delete");
        equals("ud1", HostCompat.localApiSessionDeleteRequestClass(),
                "2.3.4 mainland local API session delete request");
        equals("tn4", HostCompat.name("u82"),
                "2.3.4 mainland runBlocking holder");
        equals("se0", HostCompat.localApiAuthInterceptorClass(),
                "2.3.4 CN account auth interceptor");
        equals("cq3", HostCompat.localApiHeaderBuilderClass(),
                "2.3.4 CN account header builder");
        equals("a42", HostCompat.name("uz1"),
                "2.3.4 mainland Kotlin continuation");
        equals("yh8", HostCompat.name("p68"),
                "2.3.4 mainland cloud-directory transaction");
        equals("p6a", HostCompat.name("aw"),
                "2.3.4 mainland chat-session directory DAO");
        equals("k51", HostCompat.name("k31"),
                "2.3.4 mainland attachment composer");
        equals("b", HostCompat.method("p68", "a"),
                "2.3.4 mainland cloud-directory transaction method");
        equals("h", HostCompat.method("aw", "a"),
                "2.3.4 mainland chat-session directory reader");
        equals("B", HostCompat.instanceMethod(new gw(), "z"),
                "2.3.4 mainland dynamic message accessor shift");
        equals("A", HostCompat.staticMessageField(new ew(), "z"),
                "2.3.4 mainland static message field shift");

        setV236();
        equals("2.3.6/code249-cn", HostCompat.generationName(),
                "2.3.6 mainland generation");
        equals("td1", HostCompat.name("za1"),
                "2.3.6 chat session component");
        equals("e61", HostCompat.name("y31"),
                "2.3.6 Compose marker");
        equals("i42", HostCompat.name("uz1"),
                "2.3.6 Kotlin continuation");
        equals("h61", HostCompat.name("b41"), "2.3.6 Flow implementation");
        equals("c63", HostCompat.name("q03"), "2.3.6 Flow collector");
        equals("bg4", HostCompat.name("sf4"), "2.3.6 JSON owner");
        equals("qe4", HostCompat.name("ge4"), "2.3.6 JSON element");
        equals("rg3", HostCompat.name("xa3"), "2.3.6 Function0 callback");
        equals("sc", HostCompat.name("mc"),
                "2.3.6 mainland sidebar renderer");
        equals("k", HostCompat.method("mc", "e"),
                "2.3.6 mainland session row");
        equals("l", HostCompat.method("mc", "f"),
                "2.3.6 mainland session navigator");
        equals("ei8", HostCompat.name("p68"),
                "2.3.6 cloud-directory transaction");
        equals("v6a", HostCompat.name("aw"),
                "2.3.6 chat-session directory DAO");
        equals("b", HostCompat.method("p68", "a"),
                "2.3.6 cloud-directory transaction method");
        equals("h", HostCompat.method("aw", "a"),
                "2.3.6 chat-session directory reader");
        equals("mu8", HostCompat.unitClass(), "2.3.6 Kotlin Unit");
        equals("xc1", HostCompat.resumeMessageEventClass(),
                "2.3.6 native resume event");
        equals("se0", HostCompat.localApiAuthInterceptorClass(),
                "2.3.6 account auth interceptor");
        equals("lq3", HostCompat.localApiHeaderBuilderClass(),
                "2.3.6 account header builder");
        equals("n51", HostCompat.name("k31"),
                "2.3.6 mainland attachment composer");
        equals("dz1", HostCompat.name("wu1"),
                "2.3.6 mainland attachment metadata");
        equals("oy5", HostCompat.name("kp5"),
                "2.3.6 network success wrapper");
        equals("wp", HostCompat.name("fp"),
                "2.3.6 uploaded file record");
        equals("c0", HostCompat.method("u82", "K"),
                "2.3.6 runBlocking entry");
        equals("B", HostCompat.instanceMethod(new gw(), "z"),
                "2.3.6 dynamic message accessor shift");
        equals("A", HostCompat.staticMessageField(new ew(), "z"),
                "2.3.6 static message field shift");
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
        setBoolean("v236", false);
        setBoolean("googlePlay", false);
    }

    private static void setV234(boolean enabled, boolean google) throws Exception {
        setBoolean("initialized", true);
        setBoolean("v230", enabled);
        setBoolean("v234", enabled);
        setBoolean("v236", false);
        setBoolean("googlePlay", google);
    }

    private static void setV236() throws Exception {
        setBoolean("initialized", true);
        setBoolean("v230", true);
        setBoolean("v234", true);
        setBoolean("v236", true);
        setBoolean("googlePlay", false);
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

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
