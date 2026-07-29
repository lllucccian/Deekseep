package com.dsmod.probe;

import java.lang.reflect.Method;

public final class GooglePlayAppearanceMappingRegressionTest {
    private static final class FakeAnchors {
        public float d(Object value) throws Exception {
            Class<?> drawerValue = Class.forName("vo2");
            if (value == drawerValue.getDeclaredField("a").get(null)) return -864f;
            if (value == drawerValue.getDeclaredField("b").get(null)) return 0f;
            return Float.NaN;
        }
    }

    private static final class FakeAnchoredState {
        private final FakeAnchors anchors = new FakeAnchors();

        public FakeAnchors b() {
            return anchors;
        }
    }

    private static final class FakeDrawerState {
        @SuppressWarnings("unused")
        private final FakeAnchoredState c = new FakeAnchoredState();
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Method resolve = Main.class.getDeclaredMethod(
                "resolveSidebarDrawerWidth", Object.class, ClassLoader.class);
        resolve.setAccessible(true);
        int width = ((Number) resolve.invoke(
                null,
                new FakeDrawerState(),
                GooglePlayAppearanceMappingRegressionTest.class.getClassLoader()))
                .intValue();
        check(width == 864,
                "Google Play uo2.c -> ya.b() -> fc2.d(vo2) anchor contract should resolve");
        check(ChatAppearance.isChatRoute("r91"),
                "Google Play ChatRoute alias should be recognized");
        check(ChatAppearance.isSettingsRoute("og7"),
                "Google Play SettingsRoute alias should be recognized");
        System.out.println("Google Play appearance mapping regression tests passed");
    }
}
