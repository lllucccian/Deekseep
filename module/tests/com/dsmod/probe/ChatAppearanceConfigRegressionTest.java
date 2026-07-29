package com.dsmod.probe;

import org.json.JSONObject;

public final class ChatAppearanceConfigRegressionTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static float[] multiply(float[] left, float[] right) {
        float[] out = new float[9];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                out[row * 3 + column] =
                        left[row * 3] * right[column]
                        + left[row * 3 + 1] * right[3 + column]
                        + left[row * 3 + 2] * right[6 + column];
            }
        }
        return out;
    }

    private static float[] rotationX(float radians) {
        float cosine = (float) Math.cos(radians);
        float sine = (float) Math.sin(radians);
        return new float[]{
                1f, 0f, 0f,
                0f, cosine, -sine,
                0f, sine, cosine
        };
    }

    private static float[] rotationY(float radians) {
        float cosine = (float) Math.cos(radians);
        float sine = (float) Math.sin(radians);
        return new float[]{
                cosine, 0f, sine,
                0f, 1f, 0f,
                -sine, 0f, cosine
        };
    }

    private static float[] rotationZ(float radians) {
        float cosine = (float) Math.cos(radians);
        float sine = (float) Math.sin(radians);
        return new float[]{
                cosine, -sine, 0f,
                sine, cosine, 0f,
                0f, 0f, 1f
        };
    }

    private static void testRoundTrip() {
        ChatAppearance.Config config = new ChatAppearance.Config();
        config.enabled = true;
        config.backgroundFile = "background_1.png";
        config.backgroundOpacity = 0.37f;
        config.backgroundMode = "fit";
        config.backgroundExtent = "half_bottom";
        config.backgroundEdgeMode = "mirror";
        config.backgroundScale = 12345.5f;
        config.backgroundRotation = 91f;
        config.backgroundFocusX = 0.18f;
        config.backgroundFocusY = 0.77f;
        config.depthEnabled = false;
        config.motionEnabled = false;
        config.motionAmount = 0.19f;
        config.perScreenMotionEnabled = true;
        config.chatMotionAmount = -0.04f;
        config.sidebarMotionAmount = 0.21f;
        config.settingsMotionAmount = -0.16f;
        config.backgroundOnChat = false;
        config.backgroundOnSidebar = true;
        config.backgroundOnSettings = false;
        config.liquidGlassEnabled = true;
        config.glassQuality = "balanced";
        config.shakeParallaxEnabled = true;
        config.spatialDepthEnabled = true;
        config.spatialStrength = "strong";
        config.spatialReduceMotion = true;
        config.spatialAutoRecenter = false;
        config.spatialDirectionMultiplier = -1f;
        config.spatialEdgeExtendEnabled = false;
        config.bubbleEnabled = true;
        config.userBubble.preset = "liquid";
        config.userBubble.opacity = 0.64f;
        config.userBubble.radius = 27f;
        config.userBubble.borderWidth = 1.7f;
        config.userBubble.decorationFile = "bubble_user_1.png";
        config.userBubble.decorationSize = 54f;
        config.userBubble.decorationX = 0.22f;
        config.userBubble.decorationOpacity = 0.73f;
        config.userBubble.decorationRotation = -31f;
        config.assistantBubble.preset = "outline";
        config.assistantBubble.radius = 14f;

        ChatAppearance.Sticker sticker = new ChatAppearance.Sticker();
        sticker.id = "sticker-a";
        sticker.file = "sticker_1.webp";
        sticker.x = 0.23f;
        sticker.y = 0.71f;
        sticker.size = 0.31f;
        sticker.opacity = 0.68f;
        sticker.rotation = -42f;
        config.stickers.add(sticker);

        ChatAppearance.Config decoded =
                ChatAppearance.Config.fromJson(config.toJson().toString());
        check(decoded.enabled, "enabled flag should survive");
        check("background_1.png".equals(decoded.backgroundFile),
                "background filename should survive");
        check("fit".equals(decoded.backgroundMode), "background mode should survive");
        check("half_bottom".equals(decoded.backgroundExtent),
                "background display area should survive");
        check("mirror".equals(decoded.backgroundEdgeMode),
                "background edge mode should survive");
        check(Math.abs(decoded.backgroundScale - 12345.5f) < 0.1f,
                "background zoom should survive without an arbitrary upper clamp");
        check(Math.abs(decoded.backgroundOpacity - 0.37f) < 0.001f,
                "background opacity should survive");
        check(Math.abs(decoded.backgroundRotation - 91f) < 0.001f,
                "background rotation should survive");
        check(Math.abs(decoded.backgroundFocusX - 0.18f) < 0.001f
                        && Math.abs(decoded.backgroundFocusY - 0.77f) < 0.001f,
                "background crop focus should survive");
        check(!decoded.depthEnabled, "wallpaper depth flag should survive");
        check(!decoded.motionEnabled, "motion flag should survive");
        check(Math.abs(decoded.motionAmount - 0.19f) < 0.001f,
                "motion amount should survive");
        check(decoded.perScreenMotionEnabled
                        && Math.abs(decoded.chatMotionAmount + 0.04f) < 0.001f
                        && Math.abs(decoded.sidebarMotionAmount - 0.21f) < 0.001f
                        && Math.abs(decoded.settingsMotionAmount + 0.16f) < 0.001f,
                "per-screen motion should survive");
        check(!decoded.backgroundOnChat && decoded.backgroundOnSidebar
                        && !decoded.backgroundOnSettings,
                "background screen bindings should survive");
        check(decoded.liquidGlassEnabled
                        && "balanced".equals(decoded.glassQuality),
                "global glass configuration should survive");
        check(decoded.spatialDepthEnabled && !decoded.shakeParallaxEnabled,
                "spatial depth should survive and disable the competing shake spring");
        check("strong".equals(decoded.spatialStrength)
                        && decoded.spatialReduceMotion
                        && !decoded.spatialAutoRecenter
                        && decoded.spatialDirectionMultiplier == -1f
                        && !decoded.spatialEdgeExtendEnabled,
                "spatial motion policy should survive");
        check(decoded.bubbleEnabled, "bubble customization flag should survive");
        check("liquid".equals(decoded.userBubble.preset)
                        && Math.abs(decoded.userBubble.opacity - 0.64f) < 0.001f
                        && Math.abs(decoded.userBubble.radius - 27f) < 0.001f
                        && Math.abs(decoded.userBubble.borderWidth - 1.7f) < 0.001f,
                "user bubble surface style should survive");
        check("bubble_user_1.png".equals(decoded.userBubble.decorationFile)
                        && Math.abs(decoded.userBubble.decorationSize - 54f) < 0.001f
                        && Math.abs(decoded.userBubble.decorationX - 0.22f) < 0.001f
                        && Math.abs(decoded.userBubble.decorationOpacity - 0.73f) < 0.001f
                        && Math.abs(decoded.userBubble.decorationRotation + 31f) < 0.001f,
                "user bubble decoration should survive");
        check("outline".equals(decoded.assistantBubble.preset)
                        && Math.abs(decoded.assistantBubble.radius - 14f) < 0.001f,
                "assistant bubble style should remain independent");
        check(decoded.stickers.size() == 1, "sticker should survive");
        ChatAppearance.Sticker restored = decoded.stickers.get(0);
        check("sticker-a".equals(restored.id), "sticker id should survive");
        check(Math.abs(restored.x - 0.23f) < 0.001f, "sticker x should survive");
        check(Math.abs(restored.y - 0.71f) < 0.001f, "sticker y should survive");
        check(Math.abs(restored.size - 0.31f) < 0.001f, "sticker size should survive");
        check(Math.abs(restored.opacity - 0.68f) < 0.001f,
                "sticker opacity should survive");
        check(Math.abs(restored.rotation + 42f) < 0.001f,
                "sticker rotation should survive");
    }

    private static void testSanitization() throws Exception {
        JSONObject root = new JSONObject();
        root.put("enabled", true);
        root.put("background_file", "../outside.png");
        root.put("background_opacity", 4.5d);
        root.put("background_mode", "unknown");
        root.put("background_extent", "unknown");
        root.put("background_edge_mode", "unknown");
        root.put("background_scale", -8d);
        root.put("background_rotation", 725d);
        root.put("background_focus_x", -4d);
        root.put("background_focus_y", 8d);
        root.put("motion_amount", 5d);
        root.put("chat_motion_amount", -5d);
        root.put("sidebar_motion_amount", 5d);
        root.put("settings_motion_amount", -9d);
        root.put("liquid_glass_enabled", true);
        root.put("glass_quality", "impossible");
        root.put("spatial_strength", "impossible");
        root.put("spatial_direction_multiplier", 0d);
        root.put("bubble_enabled", true);
        JSONObject userBubble = new JSONObject();
        userBubble.put("preset", "unknown");
        userBubble.put("opacity", 7d);
        userBubble.put("radius", 999d);
        userBubble.put("border_width", -4d);
        userBubble.put("decoration_file", "../../outside.png");
        userBubble.put("decoration_size", 999d);
        userBubble.put("decoration_x", -2d);
        userBubble.put("decoration_opacity", -3d);
        userBubble.put("decoration_rotation", 725d);
        root.put("user_bubble", userBubble);

        org.json.JSONArray stickers = new org.json.JSONArray();
        for (int i = 0; i < ChatAppearance.MAX_STICKERS + 5; i++) {
            JSONObject item = new JSONObject();
            item.put("id", i < 2 ? "duplicate" : "id-" + i);
            item.put("file", i == 3 ? "../../bad.png" : "sticker_" + i + ".png");
            item.put("x", i == 0 ? -3d : 3d);
            item.put("y", 2d);
            item.put("size", 9d);
            item.put("opacity", -1d);
            item.put("rotation", 725d);
            stickers.put(item);
        }
        root.put("stickers", stickers);

        ChatAppearance.Config decoded =
                ChatAppearance.Config.fromJson(root.toString());
        check(decoded.backgroundFile.length() == 0,
                "path traversal background should be rejected");
        check(decoded.backgroundOpacity == 1f, "background opacity should clamp");
        check("crop".equals(decoded.backgroundMode), "unknown mode should become crop");
        check("full".equals(decoded.backgroundExtent),
                "unknown display area should become full screen");
        check("clip".equals(decoded.backgroundEdgeMode),
                "unknown edge mode should become clip");
        ChatAppearance.Config extended = ChatAppearance.Config.fromJson(
                "{\"background_edge_mode\":\"extend\"}");
        check("extend".equals(extended.backgroundEdgeMode),
                "outermost-pixel edge extension should survive sanitization");
        ChatAppearance.Config mirrored = ChatAppearance.Config.fromJson(
                "{\"background_edge_mode\":\"mirror\"}");
        check("mirror".equals(mirrored.backgroundEdgeMode),
                "mirrored edge extension should remain backward compatible");
        check(decoded.backgroundScale == 1f,
                "invalid background zoom should reset to one");
        check(decoded.motionAmount == ChatAppearance.MAX_MOTION_AMOUNT,
                "motion amount should clamp");
        check(decoded.backgroundRotation >= -180f
                        && decoded.backgroundRotation <= 180f,
                "background rotation should normalize");
        check(decoded.backgroundFocusX == 0f && decoded.backgroundFocusY == 1f,
                "crop focus should clamp");
        check(decoded.chatMotionAmount == -ChatAppearance.MAX_MOTION_AMOUNT
                        && decoded.sidebarMotionAmount
                        == ChatAppearance.MAX_MOTION_AMOUNT
                        && decoded.settingsMotionAmount
                        == -ChatAppearance.MAX_MOTION_AMOUNT,
                "per-screen motion should clamp");
        check(decoded.bubbleEnabled, "valid bubble enable flag should survive");
        check(decoded.liquidGlassEnabled
                        && "auto".equals(decoded.glassQuality),
                "glass enable should survive and unknown quality should sanitize");
        check("standard".equals(decoded.spatialStrength)
                        && decoded.spatialDirectionMultiplier == 1f,
                "spatial strength and direction should sanitize");
        check("glass".equals(decoded.userBubble.preset),
                "unknown bubble preset should use glass");
        check(decoded.userBubble.opacity == 1f
                        && decoded.userBubble.radius
                        == ChatAppearance.MAX_BUBBLE_RADIUS
                        && decoded.userBubble.borderWidth == 0f,
                "bubble surface values should clamp");
        check(decoded.userBubble.decorationFile.length() == 0,
                "bubble decoration path traversal should be rejected");
        check(decoded.userBubble.decorationSize
                        == ChatAppearance.MAX_BUBBLE_DECORATION_SIZE
                        && decoded.userBubble.decorationX == 0f
                        && decoded.userBubble.decorationOpacity == 0f
                        && decoded.userBubble.decorationRotation >= -180f
                        && decoded.userBubble.decorationRotation <= 180f,
                "bubble decoration values should clamp");
        check(decoded.stickers.size() <= ChatAppearance.MAX_STICKERS,
                "sticker count should be bounded");
        for (ChatAppearance.Sticker sticker : decoded.stickers) {
            check(sticker.file.indexOf('/') < 0, "sticker path should be a basename");
            check(sticker.x >= 0f && sticker.x <= 1f, "x should clamp");
            check(sticker.y >= 0f && sticker.y <= 1f, "y should clamp");
            check(sticker.size >= 0.08f && sticker.size <= 0.65f,
                    "size should clamp");
            check(sticker.opacity >= 0f && sticker.opacity <= 1f,
                    "opacity should clamp");
            check(sticker.rotation >= -180f && sticker.rotation <= 180f,
                    "rotation should normalize");
        }
    }

    private static void testRouteRecognition() {
        check(ChatAppearance.isChatRoute("com.deepseek.chat.ui.pages.ChatRoute"),
                "serialized ChatRoute should match");
        check(ChatAppearance.isChatRoute("c81"), "mapped ChatRoute should match");
        check(ChatAppearance.isChatRoute("destination route=c81"),
                "diagnostic mapped route should match");
        check(ChatAppearance.isChatRoute("r91"),
                "Google Play mapped ChatRoute should match");
        check(!ChatAppearance.isChatRoute(
                        "com.deepseek.chat.ui.pages.SettingsNestedGraph.SettingsRoute"),
                "settings route must not match");
        check(ChatAppearance.isSettingsRoute(
                        "com.deepseek.chat.ui.pages.SettingsNestedGraph.SettingsRoute"),
                "serialized settings route should match");
        check(ChatAppearance.isSettingsRoute("rc7"),
                "mapped nested settings route should match");
        check(ChatAppearance.isSettingsRoute("destination route=vc7"),
                "diagnostic mapped settings route should match");
        check(ChatAppearance.isSettingsRoute("og7"),
                "Google Play mapped SettingsRoute should match");
        check(ChatAppearance.isSettingsRoute("destination route=rg7"),
                "Google Play mapped settings graph should match");
        check(!ChatAppearance.isSettingsRoute("c81"),
                "chat route must not match settings");
        check(!ChatAppearance.isChatRoute(null), "null route must not match");
        check(!ChatAppearance.isSettingsRoute(null), "null settings route must not match");
    }

    private static void testRotatedCanvasCoverage() {
        int[] unrotated = ChatAppearance.wallpaperCanvasSize(100, 200, 10, 0f);
        check(unrotated[0] == 120 && unrotated[1] == 200,
                "unrotated canvas should overscan only the motion axis");
        int[] quarterTurn = ChatAppearance.wallpaperCanvasSize(100, 200, 10, 90f);
        check(quarterTurn[0] == 200 && quarterTurn[1] == 120,
                "quarter-turn canvas should swap axes and preserve motion overscan");
        int[] diagonal = ChatAppearance.wallpaperCanvasSize(100, 200, 10, 45f);
        check(diagonal[0] > 200 && diagonal[1] > 200,
                "diagonal rotation should reserve corner coverage");
        check(Math.abs(ChatAppearance.wallpaperRenderScale(
                        0.55f, false) - 0.55f) < 0.001f,
                "ordinary wallpaper rendering should retain user zoom");
        check(Math.abs(ChatAppearance.wallpaperRenderScale(
                        0.55f, true) - 0.55f) < 0.001f,
                "spatial rendering must not lock user zoom at one-times");
        check("fit".equals(ChatAppearance.wallpaperRenderMode(
                        "fit", true))
                        && "stretch".equals(
                        ChatAppearance.wallpaperRenderMode(
                                "stretch", true))
                        && "crop".equals(
                        ChatAppearance.wallpaperRenderMode(
                                "crop", true)),
                "spatial rendering must preserve every wallpaper framing mode");
        check("extend".equals(ChatAppearance.wallpaperRenderEdgeMode(
                        "clip", true, true))
                        && "mirror".equals(
                        ChatAppearance.wallpaperRenderEdgeMode(
                                "mirror", true, false))
                        && "clip".equals(
                        ChatAppearance.wallpaperRenderEdgeMode(
                                "clip", true, false)),
                "spatial edge extension should be explicit and otherwise preserve wallpaper mode");
        check(Math.abs(ChatAppearance.wallpaperTranslationX(
                        true, 120f, 3f, 40f) - 123f) < 0.001f,
                "spatial wallpaper must combine drawer base and camera offset");
        check(Math.abs(ChatAppearance.wallpaperTranslationX(
                        false, 120f, 3f, 40f) - 160f) < 0.001f,
                "legacy wallpaper mode must keep its shake composition");

        float[] topLeft = ChatAppearance.wallpaperContentTransform(
                1448, 1086, 2519, 3633,
                1080, 2400, "crop", 0.514f, 0f, 0f);
        float[] bottomRight = ChatAppearance.wallpaperContentTransform(
                1448, 1086, 2519, 3633,
                1080, 2400, "crop", 0.514f, 1f, 1f);
        check(Math.abs(topLeft[2] - bottomRight[2]) > 500f
                        && Math.abs(topLeft[3] - bottomRight[3]) > 1000f,
                "horizontal and vertical focus must visibly move content inside the viewport");
        float[] zoomed = ChatAppearance.wallpaperContentTransform(
                1448, 1086, 2519, 3633,
                1080, 2400, "crop", 1.028f, 0.5f, 0.5f);
        check(Math.abs(zoomed[0] / topLeft[0] - 2f) < 0.001f
                        && Math.abs(zoomed[1] / topLeft[1] - 2f) < 0.001f,
                "wallpaper zoom must remain proportional inside an oversized motion canvas");
        float[] largerBleed = ChatAppearance.wallpaperContentTransform(
                1448, 1086, 4000, 5000,
                1080, 2400, "crop", 0.514f, 0f, 0f);
        check(Math.abs(largerBleed[0] - topLeft[0]) < 0.001f
                        && Math.abs(largerBleed[1] - topLeft[1]) < 0.001f,
                "extra anti-white-edge canvas must not change framing or zoom");
    }

    private static void testWallpaperViewportLayout() {
        check(ChatAppearance.wallpaperViewportHeight(201, "full") == 201
                        && ChatAppearance.wallpaperViewportTop(201, "full") == 0,
                "full wallpaper should own the complete viewport");
        check(ChatAppearance.wallpaperViewportHeight(201, "half_top") == 101
                        && ChatAppearance.wallpaperViewportTop(201, "half_top") == 0,
                "top-half wallpaper should use and clip to the upper half");
        check(ChatAppearance.wallpaperViewportTop(201, "half_center") == 50,
                "center-half wallpaper should be centered");
        check(ChatAppearance.wallpaperViewportTop(201, "half_bottom") == 100,
                "bottom-half wallpaper should align to the bottom");
    }

    private static void testBindingMigration() {
        ChatAppearance.Config legacy = ChatAppearance.Config.fromJson(
                "{\"background_file\":\"old.png\"}");
        check("full".equals(legacy.backgroundExtent)
                        && "clip".equals(legacy.backgroundEdgeMode)
                        && legacy.backgroundScale == 1f,
                "older wallpaper configs should migrate to full-screen clip at one-times zoom");
        check(legacy.backgroundOnChat && legacy.backgroundOnSidebar
                        && legacy.backgroundOnSettings,
                "older configs should default the wallpaper to all supported screens");
        legacy.backgroundOnChat = false;
        legacy.backgroundOnSidebar = false;
        legacy.backgroundOnSettings = false;
        check(!legacy.hasBoundBackground(),
                "an unbound wallpaper should not create a live background");
        check(legacy.hasVisuals(),
                "an imported wallpaper should remain editable while unbound");
    }

    private static void testSidebarOffsetProgress() {
        check(ChatAppearance.sidebarProgressForOffset(-800f, 800) == 0f,
                "closed drawer offset should return to center progress");
        check(ChatAppearance.sidebarProgressForOffset(0f, 800) == 1f,
                "open drawer offset should reach full progress");
        check(Math.abs(ChatAppearance.sidebarProgressForOffset(-400f, 800) - 0.5f)
                        < 0.001f,
                "drawer motion should map continuously in both directions");
        check(ChatAppearance.easeOutCubic(0.5f) > 0.5f,
                "motion curve should move quickly then decelerate");
        float firstQuarter = ChatAppearance.easeOutCubic(0.25f);
        float lastQuarter = 1f - ChatAppearance.easeOutCubic(0.75f);
        check(firstQuarter > lastQuarter * 10f,
                "ease-out must make the opening visibly faster than its final quarter");
        float lag60 = 0f;
        float lag120 = 0f;
        float firstStep = ChatAppearance.laggedMotionStep(
                lag60, 1f, 1000f / 60f);
        for (int i = 0; i < 12; i++) {
            lag60 = ChatAppearance.laggedMotionStep(
                    lag60, 1f, 1000f / 60f);
        }
        for (int i = 0; i < 24; i++) {
            lag120 = ChatAppearance.laggedMotionStep(
                    lag120, 1f, 1000f / 120f);
        }
        check(firstStep > 0f && firstStep < 1f,
                "wallpaper follower must trail rather than jump to the host target");
        check(lag60 > 0.95f && lag60 < 1f,
                "wallpaper follower must converge smoothly after the host stops");
        check(Math.abs(lag60 - lag120) < 0.005f,
                "wallpaper lag must be refresh-rate independent");
        check((1f - lag60) < firstStep / 5f,
                "wallpaper follower must slow progressively near the endpoint");
    }

    private static void testPerScreenMotion() {
        ChatAppearance.Config config = new ChatAppearance.Config();
        config.motionEnabled = true;
        config.motionAmount = 0.12f;
        check(config.motionFraction(false, 0f) == 0f,
                "unified chat position should be centered");
        check(Math.abs(config.motionFraction(false, 1f) - 0.12f) < 0.001f,
                "unified sidebar should move right");
        check(Math.abs(config.motionFraction(true, 0f) + 0.12f) < 0.001f,
                "unified settings should move left");

        config.perScreenMotionEnabled = true;
        config.chatMotionAmount = 0.03f;
        config.sidebarMotionAmount = 0.2f;
        config.settingsMotionAmount = -0.17f;
        check(Math.abs(config.motionFraction(false, 0f) - 0.03f) < 0.001f,
                "chat should use its own offset");
        check(Math.abs(config.motionFraction(false, 1f) - 0.2f) < 0.001f,
                "sidebar should use its own offset");
        check(Math.abs(config.motionFraction(true, 0f) + 0.17f) < 0.001f,
                "settings should use its own offset");
        check(Math.abs(config.maxMotionMagnitude() - 0.2f) < 0.001f,
                "overscan should cover the largest per-screen offset");
    }

    private static void testSpatialMotionMath() {
        check(ChatAppearance.spatialNormalizeTilt(0f) == 0f,
                "centered spatial pose should remain still");
        check(ChatAppearance.spatialNormalizeTilt(
                        (float) Math.toRadians(0.05d)) > 0f,
                "even a very small deliberate tilt must have a continuous response");
        float atFive = ChatAppearance.spatialNormalizeTilt(
                (float) Math.toRadians(5d));
        float atTen = ChatAppearance.spatialNormalizeTilt(
                (float) Math.toRadians(10d));
        float atTwenty = ChatAppearance.spatialNormalizeTilt(
                (float) Math.toRadians(20d));
        check(Math.abs(atFive - 1f) < 0.0001f,
                "five degrees should define one spherical-projection unit");
        check(atTen > 1.98f && atTen < 2f && atTwenty > atTen,
                "small-angle spherical projection should remain continuous and responsive");
        float negative = ChatAppearance.spatialNormalizeTilt(
                (float) Math.toRadians(-10d));
        check(Math.abs(atTen + negative) < 0.0001f,
                "positive and negative optical projections should be symmetric");
        float beforeSideOn = ChatAppearance.spatialNormalizeTilt(
                (float) Math.toRadians(89d));
        float afterSideOn = ChatAppearance.spatialNormalizeTilt(
                (float) Math.toRadians(91d));
        check(beforeSideOn > 0f && afterSideOn > 0f
                        && Math.abs(beforeSideOn - afterSideOn) < 0.001f,
                "crossing a side-on pose must not flip the wallpaper to the opposite corner");
        float velocity = SpatialMotionController.targetVelocity(
                0f, 0.2f, 0.01f);
        check(Math.abs(velocity - 20f) < 0.0001f,
                "pose velocity should be derived from consecutive absolute samples");
        float predicted = SpatialMotionController.predictForDisplay(
                1f, 10f, 1_000_000_000L, 1_010_000_000L);
        check(predicted > 1.17f && predicted < 1.19f,
                "short display prediction should compensate sensor age plus scanout lead");
        float boundedPrediction = SpatialMotionController.predictForDisplay(
                1f, 10f, 1_000_000_000L, 1_200_000_000L);
        check(Math.abs(boundedPrediction - 1.24f) < 0.0001f,
                "stale samples must never be extrapolated beyond the short display horizon");

        check(SpatialMotionController.axisXForRotation(
                        android.view.Surface.ROTATION_0)
                        == android.hardware.SensorManager.AXIS_X
                        && SpatialMotionController.axisYForRotation(
                        android.view.Surface.ROTATION_0)
                        == android.hardware.SensorManager.AXIS_Y,
                "portrait axes should stay in screen coordinates");
        check(SpatialMotionController.axisXForRotation(
                        android.view.Surface.ROTATION_90)
                        == android.hardware.SensorManager.AXIS_Y
                        && SpatialMotionController.axisYForRotation(
                        android.view.Surface.ROTATION_90)
                        == android.hardware.SensorManager.AXIS_MINUS_X,
                "landscape axes should remap correctly");
        check(SpatialMotionController.axisXForRotation(
                        android.view.Surface.ROTATION_180)
                        == android.hardware.SensorManager.AXIS_MINUS_X
                        && SpatialMotionController.axisYForRotation(
                        android.view.Surface.ROTATION_180)
                        == android.hardware.SensorManager.AXIS_MINUS_Y,
                "reverse portrait axes should remap correctly");
        check(SpatialMotionController.axisXForRotation(
                        android.view.Surface.ROTATION_270)
                        == android.hardware.SensorManager.AXIS_MINUS_Y
                        && SpatialMotionController.axisYForRotation(
                        android.view.Surface.ROTATION_270)
                        == android.hardware.SensorManager.AXIS_X,
                "reverse landscape axes should remap correctly");

        float[] arbitraryReference = multiply(
                rotationZ((float) Math.toRadians(37d)),
                rotationY((float) Math.toRadians(23d)));
        float[] localForward = rotationX(
                (float) Math.toRadians(5d));
        float[] forwardCurrent = multiply(
                arbitraryReference, localForward);
        float[] localRelative = new float[9];
        SpatialMotionController.relativeRotation(
                arbitraryReference, forwardCurrent, localRelative);
        float relativePitch =
                SpatialMotionController.pitchFromRotation(localRelative);
        float relativeRoll =
                SpatialMotionController.rollFromRotation(localRelative);
        check(Math.abs(relativePitch + Math.toRadians(5d)) < 0.0001d
                        && Math.abs(relativeRoll) < 0.0001f,
                "forward tilt must remain a pure local vertical axis at any initial attitude");
        check(SpatialMotionController.cameraTargetYFromPitch(
                        relativePitch) < 0f,
                "faceward forward tilt must drive the rear wallpaper upward");

        float[] localLeft = rotationY(
                (float) Math.toRadians(5d));
        float[] leftCurrent = multiply(arbitraryReference, localLeft);
        SpatialMotionController.relativeRotation(
                arbitraryReference, leftCurrent, localRelative);
        check(Math.abs(
                        SpatialMotionController.pitchFromRotation(
                                localRelative)) < 0.0001f
                        && SpatialMotionController.cameraTargetXFromRoll(
                        SpatialMotionController.rollFromRotation(
                                localRelative)) < 0f,
                "local left tilt must stay horizontal and move the rear wallpaper left");

        check(ChatAppearance.spatialStrengthMultiplier("weak") == 0.55f
                        && ChatAppearance.spatialStrengthMultiplier(
                        "standard") == 1f
                        && ChatAppearance.spatialStrengthMultiplier(
                        "strong") == 1.25f,
                "spatial strength presets should match their safe multipliers");
        float strong = ChatAppearance.spatialStrengthMultiplier("strong");
        check(Math.abs(ChatAppearance.SPATIAL_BACKGROUND_X_DP
                        * strong - 5f) < 0.0001f
                        && Math.abs(ChatAppearance.SPATIAL_BACKGROUND_Y_DP
                        * strong - 5f) < 0.0001f
                        && Math.abs(ChatAppearance.SPATIAL_BACKGROUND_X_DP
                        * strong
                        * ChatAppearance.SPATIAL_MIDGROUND_TO_BACKGROUND_RATIO
                        - 1.5f) < 0.0001f
                        && Math.abs(ChatAppearance.SPATIAL_BACKGROUND_X_DP
                        * strong
                        * ChatAppearance.SPATIAL_FOREGROUND_TO_BACKGROUND_RATIO
                        - 0.65f) < 0.0001f,
                "strong five-degree tilt should map to background 5, subject 1.5 and UI 0.65dp");
        check(ChatAppearance.SPATIAL_BACKGROUND_SCALE == 1f,
                "spatial mode must not apply an extra outer zoom to the imported wallpaper");
        float sphericalMaximum = 1f
                / SpatialMotionController.OPTICAL_REFERENCE_SINE;
        check(ChatAppearance.SPATIAL_BACKGROUND_X_DP
                        * strong * sphericalMaximum
                        < ChatAppearance.SPATIAL_CANVAS_HEADROOM_DP
                        && ChatAppearance.SPATIAL_BACKGROUND_Y_DP
                        * strong * sphericalMaximum
                        < ChatAppearance.SPATIAL_CANVAS_HEADROOM_DP,
                "maximum spherical travel must remain inside the reserved canvas bleed");
        check(!ChatAppearance.composeSpatialModifiersEnabled(),
                "child-level Compose parallax must remain disabled");
        check(ChatAppearance.spatialWallpaperOffsetX(1f, 2f) > 0f,
                "a positive calibrated camera-X value must remain positive through layer mapping");
        check(ChatAppearance.spatialWallpaperOffsetY(-1f, 2f) < 0f,
                "top-edge-toward-viewer pitch must move the wallpaper up");
        float backgroundX =
                ChatAppearance.spatialWallpaperOffsetX(0.8f, 2f);
        float foregroundX =
                ChatAppearance.spatialForegroundOffsetX(0.8f, 2f);
        float backgroundY =
                ChatAppearance.spatialWallpaperOffsetY(-0.7f, 2f);
        float foregroundY =
                ChatAppearance.spatialForegroundOffsetY(-0.7f, 2f);
        check(backgroundX * foregroundX < 0f
                        && backgroundY * foregroundY < 0f
                        && Math.abs(Math.abs(foregroundX / backgroundX)
                        - 0.13f) < 0.0001f
                        && Math.abs(Math.abs(foregroundY / backgroundY)
                        - 0.13f) < 0.0001f,
                "the unified foreground must move oppositely at thirteen percent amplitude");
        check(Math.abs(ChatAppearance.spatialWallpaperOffsetX(2f, 2f))
                        > Math.abs(ChatAppearance.spatialWallpaperOffsetX(
                        1f, 2f))
                        && Math.abs(ChatAppearance.spatialWallpaperOffsetY(
                        -2f, 2f))
                        > Math.abs(ChatAppearance.spatialWallpaperOffsetY(
                        -1f, 2f)),
                "layer offsets must preserve the optical model without a visible position clamp");

        ChatAppearance.Config defaults = ChatAppearance.Config.fromJson("{}");
        check(!defaults.spatialDepthEnabled,
                "experimental spatial depth must default to disabled");
        check("standard".equals(defaults.spatialStrength)
                        && !defaults.spatialReduceMotion
                        && defaults.spatialAutoRecenter
                        && defaults.spatialDirectionMultiplier == 1f
                        && defaults.spatialEdgeExtendEnabled,
                "spatial defaults should be standard, dynamic and auto-centred");
        ChatAppearance.Config competing = ChatAppearance.Config.fromJson(
                "{\"shake_parallax_enabled\":true,"
                        + "\"spatial_depth_enabled\":true}");
        check(competing.spatialDepthEnabled
                        && !competing.shakeParallaxEnabled,
                "saved spatial mode must win over the incompatible shake spring");
    }

    public static void main(String[] args) throws Exception {
        testRoundTrip();
        testSanitization();
        testRouteRecognition();
        testRotatedCanvasCoverage();
        testWallpaperViewportLayout();
        testBindingMigration();
        testSidebarOffsetProgress();
        testPerScreenMotion();
        testSpatialMotionMath();
        System.out.println("Chat appearance config regression tests passed");
    }
}
