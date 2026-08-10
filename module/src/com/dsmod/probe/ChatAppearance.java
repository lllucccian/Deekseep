package com.dsmod.probe;

import android.app.Activity;
import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.util.AtomicFile;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Persistent chat wallpaper and sticker model plus the non-interactive runtime overlay.
 *
 * <p>The overlay deliberately sits above the host Compose tree and always returns false from
 * touch dispatch. This avoids depending on DeepSeek's obfuscated chat composables and keeps every
 * native chat gesture available. A low wallpaper opacity blends the selected image with the
 * host's normal white/dark surface; stickers can remain fully opaque. Editing happens in
 * {@link ChatAppearanceUi}'s isolated canvas, where gestures cannot interfere with a live chat.</p>
 */
final class ChatAppearance {
    static final String ROOT_DIR =
            "/data/data/com.deepseek.chat/files/deekseep_appearance";
    static final String ASSET_DIR = ROOT_DIR + "/assets";
    static final String CONFIG_FILE = ROOT_DIR + "/config.json";
    static final int MAX_STICKERS = 12;
    static final long MAX_IMPORT_BYTES = 32L * 1024L * 1024L;
    static final float MAX_BUBBLE_RADIUS = 36f;
    static final float MAX_BUBBLE_BORDER = 4f;
    static final float MIN_BUBBLE_DECORATION_SIZE = 18f;
    static final float MAX_BUBBLE_DECORATION_SIZE = 72f;
    static final float DEFAULT_MOTION_AMOUNT = 0.12f;
    static final float MAX_MOTION_AMOUNT = 0.24f;
    // Multiplies the effective parallax travel (canvas sizing + applied translation) so the
    // wallpaper drifts ~1/5 further than the configured amount without touching saved values.
    static final float MOTION_DISTANCE_BOOST = 1.2f;
    static final float MIN_BACKGROUND_SCALE = 0.01f;
    // Time-based one-pole follower. A fixed per-frame fraction became almost instantaneous on
    // 120 Hz panels, which removed the intended optical separation between the host drawer and
    // the wallpaper. This time constant keeps the same restrained trailing at every refresh rate.
    static final float WALLPAPER_LAG_TIME_CONSTANT_MS = 58f;
    // Let the foreground transition lead by a few frames before the far background follows.
    static final long WALLPAPER_LAG_START_DELAY_MS = 36L;
    // Route transitions (chat <-> settings) glide the wallpaper over this window with a decelerate
    // curve so it eases in rather than snapping to the new anchor.
    static final long WALLPAPER_ROUTE_GLIDE_MS = 520L;
    static final long WALLPAPER_ROUTE_START_DELAY_MS = 42L;
    // Shake parallax (Apple-style inertial drift). This is impulse-driven, NOT attitude mapping:
    // the wallpaper stays put while the phone is held still, reacts only to a quick move/shake,
    // then a spring pulls it back to rest. Angular speed (gyro) or linear-accel jerk feeds an
    // impulse into a 2D critically-under-damped spring.
    static final float SHAKE_HEADROOM_FRACTION = 0.014f; // extra canvas bleed reserved for shake
    static final float SHAKE_MAX_OFFSET_FRACTION = 0.0106f; // spring displacement clamp (of width)
    static final float SHAKE_SPRING_STIFFNESS = 90f; // higher = snappier return
    static final float SHAKE_SPRING_DAMPING = 11.5f; // higher = less overshoot / faster settle
    // Linear acceleration (translation) drives the impulse: its direction is the shake direction,
    // so the wallpaper drifts the way the phone is moved. A still phone reads ~0.
    static final float SHAKE_ACCEL_TRIGGER = 1.2f; // m/s^2 below which movement is ignored
    static final float SHAKE_ACCEL_IMPULSE = 46f; // accel magnitude -> spring velocity gain
    static final float SHAKE_IMPULSE_CLAMP = 2600f; // per-event spring velocity clamp (px/s)
    // Suppresses the weaker axis of each impulse so the drift travels in a straight line along the
    // shake direction. A symmetric diagonal (right-up / left-down) keeps both axes; a mostly-
    // horizontal or -vertical shove has its small perpendicular jitter trimmed away.
    static final float SHAKE_AXIS_STRAIGHTEN = 0.5f;
    // Optical depth units. At "slightly stronger", five degrees maps to background 5 and UI
    // 0.65dp. The cut-out subject is deliberately quieter at 1.5dp to keep it from looking loose.
    static final float SPATIAL_BACKGROUND_X_DP = 4.0f;
    static final float SPATIAL_BACKGROUND_Y_DP = 4.0f;
    static final float SPATIAL_MIDGROUND_TO_BACKGROUND_RATIO = 0.30f;
    static final float SPATIAL_FOREGROUND_TO_BACKGROUND_RATIO = 0.13f;
    static final float SPATIAL_CONTENT_X_DP = 3.0f;
    static final float SPATIAL_CONTENT_Y_DP = 2.2f;
    static final float SPATIAL_INPUT_X_DP = 4.0f;
    static final float SPATIAL_INPUT_Y_DP = 2.7f;
    // Reserved for the disabled child-level Compose experiment. The active implementation does
    // not register these modifiers; it translates one host root without rotation or scaling.
    static final float SPATIAL_CONTENT_ROTATION_DEGREES = 0.16f;
    static final float SPATIAL_INPUT_ROTATION_DEGREES = 0.20f;
    static final float SPATIAL_CONTENT_BASE_SCALE = 1.0015f;
    static final float SPATIAL_INPUT_EXTRA_BASE_SCALE = 1.00075f;
    // The oversized backing canvas and CLAMP edge fill provide bleed. Do not additionally zoom
    // the user's picture; that made the imported crop look different as soon as spatial mode ran.
    static final float SPATIAL_BACKGROUND_SCALE = 1.0f;
    // Exceeds the spherical projection's worst possible per-axis travel, even at the strong
    // preset. The derived edge extension remains the final fallback.
    static final float SPATIAL_CANVAS_HEADROOM_DP = 128.0f;
    private static final long MAX_IMAGE_PIXELS = 100_000_000L;
    private static final long MAX_DECODE_PIXELS = 8_000_000L;
    private static final String OVERLAY_TAG = "deekseep_chat_appearance_overlay_v1";

    private static final Object CONFIG_LOCK = new Object();
    private static volatile Config cached;
    private static volatile RuntimeOverlay currentOverlay;
    private static volatile WeakReference<Activity> currentActivityRef =
            new WeakReference<>(null);
    private static volatile String currentRoute;
    private static volatile float sidebarProgress;
    private static volatile float sidebarRawProgress;
    private static final CopyOnWriteArrayList<SpatialPoseListener>
            SPATIAL_POSE_LISTENERS = new CopyOnWriteArrayList<>();
    // Compose snapshots can retain prior state values briefly. A 128-entry ring avoids allocating
    // a pose every frame while leaving more than one second before a slot is reused at 120 Hz.
    private static final SpatialPose[] SPATIAL_POSE_POOL =
            new SpatialPose[128];
    private static int spatialPosePoolIndex;
    private static volatile SpatialPose currentSpatialPose = SpatialPose.DISABLED;

    static {
        for (int i = 0; i < SPATIAL_POSE_POOL.length; i++) {
            SPATIAL_POSE_POOL[i] = new SpatialPose(true, 0f, 0f);
        }
    }

    private ChatAppearance() {}

    static final class SpatialPose {
        static final SpatialPose DISABLED = new SpatialPose(false, 0f, 0f);
        static final SpatialPose CENTERED = new SpatialPose(true, 0f, 0f);

        boolean active;
        float x;
        float y;

        SpatialPose(boolean active, float x, float y) {
            set(active, x, y);
        }

        void set(boolean active, float x, float y) {
            this.active = active;
            // Preserve the complete optical projection. Clamping the shared pose here made every
            // layer hit an artificial wall even though the sensor/controller itself kept moving.
            this.x = finiteOrZero(x);
            this.y = finiteOrZero(y);
        }

        @Override public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof SpatialPose)) return false;
            SpatialPose other = (SpatialPose) value;
            return active == other.active
                    && Float.floatToIntBits(x) == Float.floatToIntBits(other.x)
                    && Float.floatToIntBits(y) == Float.floatToIntBits(other.y);
        }

        @Override public int hashCode() {
            int result = active ? 1 : 0;
            result = result * 31 + Float.floatToIntBits(x);
            return result * 31 + Float.floatToIntBits(y);
        }
    }

    interface SpatialPoseListener {
        void onSpatialPose(SpatialPose pose);
    }

    static void registerSpatialPoseListener(SpatialPoseListener listener) {
        if (listener == null) return;
        SPATIAL_POSE_LISTENERS.addIfAbsent(listener);
        listener.onSpatialPose(currentSpatialPose);
    }

    static void unregisterSpatialPoseListener(SpatialPoseListener listener) {
        if (listener != null) SPATIAL_POSE_LISTENERS.remove(listener);
    }

    static SpatialPose currentSpatialPose() {
        return currentSpatialPose;
    }

    static final class Sticker {
        String id = "";
        String file = "";
        float x = 0.5f;
        float y = 0.5f;
        float size = 0.24f;
        float opacity = 1f;
        float rotation = 0f;

        Sticker copy() {
            Sticker out = new Sticker();
            out.id = id;
            out.file = file;
            out.x = x;
            out.y = y;
            out.size = size;
            out.opacity = opacity;
            out.rotation = rotation;
            return out;
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("id", id);
                json.put("file", file);
                json.put("x", x);
                json.put("y", y);
                json.put("size", size);
                json.put("opacity", opacity);
                json.put("rotation", rotation);
            } catch (Throwable ignored) {}
            return json;
        }

        static Sticker fromJson(JSONObject json) {
            Sticker out = new Sticker();
            if (json == null) return out;
            out.id = json.optString("id", "");
            out.file = safeAssetName(json.optString("file", ""));
            out.x = (float) json.optDouble("x", 0.5d);
            out.y = (float) json.optDouble("y", 0.5d);
            out.size = (float) json.optDouble("size", 0.24d);
            out.opacity = (float) json.optDouble("opacity", 1d);
            out.rotation = (float) json.optDouble("rotation", 0d);
            out.sanitize();
            return out;
        }

        void sanitize() {
            if (id == null || id.trim().length() == 0) id = UUID.randomUUID().toString();
            file = safeAssetName(file);
            x = clamp(x, 0f, 1f);
            y = clamp(y, 0f, 1f);
            size = clamp(size, 0.08f, 0.65f);
            opacity = clamp(opacity, 0f, 1f);
            rotation = normalizeRotation(rotation);
        }
    }

    static final class BubbleStyle {
        String preset = "glass";
        float opacity = 0.78f;
        float radius = 22f;
        float borderWidth = 1f;
        String decorationFile = "";
        float decorationSize = 40f;
        float decorationX = 0.82f;
        float decorationOpacity = 1f;
        float decorationRotation;

        BubbleStyle copy() {
            BubbleStyle out = new BubbleStyle();
            out.preset = preset;
            out.opacity = opacity;
            out.radius = radius;
            out.borderWidth = borderWidth;
            out.decorationFile = decorationFile;
            out.decorationSize = decorationSize;
            out.decorationX = decorationX;
            out.decorationOpacity = decorationOpacity;
            out.decorationRotation = decorationRotation;
            return out;
        }

        boolean hasCustomization() {
            return !"original".equals(preset) || decorationFile.length() > 0;
        }

        boolean hasDecoration() {
            return decorationFile.length() > 0;
        }

        void sanitize() {
            if (!"original".equals(preset) && !"soft".equals(preset)
                    && !"outline".equals(preset) && !"glass".equals(preset)
                    && !"liquid".equals(preset)) {
                preset = "glass";
            }
            opacity = clamp(opacity, 0f, 1f);
            radius = clamp(radius, 0f, MAX_BUBBLE_RADIUS);
            borderWidth = clamp(borderWidth, 0f, MAX_BUBBLE_BORDER);
            decorationFile = safeAssetName(decorationFile);
            decorationSize = clamp(decorationSize,
                    MIN_BUBBLE_DECORATION_SIZE, MAX_BUBBLE_DECORATION_SIZE);
            decorationX = clamp(decorationX, 0f, 1f);
            decorationOpacity = clamp(decorationOpacity, 0f, 1f);
            decorationRotation = normalizeRotation(decorationRotation);
        }

        JSONObject toJson() {
            sanitize();
            JSONObject json = new JSONObject();
            try {
                json.put("preset", preset);
                json.put("opacity", opacity);
                json.put("radius", radius);
                json.put("border_width", borderWidth);
                json.put("decoration_file", decorationFile);
                json.put("decoration_size", decorationSize);
                json.put("decoration_x", decorationX);
                json.put("decoration_opacity", decorationOpacity);
                json.put("decoration_rotation", decorationRotation);
            } catch (Throwable ignored) {}
            return json;
        }

        static BubbleStyle fromJson(JSONObject json) {
            BubbleStyle out = new BubbleStyle();
            if (json == null) return out;
            out.preset = json.optString("preset", "glass");
            out.opacity = (float) json.optDouble("opacity", 0.78d);
            out.radius = (float) json.optDouble("radius", 22d);
            out.borderWidth = (float) json.optDouble("border_width", 1d);
            out.decorationFile = json.optString("decoration_file", "");
            out.decorationSize = (float) json.optDouble("decoration_size", 40d);
            out.decorationX = (float) json.optDouble("decoration_x", 0.82d);
            out.decorationOpacity =
                    (float) json.optDouble("decoration_opacity", 1d);
            out.decorationRotation =
                    (float) json.optDouble("decoration_rotation", 0d);
            out.sanitize();
            return out;
        }
    }

    static final class Config {
        int version = 10;
        boolean enabled;
        String backgroundFile = "";
        float backgroundOpacity = 0.24f;
        String backgroundMode = "crop";
        String backgroundExtent = "full";
        String backgroundEdgeMode = "clip";
        float backgroundScale = 1f;
        float backgroundRotation;
        float backgroundFocusX = 0.5f;
        float backgroundFocusY = 0.5f;
        boolean depthEnabled = true;
        boolean motionEnabled = true;
        float motionAmount = DEFAULT_MOTION_AMOUNT;
        boolean perScreenMotionEnabled;
        float chatMotionAmount;
        float sidebarMotionAmount = DEFAULT_MOTION_AMOUNT;
        float settingsMotionAmount = -DEFAULT_MOTION_AMOUNT;
        boolean backgroundOnChat = true;
        boolean backgroundOnSidebar = true;
        boolean backgroundOnSettings = true;
        boolean liquidGlassEnabled;
        String glassQuality = "auto";
        boolean shakeParallaxEnabled;
        boolean spatialDepthEnabled;
        String spatialStrength = "standard";
        boolean spatialReduceMotion;
        boolean spatialAutoRecenter = true;
        float spatialDirectionMultiplier = 1f;
        boolean spatialEdgeExtendEnabled = true;
        boolean bubbleEnabled;
        BubbleStyle userBubble = new BubbleStyle();
        BubbleStyle assistantBubble = new BubbleStyle();
        final ArrayList<Sticker> stickers = new ArrayList<>();

        Config copy() {
            Config out = new Config();
            out.version = version;
            out.enabled = enabled;
            out.backgroundFile = backgroundFile;
            out.backgroundOpacity = backgroundOpacity;
            out.backgroundMode = backgroundMode;
            out.backgroundExtent = backgroundExtent;
            out.backgroundEdgeMode = backgroundEdgeMode;
            out.backgroundScale = backgroundScale;
            out.backgroundRotation = backgroundRotation;
            out.backgroundFocusX = backgroundFocusX;
            out.backgroundFocusY = backgroundFocusY;
            out.depthEnabled = depthEnabled;
            out.motionEnabled = motionEnabled;
            out.motionAmount = motionAmount;
            out.perScreenMotionEnabled = perScreenMotionEnabled;
            out.chatMotionAmount = chatMotionAmount;
            out.sidebarMotionAmount = sidebarMotionAmount;
            out.settingsMotionAmount = settingsMotionAmount;
            out.backgroundOnChat = backgroundOnChat;
            out.backgroundOnSidebar = backgroundOnSidebar;
            out.backgroundOnSettings = backgroundOnSettings;
            out.liquidGlassEnabled = liquidGlassEnabled;
            out.glassQuality = glassQuality;
            out.shakeParallaxEnabled = shakeParallaxEnabled;
            out.spatialDepthEnabled = spatialDepthEnabled;
            out.spatialStrength = spatialStrength;
            out.spatialReduceMotion = spatialReduceMotion;
            out.spatialAutoRecenter = spatialAutoRecenter;
            out.spatialDirectionMultiplier = spatialDirectionMultiplier;
            out.spatialEdgeExtendEnabled = spatialEdgeExtendEnabled;
            out.bubbleEnabled = bubbleEnabled;
            out.userBubble = userBubble == null ? new BubbleStyle() : userBubble.copy();
            out.assistantBubble =
                    assistantBubble == null ? new BubbleStyle() : assistantBubble.copy();
            for (Sticker sticker : stickers) out.stickers.add(sticker.copy());
            return out;
        }

        boolean hasVisuals() {
            return backgroundFile.length() > 0 || !stickers.isEmpty();
        }

        boolean hasBoundBackground() {
            return backgroundFile.length() > 0
                    && (backgroundOnChat || backgroundOnSidebar || backgroundOnSettings);
        }

        float maxMotionMagnitude() {
            if (!motionEnabled) return 0f;
            if (!perScreenMotionEnabled) return motionAmount;
            return Math.max(Math.abs(chatMotionAmount),
                    Math.max(Math.abs(sidebarMotionAmount),
                            Math.abs(settingsMotionAmount)));
        }

        float motionFraction(boolean settings, float drawerProgress) {
            if (!motionEnabled) return 0f;
            if (!perScreenMotionEnabled) {
                return settings ? -motionAmount : motionAmount * drawerProgress;
            }
            if (settings) return settingsMotionAmount;
            return chatMotionAmount
                    + (sidebarMotionAmount - chatMotionAmount) * drawerProgress;
        }

        Sticker sticker(String id) {
            if (id == null) return null;
            for (Sticker sticker : stickers) {
                if (id.equals(sticker.id)) return sticker;
            }
            return null;
        }

        BubbleStyle bubble(boolean user) {
            return user ? userBubble : assistantBubble;
        }

        void sanitize() {
            version = 10;
            backgroundFile = safeAssetName(backgroundFile);
            backgroundOpacity = clamp(backgroundOpacity, 0f, 1f);
            if (Float.isNaN(backgroundScale) || Float.isInfinite(backgroundScale)
                    || backgroundScale <= 0f) {
                backgroundScale = 1f;
            } else {
                // Deliberately no upper clamp: the editor uses a relative zoom control that can
                // be repeated indefinitely. Rendering still guards float overflow internally.
                backgroundScale = Math.max(MIN_BACKGROUND_SCALE, backgroundScale);
            }
            backgroundRotation = normalizeRotation(backgroundRotation);
            backgroundFocusX = clamp(backgroundFocusX, 0f, 1f);
            backgroundFocusY = clamp(backgroundFocusY, 0f, 1f);
            motionAmount = clamp(motionAmount, 0f, MAX_MOTION_AMOUNT);
            chatMotionAmount =
                    clamp(chatMotionAmount, -MAX_MOTION_AMOUNT, MAX_MOTION_AMOUNT);
            sidebarMotionAmount =
                    clamp(sidebarMotionAmount, -MAX_MOTION_AMOUNT, MAX_MOTION_AMOUNT);
            settingsMotionAmount =
                    clamp(settingsMotionAmount, -MAX_MOTION_AMOUNT, MAX_MOTION_AMOUNT);
            if (!"fit".equals(backgroundMode) && !"stretch".equals(backgroundMode)) {
                backgroundMode = "crop";
            }
            if (!"half_top".equals(backgroundExtent)
                    && !"half_center".equals(backgroundExtent)
                    && !"half_bottom".equals(backgroundExtent)) {
                backgroundExtent = "full";
            }
            if (!"mirror".equals(backgroundEdgeMode)
                    && !"extend".equals(backgroundEdgeMode)) {
                backgroundEdgeMode = "clip";
            }
            if (!"high".equals(glassQuality) && !"balanced".equals(glassQuality)
                    && !"saver".equals(glassQuality)) {
                glassQuality = "auto";
            }
            if (!"weak".equals(spatialStrength)
                    && !"strong".equals(spatialStrength)) {
                spatialStrength = "standard";
            }
            spatialDirectionMultiplier =
                    spatialDirectionMultiplier < 0f ? -1f : 1f;
            // Both features own the same physical scene. Keeping them mutually exclusive avoids
            // adding an impulse spring to a live attitude transform and exposing wallpaper edges.
            if (spatialDepthEnabled) shakeParallaxEnabled = false;
            if (userBubble == null) userBubble = new BubbleStyle();
            if (assistantBubble == null) assistantBubble = new BubbleStyle();
            userBubble.sanitize();
            assistantBubble.sanitize();
            ArrayList<Sticker> clean = new ArrayList<>();
            for (Sticker sticker : stickers) {
                if (sticker == null) continue;
                sticker.sanitize();
                if (sticker.file.length() == 0) continue;
                boolean duplicate = false;
                for (Sticker existing : clean) {
                    if (existing.id.equals(sticker.id)) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate && clean.size() < MAX_STICKERS) clean.add(sticker);
            }
            stickers.clear();
            stickers.addAll(clean);
        }

        JSONObject toJson() {
            sanitize();
            JSONObject json = new JSONObject();
            JSONArray stickerJson = new JSONArray();
            for (Sticker sticker : stickers) stickerJson.put(sticker.toJson());
            try {
                json.put("version", version);
                json.put("enabled", enabled);
                json.put("background_file", backgroundFile);
                json.put("background_opacity", backgroundOpacity);
                json.put("background_mode", backgroundMode);
                json.put("background_extent", backgroundExtent);
                json.put("background_edge_mode", backgroundEdgeMode);
                json.put("background_scale", backgroundScale);
                json.put("background_rotation", backgroundRotation);
                json.put("background_focus_x", backgroundFocusX);
                json.put("background_focus_y", backgroundFocusY);
                json.put("depth_enabled", depthEnabled);
                json.put("motion_enabled", motionEnabled);
                json.put("motion_amount", motionAmount);
                json.put("per_screen_motion_enabled", perScreenMotionEnabled);
                json.put("chat_motion_amount", chatMotionAmount);
                json.put("sidebar_motion_amount", sidebarMotionAmount);
                json.put("settings_motion_amount", settingsMotionAmount);
                json.put("background_on_chat", backgroundOnChat);
                json.put("background_on_sidebar", backgroundOnSidebar);
                json.put("background_on_settings", backgroundOnSettings);
                json.put("liquid_glass_enabled", liquidGlassEnabled);
                json.put("glass_quality", glassQuality);
                json.put("shake_parallax_enabled", shakeParallaxEnabled);
                json.put("spatial_depth_enabled", spatialDepthEnabled);
                json.put("spatial_strength", spatialStrength);
                json.put("spatial_reduce_motion", spatialReduceMotion);
                json.put("spatial_auto_recenter", spatialAutoRecenter);
                json.put("spatial_direction_multiplier",
                        spatialDirectionMultiplier);
                json.put("spatial_edge_extend_enabled",
                        spatialEdgeExtendEnabled);
                json.put("bubble_enabled", bubbleEnabled);
                json.put("user_bubble", userBubble.toJson());
                json.put("assistant_bubble", assistantBubble.toJson());
                json.put("stickers", stickerJson);
            } catch (Throwable ignored) {}
            return json;
        }

        static Config fromJson(String value) {
            Config out = new Config();
            if (value == null || value.trim().length() == 0) return out;
            try {
                JSONObject json = new JSONObject(value);
                out.version = json.optInt("version", 1);
                out.enabled = json.optBoolean("enabled", false);
                out.backgroundFile = json.optString("background_file", "");
                out.backgroundOpacity =
                        (float) json.optDouble("background_opacity", 0.24d);
                out.backgroundMode = json.optString("background_mode", "crop");
                out.backgroundExtent =
                        json.optString("background_extent", "full");
                out.backgroundEdgeMode =
                        json.optString("background_edge_mode", "clip");
                out.backgroundScale =
                        (float) json.optDouble("background_scale", 1d);
                out.backgroundRotation =
                        (float) json.optDouble("background_rotation", 0d);
                out.backgroundFocusX =
                        (float) json.optDouble("background_focus_x", 0.5d);
                out.backgroundFocusY =
                        (float) json.optDouble("background_focus_y", 0.5d);
                out.depthEnabled = json.optBoolean("depth_enabled", true);
                out.motionEnabled = json.optBoolean("motion_enabled", true);
                out.motionAmount = (float) json.optDouble(
                        "motion_amount", DEFAULT_MOTION_AMOUNT);
                out.perScreenMotionEnabled =
                        json.optBoolean("per_screen_motion_enabled", false);
                out.chatMotionAmount =
                        (float) json.optDouble("chat_motion_amount", 0d);
                out.sidebarMotionAmount = (float) json.optDouble(
                        "sidebar_motion_amount", DEFAULT_MOTION_AMOUNT);
                out.settingsMotionAmount = (float) json.optDouble(
                        "settings_motion_amount", -DEFAULT_MOTION_AMOUNT);
                out.backgroundOnChat = json.optBoolean("background_on_chat", true);
                out.backgroundOnSidebar =
                        json.optBoolean("background_on_sidebar", true);
                out.backgroundOnSettings =
                        json.optBoolean("background_on_settings", true);
                out.liquidGlassEnabled =
                        json.optBoolean("liquid_glass_enabled", false);
                out.glassQuality = json.optString("glass_quality", "auto");
                out.shakeParallaxEnabled =
                        json.optBoolean("shake_parallax_enabled", false);
                out.spatialDepthEnabled =
                        json.optBoolean("spatial_depth_enabled", false);
                out.spatialStrength =
                        json.optString("spatial_strength", "standard");
                out.spatialReduceMotion =
                        json.optBoolean("spatial_reduce_motion", false);
                out.spatialAutoRecenter =
                        json.optBoolean("spatial_auto_recenter", true);
                out.spatialDirectionMultiplier = (float) json.optDouble(
                        "spatial_direction_multiplier", 1d);
                out.spatialEdgeExtendEnabled = json.optBoolean(
                        "spatial_edge_extend_enabled", true);
                out.bubbleEnabled = json.optBoolean("bubble_enabled", false);
                out.userBubble = BubbleStyle.fromJson(
                        json.optJSONObject("user_bubble"));
                out.assistantBubble = BubbleStyle.fromJson(
                        json.optJSONObject("assistant_bubble"));
                JSONArray stickers = json.optJSONArray("stickers");
                if (stickers != null) {
                    for (int i = 0; i < stickers.length() && i < MAX_STICKERS; i++) {
                        JSONObject item = stickers.optJSONObject(i);
                        if (item != null) out.stickers.add(Sticker.fromJson(item));
                    }
                }
            } catch (Throwable t) {
                Main.log("appearance config parse failed: " + t);
            }
            out.sanitize();
            return out;
        }
    }

    static final class ImportResult {
        final boolean ok;
        final String message;
        final String stickerId;

        ImportResult(boolean ok, String message, String stickerId) {
            this.ok = ok;
            this.message = message == null ? "" : message;
            this.stickerId = stickerId;
        }
    }

    static Config load() {
        synchronized (CONFIG_LOCK) {
            if (cached == null) cached = readConfig();
            return cached.copy();
        }
    }

    /**
     * Returns only the tiny immutable-for-this-render snapshot needed by the Compose hooks.
     * Message rows are composed frequently, so avoid copying the wallpaper and page-sticker
     * collections for every bubble.
     */
    static BubbleStyle bubbleStyleForRender(boolean user) {
        synchronized (CONFIG_LOCK) {
            if (cached == null) cached = readConfig();
            BubbleStyle configured = cached.bubble(user);
            if (cached.liquidGlassEnabled) {
                BubbleStyle style = configured == null
                        ? new BubbleStyle() : configured.copy();
                if (!cached.bubbleEnabled) {
                    style.opacity = user ? 0.68f : 0.60f;
                    style.radius = user ? 22f : 18f;
                    style.borderWidth = 0.8f;
                    style.decorationFile = "";
                }
                // The global switch is authoritative for material while retaining the user's
                // independent radius, opacity and decoration layout.
                style.preset = "liquid";
                style.sanitize();
                return style;
            }
            if (!cached.bubbleEnabled || configured == null
                    || !configured.hasCustomization()) {
                return null;
            }
            return configured.copy();
        }
    }

    static boolean glassEnabledForRender() {
        synchronized (CONFIG_LOCK) {
            if (cached == null) cached = readConfig();
            return cached.liquidGlassEnabled;
        }
    }

    static boolean spatialDepthEnabledForRender() {
        synchronized (CONFIG_LOCK) {
            if (cached == null) cached = readConfig();
            return cached.spatialDepthEnabled;
        }
    }

    static float spatialStrengthMultiplier(String value) {
        if ("weak".equals(value)) return 0.55f;
        if ("strong".equals(value)) return 1.25f;
        return 1f;
    }

    static void recenterSpatialMotion() {
        RuntimeOverlay overlay = currentOverlay;
        if (overlay != null) overlay.recenterSpatialMotion();
    }

    static boolean save(Config config) {
        if (config == null) return false;
        Config clean = config.copy();
        clean.sanitize();
        synchronized (CONFIG_LOCK) {
            File root = new File(ROOT_DIR);
            if (!root.exists() && !root.mkdirs()) return false;
            AtomicFile atomic = new AtomicFile(new File(CONFIG_FILE));
            FileOutputStream output = null;
            try {
                output = atomic.startWrite();
                output.write(clean.toJson().toString(2).getBytes(StandardCharsets.UTF_8));
                output.flush();
                atomic.finishWrite(output);
                cached = clean;
            } catch (Throwable t) {
                if (output != null) atomic.failWrite(output);
                Main.log("appearance config save failed: " + t);
                return false;
            }
        }
        refresh();
        return true;
    }

    private static Config readConfig() {
        File file = new File(CONFIG_FILE);
        File backup = new File(CONFIG_FILE + ".bak");
        if (!file.isFile() && !backup.isFile()) return new Config();
        FileInputStream input = null;
        try {
            input = new AtomicFile(file).openRead();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            while (true) {
                int read = input.read(buffer);
                if (read < 0) break;
                total += read;
                if (total > 1024 * 1024) {
                    Main.log("appearance config rejected: larger than 1 MB");
                    return new Config();
                }
                bytes.write(buffer, 0, read);
            }
            return Config.fromJson(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } catch (Throwable t) {
            Main.log("appearance config read failed: " + t);
            return new Config();
        } finally {
            if (input != null) try { input.close(); } catch (Throwable ignored) {}
        }
    }

    static ImportResult importImage(Activity activity, Uri uri, boolean background) {
        return importImage(activity, uri, background ? 0 : 1);
    }

    static ImportResult importBubbleDecoration(
            Activity activity, Uri uri, boolean user) {
        return importImage(activity, uri, user ? 2 : 3);
    }

    static ImportResult saveStickerCutout(String stickerId, Bitmap bitmap) {
        if (stickerId == null || bitmap == null
                || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return new ImportResult(false, "没有可保存的抠图结果", null);
        }
        synchronized (CONFIG_LOCK) {
            Config config = cached == null ? readConfig() : cached.copy();
            Sticker sticker = config.sticker(stickerId);
            if (sticker == null) {
                return new ImportResult(false, "找不到要编辑的贴纸", null);
            }
            String old = sticker.file;
            String name = writeCutoutBitmapLocked(bitmap, "cutout_sticker_");
            if (name == null) {
                return new ImportResult(false, "抠图结果保存失败", null);
            }
            sticker.file = name;
            config.enabled = true;
            if (!writeConfigLocked(config)) {
                new File(ASSET_DIR, name).delete();
                return new ImportResult(false, "抠图设置保存失败", null);
            }
            if (old.length() > 0 && !isAssetUsed(config, old)) {
                new File(ASSET_DIR, safeAssetName(old)).delete();
            }
        }
        refresh();
        return new ImportResult(true, "贴纸抠图已保存", stickerId);
    }

    static ImportResult saveBubbleCutout(boolean user, Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return new ImportResult(false, "没有可保存的抠图结果", null);
        }
        synchronized (CONFIG_LOCK) {
            Config config = cached == null ? readConfig() : cached.copy();
            BubbleStyle bubble = config.bubble(user);
            if (bubble == null || !bubble.hasDecoration()) {
                return new ImportResult(false, "尚未导入气泡贴纸", null);
            }
            String old = bubble.decorationFile;
            String name = writeCutoutBitmapLocked(
                    bitmap, user ? "cutout_bubble_user_" : "cutout_bubble_ai_");
            if (name == null) {
                return new ImportResult(false, "抠图结果保存失败", null);
            }
            bubble.decorationFile = name;
            config.bubbleEnabled = true;
            if (!writeConfigLocked(config)) {
                new File(ASSET_DIR, name).delete();
                return new ImportResult(false, "抠图设置保存失败", null);
            }
            if (old.length() > 0 && !isAssetUsed(config, old)) {
                new File(ASSET_DIR, safeAssetName(old)).delete();
            }
        }
        refresh();
        return new ImportResult(
                true,
                user ? "用户气泡贴纸抠图已保存" : "DeepSeek 气泡贴纸抠图已保存",
                null);
    }

    private static String writeCutoutBitmapLocked(Bitmap bitmap, String prefix) {
        File assets = new File(ASSET_DIR);
        if (!assets.exists() && !assets.mkdirs()) return null;
        String name = prefix + System.currentTimeMillis() + "_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8)
                + ".png";
        File target = new File(assets, name);
        FileOutputStream output = null;
        boolean success = false;
        try {
            output = new FileOutputStream(target, false);
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                return null;
            }
            output.flush();
            try { output.getFD().sync(); } catch (Throwable ignored) {}
            success = true;
            return name;
        } catch (Throwable t) {
            Main.log("cutout bitmap save failed: " + t);
            return null;
        } finally {
            if (output != null) try { output.close(); } catch (Throwable ignored) {}
            if (!success || (target.isFile() && target.length() == 0L)) {
                target.delete();
            }
        }
    }

    private static ImportResult importImage(Activity activity, Uri uri, int role) {
        if (activity == null || uri == null) {
            return new ImportResult(false, "没有选择图片", null);
        }
        boolean background = role == 0;
        boolean pageSticker = role == 1;
        boolean userBubbleDecoration = role == 2;
        synchronized (CONFIG_LOCK) {
            Config config = cached == null ? readConfig() : cached.copy();
            if (pageSticker && config.stickers.size() >= MAX_STICKERS) {
                return new ImportResult(false, "最多只能添加 " + MAX_STICKERS + " 张贴纸", null);
            }
            File assets = new File(ASSET_DIR);
            if (!assets.exists() && !assets.mkdirs()) {
                return new ImportResult(false, "无法创建外观图片目录", null);
            }
            String extension = imageExtension(activity.getContentResolver(), uri);
            String prefix = background ? "background_"
                    : (pageSticker ? "sticker_"
                    : (userBubbleDecoration ? "bubble_user_" : "bubble_ai_"));
            String name = prefix + System.currentTimeMillis() + "_"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8)
                    + "." + extension;
            File target = new File(assets, name);
            String error = copyAndValidateImage(activity.getContentResolver(), uri, target);
            if (error != null) {
                if (target.exists()) target.delete();
                return new ImportResult(false, error, null);
            }

            String obsolete = null;
            String stickerId = null;
            if (background) {
                obsolete = config.backgroundFile;
                config.backgroundFile = name;
                config.enabled = true;
            } else if (pageSticker) {
                Sticker sticker = new Sticker();
                sticker.id = UUID.randomUUID().toString();
                sticker.file = name;
                int index = config.stickers.size();
                sticker.x = clamp(0.5f + ((index % 3) - 1) * 0.12f, 0.12f, 0.88f);
                sticker.y = clamp(0.42f + (index % 4) * 0.09f, 0.12f, 0.88f);
                sticker.size = 0.24f;
                sticker.opacity = 1f;
                config.stickers.add(sticker);
                stickerId = sticker.id;
                config.enabled = true;
            } else {
                BubbleStyle bubble =
                        userBubbleDecoration ? config.userBubble : config.assistantBubble;
                obsolete = bubble.decorationFile;
                bubble.decorationFile = name;
                config.bubbleEnabled = true;
            }
            if (!writeConfigLocked(config)) {
                target.delete();
                return new ImportResult(false, "外观设置保存失败", null);
            }
            if (obsolete != null && obsolete.length() > 0
                    && !isAssetUsed(config, obsolete)) {
                new File(assets, safeAssetName(obsolete)).delete();
            }
            final String importedId = stickerId;
            refresh();
            String message = background ? "背景图已导入"
                    : (pageSticker ? "贴纸已添加"
                    : (userBubbleDecoration
                    ? "用户气泡贴纸已导入" : "DeepSeek 气泡贴纸已导入"));
            return new ImportResult(true, message, importedId);
        }
    }

    private static boolean writeConfigLocked(Config config) {
        Config clean = config.copy();
        clean.sanitize();
        File root = new File(ROOT_DIR);
        if (!root.exists() && !root.mkdirs()) return false;
        AtomicFile atomic = new AtomicFile(new File(CONFIG_FILE));
        FileOutputStream output = null;
        try {
            output = atomic.startWrite();
            output.write(clean.toJson().toString(2).getBytes(StandardCharsets.UTF_8));
            output.flush();
            atomic.finishWrite(output);
            cached = clean;
            return true;
        } catch (Throwable t) {
            if (output != null) atomic.failWrite(output);
            Main.log("appearance config save failed: " + t);
            return false;
        }
    }

    static boolean removeBackground() {
        synchronized (CONFIG_LOCK) {
            Config config = cached == null ? readConfig() : cached.copy();
            String old = config.backgroundFile;
            config.backgroundFile = "";
            if (!writeConfigLocked(config)) return false;
            if (old.length() > 0 && !isAssetUsed(config, old)) {
                new File(ASSET_DIR, safeAssetName(old)).delete();
            }
        }
        refresh();
        return true;
    }

    static boolean removeSticker(String id) {
        if (id == null) return false;
        synchronized (CONFIG_LOCK) {
            Config config = cached == null ? readConfig() : cached.copy();
            Sticker removed = null;
            for (Sticker sticker : new ArrayList<>(config.stickers)) {
                if (id.equals(sticker.id)) {
                    removed = sticker;
                    config.stickers.remove(sticker);
                    break;
                }
            }
            if (removed == null) return false;
            if (!writeConfigLocked(config)) return false;
            if (!isAssetUsed(config, removed.file)) {
                new File(ASSET_DIR, safeAssetName(removed.file)).delete();
            }
        }
        refresh();
        return true;
    }

    static boolean removeBubbleDecoration(boolean user) {
        synchronized (CONFIG_LOCK) {
            Config config = cached == null ? readConfig() : cached.copy();
            BubbleStyle bubble = user ? config.userBubble : config.assistantBubble;
            String old = bubble.decorationFile;
            if (old.length() == 0) return true;
            bubble.decorationFile = "";
            if (!writeConfigLocked(config)) return false;
            if (!isAssetUsed(config, old)) {
                new File(ASSET_DIR, safeAssetName(old)).delete();
            }
        }
        refresh();
        return true;
    }

    static boolean clearAll() {
        synchronized (CONFIG_LOCK) {
            Config old = cached == null ? readConfig() : cached.copy();
            Config empty = new Config();
            if (!writeConfigLocked(empty)) return false;
            ArrayList<String> files = new ArrayList<>();
            if (old.backgroundFile.length() > 0) files.add(old.backgroundFile);
            for (Sticker sticker : old.stickers) files.add(sticker.file);
            if (old.userBubble.decorationFile.length() > 0) {
                files.add(old.userBubble.decorationFile);
            }
            if (old.assistantBubble.decorationFile.length() > 0) {
                files.add(old.assistantBubble.decorationFile);
            }
            for (String name : files) {
                String safe = safeAssetName(name);
                if (safe.length() > 0) new File(ASSET_DIR, safe).delete();
            }
        }
        refresh();
        return true;
    }

    private static boolean isAssetUsed(Config config, String name) {
        String safe = safeAssetName(name);
        if (safe.length() == 0) return false;
        if (safe.equals(config.backgroundFile)) return true;
        if (safe.equals(config.userBubble.decorationFile)
                || safe.equals(config.assistantBubble.decorationFile)) {
            return true;
        }
        for (Sticker sticker : config.stickers) {
            if (safe.equals(sticker.file)) return true;
        }
        return false;
    }

    static int bubbleFillColor(BubbleStyle style, boolean user, boolean dark) {
        if (style == null || "original".equals(style.preset)) return 0;
        int rgb;
        float baseAlpha;
        if ("outline".equals(style.preset)) {
            rgb = user ? (dark ? 0xFF7894FF : 0xFF5578F6)
                    : (dark ? 0xFFDCE2F1 : 0xFF6E7480);
            baseAlpha = dark ? 0.10f : 0.06f;
        } else if ("soft".equals(style.preset)) {
            rgb = user ? (dark ? 0xFF2B3B61 : 0xFFE3EAFF)
                    : (dark ? 0xFF2B2D33 : 0xFFF1F2F5);
            baseAlpha = 0.96f;
        } else if ("liquid".equals(style.preset)) {
            // The refracted image is the material. Keep the semantic fill nearly colourless so
            // user and assistant bubbles do not acquire a separate blue slab underneath it.
            rgb = dark ? 0xFFF4F6FA : 0xFFFFFFFF;
            baseAlpha = glassEnabledForRender()
                    ? 0.025f
                    : (dark ? 0.40f : 0.34f);
        } else {
            rgb = user ? (dark ? 0xFF293852 : 0xFFEAF0FF)
                    : (dark ? 0xFF252B36 : 0xFFFFFFFF);
            baseAlpha = dark ? 0.72f : 0.66f;
        }
        return withAlpha(rgb, baseAlpha * style.opacity);
    }

    static int bubbleBorderColor(BubbleStyle style, boolean user, boolean dark) {
        if (style == null || "original".equals(style.preset)
                || style.borderWidth <= 0f) {
            return 0;
        }
        int rgb;
        float alpha;
        if ("outline".equals(style.preset)) {
            rgb = user ? 0xFF6D89F7 : (dark ? 0xFFDDE4F4 : 0xFF818894);
            alpha = 0.78f;
        } else if ("liquid".equals(style.preset)) {
            rgb = 0xFFFFFFFF;
            alpha = dark ? 0.15f : 0.18f;
        } else if ("glass".equals(style.preset)) {
            rgb = dark ? 0xFFFFFFFF : (user ? 0xFFADC0FF : 0xFFFFFFFF);
            alpha = dark ? 0.30f : 0.70f;
        } else {
            rgb = user ? 0xFF9FB4F7 : (dark ? 0xFFFFFFFF : 0xFFC7CAD2);
            alpha = dark ? 0.22f : 0.42f;
        }
        return withAlpha(rgb, alpha * Math.max(0.35f, style.opacity));
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.round(clamp(alpha, 0f, 1f) * 255f);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static String copyAndValidateImage(ContentResolver resolver, Uri uri, File target) {
        InputStream input = null;
        FileOutputStream output = null;
        try {
            input = resolver.openInputStream(uri);
            if (input == null) return "无法读取所选图片";
            output = new FileOutputStream(target, false);
            byte[] buffer = new byte[64 * 1024];
            long total = 0L;
            while (true) {
                int read = input.read(buffer);
                if (read < 0) break;
                total += read;
                if (total > MAX_IMPORT_BYTES) return "图片不能超过 32 MB";
                output.write(buffer, 0, read);
            }
            output.flush();
            try { output.getFD().sync(); } catch (Throwable ignored) {}
            if (total == 0L) return "所选图片为空";
        } catch (Throwable t) {
            Main.log("appearance image copy failed: " + t);
            return "复制图片失败";
        } finally {
            if (input != null) try { input.close(); } catch (Throwable ignored) {}
            if (output != null) try { output.close(); } catch (Throwable ignored) {}
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(target.getAbsolutePath(), bounds);
        long pixels = (long) bounds.outWidth * (long) bounds.outHeight;
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || pixels <= 0L) {
            return "所选文件不是可识别的图片";
        }
        if (bounds.outWidth > 20000 || bounds.outHeight > 20000
                || pixels > MAX_IMAGE_PIXELS) {
            return "图片尺寸过大，请选择较小的图片";
        }
        return null;
    }

    private static String imageExtension(ContentResolver resolver, Uri uri) {
        String mime = null;
        String displayName = null;
        Cursor cursor = null;
        try {
            mime = resolver.getType(uri);
            cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0 && !cursor.isNull(column)) displayName = cursor.getString(column);
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) try { cursor.close(); } catch (Throwable ignored) {}
        }
        if (mime != null) {
            String lower = mime.toLowerCase(Locale.US);
            if (lower.contains("png")) return "png";
            if (lower.contains("webp")) return "webp";
            if (lower.contains("gif")) return "gif";
            if (lower.contains("bmp")) return "bmp";
            if (lower.contains("heic") || lower.contains("heif")) return "heic";
            if (lower.contains("jpeg") || lower.contains("jpg")) return "jpg";
        }
        if (displayName != null) {
            int dot = displayName.lastIndexOf('.');
            if (dot >= 0 && dot + 1 < displayName.length()) {
                String ext = displayName.substring(dot + 1).toLowerCase(Locale.US);
                if ("png".equals(ext) || "webp".equals(ext) || "gif".equals(ext)
                        || "bmp".equals(ext) || "heic".equals(ext)
                        || "heif".equals(ext) || "jpeg".equals(ext)
                        || "jpg".equals(ext)) {
                    return "jpeg".equals(ext) ? "jpg" : ext;
                }
            }
        }
        return "img";
    }

    static File assetFile(String name) {
        String safe = safeAssetName(name);
        return safe.length() == 0 ? new File(ASSET_DIR, "__missing__")
                : new File(ASSET_DIR, safe);
    }

    static Bitmap loadBitmap(File file, int requestedWidth, int requestedHeight) {
        if (file == null || !file.isFile()) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int reqW = Math.max(1, requestedWidth);
            int reqH = Math.max(1, requestedHeight);
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= reqW
                    && bounds.outHeight / (sample * 2) >= reqH) {
                sample *= 2;
            }
            while ((long) Math.max(1, bounds.outWidth / sample)
                    * (long) Math.max(1, bounds.outHeight / sample)
                    > MAX_DECODE_PIXELS) {
                sample *= 2;
            }
            BitmapFactory.Options decode = new BitmapFactory.Options();
            decode.inSampleSize = Math.max(1, sample);
            decode.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), decode);
        } catch (Throwable t) {
            Main.log("appearance bitmap decode failed: " + t);
            return null;
        }
    }

    static ImageView.ScaleType scaleType(String mode) {
        if ("fit".equals(mode)) return ImageView.ScaleType.FIT_CENTER;
        if ("stretch".equals(mode)) return ImageView.ScaleType.FIT_XY;
        return ImageView.ScaleType.CENTER_CROP;
    }

    static int wallpaperViewportHeight(int fullHeight, String extent) {
        int height = Math.max(1, fullHeight);
        return "full".equals(extent) ? height : Math.max(1, (height + 1) / 2);
    }

    static int wallpaperViewportTop(int fullHeight, String extent) {
        int height = Math.max(1, fullHeight);
        int viewportHeight = wallpaperViewportHeight(height, extent);
        if ("half_center".equals(extent)) {
            return Math.max(0, (height - viewportHeight) / 2);
        }
        if ("half_bottom".equals(extent)) {
            return Math.max(0, height - viewportHeight);
        }
        return 0;
    }

    private static float renderBackgroundScale(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value <= 0f) return 1f;
        // One million times already maps a sub-pixel-sized source area across the entire display.
        // Saturating only the GPU matrix here prevents overflow while the saved/user-facing value
        // remains free of an arbitrary upper limit.
        return Math.max(MIN_BACKGROUND_SCALE, Math.min(1_000_000f, value));
    }

    static float wallpaperRenderScale(float value, boolean spatial) {
        // The user's matrix scale is authoritative in every mode. Spatial bleed is supplied by
        // the larger backing canvas and optional edge extension, never by replacing this value.
        return renderBackgroundScale(value);
    }

    static String wallpaperRenderMode(
            String configuredMode, boolean spatial) {
        if ("fit".equals(configuredMode)
                || "stretch".equals(configuredMode)) {
            return configuredMode;
        }
        return "crop";
    }

    static String wallpaperRenderEdgeMode(
            String configuredMode, boolean spatial,
            boolean spatialEdgeExtendEnabled) {
        String mode = configuredMode == null ? "clip" : configuredMode;
        if (spatial && spatialEdgeExtendEnabled) return "extend";
        return mode;
    }

    static float wallpaperTranslationX(
            boolean spatial, float baseOffset,
            float spatialOffset, float shakeOffset) {
        return baseOffset + (spatial ? spatialOffset : shakeOffset);
    }

    static void applyWallpaperPresentation(
            ImageView image, Bitmap bitmap, Config config, int viewWidth, int viewHeight) {
        applyWallpaperPresentation(
                image, bitmap, config, viewWidth, viewHeight, false);
    }

    private static void applyWallpaperPresentation(
            ImageView image, Bitmap bitmap, Config config,
            int viewWidth, int viewHeight, boolean transparentSubjectPlane) {
        if (image == null || bitmap == null || config == null) return;
        Drawable current = image.getDrawable();
        WallpaperDrawable drawable;
        if (current instanceof WallpaperDrawable
                && ((WallpaperDrawable) current).owns(
                        bitmap, transparentSubjectPlane)) {
            drawable = (WallpaperDrawable) current;
        } else {
            drawable = new WallpaperDrawable(
                    bitmap, transparentSubjectPlane);
            image.setImageDrawable(drawable);
        }
        drawable.update(config, viewWidth, viewHeight);
        // WallpaperDrawable has no intrinsic size, so FIT_XY gives it the exact ImageView bounds;
        // the drawable itself owns crop/fit/stretch, focus, free zoom, and edge sampling.
        image.setScaleType(ImageView.ScaleType.FIT_XY);
        image.setRotation(config.backgroundRotation);
        applyWallpaperDepth(
                image, !transparentSubjectPlane && config.depthEnabled);
        if (config.spatialDepthEnabled) {
            image.setScaleX(SPATIAL_BACKGROUND_SCALE);
            image.setScaleY(SPATIAL_BACKGROUND_SCALE);
        }
    }

    private static final class WallpaperDrawable extends Drawable {
        private final Bitmap bitmap;
        private final Paint paint = new Paint(
                Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private final Matrix matrix = new Matrix();
        private final android.graphics.BitmapShader mirrorShader;
        private final android.graphics.BitmapShader extendShader;
        private final boolean transparentEdges;
        private String mode = "crop";
        private String edgeMode = "clip";
        private float focusX = 0.5f;
        private float focusY = 0.5f;
        private float scale = 1f;
        private int viewportWidth;
        private int viewportHeight;

        WallpaperDrawable(Bitmap bitmap, boolean transparentEdges) {
            this.bitmap = bitmap;
            this.transparentEdges = transparentEdges;
            mirrorShader = new android.graphics.BitmapShader(
                    bitmap, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR);
            extendShader = new android.graphics.BitmapShader(
                    bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        }

        boolean owns(Bitmap value, boolean requestedTransparentEdges) {
            return bitmap == value
                    && transparentEdges == requestedTransparentEdges;
        }

        void update(
                Config config, int requestedViewportWidth,
                int requestedViewportHeight) {
            // Spatial motion changes only the camera offset. Display mode, crop focus and zoom
            // remain the user's live wallpaper settings and must never be replaced here.
            mode = wallpaperRenderMode(
                    config.backgroundMode,
                    config.spatialDepthEnabled);
            edgeMode = transparentEdges ? "clip"
                    : wallpaperRenderEdgeMode(
                            config.backgroundEdgeMode,
                            config.spatialDepthEnabled,
                            config.spatialEdgeExtendEnabled);
            focusX = config.backgroundFocusX;
            focusY = config.backgroundFocusY;
            scale = wallpaperRenderScale(
                    config.backgroundScale, config.spatialDepthEnabled);
            viewportWidth = Math.max(1, requestedViewportWidth);
            viewportHeight = Math.max(1, requestedViewportHeight);
            invalidateSelf();
        }

        @Override public void draw(Canvas canvas) {
            if (bitmap == null || bitmap.isRecycled() || getBounds().isEmpty()) return;
            int width = Math.max(1, getBounds().width());
            int height = Math.max(1, getBounds().height());
            int bitmapWidth = Math.max(1, bitmap.getWidth());
            int bitmapHeight = Math.max(1, bitmap.getHeight());

            float[] transform = wallpaperContentTransform(
                    bitmapWidth, bitmapHeight,
                    width, height,
                    viewportWidth, viewportHeight,
                    mode, scale, focusX, focusY);
            matrix.setScale(transform[0], transform[1]);
            matrix.postTranslate(
                    getBounds().left + transform[2],
                    getBounds().top + transform[3]);

            if ("mirror".equals(edgeMode)) {
                mirrorShader.setLocalMatrix(matrix);
                paint.setShader(mirrorShader);
                canvas.drawRect(getBounds(), paint);
            } else if ("extend".equals(edgeMode)) {
                // CLAMP repeats the nearest outermost source pixel into every exposed area.
                // A flat-color image border therefore extends as a seamless solid background.
                extendShader.setLocalMatrix(matrix);
                paint.setShader(extendShader);
                canvas.drawRect(getBounds(), paint);
            } else {
                // Canvas/viewport clipping discards everything beyond the chosen display area;
                // any space revealed by zooming below 100% remains transparent.
                paint.setShader(null);
                canvas.drawBitmap(bitmap, matrix, paint);
            }
        }

        @Override public void setAlpha(int alpha) {
            paint.setAlpha(Math.max(0, Math.min(255, alpha)));
            invalidateSelf();
        }

        @Override public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /**
     * Computes crop/fit/stretch against the visible viewport, not the oversized motion canvas.
     * The canvas exists only as bleed around that viewport and must not dilute focus or zoom.
     */
    static float[] wallpaperContentTransform(
            int bitmapWidth, int bitmapHeight,
            int canvasWidth, int canvasHeight,
            int requestedViewportWidth, int requestedViewportHeight,
            String mode, float userScale, float focusX, float focusY) {
        int sourceWidth = Math.max(1, bitmapWidth);
        int sourceHeight = Math.max(1, bitmapHeight);
        int fullWidth = Math.max(1, canvasWidth);
        int fullHeight = Math.max(1, canvasHeight);
        int visibleWidth = Math.max(
                1, Math.min(fullWidth, requestedViewportWidth));
        int visibleHeight = Math.max(
                1, Math.min(fullHeight, requestedViewportHeight));
        float visibleLeft = (fullWidth - visibleWidth) * 0.5f;
        float visibleTop = (fullHeight - visibleHeight) * 0.5f;
        float cleanScale = renderBackgroundScale(userScale);
        float scaleX;
        float scaleY;
        if ("stretch".equals(mode)) {
            scaleX = visibleWidth / (float) sourceWidth;
            scaleY = visibleHeight / (float) sourceHeight;
        } else {
            float base = "fit".equals(mode)
                    ? Math.min(
                            visibleWidth / (float) sourceWidth,
                            visibleHeight / (float) sourceHeight)
                    : Math.max(
                            visibleWidth / (float) sourceWidth,
                            visibleHeight / (float) sourceHeight);
            scaleX = base;
            scaleY = base;
        }
        scaleX *= cleanScale;
        scaleY *= cleanScale;
        double scaledWidth = sourceWidth * (double) scaleX;
        double scaledHeight = sourceHeight * (double) scaleY;
        float cleanFocusX = clamp(focusX, 0f, 1f);
        float cleanFocusY = clamp(focusY, 0f, 1f);
        float dx = visibleLeft
                + (float) ((visibleWidth - scaledWidth) * cleanFocusX);
        float dy = visibleTop
                + (float) ((visibleHeight - scaledHeight) * cleanFocusY);
        return new float[]{scaleX, scaleY, dx, dy};
    }

    static void applyWallpaperDepth(ImageView image, boolean enabled) {
        if (image == null) return;
        if (enabled) {
            ColorMatrix color = new ColorMatrix();
            color.setSaturation(0.88f);
            ColorMatrix dim = new ColorMatrix(new float[]{
                    0.94f, 0f, 0f, 0f, 0f,
                    0f, 0.94f, 0f, 0f, 0f,
                    0f, 0f, 0.94f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
            });
            color.postConcat(dim);
            image.setColorFilter(new ColorMatrixColorFilter(color));
            image.setScaleX(1.015f);
            image.setScaleY(1.015f);
        } else {
            image.clearColorFilter();
            image.setScaleX(1f);
            image.setScaleY(1f);
        }
        if (Build.VERSION.SDK_INT >= 31) DepthApi31.apply(image, enabled);
    }

    private static final class DepthApi31 {
        static void apply(ImageView image, boolean enabled) {
            if (!enabled) {
                image.setRenderEffect(null);
                return;
            }
            float radius = Math.max(1f,
                    image.getResources().getDisplayMetrics().density * 2.2f);
            image.setRenderEffect(RenderEffect.createBlurEffect(
                    radius, radius, Shader.TileMode.CLAMP));
        }
    }

    static boolean isChatRoute(String route) {
        if (route == null) return false;
        String value = route.trim();
        return value.contains("com.deepseek.chat.ui.pages.ChatRoute")
                || "c81".equals(value)
                || value.endsWith(".c81")
                || value.contains(" route=c81")
                || "r91".equals(value)
                || value.endsWith(".r91")
                || value.contains(" route=r91");
    }

    static boolean isSettingsRoute(String route) {
        if (route == null) return false;
        String value = route.trim();
        if (value.contains("com.deepseek.chat.ui.pages.SettingsNestedGraph")) return true;
        String[] mapped = {
                // Mainland 2.2.2 (233).
                "qc7", "rc7", "tc7", "uc7", "vc7", "wc7", "xc7", "yc7",
                // Google Play 2.2.2 (236).
                "jg7", "kg7", "mg7", "ng7", "og7", "pg7", "qg7", "rg7"
        };
        for (String name : mapped) {
            if (name.equals(value) || value.endsWith("." + name)
                    || value.contains(" route=" + name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Receives DrawerState's live pixel offset. DeepSeek anchors the closed drawer at roughly
     * -drawerWidth and the open drawer at 0. The raw progress updates the follower's target instead
     * of directly writing the image position. The image therefore trails the host surface and
     * finishes its final ease-out shortly after the drawer reaches its anchor.
     */
    static void onSidebarOffset(float offsetPx) {
        onSidebarOffset(offsetPx, 0);
    }

    static void onSidebarOffset(float offsetPx, int exactDrawerWidthPx) {
        if (!isChatRoute(currentRoute)) return;
        if (Float.isNaN(offsetPx) || Float.isInfinite(offsetPx)) return;
        int sidebarWidth = Math.max(0, exactDrawerWidthPx);
        Activity activity = currentActivity();
        if (sidebarWidth <= 0 && activity != null) {
            try {
                int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
                float density = activity.getResources().getDisplayMetrics().density;
                float screenDp = screenWidth / Math.max(0.01f, density);
                sidebarWidth = screenDp < 600f
                        ? Math.round(screenWidth * 0.8f)
                        : Math.min(Math.round(320f * density), screenWidth);
            } catch (Throwable ignored) {}
        }
        if (sidebarWidth <= 0) sidebarWidth = 1;
        float progress = sidebarProgressForOffset(offsetPx, sidebarWidth);
        LiquidGlassEngine.onSidebarProgress(progress, sidebarWidth);
        updateSidebarTarget(progress);
    }

    static float sidebarProgressForOffset(float offsetPx, int sidebarWidthPx) {
        int width = Math.max(1, sidebarWidthPx);
        return clamp((offsetPx + width) / width, 0f, 1f);
    }

    private static synchronized void updateSidebarTarget(float rawProgress) {
        float raw = clamp(rawProgress, 0f, 1f);
        float previous = sidebarRawProgress;
        if (Math.abs(raw - previous) < 0.0005f) return;
        sidebarRawProgress = raw;
        sidebarProgress = raw;
        dispatchSidebarScene();
    }

    private static void dispatchSidebarScene() {
        final Activity activity = currentActivity();
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                RuntimeOverlay overlay = currentOverlay;
                if (overlay != null && isChatRoute(currentRoute)) {
                    overlay.applyScene(true, false);
                }
            }
        });
    }

    static float easeOutCubic(float value) {
        float t = clamp(value, 0f, 1f);
        float inverse = 1f - t;
        return 1f - inverse * inverse * inverse;
    }

    static float laggedMotionStep(float current, float target) {
        return laggedMotionStep(current, target, 1000f / 60f);
    }

    static float laggedMotionStep(float current, float target, float deltaMs) {
        if (Float.isNaN(current) || Float.isInfinite(current)) return target;
        if (Float.isNaN(target) || Float.isInfinite(target)) return current;
        float dt = clamp(deltaMs, 1f, 50f);
        float alpha = 1f - (float) Math.exp(
                -dt / WALLPAPER_LAG_TIME_CONSTANT_MS);
        return current + (target - current) * alpha;
    }

    static void onActivityResumed(Activity activity) {
        if (!isMainActivity(activity)) return;
        currentActivityRef = new WeakReference<>(activity);
        // Navigation state is reported asynchronously after a cold start. Treat the main
        // activity as chat until the real route arrives so a freshly imported wallpaper is not
        // rendered at zero alpha forever when the initial route callback is skipped.
        if (currentRoute == null || currentRoute.trim().length() == 0) {
            currentRoute = "com.deepseek.chat.ui.pages.ChatRoute";
            LiquidGlassEngine.onRouteChanged(currentRoute);
        }
        ensureOverlay(activity);
        updateVisibility(false);
    }

    static void onActivityPaused(Activity activity) {
        if (!isMainActivity(activity)) return;
        RuntimeOverlay overlay = currentOverlay;
        if (overlay != null && overlay.getContext() == activity) {
            overlay.onHostPaused();
        }
    }

    static void onActivityDestroyed(Activity activity) {
        if (activity == null) return;
        RuntimeOverlay overlay = currentOverlay;
        if (overlay != null && overlay.getContext() == activity) {
            overlay.release();
            Object parent = overlay.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(overlay);
            currentOverlay = null;
        }
        if (currentActivity() == activity) {
            currentActivityRef = new WeakReference<>(null);
            currentRoute = null;
            sidebarProgress = 0f;
            sidebarRawProgress = 0f;
            LiquidGlassEngine.onRouteChanged(null);
        }
    }

    static void onRouteChanged(Activity activity, String route) {
        long t0 = android.os.SystemClock.uptimeMillis();
        String from = currentRoute;
        if (activity != null && isMainActivity(activity)) {
            currentActivityRef = new WeakReference<>(activity);
        }
        if (route == null || route.trim().length() == 0) {
            // Some host pop/recomposition callbacks transiently expose a null navigation entry.
            // MainActivity still has a visible destination, so keep the last real route instead
            // of hiding the wallpaper and snapping the glass layer out for one or more frames.
            if (currentRoute == null || currentRoute.trim().length() == 0) {
                currentRoute = "com.deepseek.chat.ui.pages.ChatRoute";
                LiquidGlassEngine.onRouteChanged(currentRoute);
            }
            updateVisibility(true);
            return;
        }
        boolean stayedInChat = isChatRoute(currentRoute) && isChatRoute(route);
        // DeepSeek re-emits the same navigation entry many times within a few ms during a single
        // transition. Each redundant pass forced a bringToFront()+relayout on the host content
        // root, stacking multiple full relayouts on top of the host's own transition animation —
        // the visible hitch when entering/leaving settings. Collapse exact-duplicate callbacks.
        if (route.equals(from)) {
            return;
        }
        currentRoute = route;
        LiquidGlassEngine.onRouteChanged(route);
        if (!stayedInChat && !isChatRoute(route)) {
            sidebarProgress = 0f;
            sidebarRawProgress = 0f;
        }
        Activity active = currentActivity();
        if (active != null) ensureOverlay(active);
        updateVisibility(true);
        Main.log("appearance route change from=" + from + " to=" + route
                + " settings->chat=" + (isSettingsRoute(from) && isChatRoute(route))
                + " cost=" + (android.os.SystemClock.uptimeMillis() - t0) + "ms");
    }

    static void refresh() {
        final Activity activity = currentActivity();
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                RuntimeOverlay overlay = ensureOverlay(activity);
                if (overlay != null) overlay.render(load());
                updateVisibility(false);
            }
        });
    }

    private static RuntimeOverlay ensureOverlay(Activity activity) {
        if (!isMainActivity(activity) || activity.isFinishing()) return null;
        RuntimeOverlay existing = currentOverlay;
        if (existing != null && existing.getContext() == activity
                && existing.getParent() != null) return existing;
        try {
            ViewGroup content = activity.findViewById(android.R.id.content);
            if (content == null) return null;
            for (int i = 0; i < content.getChildCount(); i++) {
                View child = content.getChildAt(i);
                if (OVERLAY_TAG.equals(child.getTag()) && child instanceof RuntimeOverlay) {
                    currentOverlay = (RuntimeOverlay) child;
                    currentOverlay.render(load());
                    return currentOverlay;
                }
            }
            RuntimeOverlay overlay = new RuntimeOverlay(activity);
            overlay.setTag(OVERLAY_TAG);
            overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            if (Build.VERSION.SDK_INT >= 21) {
                // Compose can keep a positive RenderNode Z for its root during navigation. Child
                // order alone is therefore not a sufficient guarantee that an ordinary sibling
                // remains visible after the settings/chat transition.
                overlay.setElevation(32f
                        * activity.getResources().getDisplayMetrics().density);
            }
            ViewGroup.LayoutParams params;
            if (content instanceof FrameLayout) {
                params = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            } else {
                params = new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            }
            content.addView(overlay, params);
            overlay.render(load());
            currentOverlay = overlay;
            Main.log("chat appearance overlay attached above host content"
                    + " index=" + content.indexOfChild(overlay)
                    + "/" + content.getChildCount());
            return overlay;
        } catch (Throwable t) {
            Main.log("chat appearance overlay attach failed: " + t);
            return null;
        }
    }

    private static void updateVisibility(boolean animate) {
        RuntimeOverlay overlay = currentOverlay;
        if (overlay == null) return;
        Config config = load();
        boolean chat = isChatRoute(currentRoute);
        boolean settings = isSettingsRoute(currentRoute);
        boolean appearance = config.enabled
                && ((chat && (!config.stickers.isEmpty()
                        || (config.backgroundFile.length() > 0
                        && (config.backgroundOnChat || config.backgroundOnSidebar))))
                || (settings && (!config.stickers.isEmpty()
                        || (config.backgroundFile.length() > 0
                        && config.backgroundOnSettings))));
        boolean show = appearance
                || (config.liquidGlassEnabled && (chat || settings))
                || (config.spatialDepthEnabled && chat);
        if (show) {
            boolean alreadyRendered = overlay.getChildCount() > 0;
            if (!alreadyRendered) overlay.render(config);
            overlay.setVisibility(View.VISIBLE);
            overlay.bringToFront();
            if (Build.VERSION.SDK_INT >= 21) {
                overlay.setElevation(32f
                        * overlay.getResources().getDisplayMetrics().density);
            }
            keepSettingsEntryAbove(overlay);
            overlay.applyScene(animate && alreadyRendered, animate && alreadyRendered);
        } else {
            overlay.setVisibility(View.GONE);
            overlay.stopSceneSensors();
            // Keep the rendered wallpaper/stickers across transient host routes. DeepSeek briefly
            // reports intermediate destinations while opening/closing drawers and settings; fully
            // releasing here made the chat return before its background had been decoded again.
            // The Activity teardown still calls release(), and refresh() still replaces the scene.
        }
    }

    private static void keepSettingsEntryAbove(RuntimeOverlay overlay) {
        if (overlay == null || !(overlay.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) overlay.getParent();
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (DeekseepUi.ENTRY_BUTTON_TAG.equals(child.getTag())) {
                child.bringToFront();
                if (Build.VERSION.SDK_INT >= 21) {
                    child.setElevation(33f
                            * child.getResources().getDisplayMetrics().density);
                }
                return;
            }
        }
    }

    private static boolean isMainActivity(Activity activity) {
        return activity != null
                && "com.deepseek.chat.MainActivity".equals(activity.getClass().getName());
    }

    private static Activity currentActivity() {
        WeakReference<Activity> reference = currentActivityRef;
        return reference == null ? null : reference.get();
    }

    private static String safeAssetName(String value) {
        if (value == null) return "";
        String name = new File(value).getName();
        if (!name.equals(value) || ".".equals(name) || "..".equals(name)) return "";
        return name.matches("[A-Za-z0-9._-]+") ? name : "";
    }

    static float spatialNormalizeTilt(float deltaRadians) {
        return SpatialMotionController.normalizeTilt(deltaRadians);
    }

    /**
     * Foreground parallax is applied once to the host View root. Child-level Compose modifiers stay
     * disabled so bubbles and the input box cannot acquire separate filters or gelatinous motion.
     */
    static boolean composeSpatialModifiersEnabled() {
        return false;
    }

    static float spatialWallpaperOffsetX(float cameraX, float density) {
        return finiteOrZero(cameraX)
                * SPATIAL_BACKGROUND_X_DP * Math.max(0.01f, density);
    }

    static float spatialWallpaperOffsetY(float cameraY, float density) {
        return finiteOrZero(cameraY)
                * SPATIAL_BACKGROUND_Y_DP * Math.max(0.01f, density);
    }

    static float spatialForegroundOffsetX(float cameraX, float density) {
        return -spatialWallpaperOffsetX(cameraX, density)
                * SPATIAL_FOREGROUND_TO_BACKGROUND_RATIO;
    }

    static float spatialForegroundOffsetY(float cameraY, float density) {
        return -spatialWallpaperOffsetY(cameraY, density)
                * SPATIAL_FOREGROUND_TO_BACKGROUND_RATIO;
    }

    private static void publishSpatialPose(boolean active, float x, float y) {
        float cleanX = finiteOrZero(x);
        float cleanY = finiteOrZero(y);
        SpatialPose current = currentSpatialPose;
        if (current.active == active
                && Float.floatToIntBits(current.x)
                        == Float.floatToIntBits(active ? cleanX : 0f)
                && Float.floatToIntBits(current.y)
                        == Float.floatToIntBits(active ? cleanY : 0f)) {
            return;
        }
        SpatialPose next;
        if (active) {
            next = SPATIAL_POSE_POOL[
                    spatialPosePoolIndex++
                            & (SPATIAL_POSE_POOL.length - 1)];
            next.set(true, cleanX, cleanY);
        } else {
            next = SpatialPose.DISABLED;
        }
        currentSpatialPose = next;
        RuntimeOverlay overlay = currentOverlay;
        if (overlay != null) overlay.applySpatialPose(next);
        for (SpatialPoseListener listener : SPATIAL_POSE_LISTENERS) {
            try {
                listener.onSpatialPose(next);
            } catch (Throwable t) {
                Main.log("spatial pose listener failed: " + t);
            }
        }
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static float finiteOrZero(float value) {
        return Float.isNaN(value) || Float.isInfinite(value)
                ? 0f : value;
    }

    private static float normalizeRotation(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 0f;
        float out = value % 360f;
        if (out > 180f) out -= 360f;
        if (out < -180f) out += 360f;
        return out;
    }

    /**
     * Returns the smallest centered pre-rotation canvas that covers the viewport after rotating
     * and translating horizontally by {@code shiftPx}. This prevents exposed corners without
     * forcing every unrotated wallpaper into a square crop.
     */
    static int[] wallpaperCanvasSize(
            int viewportWidth, int viewportHeight, int shiftPx, float rotation) {
        int width = Math.max(1, viewportWidth);
        int height = Math.max(1, viewportHeight);
        int shift = Math.max(0, shiftPx);
        double radians = Math.toRadians(normalizeRotation(rotation));
        double cos = Math.abs(Math.cos(radians));
        double sin = Math.abs(Math.sin(radians));
        if (cos < 0.000001d) cos = 0d;
        if (sin < 0.000001d) sin = 0d;
        int canvasWidth = Math.max(1, (int) Math.ceil(
                width * cos + height * sin + shift * 2d * cos));
        int canvasHeight = Math.max(1, (int) Math.ceil(
                width * sin + height * cos + shift * 2d * sin));
        return new int[]{canvasWidth, canvasHeight};
    }

    private static final class RuntimeOverlay extends FrameLayout {
        private int renderGeneration;
        private Config renderedConfig;
        private FrameLayout wallpaperViewport;
        private ImageView wallpaperView;
        private ImageView midgroundView;
        private LiquidGlassEngine.LayerView glassLayer;
        private final ArrayList<ImageView> stickerViews = new ArrayList<>();
        private int maxShiftPx;
        private float motionTargetX;
        private boolean motionFollowerRunning;
        private int motionFollowerGeneration;
        private long motionFollowerStartAt;
        private long motionFollowerLastFrameAt;
        private boolean wallpaperRevealPending;
        private String loggedScene = "";
        private android.animation.ValueAnimator routeMotionAnimator;
        private Bitmap cachedBackgroundBitmap;
        private String cachedBackgroundKey = "";
        private Bitmap cachedMidgroundBitmap;
        private String cachedMidgroundKey = "";
        private String requestedSpatialLayerKey = "";
        // Parallax (drawer/route) translation, tracked separately so the impulse-driven shake
        // spring can be summed on top without corrupting the follower's per-frame stepping.
        private float parallaxX;
        private int shakeHeadroomPx;
        private int shakeMaxOffsetPx;
        private float shakeOffsetX;
        private float shakeOffsetY;
        private float shakeVelX;
        private float shakeVelY;
        private boolean shakeSpringRunning;
        private int shakeSpringGeneration;
        private long shakeLastFrameNanos;
        private ShakeSensor shakeSensor;
        private float spatialOffsetX;
        private float spatialOffsetY;
        private float spatialDensity = 1f;
        private SpatialMotionController spatialMotionController;
        private boolean spatialMotionResumed;
        private final SpatialViewLayer spatialContentLayer =
                new SpatialViewLayer();

        RuntimeOverlay(Activity activity) {
            super(activity);
            setClickable(false);
            setFocusable(false);
            setFocusableInTouchMode(false);
            setMotionEventSplittingEnabled(false);
            spatialDensity = Math.max(0.01f,
                    getResources().getDisplayMetrics().density);
        }

        @Override protected void onSizeChanged(
                int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            if (width <= 0 || height <= 0 || renderedConfig == null) return;
            if (getChildCount() > 0
                    && width == oldWidth && height == oldHeight) {
                return;
            }
            final Config retry = renderedConfig.copy();
            post(new Runnable() {
                @Override public void run() {
                    if (getWidth() > 0 && getHeight() > 0) render(retry);
                }
            });
        }

        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            return false;
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            return false;
        }

        @Override protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            if (visibility == View.VISIBLE) {
                if (renderedConfig != null) {
                    updateShakeSensorState(renderedConfig);
                    updateSpatialMotionState(renderedConfig);
                }
            } else {
                stopSceneSensors();
            }
        }

        void render(final Config config) {
            final int generation = ++renderGeneration;
            final Config snapshot = config == null ? new Config() : config.copy();
            renderedConfig = snapshot;
            stopMotionFollower();
            cancelRouteMotion();
            stopShakeSpring();
            if (wallpaperViewport != null) wallpaperViewport.animate().cancel();
            if (wallpaperView != null) wallpaperView.animate().cancel();
            if (glassLayer != null) glassLayer.release();
            removeAllViews();
            wallpaperViewport = null;
            wallpaperView = null;
            midgroundView = null;
            glassLayer = null;
            stickerViews.clear();
            maxShiftPx = 0;
            motionTargetX = 0f;
            parallaxX = 0f;
            shakeHeadroomPx = 0;
            shakeMaxOffsetPx = 0;
            spatialOffsetX = 0f;
            spatialOffsetY = 0f;
            wallpaperRevealPending = false;
            post(new Runnable() {
                @Override public void run() {
                    if (generation != renderGeneration || getWidth() <= 0 || getHeight() <= 0) {
                        return;
                    }
                    long t0 = android.os.SystemClock.uptimeMillis();
                    if (snapshot.enabled) {
                        addBackground(snapshot);
                        addStickers(snapshot);
                    }
                    addGlass(snapshot);
                    applyScene(false);
                    long cost = android.os.SystemClock.uptimeMillis() - t0;
                    if (cost > 4) {
                        Main.log("appearance render body cost=" + cost + "ms"
                                + " route=" + currentRoute
                                + " bg=" + (snapshot.backgroundFile.length() > 0)
                                + " stickers=" + snapshot.stickers.size());
                    }
                }
            });
        }

        void release() {
            renderGeneration++;
            stopMotionFollower();
            cancelRouteMotion();
            stopShakeSpring();
            disposeSpatialMotion();
            if (shakeSensor != null) {
                shakeSensor.stop();
                shakeSensor = null;
            }
            if (wallpaperViewport != null) wallpaperViewport.animate().cancel();
            if (wallpaperView != null) wallpaperView.animate().cancel();
            if (glassLayer != null) glassLayer.release();
            removeAllViews();
            renderedConfig = null;
            wallpaperViewport = null;
            wallpaperView = null;
            midgroundView = null;
            glassLayer = null;
            stickerViews.clear();
            maxShiftPx = 0;
            motionTargetX = 0f;
            parallaxX = 0f;
            spatialOffsetX = 0f;
            spatialOffsetY = 0f;
            wallpaperRevealPending = false;
            cachedBackgroundBitmap = null;
            cachedBackgroundKey = "";
            cachedMidgroundBitmap = null;
            cachedMidgroundKey = "";
            requestedSpatialLayerKey = "";
        }

        void applyScene(boolean animate) {
            applyScene(animate, false);
        }

        void applyScene(boolean animate, boolean routeTransition) {
            Config config = renderedConfig;
            if (config == null) return;
            boolean settings = isSettingsRoute(currentRoute);
            // Spatial mode and drawer layout share one final writer. The legacy lag follower and
            // shake spring remain disabled, but the deterministic drawer/route base offset is
            // preserved instead of incorrectly locking the wallpaper at x=0.
            boolean spatial = config.spatialDepthEnabled;
            float target = getWidth() * config.motionFraction(
                    settings, sidebarProgress) * MOTION_DISTANCE_BOOST;
            float visibility = 0f;
            if (settings) {
                visibility = config.backgroundOnSettings ? 1f : 0f;
            } else if (isChatRoute(currentRoute)) {
                float closed = config.backgroundOnChat ? 1f : 0f;
                float open = config.backgroundOnSidebar ? 1f : 0f;
                visibility = closed + (open - closed) * sidebarProgress;
            }
            if (wallpaperView != null && wallpaperViewport != null) {
                float targetAlpha =
                        config.enabled ? config.backgroundOpacity * visibility : 0f;
                String scene = currentRoute + "|"
                        + Math.round(targetAlpha * 100f) + "|"
                        + Math.round(sidebarProgress * 100f);
                if (!scene.equals(loggedScene)) {
                    loggedScene = scene;
                    Main.log("appearance scene wallpaper="
                            + Math.round(targetAlpha * 100f) + "%"
                            + " route=" + currentRoute
                            + " drawer=" + Math.round(sidebarProgress * 100f) + "%");
                }
                if (wallpaperRevealPending && targetAlpha > 0f) {
                    wallpaperRevealPending = false;
                    float targetScale = config.spatialDepthEnabled
                            ? SPATIAL_BACKGROUND_SCALE
                            : (config.depthEnabled ? 1.015f : 1f);
                    wallpaperViewport.animate().cancel();
                    wallpaperView.animate().cancel();
                    wallpaperViewport.setAlpha(0f);
                    wallpaperView.setScaleX(spatial
                            ? targetScale : targetScale + 0.018f);
                    wallpaperView.setScaleY(spatial
                            ? targetScale : targetScale + 0.018f);
                    wallpaperViewport.animate()
                            .alpha(targetAlpha)
                            .setDuration(420L)
                            .setInterpolator(new DecelerateInterpolator(1.7f))
                            .start();
                    if (!spatial) {
                        wallpaperView.animate()
                                .scaleX(targetScale)
                                .scaleY(targetScale)
                                .setDuration(420L)
                                .setInterpolator(new DecelerateInterpolator(1.7f))
                                .start();
                    }
                } else {
                    wallpaperViewport.animate().cancel();
                    wallpaperViewport.setAlpha(targetAlpha);
                }
                if (spatial) {
                    stopShakeSpring();
                    shakeOffsetX = 0f;
                    shakeOffsetY = 0f;
                }
                // Sensor pose remains direct, but the deterministic chat/sidebar/settings base
                // offset uses the same proven lag/glide path whether spatial motion is on or off.
                if (routeTransition) {
                    startRouteMotionGlide(target);
                } else if (animate) {
                    motionTargetX = target;
                    // Drawer state can emit an initial open/closed pair immediately after
                    // Settings pops. Retarget the active route glide instead of cancelling it;
                    // otherwise that pair snaps the wallpaper and erases the return delay.
                    if (routeMotionAnimator == null) {
                        startMotionFollower();
                    }
                } else {
                    cancelRouteMotion();
                    stopMotionFollower();
                    motionTargetX = target;
                    parallaxX = target;
                    applyWallpaperTranslation();
                }
            } else {
                stopMotionFollower();
                motionTargetX = target;
            }
            for (ImageView sticker : stickerViews) {
                sticker.setAlpha(config.enabled ? sticker.getAlpha() : 0f);
            }
            updateShakeSensorState(config);
            updateSpatialMotionState(config);
        }

        // Single writer for the wallpaper's on-screen position. The experimental spatial offset
        // intentionally does not feed the liquid-glass scene yet: this branch is meant to validate
        // the host UI/body motion first, and the glass compositor will be synchronized later.
        private void applyWallpaperTranslation() {
            if (wallpaperView == null) return;
            boolean spatial = renderedConfig != null
                    && renderedConfig.spatialDepthEnabled;
            float x = wallpaperTranslationX(
                    spatial, parallaxX, spatialOffsetX, shakeOffsetX);
            float y = spatial ? spatialOffsetY : shakeOffsetY;
            wallpaperView.setTranslationX(x);
            wallpaperView.setTranslationY(y);
            if (midgroundView != null) {
                midgroundView.setTranslationX(
                        parallaxX + (spatial
                                ? spatialOffsetX
                                * SPATIAL_MIDGROUND_TO_BACKGROUND_RATIO
                                : shakeOffsetX));
                midgroundView.setTranslationY(
                        spatial ? spatialOffsetY
                                * SPATIAL_MIDGROUND_TO_BACKGROUND_RATIO
                                : shakeOffsetY);
            }
            if (glassLayer != null) {
                glassLayer.setSceneTranslation(
                        spatial ? 0f : parallaxX + shakeOffsetX);
            }
        }

        private void cancelRouteMotion() {
            android.animation.ValueAnimator animator = routeMotionAnimator;
            routeMotionAnimator = null;
            if (animator != null) {
                animator.cancel();
            }
        }

        // Route changes (chat <-> settings) deliver the motion target as a single jump. The
        // per-frame one-pole follower front-loads such a jump (biggest step on frame one), which
        // reads as an abrupt slide. Drive route transitions with a longer decelerate glide so the
        // wallpaper eases into place instead of snapping.
        private void startRouteMotionGlide(float target) {
            if (wallpaperView == null) return;
            stopMotionFollower();
            cancelRouteMotion();
            motionTargetX = target;
            float start = parallaxX;
            if (Math.abs(target - start) <= 0.5f) {
                parallaxX = target;
                applyWallpaperTranslation();
                return;
            }
            android.animation.ValueAnimator animator =
                    android.animation.ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(WALLPAPER_ROUTE_GLIDE_MS);
            animator.setStartDelay(WALLPAPER_ROUTE_START_DELAY_MS);
            animator.setInterpolator(new DecelerateInterpolator(1.8f));
            animator.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(android.animation.ValueAnimator a) {
                    if (wallpaperView == null) return;
                    float progress = (Float) a.getAnimatedValue();
                    parallaxX = start + (motionTargetX - start) * progress;
                    applyWallpaperTranslation();
                }
            });
            animator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    if (routeMotionAnimator != animation) return;
                    parallaxX = motionTargetX;
                    applyWallpaperTranslation();
                    routeMotionAnimator = null;
                }
            });
            routeMotionAnimator = animator;
            animator.start();
        }

        private void startMotionFollower() {
            if (wallpaperView == null || motionFollowerRunning) return;
            motionFollowerRunning = true;
            motionFollowerStartAt =
                    android.os.SystemClock.uptimeMillis() + WALLPAPER_LAG_START_DELAY_MS;
            motionFollowerLastFrameAt = 0L;
            final int generation = ++motionFollowerGeneration;
            postOnAnimation(new Runnable() {
                @Override public void run() {
                    if (generation != motionFollowerGeneration
                            || !motionFollowerRunning || wallpaperView == null) {
                        return;
                    }
                    long now = android.os.SystemClock.uptimeMillis();
                    if (now < motionFollowerStartAt) {
                        postOnAnimation(this);
                        return;
                    }
                    float deltaMs = motionFollowerLastFrameAt == 0L
                            ? 1000f / 60f : now - motionFollowerLastFrameAt;
                    motionFollowerLastFrameAt = now;
                    float current = parallaxX;
                    float remaining = motionTargetX - current;
                    if (Math.abs(remaining) <= 0.4f) {
                        parallaxX = motionTargetX;
                        applyWallpaperTranslation();
                        motionFollowerRunning = false;
                        return;
                    }
                    float next = laggedMotionStep(
                            current, motionTargetX, deltaMs);
                    parallaxX = next;
                    applyWallpaperTranslation();
                    postOnAnimation(this);
                }
            });
        }

        private void stopMotionFollower() {
            motionFollowerGeneration++;
            motionFollowerRunning = false;
            motionFollowerLastFrameAt = 0L;
        }

        // Feeds an impulse into the 2D shake spring. Called from the sensor when a quick move is
        // detected; the phone being held still produces no impulse (see the trigger gates), so the
        // wallpaper stays at rest instead of tracking attitude.
        void addShakeImpulse(float vx, float vy) {
            if (wallpaperView == null || shakeMaxOffsetPx <= 0
                    || (renderedConfig != null
                    && renderedConfig.spatialDepthEnabled)) {
                return;
            }
            shakeVelX = clamp(shakeVelX + vx, -SHAKE_IMPULSE_CLAMP, SHAKE_IMPULSE_CLAMP);
            shakeVelY = clamp(shakeVelY + vy, -SHAKE_IMPULSE_CLAMP, SHAKE_IMPULSE_CLAMP);
            startShakeSpring();
        }

        private void startShakeSpring() {
            if (shakeSpringRunning || wallpaperView == null) return;
            shakeSpringRunning = true;
            shakeLastFrameNanos = 0L;
            final int generation = ++shakeSpringGeneration;
            postOnAnimation(new Runnable() {
                @Override public void run() {
                    if (generation != shakeSpringGeneration
                            || !shakeSpringRunning || wallpaperView == null) {
                        return;
                    }
                    long nowNanos = System.nanoTime();
                    float dt = shakeLastFrameNanos == 0L
                            ? 0.016f : (nowNanos - shakeLastFrameNanos) / 1_000_000_000f;
                    shakeLastFrameNanos = nowNanos;
                    dt = clamp(dt, 0.001f, 0.05f);
                    // Critically-ish damped spring toward the origin: quick response to the impulse,
                    // a little inertial overshoot, then a slow settle back to rest.
                    float ax = -SHAKE_SPRING_STIFFNESS * shakeOffsetX
                            - SHAKE_SPRING_DAMPING * shakeVelX;
                    float ay = -SHAKE_SPRING_STIFFNESS * shakeOffsetY
                            - SHAKE_SPRING_DAMPING * shakeVelY;
                    shakeVelX += ax * dt;
                    shakeVelY += ay * dt;
                    shakeOffsetX += shakeVelX * dt;
                    shakeOffsetY += shakeVelY * dt;
                    float lim = shakeMaxOffsetPx;
                    if (shakeOffsetX > lim) { shakeOffsetX = lim; shakeVelX *= -0.3f; }
                    if (shakeOffsetX < -lim) { shakeOffsetX = -lim; shakeVelX *= -0.3f; }
                    if (shakeOffsetY > lim) { shakeOffsetY = lim; shakeVelY *= -0.3f; }
                    if (shakeOffsetY < -lim) { shakeOffsetY = -lim; shakeVelY *= -0.3f; }
                    applyWallpaperTranslation();
                    boolean atRest = Math.abs(shakeOffsetX) < 0.3f
                            && Math.abs(shakeOffsetY) < 0.3f
                            && Math.abs(shakeVelX) < 2f && Math.abs(shakeVelY) < 2f;
                    if (atRest) {
                        shakeOffsetX = 0f; shakeOffsetY = 0f;
                        shakeVelX = 0f; shakeVelY = 0f;
                        applyWallpaperTranslation();
                        shakeSpringRunning = false;
                        return;
                    }
                    postOnAnimation(this);
                }
            });
        }

        private void stopShakeSpring() {
            shakeSpringGeneration++;
            shakeSpringRunning = false;
            shakeOffsetX = 0f; shakeOffsetY = 0f;
            shakeVelX = 0f; shakeVelY = 0f;
        }

        private void updateShakeSensorState(Config config) {
            boolean want = config != null && config.enabled
                    && config.shakeParallaxEnabled
                    && !config.spatialDepthEnabled
                    && wallpaperView != null && shakeMaxOffsetPx > 0;
            if (want) {
                if (shakeSensor == null) {
                    shakeSensor = new ShakeSensor(getContext(), this);
                }
                shakeSensor.start();
            } else if (shakeSensor != null) {
                shakeSensor.stop();
                stopShakeSpring();
                applyWallpaperTranslation();
            }
        }

        private void updateSpatialMotionState(Config config) {
            boolean want = config != null && config.spatialDepthEnabled
                    && isChatRoute(currentRoute)
                    && getVisibility() == View.VISIBLE
                    && getWindowVisibility() == View.VISIBLE;
            if (config == null || !config.spatialDepthEnabled) {
                detachSpatialMotion();
                return;
            }
            if (spatialMotionController == null) {
                spatialMotionController = new SpatialMotionController(
                        getContext(),
                        new SpatialMotionController.FrameListener() {
                            @Override public void onSpatialFrame(
                                    boolean active, float cameraX,
                                    float cameraY) {
                                publishSpatialPose(
                                        active, cameraX, cameraY);
                            }
                        });
            }
            spatialMotionController.configure(
                    spatialStrengthMultiplier(config.spatialStrength),
                    config.spatialReduceMotion,
                    config.spatialAutoRecenter,
                    config.spatialDirectionMultiplier);
            if (want && getContext() instanceof Activity) {
                // Resolve one coherent foreground plane once. Individual messages and the input
                // box never receive their own sensor state or filter.
                bindSpatialContentLayer();
                spatialMotionController.attach((Activity) getContext());
                spatialMotionResumed = true;
            } else if (spatialMotionResumed) {
                spatialMotionController.pause();
                spatialMotionResumed = false;
                spatialContentLayer.restore();
            }
        }

        private void detachSpatialMotion() {
            if (spatialMotionController != null) {
                spatialMotionController.detach();
                spatialMotionController = null;
            }
            spatialContentLayer.unbind();
            spatialMotionResumed = false;
        }

        private void disposeSpatialMotion() {
            if (spatialMotionController != null) {
                spatialMotionController.dispose();
                spatialMotionController = null;
            }
            spatialContentLayer.unbind();
            spatialMotionResumed = false;
            // Activity destruction has no subsequent scene frame. Reset the process-wide Compose
            // snapshot now, after the controller has removed its Choreographer callback.
            publishSpatialPose(false, 0f, 0f);
        }

        void recenterSpatialMotion() {
            if (spatialMotionController != null) {
                spatialMotionController.recenter();
            }
        }

        void onHostPaused() {
            if (shakeSensor != null) shakeSensor.stop();
            stopShakeSpring();
            if (spatialMotionController != null && spatialMotionResumed) {
                spatialMotionController.pause();
                spatialMotionResumed = false;
            }
            spatialOffsetX = 0f;
            spatialOffsetY = 0f;
            spatialContentLayer.restore();
            applyWallpaperTranslation();
        }

        void stopSceneSensors() {
            if (shakeSensor != null) shakeSensor.stop();
            stopShakeSpring();
            if (spatialMotionController != null && spatialMotionResumed) {
                spatialMotionController.pause();
                spatialMotionResumed = false;
            }
            spatialOffsetX = 0f;
            spatialOffsetY = 0f;
            spatialContentLayer.restore();
            applyWallpaperTranslation();
        }

        void applySpatialPose(SpatialPose pose) {
            SpatialPose value = pose == null ? SpatialPose.DISABLED : pose;
            // The whole host UI is one restrained foreground plane. It moves opposite the rear
            // wallpaper at one tenth amplitude; no bubble/input child moves independently.
            spatialContentLayer.apply(
                    value.active, value.x, value.y, spatialDensity);
            if (value.active) {
                spatialOffsetX = spatialWallpaperOffsetX(
                        value.x, spatialDensity);
                spatialOffsetY = spatialWallpaperOffsetY(
                        value.y, spatialDensity);
            } else {
                spatialOffsetX = 0f;
                spatialOffsetY = 0f;
            }
            applyWallpaperTranslation();
        }

        /**
         * Resolves the host content plane once at scene attachment. This is deliberately never
         * called by a sensor callback or display frame: the complete View tree is not a frame-time
         * data structure.
         */
        private void bindSpatialContentLayer() {
            Object rawParent = getParent();
            if (!(rawParent instanceof ViewGroup)) return;
            ViewGroup parent = (ViewGroup) rawParent;
            View fallback = null;
            long fallbackArea = -1L;
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                if (child == null || child == this
                        || OVERLAY_TAG.equals(child.getTag())
                        || DeekseepUi.ENTRY_BUTTON_TAG.equals(child.getTag())) {
                    continue;
                }
                View compose = findComposeLayer(child);
                if (compose != null) {
                    spatialContentLayer.bind(compose);
                    return;
                }
                long area = (long) Math.max(0, child.getWidth())
                        * Math.max(0, child.getHeight());
                if (child.getVisibility() == View.VISIBLE
                        && area > fallbackArea) {
                    fallback = child;
                    fallbackArea = area;
                }
            }
            // A renamed/wrapped Compose host can still use its largest full-screen content child.
            // If no safe layer exists, this binds nothing and the remaining planes keep working.
            spatialContentLayer.bind(fallback);
        }

        private static View findComposeLayer(View root) {
            if (root == null || root instanceof RuntimeOverlay) return null;
            String name = root.getClass().getName();
            if (name != null && (name.endsWith(".AndroidComposeView")
                    || name.endsWith(".ComposeView"))) {
                return root;
            }
            if (!(root instanceof ViewGroup)) return null;
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findComposeLayer(group.getChildAt(i));
                if (found != null) return found;
            }
            return null;
        }

        private void addBackground(Config config) {
            if (config.backgroundFile.length() == 0) return;
            File sourceFile = assetFile(config.backgroundFile);
            SpatialLayerCache.Layers opticalLayers =
                    SpatialLayerCache.filesFor(sourceFile);
            boolean layered = config.spatialDepthEnabled
                    && opticalLayers.ready();
            if (config.spatialDepthEnabled && !layered) {
                requestSpatialLayers(
                        config.backgroundFile, sourceFile, opticalLayers);
            }
            File backgroundSource = layered
                    ? opticalLayers.backplate : sourceFile;
            // Drawer/settings travel still exists in spatial mode; reserve its full horizontal
            // range in addition to the tiny sensor headroom.
            maxShiftPx = Math.round(getWidth() * config.maxMotionMagnitude()
                    * MOTION_DISTANCE_BOOST);
            // Reserve a symmetric bleed on both axes so the shake spring can push the wallpaper
            // in any direction without exposing the canvas edge. Only paid for when enabled.
            shakeHeadroomPx = config.shakeParallaxEnabled
                    ? Math.round(getWidth() * SHAKE_HEADROOM_FRACTION) : 0;
            shakeMaxOffsetPx = config.shakeParallaxEnabled
                    ? Math.min(shakeHeadroomPx,
                            Math.round(getWidth() * SHAKE_MAX_OFFSET_FRACTION))
                    : 0;
            int viewportHeight = wallpaperViewportHeight(
                    getHeight(), config.backgroundExtent);
            int viewportTop = wallpaperViewportTop(
                    getHeight(), config.backgroundExtent);
            int[] canvas = wallpaperCanvasSize(
                    getWidth(), viewportHeight, maxShiftPx, config.backgroundRotation);
            int spatialHeadroomPx = config.spatialDepthEnabled
                    ? Math.round(SPATIAL_CANVAS_HEADROOM_DP * spatialDensity) : 0;
            int sensorHeadroomPx = Math.max(
                    shakeHeadroomPx, spatialHeadroomPx);
            int canvasWidth = canvas[0] + sensorHeadroomPx * 2;
            int canvasHeight = canvas[1] + sensorHeadroomPx * 2;
            String bitmapKey = backgroundSource.getAbsolutePath()
                    + "@" + backgroundSource.lastModified()
                    + "@" + canvasWidth + "x" + canvasHeight;
            Bitmap bitmap;
            if (bitmapKey.equals(cachedBackgroundKey)
                    && cachedBackgroundBitmap != null
                    && !cachedBackgroundBitmap.isRecycled()) {
                bitmap = cachedBackgroundBitmap;
            } else {
                long decodeStart = android.os.SystemClock.uptimeMillis();
                bitmap = loadBitmap(backgroundSource,
                        canvasWidth, canvasHeight);
                long decodeCost = android.os.SystemClock.uptimeMillis() - decodeStart;
                if (decodeCost > 4) {
                    Main.log("appearance background decode cost=" + decodeCost + "ms"
                            + " canvas=" + canvasWidth + "x" + canvasHeight
                            + " route=" + currentRoute);
                }
                cachedBackgroundBitmap = bitmap;
                cachedBackgroundKey = bitmap == null ? "" : bitmapKey;
            }
            if (bitmap == null) {
                Main.log("appearance background load failed: "
                        + config.backgroundFile);
                return;
            }
            FrameLayout viewport = new FrameLayout(getContext());
            viewport.setTag("deekseep_wallpaper_source");
            viewport.setClipChildren(true);
            viewport.setClipToPadding(true);
            viewport.setClickable(false);
            viewport.setFocusable(false);
            ImageView image = new ImageView(getContext());
            applyWallpaperPresentation(
                    image, bitmap, config, getWidth(), viewportHeight);
            image.setClickable(false);
            image.setFocusable(false);
            FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(
                    canvasWidth, canvasHeight);
            imageParams.leftMargin = Math.round((getWidth() - canvasWidth) / 2f);
            imageParams.topMargin = Math.round((viewportHeight - canvasHeight) / 2f);
            viewport.addView(image, imageParams);
            ImageView middle = null;
            if (layered) {
                String middleKey = opticalLayers.midground.getAbsolutePath()
                        + "@" + opticalLayers.midground.lastModified()
                        + "@" + canvasWidth + "x" + canvasHeight;
                Bitmap middleBitmap;
                if (middleKey.equals(cachedMidgroundKey)
                        && cachedMidgroundBitmap != null
                        && !cachedMidgroundBitmap.isRecycled()) {
                    middleBitmap = cachedMidgroundBitmap;
                } else {
                    middleBitmap = loadBitmap(
                            opticalLayers.midground,
                            canvasWidth, canvasHeight);
                    cachedMidgroundBitmap = middleBitmap;
                    cachedMidgroundKey =
                            middleBitmap == null ? "" : middleKey;
                }
                if (middleBitmap != null) {
                    middle = new ImageView(getContext());
                    applyWallpaperPresentation(
                            middle, middleBitmap, config,
                            getWidth(), viewportHeight, true);
                    middle.setClickable(false);
                    middle.setFocusable(false);
                    FrameLayout.LayoutParams middleParams =
                            new FrameLayout.LayoutParams(
                                    canvasWidth, canvasHeight);
                    middleParams.leftMargin = imageParams.leftMargin;
                    middleParams.topMargin = imageParams.topMargin;
                    viewport.addView(middle, middleParams);
                }
            }
            FrameLayout.LayoutParams viewportParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, viewportHeight);
            viewportParams.topMargin = viewportTop;
            wallpaperViewport = viewport;
            wallpaperView = image;
            midgroundView = middle;
            wallpaperRevealPending = true;
            addView(viewport, viewportParams);
            Main.log("appearance background ready: "
                    + config.backgroundFile + " canvas="
                    + canvasWidth + "x" + canvasHeight
                    + " extent=" + config.backgroundExtent
                    + " mode=" + config.backgroundMode
                    + " edge=" + config.backgroundEdgeMode
                    + " scale=" + config.backgroundScale
                    + " focus=" + Math.round(config.backgroundFocusX * 100f)
                    + "," + Math.round(config.backgroundFocusY * 100f)
                    + " optical_layers=" + (middle != null));
        }

        private void requestSpatialLayers(
                final String backgroundFile, File source,
                SpatialLayerCache.Layers layers) {
            if (layers == null || layers.key.equals(
                    requestedSpatialLayerKey)) {
                return;
            }
            requestedSpatialLayerKey = layers.key;
            final WeakReference<RuntimeOverlay> overlayRef =
                    new WeakReference<>(this);
            SpatialLayerCache.generateAsync(
                    source, new SpatialLayerCache.Completion() {
                        @Override public void onComplete(boolean ready) {
                            final RuntimeOverlay overlay = overlayRef.get();
                            if (!ready || overlay == null) return;
                            overlay.post(new Runnable() {
                                @Override public void run() {
                                    Config active = overlay.renderedConfig;
                                    if (active == null
                                            || !active.spatialDepthEnabled
                                            || !backgroundFile.equals(
                                            active.backgroundFile)) {
                                        return;
                                    }
                                    overlay.render(active.copy());
                                }
                            });
                        }
                    });
        }

        private void addStickers(Config config) {
            int shortSide = Math.max(1, Math.min(getWidth(), getHeight()));
            for (Sticker sticker : config.stickers) {
                int size = Math.max(1, Math.round(shortSide * sticker.size));
                Bitmap bitmap = loadBitmap(assetFile(sticker.file), size, size);
                if (bitmap == null) continue;
                ImageView image = new ImageView(getContext());
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                image.setImageBitmap(bitmap);
                image.setAlpha(sticker.opacity);
                image.setRotation(sticker.rotation);
                image.setClickable(false);
                image.setFocusable(false);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
                params.leftMargin = Math.round(sticker.x * getWidth() - size / 2f);
                params.topMargin = Math.round(sticker.y * getHeight() - size / 2f);
                addView(image, params);
                stickerViews.add(image);
            }
        }

        private void addGlass(Config config) {
            if (!config.liquidGlassEnabled) return;
            LiquidGlassEngine.LayerView layer =
                    new LiquidGlassEngine.LayerView(getContext(), this);
            layer.updateConfig(config);
            layer.setClickable(false);
            layer.setFocusable(false);
            glassLayer = layer;
            addView(layer, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    /**
     * Saved-property wrapper for the host's one middle content plane. View transforms use the
     * hardware RenderNode path and Android inverse-maps touch coordinates, so the sub-pixel
     * translation does not intercept typing, cursor dragging, keyboard gestures or list scrolling.
     */
    private static final class SpatialViewLayer {
        private WeakReference<View> viewRef = new WeakReference<>(null);
        private float translationX;
        private float translationY;
        private float rotationX;
        private float rotationY;
        private float scaleX = 1f;
        private float scaleY = 1f;
        private boolean captured;
        private boolean transformed;

        void bind(View view) {
            View current = viewRef.get();
            if (current == view && captured) return;
            unbind();
            if (view == null) return;
            viewRef = new WeakReference<>(view);
            translationX = view.getTranslationX();
            translationY = view.getTranslationY();
            rotationX = view.getRotationX();
            rotationY = view.getRotationY();
            scaleX = view.getScaleX();
            scaleY = view.getScaleY();
            captured = true;
            Main.log("spatial middle content layer bound: "
                    + view.getClass().getName());
        }

        void apply(boolean active, float cameraX, float cameraY, float density) {
            View view = viewRef.get();
            if (view == null || !captured) return;
            if (!active) {
                restore();
                return;
            }
            float cleanDensity = Math.max(0.01f, density);
            float x = finiteOrZero(cameraX);
            float y = finiteOrZero(cameraY);
            view.setTranslationX(
                    translationX + spatialForegroundOffsetX(
                            x, cleanDensity));
            view.setTranslationY(
                    translationY + spatialForegroundOffsetY(
                            y, cleanDensity));
            // Translation alone creates the requested occlusion reveal. Rotation and scale remain
            // exactly at the host values so text stays crisp and the effect never reads as a card.
            transformed = true;
        }

        void restore() {
            View view = viewRef.get();
            if (view == null || !captured || !transformed) return;
            view.setTranslationX(translationX);
            view.setTranslationY(translationY);
            view.setRotationX(rotationX);
            view.setRotationY(rotationY);
            view.setScaleX(scaleX);
            view.setScaleY(scaleY);
            transformed = false;
        }

        void unbind() {
            restore();
            viewRef.clear();
            captured = false;
        }
    }

    // Legacy, separately selectable shake effect. It uses linear acceleration only and is kept
    // mutually exclusive with SpatialMotionController at config, sensor and wallpaper-writer
    // levels, so its deliberately elastic motion can never leak into the stable spatial scene.
    private static final class ShakeSensor implements android.hardware.SensorEventListener {
        private final android.hardware.SensorManager manager;
        private final android.hardware.Sensor linearAccel;
        private final android.hardware.Sensor accelerometer;
        private final RuntimeOverlay overlay;
        private boolean registered;
        private final float[] gravity = new float[3];
        private boolean haveGravity;

        ShakeSensor(android.content.Context context, RuntimeOverlay overlay) {
            this.overlay = overlay;
            android.hardware.SensorManager sm = (android.hardware.SensorManager)
                    context.getSystemService(android.content.Context.SENSOR_SERVICE);
            manager = sm;
            // Linear acceleration is a translation signal: its direction IS the direction you move
            // the phone, and gravity is already removed so a still phone reads ~0. The gyroscope was
            // wrong here because it measures rotation, not the direction of a shake.
            linearAccel = sm == null ? null
                    : sm.getDefaultSensor(android.hardware.Sensor.TYPE_LINEAR_ACCELERATION);
            accelerometer = sm == null ? null
                    : sm.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER);
        }

        void start() {
            if (registered || manager == null) return;
            android.hardware.Sensor sensor = linearAccel != null ? linearAccel : accelerometer;
            if (sensor == null) return;
            registered = manager.registerListener(
                    this, sensor, android.hardware.SensorManager.SENSOR_DELAY_GAME);
            if (registered) {
                Main.log("shake parallax sensor started: "
                        + (linearAccel != null ? "linear-accel" : "accelerometer"));
            }
        }

        void stop() {
            if (!registered || manager == null) return;
            manager.unregisterListener(this);
            registered = false;
            haveGravity = false;
        }

        @Override public void onSensorChanged(android.hardware.SensorEvent event) {
            float ax;
            float ay;
            if (event.sensor.getType() == android.hardware.Sensor.TYPE_LINEAR_ACCELERATION) {
                ax = event.values[0];
                ay = event.values[1];
            } else {
                // Fallback: low-pass out gravity from the raw accelerometer, keep the remainder.
                float g = 0.8f;
                gravity[0] = g * gravity[0] + (1 - g) * event.values[0];
                gravity[1] = g * gravity[1] + (1 - g) * event.values[1];
                if (!haveGravity) { haveGravity = true; return; }
                ax = event.values[0] - gravity[0];
                ay = event.values[1] - gravity[1];
            }
            float mag = (float) Math.sqrt(ax * ax + ay * ay);
            if (mag < SHAKE_ACCEL_TRIGGER) return;
            // Straighten the drift: shrink whichever axis is the smaller share of this shove so the
            // motion runs along its dominant direction instead of wandering. A balanced diagonal
            // (both axes comparable) is left intact so right-up / left-down shakes stay diagonal.
            float axisRatio = Math.min(Math.abs(ax), Math.abs(ay))
                    / Math.max(Math.abs(ax), Math.abs(ay) + 0.0001f);
            float trim = SHAKE_AXIS_STRAIGHTEN + (1f - SHAKE_AXIS_STRAIGHTEN) * axisRatio;
            if (Math.abs(ax) < Math.abs(ay)) {
                ax *= trim;
            } else {
                ay *= trim;
            }
            // Device axes in portrait: +X points right, +Y points up. Screen translation is +X right,
            // +Y down, so vertical is negated. The wallpaper drifts in the direction of the shove.
            float scale = SHAKE_ACCEL_IMPULSE * (mag - SHAKE_ACCEL_TRIGGER) / mag;
            overlay.addShakeImpulse(ax * scale, -ay * scale);
        }

        @Override public void onAccuracyChanged(android.hardware.Sensor sensor, int accuracy) {}
    }
}
