package com.dsmod.probe;

import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Shared liquid-glass compositor used by every host surface.
 *
 * <p>The expensive input is one down-sampled backdrop texture owned by {@link LayerView}. Chat
 * bubbles, buttons, panels and dividers only add cheap rounded masks over that shared texture.
 * There is deliberately no per-control screenshot or blur allocation.</p>
 */
final class LiquidGlassEngine {
    static final int KIND_USER_BUBBLE = 1;
    static final int KIND_ASSISTANT_BUBBLE = 2;
    static final int KIND_INPUT = 3;
    static final int KIND_ACTION = 4;
    static final int KIND_ATTACHMENT = 5;
    static final int KIND_SIDEBAR_PANEL = 6;
    static final int KIND_SETTINGS_CARD = 7;
    static final int KIND_DIVIDER = 8;
    static final int KIND_MODE_ITEM = 9;
    static final int KIND_MODE_SELECTED = 10;
    static final int KIND_SIDEBAR_SEARCH = 11;
    static final int KIND_FULLSCREEN_EDGE = 12;

    private static final int MAX_SURFACES = 144;
    private static final Object LOCK = new Object();
    private static final ArrayList<SurfaceRecord> SURFACES = new ArrayList<>();
    private static final ArrayList<NativeAnchor> TEXT_ANCHORS = new ArrayList<>();
    private static long nextSurfaceId = 1L;
    private static int routeGeneration = 1;
    private static String currentRoute = "";
    private static float sidebarProgress;
    private static int sidebarWidthPx;
    private static WeakReference<LayerView> currentLayer = new WeakReference<>(null);
    private static boolean inputBoundsLogged;
    private static boolean inputOuterBoundsLogged;
    private static boolean modeBoundsLogged;
    private static boolean assistantBoundsLogged;
    private static long lastNativeDiscoveryAt;
    private static int sidebarLogBucket = -1;

    private LiquidGlassEngine() {}

    static final class SurfaceHandle {
        private final SurfaceRecord record;

        SurfaceHandle(SurfaceRecord record) {
            this.record = record;
        }

        void setBounds(int left, int top, int right, int bottom) {
            if (right <= left || bottom <= top) return;
            long now = SystemClock.uptimeMillis();
            synchronized (LOCK) {
                if (record.generation != routeGeneration) return;
                float oldCx = record.bounds.centerX();
                float oldCy = record.bounds.centerY();
                long elapsed = Math.max(1L, now - record.lastBoundsAt);
                if (record.hasBounds && elapsed < 250L) {
                    float vx = (left + (right - left) * 0.5f - oldCx)
                            * 16.67f / elapsed;
                    float vy = (top + (bottom - top) * 0.5f - oldCy)
                            * 16.67f / elapsed;
                    record.velocityX = record.velocityX * 0.68f + vx * 0.32f;
                    record.velocityY = record.velocityY * 0.68f + vy * 0.32f;
                }
                record.bounds.set(left, top, right, bottom);
                record.hasBounds = true;
                record.lastBoundsAt = now;
                record.capturedSidebarShiftPx =
                        sidebarProgress * Math.max(0, sidebarWidthPx);
                if (record.appearAt == 0L) record.appearAt = now;
                if (record.kind == KIND_INPUT && !inputBoundsLogged) {
                    inputBoundsLogged = true;
                    record.boundsLogged = true;
                    Main.log("input glass exact bounds="
                            + left + "," + top + "-" + right + "," + bottom);
                }
            }
            invalidateLayer();
        }

        void setBottomInsetDp(float insetDp) {
            synchronized (LOCK) {
                record.bottomInsetDp = Math.max(0f, insetDp);
            }
        }

        void bindOwner(Object owner) {
            if (owner == null) return;
            synchronized (LOCK) {
                record.owner = new WeakReference<>(owner);
                record.ownerBound = true;
            }
        }
    }

    private static final class SurfaceRecord {
        final long id;
        final int kind;
        final float radiusDp;
        final int generation;
        final long registeredAt;
        final RectF bounds = new RectF();
        boolean hasBounds;
        long lastBoundsAt;
        long appearAt;
        float velocityX;
        float velocityY;
        float capturedSidebarShiftPx;
        float bottomInsetDp;
        boolean boundsLogged;
        boolean ownerBound;
        WeakReference<Object> owner = new WeakReference<>(null);

        SurfaceRecord(long id, int kind, float radiusDp, int generation) {
            this.id = id;
            this.kind = kind;
            this.radiusDp = radiusDp;
            this.generation = generation;
            registeredAt = SystemClock.uptimeMillis();
        }
    }

    private static final class NativeAnchor {
        final WeakReference<View> view;
        final long boundAt;
        boolean rawLogged;
        boolean inputLogged;
        boolean sidebarLogged;

        NativeAnchor(View view) {
            this.view = new WeakReference<>(view);
            boundAt = SystemClock.uptimeMillis();
        }
    }

    private static final class DrawRegion {
        final long id;
        final int kind;
        final RectF bounds;
        final float radiusPx;
        final long appearAt;
        final long lastBoundsAt;
        final float velocityX;
        final float velocityY;

        DrawRegion(
                long id, int kind, RectF bounds, float radiusPx,
                long appearAt, long lastBoundsAt,
                float velocityX, float velocityY) {
            this.id = id;
            this.kind = kind;
            this.bounds = bounds;
            this.radiusPx = radiusPx;
            this.appearAt = appearAt;
            this.lastBoundsAt = lastBoundsAt;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
        }

        boolean isSmall() {
            return kind == KIND_ACTION || kind == KIND_ATTACHMENT
                    || kind == KIND_MODE_ITEM || kind == KIND_MODE_SELECTED
                    || kind == KIND_SIDEBAR_SEARCH
                    || Math.min(bounds.width(), bounds.height()) < radiusPx * 4.5f;
        }

        boolean isDivider() {
            return kind == KIND_DIVIDER;
        }

        boolean isFullscreenEdge() {
            return kind == KIND_FULLSCREEN_EDGE;
        }

        boolean isTextBearing() {
            return kind == KIND_USER_BUBBLE
                    || kind == KIND_ASSISTANT_BUBBLE
                    || kind == KIND_INPUT
                    || kind == KIND_MODE_ITEM
                    || kind == KIND_MODE_SELECTED
                    || kind == KIND_SIDEBAR_SEARCH;
        }
    }

    static SurfaceHandle registerSurface(int kind, float radiusDp) {
        if (!ChatAppearance.glassEnabledForRender()) return null;
        SurfaceRecord record;
        synchronized (LOCK) {
            record = new SurfaceRecord(
                    nextSurfaceId++, kind, Math.max(0f, radiusDp), routeGeneration);
            SURFACES.add(record);
            if (SURFACES.size() > MAX_SURFACES) {
                int remove = SURFACES.size() - MAX_SURFACES;
                for (int i = 0; i < remove; i++) SURFACES.remove(0);
            }
        }
        return new SurfaceHandle(record);
    }

    /**
     * Retires transient Compose surfaces before their owning composable builds its next
     * generation.  Unlike a time-to-live, this keeps static controls visible indefinitely while
     * still removing glass in the exact recomposition that removes the host node.
     */
    static void clearSurfaceKinds(int... kinds) {
        if (kinds == null || kinds.length == 0) return;
        boolean removed = false;
        boolean modeRemoved = false;
        synchronized (LOCK) {
            for (int i = SURFACES.size() - 1; i >= 0; i--) {
                int kind = SURFACES.get(i).kind;
                for (int wanted : kinds) {
                    if (kind == wanted) {
                        SURFACES.remove(i);
                        removed = true;
                        if (kind == KIND_MODE_ITEM
                                || kind == KIND_MODE_SELECTED) {
                            modeRemoved = true;
                        }
                        break;
                    }
                }
            }
            if (modeRemoved) modeBoundsLogged = false;
        }
        if (removed) invalidateLayer();
    }

    /**
     * Binds DeepSeek's real Android text editor instead of using Compose Lookahead coordinates.
     * The same host factory creates the bottom message editor and the sidebar search editor, so
     * classification is intentionally deferred until drawing, when their true window positions
     * are known.
     */
    static void bindTextAnchor(final View view) {
        if (view == null) return;
        synchronized (LOCK) {
            for (int i = TEXT_ANCHORS.size() - 1; i >= 0; i--) {
                View existing = TEXT_ANCHORS.get(i).view.get();
                if (existing == null) {
                    TEXT_ANCHORS.remove(i);
                } else if (existing == view) {
                    return;
                }
            }
            TEXT_ANCHORS.add(new NativeAnchor(view));
            while (TEXT_ANCHORS.size() > 24) TEXT_ANCHORS.remove(0);
        }
        Main.log("liquid glass native text anchor bound: "
                + view.getClass().getName());
        view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(
                    View changed, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                invalidateLayer();
            }
        });
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View attached) {
                invalidateLayer();
            }

            @Override public void onViewDetachedFromWindow(View detached) {
                invalidateLayer();
            }
        });
        invalidateLayer();
    }

    private static void discoverNativeTextAnchors(View view) {
        if (view == null) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastNativeDiscoveryAt < 250L) return;
        lastNativeDiscoveryAt = now;
        discoverNativeTextAnchorsRecursive(view);
    }

    private static void discoverNativeTextAnchorsRecursive(View view) {
        if (view instanceof android.widget.EditText) {
            bindTextAnchor(view);
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            discoverNativeTextAnchorsRecursive(group.getChildAt(i));
        }
    }

    static void onRouteChanged(String route) {
        String value = route == null ? "" : route;
        synchronized (LOCK) {
            if (!value.equals(currentRoute)) {
                currentRoute = value;
                routeGeneration++;
                SURFACES.clear();
                inputBoundsLogged = false;
                inputOuterBoundsLogged = false;
                modeBoundsLogged = false;
                assistantBoundsLogged = false;
                sidebarLogBucket = -1;
            }
            if (!ChatAppearance.isChatRoute(value)) {
                sidebarProgress = 0f;
                sidebarWidthPx = 0;
            }
        }
        invalidateLayer();
    }

    static void onSidebarProgress(float progress, int widthPx) {
        float resolved = clamp(progress, 0f, 1f);
        int bucket = resolved <= 0.01f ? 0 : (resolved >= 0.99f ? 2 : 1);
        boolean shouldLog;
        int resolvedWidth;
        synchronized (LOCK) {
            sidebarProgress = resolved;
            if (widthPx > 0) sidebarWidthPx = widthPx;
            if (resolved <= 0.01f) {
                for (int i = SURFACES.size() - 1; i >= 0; i--) {
                    if (SURFACES.get(i).kind == KIND_SIDEBAR_SEARCH) {
                        SURFACES.remove(i);
                    }
                }
            }
            resolvedWidth = sidebarWidthPx;
            shouldLog = bucket != sidebarLogBucket;
            sidebarLogBucket = bucket;
        }
        if (shouldLog) {
            Main.log("sidebar liquid seam progress="
                    + Math.round(resolved * 100f) + "% width=" + resolvedWidth);
        }
        invalidateLayer();
    }

    static void onTouchEvent(MotionEvent event) {
        if (event == null) return;
        LayerView layer = currentLayer.get();
        if (layer != null) layer.observeTouch(event);
    }

    static String capabilitySummary(Context context, ChatAppearance.Config config) {
        Profile profile = Profile.resolve(context, config);
        if (profile.backend == Profile.BACKEND_SHADER) {
            return UiLanguage.text(context,
                    "实时折射 · 共享纹理 · " + profile.label,
                    "Real-time refraction · shared texture · "
                            + profile.englishLabel);
        }
        if (profile.backend == Profile.BACKEND_CACHED) {
            return UiLanguage.text(context,
                    "共享模糊 · 动态高光 · " + profile.label,
                    "Shared blur · dynamic highlights · "
                            + profile.englishLabel);
        }
        return UiLanguage.text(context,
                "静态磨砂 · 低功耗兼容",
                "Static frosting · low-power compatibility");
    }

    private static void attach(LayerView layer) {
        currentLayer = new WeakReference<>(layer);
    }

    private static void detach(LayerView layer) {
        if (currentLayer.get() == layer) currentLayer = new WeakReference<>(null);
    }

    private static void invalidateLayer() {
        final LayerView layer = currentLayer.get();
        if (layer == null) return;
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            layer.invalidate();
        } else {
            layer.postInvalidateOnAnimation();
        }
    }

    private static ArrayList<DrawRegion> snapshotRegions(
            LayerView layer, float density, int profileLevel) {
        int[] location = new int[2];
        layer.getLocationInWindow(location);
        int width = layer.getWidth();
        int height = layer.getHeight();
        RectF viewport = new RectF(0f, 0f, width, height);
        ArrayList<DrawRegion> result = new ArrayList<>();
        ArrayList<SurfaceRecord> copy;
        ArrayList<NativeAnchor> nativeAnchors;
        float drawer;
        int drawerWidth;
        String route;
        synchronized (LOCK) {
            copy = new ArrayList<>(SURFACES);
            nativeAnchors = new ArrayList<>(TEXT_ANCHORS);
            drawer = sidebarProgress;
            drawerWidth = sidebarWidthPx;
            route = currentRoute;
        }
        int resolvedDrawerWidth = drawerWidth > 0
                ? drawerWidth : Math.round(width * 0.8f);
        float liveDrawerEdge = drawer > 0.002f
                ? clamp(resolvedDrawerWidth * drawer, 0f, Math.max(0f, width - 1f))
                : 0f;

        // The main glass sheet starts at the host drawer's live boundary. Keeping this region at
        // x=0 produced a second, stationary refracting edge while DeepSeek's content moved right.
        // bn2.c() supplies drawer on every native frame, so this edge and the real divider now
        // share exactly the same coordinate throughout opening, closing, and drag gestures.
        long snapshotAt = SystemClock.uptimeMillis();
        result.add(new DrawRegion(
                -12L, KIND_FULLSCREEN_EDGE,
                new RectF(liveDrawerEdge, 0f, width, height),
                22f * density, snapshotAt - 1000L, snapshotAt,
                0f, 0f));

        // Newest callbacks win. This collapses duplicate nodes produced by recomposition without
        // expiring static nodes whose onGloballyPositioned callback quite correctly stops firing.
        Collections.sort(copy, new Comparator<SurfaceRecord>() {
            @Override public int compare(SurfaceRecord first, SurfaceRecord second) {
                return first.registeredAt < second.registeredAt ? 1
                        : (first.registeredAt == second.registeredAt ? 0 : -1);
            }
        });
        boolean selectedModeAdded = false;
        for (SurfaceRecord surface : copy) {
            if (!surface.hasBounds || surface.generation != routeGeneration) continue;
            if (surface.ownerBound && surface.owner.get() == null) continue;
            if (surface.kind == KIND_MODE_SELECTED) {
                if (selectedModeAdded) continue;
                selectedModeAdded = true;
            }
            RectF local = new RectF(
                    surface.bounds.left - location[0],
                    surface.bounds.top - location[1],
                    surface.bounds.right - location[0],
                    surface.bounds.bottom - location[1]);
            if (surface.kind == KIND_INPUT) {
                // DeepSeek's Modifier is attached to the editable text slab, while the visible
                // send box continues through the attachment/mode/action row below it. Expand that
                // measured slab to the real outer shell. The height guard avoids over-expanding
                // variants whose host Modifier already owns the complete container.
                float side = 10f * density;
                float top = 4f * density;
                float bottom = local.height() < 72f * density
                        ? 49f * density : 8f * density;
                local.set(
                        local.left - side, local.top - top,
                        local.right + side, local.bottom + bottom);
                if (!inputOuterBoundsLogged) {
                    inputOuterBoundsLogged = true;
                    Main.log("input glass outer shell bounds="
                            + Math.round(local.left) + "," + Math.round(local.top)
                            + "-" + Math.round(local.right) + ","
                            + Math.round(local.bottom));
                }
            }
            if (surface.bottomInsetDp > 0f) {
                float inset = Math.min(
                        surface.bottomInsetDp * density,
                        Math.max(0f, local.height() - 12f * density));
                local.bottom -= inset;
            }
            if (ChatAppearance.isChatRoute(route)) {
                // Compose drawer motion is implemented as a graphics-layer transform. Its
                // onGloballyPositioned callback is therefore not guaranteed to run on every
                // animation frame. Move the glass by the drawer delta since the bounds were
                // captured, so it remains attached to the input and message surfaces instead
                // of staying fixed over the newly revealed sidebar.
                local.offset(
                        liveDrawerEdge - surface.capturedSidebarShiftPx,
                        0f);
            }
            if (!RectF.intersects(local, viewport)) continue;
            local.intersect(viewport);
            if (local.width() < 2f || local.height() < 2f) continue;
            boolean duplicate = false;
            for (DrawRegion existing : result) {
                if (existing.kind == surface.kind
                        && Math.abs(existing.bounds.left - local.left) < 2.5f
                        && Math.abs(existing.bounds.top - local.top) < 2.5f
                        && Math.abs(existing.bounds.right - local.right) < 2.5f
                        && Math.abs(existing.bounds.bottom - local.bottom) < 2.5f) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                result.add(new DrawRegion(
                        surface.id, surface.kind, local,
                        surface.radiusDp * density, surface.appearAt,
                        surface.lastBoundsAt,
                        surface.velocityX, surface.velocityY));
                if (surface.kind == KIND_ASSISTANT_BUBBLE
                        && !assistantBoundsLogged) {
                    assistantBoundsLogged = true;
                    Main.log("assistant glass body bounds="
                            + Math.round(local.left) + ","
                            + Math.round(local.top) + "-"
                            + Math.round(local.right) + ","
                            + Math.round(local.bottom)
                            + (surface.bottomInsetDp > 0f
                                    ? " (feedback row excluded)" : ""));
                }
            }
        }

        if (ChatAppearance.isChatRoute(route)) {
            DrawRegion inputAnchor = null;
            NativeAnchor inputOwner = null;
            DrawRegion sidebarSearch = null;
            NativeAnchor sidebarOwner = null;
            float drawerEdge = liveDrawerEdge;
            for (NativeAnchor anchor : nativeAnchors) {
                View view = anchor.view.get();
                if (view == null || !view.isAttachedToWindow() || !view.isShown()
                        || view.getWidth() <= 0 || view.getHeight() <= 0) {
                    continue;
                }
                int[] anchorLocation = new int[2];
                view.getLocationInWindow(anchorLocation);
                RectF raw = new RectF(
                        anchorLocation[0] - location[0],
                        anchorLocation[1] - location[1],
                        anchorLocation[0] - location[0] + view.getWidth(),
                        anchorLocation[1] - location[1] + view.getHeight());
                if (!anchor.rawLogged) {
                    anchor.rawLogged = true;
                    Main.log("liquid glass native text anchor raw="
                            + Math.round(raw.left) + "," + Math.round(raw.top)
                            + "-" + Math.round(raw.right) + ","
                            + Math.round(raw.bottom));
                }
                if (!RectF.intersects(raw, viewport)) continue;

                // The native editor occupies the text portion of DeepSeek's Compose send box.
                // Expand by the host's measured paddings to cover the complete input surface,
                // including its bottom action row. This naturally follows keyboard and multiline
                // resizing because the anchor is queried in window coordinates every frame.
                if (raw.bottom >= height * 0.68f && raw.width() >= width * 0.42f) {
                    RectF outer = new RectF(
                            raw.left - 10f * density,
                            raw.top - 4f * density,
                            raw.right + 10f * density,
                            raw.bottom + 49f * density);
                    outer.intersect(viewport);
                    DrawRegion candidate = new DrawRegion(
                            -100000L - (System.identityHashCode(view) & 0x7FFFFFFFL),
                            KIND_INPUT, outer, 22f * density,
                            anchor.boundAt, SystemClock.uptimeMillis(), 0f, 0f);
                    if (inputAnchor == null
                            || candidate.bounds.width() > inputAnchor.bounds.width()) {
                        inputAnchor = candidate;
                        inputOwner = anchor;
                    }
                    continue;
                }

                // A visible single-line editor inside the revealed drawer is its search surface.
                // It uses its actual transformed location and never receives the chat-content
                // drawer offset, eliminating the former cumulative grow/drift.
                if (drawer > 0.02f && raw.top < height * 0.42f
                        && raw.centerX() <= drawerEdge + 18f * density
                        && raw.width() >= Math.max(80f * density, drawerEdge * 0.35f)) {
                    RectF search = new RectF(
                            raw.left - 5f * density,
                            raw.top - 3f * density,
                            raw.right + 5f * density,
                            raw.bottom + 3f * density);
                    search.intersect(viewport);
                    DrawRegion candidate = new DrawRegion(
                            -200000L - (System.identityHashCode(view) & 0x7FFFFFFFL),
                            KIND_SIDEBAR_SEARCH, search, 16f * density,
                            anchor.boundAt, SystemClock.uptimeMillis(), 0f, 0f);
                    if (sidebarSearch == null
                            || candidate.bounds.width() > sidebarSearch.bounds.width()) {
                        sidebarSearch = candidate;
                        sidebarOwner = anchor;
                    }
                }
            }
            if (inputAnchor != null) {
                result.add(inputAnchor);
                if (inputOwner != null && !inputOwner.inputLogged) {
                    inputOwner.inputLogged = true;
                    RectF bounds = inputAnchor.bounds;
                    Main.log("input glass native anchor bounds="
                            + Math.round(bounds.left) + "," + Math.round(bounds.top)
                            + "-" + Math.round(bounds.right) + ","
                            + Math.round(bounds.bottom));
                }
            }
            if (sidebarSearch != null) {
                result.add(sidebarSearch);
                if (sidebarOwner != null && !sidebarOwner.sidebarLogged) {
                    sidebarOwner.sidebarLogged = true;
                    RectF bounds = sidebarSearch.bounds;
                    Main.log("sidebar search glass native anchor bounds="
                            + Math.round(bounds.left) + "," + Math.round(bounds.top)
                            + "-" + Math.round(bounds.right) + ","
                            + Math.round(bounds.bottom));
                }
            }

        }

        // Drawer progress is reset whenever the host leaves chat, so the seam does not need a
        // fragile route-name gate. This also keeps it visible during transient navigation entries
        // emitted while the drawer is opening or closing.
        if (drawer > 0.002f) {
            float edge = liveDrawerEdge;
            // The drawer boundary is a broad transparent optical band, not a one-pixel divider.
            // Use dp rather than a device-specific fraction so phones, tablets and foldables keep
            // the same physical feel, and clip it at the viewport while the drawer first appears.
            float halfBand = Math.max(32f * density,
                    Math.min(48f * density, width * 0.104f));
            if (edge > 0.5f && edge < width - 0.5f) {
                float left = Math.max(0f, edge - halfBand);
                float right = Math.min(width, edge + halfBand);
                result.add(new DrawRegion(
                        -11L, KIND_DIVIDER,
                        new RectF(left, 0f, right, height),
                        Math.min(18f * density, (right - left) * 0.5f),
                        SystemClock.uptimeMillis() - 1000L,
                        SystemClock.uptimeMillis(), 0f, 0f));
            }
        }

        int limit = profileLevel >= 3 ? 52 : (profileLevel == 2 ? 32 : 18);
        if (result.size() > limit) {
            // Keep structural panels and the newest visible controls. Large off-screen histories
            // cannot force the compositor to do unbounded work.
            ArrayList<DrawRegion> limited = new ArrayList<>();
            for (DrawRegion region : result) {
                if (region.kind == KIND_SIDEBAR_PANEL
                        || region.kind == KIND_DIVIDER
                        || region.kind == KIND_FULLSCREEN_EDGE) {
                    limited.add(region);
                }
            }
            for (DrawRegion region : result) {
                if (limited.size() >= limit) break;
                if (region.kind != KIND_SIDEBAR_PANEL
                        && region.kind != KIND_DIVIDER
                        && region.kind != KIND_FULLSCREEN_EDGE) {
                    limited.add(region);
                }
            }
            result = limited;
        }
        if (!modeBoundsLogged) {
            for (DrawRegion region : result) {
                if (region.kind == KIND_MODE_ITEM
                        || region.kind == KIND_MODE_SELECTED) {
                    modeBoundsLogged = true;
                    RectF bounds = region.bounds;
                    Main.log("model mode glass bounds="
                            + Math.round(bounds.left) + "," + Math.round(bounds.top)
                            + "-" + Math.round(bounds.right) + ","
                            + Math.round(bounds.bottom));
                    break;
                }
            }
        }
        return result;
    }

    static final class LayerView extends View {
        private static final long APPEAR_DURATION_MS = 230L;
        private final ViewGroup sourceHost;
        private final Paint glassPaint = new Paint(Paint.ANTI_ALIAS_FLAG
                | Paint.FILTER_BITMAP_FLAG);
        private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clipPath = new Path();
        private final Matrix bitmapMatrix = new Matrix();
        private ChatAppearance.Config config = new ChatAppearance.Config();
        private Profile profile;
        private Bitmap backdrop;
        private float sourceScale = 0.5f;
        private float capturedSceneX;
        private float sceneX;
        private float sceneVelocityX;
        private long sceneAt;
        private float press;
        private float pressTarget;
        private float touchX;
        private float touchY;
        private long lastFrameAt;
        private long lastProfileCheckAt;
        private boolean sourceDirty = true;
        private RuntimeShader shader;
        private BitmapShader inputShader;
        private String shaderKey = "";
        private String loggedProfile = "";
        private int loggedRegionKindsMask;
        private boolean fullscreenBaseLogged;
        private boolean stableMotionSourceLogged;

        LayerView(Context context, ViewGroup sourceHost) {
            super(context);
            this.sourceHost = sourceHost;
            setWillNotDraw(false);
            setClickable(false);
            setFocusable(false);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            attach(this);
        }

        void updateConfig(ChatAppearance.Config value) {
            config = value == null ? new ChatAppearance.Config() : value.copy();
            profile = null;
            sourceDirty = true;
            shader = null;
            inputShader = null;
            shaderKey = "";
            loggedRegionKindsMask = 0;
            fullscreenBaseLogged = false;
            invalidate();
        }

        void setSceneTranslation(float translationX) {
            long now = SystemClock.uptimeMillis();
            if (sceneAt > 0L) {
                float elapsedFrames = Math.max(0.5f, (now - sceneAt) / 16.67f);
                float velocity = (translationX - sceneX) / elapsedFrames;
                sceneVelocityX = sceneVelocityX * 0.64f + velocity * 0.36f;
            }
            sceneAt = now;
            sceneX = translationX;
            // Keep one source texture for the whole transition. A delayed recapture used to swap
            // the bitmap 140 ms after movement stopped, which was visible as a one-frame flash and
            // an abrupt change in refraction. sceneShift already tracks the moving wallpaper.
            if (!stableMotionSourceLogged) {
                stableMotionSourceLogged = true;
                Main.log("liquid glass motion source stable; settle recapture disabled");
            }
            invalidate();
        }

        void release() {
            if (backdrop != null) {
                backdrop.recycle();
                backdrop = null;
            }
            shader = null;
            inputShader = null;
            detach(this);
        }

        void observeTouch(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                touchX = event.getRawX();
                touchY = event.getRawY();
                int[] location = new int[2];
                getLocationInWindow(location);
                touchX -= location[0];
                touchY -= location[1];
                pressTarget = hitGlass(touchX, touchY) ? 1f : 0f;
            } else if (action == MotionEvent.ACTION_MOVE) {
                int[] location = new int[2];
                getLocationInWindow(location);
                touchX = event.getRawX() - location[0];
                touchY = event.getRawY() - location[1];
                if (pressTarget > 0f && !hitGlass(touchX, touchY)) pressTarget = 0f;
            } else if (action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_CANCEL) {
                pressTarget = 0f;
            }
            if (Math.abs(pressTarget - press) > 0.001f) postInvalidateOnAnimation();
        }

        private boolean hitGlass(float x, float y) {
            Profile resolved = resolveProfile(false);
            ArrayList<DrawRegion> regions = snapshotRegions(
                    this, getResources().getDisplayMetrics().density,
                    resolved.level);
            for (int i = regions.size() - 1; i >= 0; i--) {
                DrawRegion region = regions.get(i);
                if (!region.isFullscreenEdge()
                        && region.bounds.contains(x, y)) {
                    return true;
                }
            }
            return false;
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            Main.log("liquid glass layer attached");
            postInvalidateOnAnimation();
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            release();
        }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            sourceDirty = true;
            shader = null;
            inputShader = null;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!config.liquidGlassEnabled || getWidth() <= 0 || getHeight() <= 0) return;
            long now = SystemClock.uptimeMillis();
            updateAnimation(now);
            Profile resolved = resolveProfile(true);
            if (sourceDirty || backdrop == null || backdrop.isRecycled()) {
                rebuildBackdrop(resolved);
            }
            if (backdrop == null || backdrop.isRecycled()) return;

            float density = getResources().getDisplayMetrics().density;
            drawFullscreenGlassBase(canvas);
            ArrayList<DrawRegion> regions =
                    snapshotRegions(this, density, resolved.level);
            if (regions.isEmpty()) return;
            logNewRegionKinds(regions);

            if (resolved.backend == Profile.BACKEND_SHADER
                    && Build.VERSION.SDK_INT >= 33
                    && ensureRuntimeShader(resolved)) {
                Api33.draw(
                        this, canvas, regions, shader, inputShader, glassPaint,
                        edgePaint, density, now);
            } else {
                drawCached(canvas, regions, resolved, density, now);
            }
            drawMagneticBridges(canvas, regions, resolved, density, now);

            boolean animate = Math.abs(pressTarget - press) > 0.003f
                    || Math.abs(sceneVelocityX) > 0.08f;
            if (!animate) {
                for (DrawRegion region : regions) {
                    if (now - region.appearAt < APPEAR_DURATION_MS
                            || (now - region.lastBoundsAt < 280L
                            && (Math.abs(region.velocityX) > 0.04f
                            || Math.abs(region.velocityY) > 0.04f))) {
                        animate = true;
                        break;
                    }
                }
            }
            sceneVelocityX *= 0.82f;
            if (animate) postInvalidateOnAnimation();
        }

        /**
         * Draws the centre of the one-piece viewport glass with one filtered bitmap sample.
         * RuntimeShader work is then limited to edge bands, keeping full-screen glass practical on
         * mid-range devices instead of evaluating a multi-tap shader for every screen pixel.
         */
        private void drawFullscreenGlassBase(Canvas canvas) {
            float inverse = 1f / Math.max(0.01f, sourceScale);
            bitmapMatrix.reset();
            bitmapMatrix.setScale(inverse, inverse);
            bitmapMatrix.postTranslate(sceneX - capturedSceneX, 0f);
            bitmapMatrix.postScale(
                    1.0035f, 1.0035f,
                    getWidth() * 0.5f, getHeight() * 0.5f);
            glassPaint.setShader(null);
            // The centre is intentionally almost clear. A stronger full-screen bitmap pass sat
            // above every native glyph and made DeepSeek's black/white text look gray even though
            // its actual text colour was unchanged. Refraction and highlights remain at edges.
            glassPaint.setAlpha(6);
            canvas.drawBitmap(backdrop, bitmapMatrix, glassPaint);
            glassPaint.setAlpha(255);

            tintPaint.setShader(null);
            tintPaint.setStyle(Paint.Style.FILL);
            tintPaint.setColor(0x01FFFFFF);
            canvas.drawRect(0f, 0f, getWidth(), getHeight(), tintPaint);
            if (!fullscreenBaseLogged) {
                fullscreenBaseLogged = true;
                Main.log("fullscreen liquid glass base active; refraction limited to edges");
            }
        }

        private void logNewRegionKinds(ArrayList<DrawRegion> regions) {
            int visible = 0;
            for (DrawRegion region : regions) {
                if (region.kind > 0 && region.kind < 31) {
                    visible |= 1 << region.kind;
                }
            }
            int added = visible & ~loggedRegionKindsMask;
            if (added == 0) return;
            loggedRegionKindsMask |= added;
            StringBuilder names = new StringBuilder();
            for (int kind = 1; kind <= KIND_FULLSCREEN_EDGE; kind++) {
                if ((added & (1 << kind)) == 0) continue;
                if (names.length() > 0) names.append(',');
                names.append(kindName(kind));
            }
            Main.log("liquid glass draw active kinds=" + names);
        }

        private static String kindName(int kind) {
            switch (kind) {
                case KIND_USER_BUBBLE: return "user-bubble";
                case KIND_ASSISTANT_BUBBLE: return "assistant-bubble";
                case KIND_INPUT: return "input";
                case KIND_ACTION: return "action";
                case KIND_ATTACHMENT: return "attachment";
                case KIND_SIDEBAR_PANEL: return "sidebar";
                case KIND_SETTINGS_CARD: return "settings-card";
                case KIND_DIVIDER: return "divider";
                case KIND_MODE_ITEM: return "mode";
                case KIND_MODE_SELECTED: return "mode-selected";
                case KIND_SIDEBAR_SEARCH: return "sidebar-search";
                case KIND_FULLSCREEN_EDGE: return "fullscreen-edge";
                default: return String.valueOf(kind);
            }
        }

        private void updateAnimation(long now) {
            float frameScale = lastFrameAt == 0L
                    ? 1f : clamp((now - lastFrameAt) / 16.67f, 0.5f, 3f);
            lastFrameAt = now;
            float response = pressTarget > press ? 0.28f : 0.18f;
            press += (pressTarget - press)
                    * (1f - (float) Math.pow(1f - response, frameScale));
            if (Math.abs(pressTarget - press) < 0.002f) press = pressTarget;
        }

        private Profile resolveProfile(boolean periodically) {
            long now = SystemClock.uptimeMillis();
            if (profile == null || (periodically && now - lastProfileCheckAt > 2400L)) {
                Profile resolved = Profile.resolve(getContext(), config);
                lastProfileCheckAt = now;
                if (profile == null || !profile.sameRendering(resolved)) {
                    profile = resolved;
                    sourceScale = resolved.sourceScale;
                    sourceDirty = true;
                    shader = null;
                    inputShader = null;
                } else {
                    profile = resolved;
                }
                String state = resolved.backend + "|" + resolved.level + "|"
                        + resolved.label;
                if (!state.equals(loggedProfile)) {
                    loggedProfile = state;
                    Main.log("liquid glass profile: " + capabilitySummary(getContext(), config)
                            + ", api=" + Build.VERSION.SDK_INT);
                }
            }
            return profile;
        }

        private void rebuildBackdrop(Profile resolved) {
            sourceDirty = false;
            int width = Math.max(2, Math.round(getWidth() * resolved.sourceScale));
            int height = Math.max(2, Math.round(getHeight() * resolved.sourceScale));
            Bitmap next;
            try {
                next = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            } catch (Throwable t) {
                Main.log("liquid glass backdrop allocation failed: " + t);
                return;
            }
            Canvas target = new Canvas(next);
            boolean dark = isDark();
            Paint ambient = new Paint(Paint.ANTI_ALIAS_FLAG);
            ambient.setShader(new LinearGradient(
                    0f, 0f, width, height,
                    dark ? 0xFF1C1C1E : 0xFFF8F8F8,
                    dark ? 0xFF2A2A2D : 0xFFEFEFF0,
                    Shader.TileMode.CLAMP));
            target.drawRect(0f, 0f, width, height, ambient);
            target.save();
            target.scale(resolved.sourceScale, resolved.sourceScale);
            for (int i = 0; i < sourceHost.getChildCount(); i++) {
                View child = sourceHost.getChildAt(i);
                boolean wallpaper =
                        "deekseep_wallpaper_source".equals(child.getTag());
                if (child == this || child.getVisibility() != View.VISIBLE
                        || (!wallpaper && child.getAlpha() <= 0.001f)) {
                    continue;
                }
                float captureAlpha = wallpaper
                        ? Math.max(0.78f, child.getAlpha())
                        : clamp(child.getAlpha(), 0f, 1f);
                int save = target.saveLayerAlpha(
                        0f, 0f, getWidth(), getHeight(),
                        Math.round(captureAlpha * 255f));
                try {
                    target.translate(
                            child.getLeft() + child.getTranslationX(),
                            child.getTop() + child.getTranslationY());
                    target.concat(child.getMatrix());
                    child.draw(target);
                } catch (Throwable t) {
                    Main.log("liquid glass source child skipped: " + t);
                } finally {
                    target.restoreToCount(save);
                }
            }
            target.restore();
            if (backdrop != null && backdrop != next && !backdrop.isRecycled()) {
                backdrop.recycle();
            }
            backdrop = next;
            capturedSceneX = sceneX;
            inputShader = null;
            shader = null;
        }

        private boolean ensureRuntimeShader(Profile resolved) {
            if (Build.VERSION.SDK_INT < 33 || backdrop == null) return false;
            try {
                String key = resolved.level + "|" + backdrop.getWidth()
                        + "x" + backdrop.getHeight();
                if (shader == null || inputShader == null || !key.equals(shaderKey)) {
                    shader = Api33.createShader(resolved.level >= 3);
                    inputShader = new BitmapShader(
                            backdrop, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    Matrix inputMatrix = new Matrix();
                    float inverse = 1f / Math.max(0.01f, sourceScale);
                    inputMatrix.setScale(inverse, inverse);
                    inputShader.setLocalMatrix(inputMatrix);
                    shader.setInputShader("backdrop", inputShader);
                    shaderKey = key;
                }
                return true;
            } catch (Throwable t) {
                shader = null;
                inputShader = null;
                shaderKey = "";
                Main.log("liquid glass RuntimeShader unavailable, using cached fallback: " + t);
                return false;
            }
        }

        private void drawCached(
                Canvas canvas, ArrayList<DrawRegion> regions,
                Profile resolved, float density, long now) {
            float inverse = 1f / Math.max(0.01f, sourceScale);
            for (DrawRegion region : regions) {
                RectF rect = animatedBounds(region, now, density);
                float radius = Math.min(region.radiusPx,
                        Math.min(rect.width(), rect.height()) * 0.5f);
                bitmapMatrix.reset();
                bitmapMatrix.setScale(inverse, inverse);
                bitmapMatrix.postTranslate(sceneX - capturedSceneX, 0f);
                float magnification = region.isSmall() ? 1.045f : 1.026f;
                magnification += pressFor(region) * 0.018f;
                bitmapMatrix.postScale(
                        magnification, magnification,
                        rect.centerX(), rect.centerY());
                // The viewport base already covers every glyph and centre. Build the extra
                // refraction as overlapping low-alpha bands: the outer edge receives every pass,
                // while each step toward the centre receives fewer passes. This is the cached
                // equivalent of the shader's continuous falloff and avoids a visibly clipped rim.
                if (region.isDivider()) {
                    drawCachedSeamGradient(
                            canvas, rect, radius, density, resolved, region);
                } else {
                    drawCachedEdgeGradient(
                            canvas, rect, radius, density, resolved, region);
                }
                drawTintAndEdge(canvas, region, rect, radius, density);
            }
            glassPaint.setAlpha(255);
        }

        private void drawCachedEdgeGradient(
                Canvas canvas, RectF rect, float radius, float density,
                Profile resolved, DrawRegion region) {
            float bandDp = region.isFullscreenEdge()
                    ? 48f
                    : (region.isTextBearing()
                            ? (region.isSmall() ? 8f : 12f)
                            : (region.isSmall() ? 22f : 34f));
            float[] widths = new float[]{
                    bandDp, bandDp * 0.76f, bandDp * 0.52f, bandDp * 0.30f
            };
            int[] alphas = region.isFullscreenEdge()
                    ? new int[]{12, 16, 21, 29}
                    : (region.isSmall()
                            ? new int[]{13, 18, 23, 32}
                            : new int[]{12, 17, 22, 29});
            for (int i = 0; i < widths.length; i++) {
                drawCachedTexturePass(
                        canvas, rect, radius, density, resolved, region,
                        true, widths[i], alphas[i],
                        resolved.backend == Profile.BACKEND_CACHED && i == 0 ? 2 : 0);
            }
        }

        private void drawCachedSeamGradient(
                Canvas canvas, RectF rect, float radius, float density,
                Profile resolved, DrawRegion region) {
            float[] widthFractions =
                    new float[]{1f, 0.82f, 0.64f, 0.46f, 0.28f, 0.12f};
            int[] alphas = new int[]{4, 8, 11, 15, 19, 14};
            for (int i = 0; i < widthFractions.length; i++) {
                float halfWidth = rect.width() * widthFractions[i] * 0.5f;
                RectF band = new RectF(
                        rect.centerX() - halfWidth, rect.top,
                        rect.centerX() + halfWidth, rect.bottom);
                float bandRadius = Math.min(
                        radius, Math.min(band.width(), band.height()) * 0.5f);
                drawCachedTexturePass(
                        canvas, band, bandRadius, density, resolved, region,
                        false, 0f, alphas[i],
                        resolved.backend == Profile.BACKEND_CACHED && i == 0 ? 1 : 0);
            }
        }

        private void drawCachedTexturePass(
                Canvas canvas, RectF rect, float radius, float density,
                Profile resolved, DrawRegion region, boolean edgeOnly,
                float edgeInsetDp, int textureAlpha, int blurTapAlpha) {
            int save = canvas.save();
            if (edgeOnly) {
                clipToLensRing(canvas, rect, radius, density, edgeInsetDp);
            } else {
                clipPath.reset();
                clipPath.setFillType(Path.FillType.WINDING);
                clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW);
                canvas.clipPath(clipPath);
            }
            if (resolved.backend == Profile.BACKEND_CACHED && blurTapAlpha > 0) {
                float blur = (region.isSmall() ? 2.2f : 1.4f) * density;
                glassPaint.setAlpha(blurTapAlpha);
                canvas.save();
                canvas.translate(-blur, 0f);
                canvas.drawBitmap(backdrop, bitmapMatrix, glassPaint);
                canvas.restore();
                canvas.save();
                canvas.translate(blur, 0f);
                canvas.drawBitmap(backdrop, bitmapMatrix, glassPaint);
                canvas.restore();
                canvas.save();
                canvas.translate(0f, -blur);
                canvas.drawBitmap(backdrop, bitmapMatrix, glassPaint);
                canvas.restore();
                canvas.save();
                canvas.translate(0f, blur);
                canvas.drawBitmap(backdrop, bitmapMatrix, glassPaint);
                canvas.restore();
            }
            glassPaint.setAlpha(textureAlpha);
            canvas.drawBitmap(backdrop, bitmapMatrix, glassPaint);
            canvas.restoreToCount(save);
        }

        /** Clips one band of the cached optical falloff. */
        private void clipToLensRing(
                Canvas canvas, RectF rect, float radius, float density,
                float insetDp) {
            float maximum = Math.max(1f,
                    Math.min(rect.width(), rect.height()) * 0.48f);
            float inset = Math.min(Math.max(1f, insetDp * density), maximum);
            RectF inner = new RectF(rect);
            inner.inset(inset, inset);
            clipPath.reset();
            clipPath.setFillType(Path.FillType.EVEN_ODD);
            clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW);
            if (inner.width() > 1f && inner.height() > 1f) {
                float innerRadius = Math.max(0f, radius - inset);
                clipPath.addRoundRect(
                        inner, innerRadius, innerRadius, Path.Direction.CW);
            }
            canvas.clipPath(clipPath);
        }

        private void drawTintAndEdge(
                Canvas canvas, DrawRegion region, RectF rect,
                float radius, float density) {
            boolean dark = isDark();
            if (region.isFullscreenEdge()) {
                return;
            }
            if (region.isDivider()) {
                // Let the whole drawer seam read as a wide lens. The middle is only marginally
                // brighter and both sides fade to clear; there is deliberately no thin centre rail.
                tintPaint.setStyle(Paint.Style.FILL);
                tintPaint.setShader(new LinearGradient(
                        rect.left, 0f, rect.right, 0f,
                        new int[]{
                                0x00FFFFFF,
                                dark ? 0x0CFFFFFF : 0x0AFFFFFF,
                                dark ? 0x16FFFFFF : 0x12FFFFFF,
                                dark ? 0x0CFFFFFF : 0x0AFFFFFF,
                                0x00FFFFFF
                        },
                        new float[]{0f, 0.22f, 0.5f, 0.78f, 1f},
                        Shader.TileMode.CLAMP));
                canvas.drawRoundRect(rect, radius, radius, tintPaint);
                tintPaint.setShader(null);
                return;
            }

            int baseAlpha;
            if (region.kind == KIND_SIDEBAR_PANEL || region.isTextBearing()) baseAlpha = 0;
            else if (region.isSmall()) baseAlpha = dark ? 5 : 4;
            else baseAlpha = dark ? 4 : 3;
            tintPaint.setStyle(Paint.Style.FILL);
            tintPaint.setColor(Color.argb(baseAlpha, 255, 255, 255));
            canvas.drawRoundRect(rect, radius, radius, tintPaint);

            edgePaint.setStyle(Paint.Style.STROKE);
            edgePaint.setStrokeWidth(Math.max(0.8f, 0.58f * density));
            edgePaint.setShader(new LinearGradient(
                    rect.left, rect.top, rect.right, rect.bottom,
                    dark ? 0x1EFFFFFF : 0x22FFFFFF,
                    dark ? 0x08FFFFFF : 0x0AFFFFFF,
                    Shader.TileMode.CLAMP));
            canvas.drawRoundRect(
                    insetCopy(rect, edgePaint.getStrokeWidth() * 0.5f),
                    Math.max(0f, radius - edgePaint.getStrokeWidth() * 0.5f),
                    Math.max(0f, radius - edgePaint.getStrokeWidth() * 0.5f),
                    edgePaint);
            edgePaint.setShader(null);

            // Directional highlights copied from the successful VideoDownloader panel treatment.
            // Uneven arcs read as a curved reflective surface; a uniform bright outline reads as
            // a flat translucent border.
            RectF highlight = insetCopy(rect, 2.2f * density);
            float highlightRadius = Math.max(0f, radius - 2.2f * density);
            edgePaint.setStrokeWidth(Math.max(1f, 1.05f * density));
            edgePaint.setColor(dark ? 0x2CFFFFFF : 0x34FFFFFF);
            canvas.drawArc(highlight, 205f, 78f, false, edgePaint);
            edgePaint.setStrokeWidth(Math.max(0.8f, 0.72f * density));
            edgePaint.setColor(dark ? 0x16FFFFFF : 0x1CFFFFFF);
            canvas.drawArc(highlight, 298f, 44f, false, edgePaint);
            if (highlightRadius > 0f && rect.width() > highlightRadius * 2f) {
                edgePaint.setStrokeWidth(Math.max(0.8f, 0.62f * density));
                edgePaint.setColor(dark ? 0x1CFFFFFF : 0x24FFFFFF);
                canvas.drawLine(
                        rect.left + highlightRadius * 0.72f,
                        rect.top + 1.6f * density,
                        rect.right - highlightRadius * 0.86f,
                        rect.top + 1.6f * density,
                        edgePaint);
            }
        }

        private void drawMagneticBridges(
                Canvas canvas, ArrayList<DrawRegion> regions,
                Profile resolved, float density, long now) {
            float threshold = 12f * density;
            for (int i = 0; i < regions.size(); i++) {
                DrawRegion first = regions.get(i);
                if (!first.isSmall() || first.isDivider()) continue;
                RectF a = animatedBounds(first, now, density);
                for (int j = i + 1; j < regions.size(); j++) {
                    DrawRegion second = regions.get(j);
                    if (!second.isSmall() || second.isDivider()) continue;
                    RectF b = animatedBounds(second, now, density);
                    RectF bridge = bridgeBetween(a, b, threshold);
                    if (bridge == null) continue;
                    float radius = Math.min(bridge.width(), bridge.height()) * 0.5f;
                    tintPaint.setStyle(Paint.Style.FILL);
                    tintPaint.setColor(isDark() ? 0x18FFFFFF : 0x20FFFFFF);
                    canvas.drawRoundRect(bridge, radius, radius, tintPaint);
                }
            }
        }

        private RectF bridgeBetween(RectF first, RectF second, float threshold) {
            float verticalOverlap = Math.min(first.bottom, second.bottom)
                    - Math.max(first.top, second.top);
            if (verticalOverlap > 0f) {
                RectF left = first.centerX() <= second.centerX() ? first : second;
                RectF right = left == first ? second : first;
                float gap = right.left - left.right;
                if (gap >= 0f && gap <= threshold) {
                    float cy = (Math.max(first.top, second.top)
                            + Math.min(first.bottom, second.bottom)) * 0.5f;
                    float half = Math.min(verticalOverlap * 0.22f, 5f
                            * getResources().getDisplayMetrics().density);
                    return new RectF(left.right - half, cy - half,
                            right.left + half, cy + half);
                }
            }
            float horizontalOverlap = Math.min(first.right, second.right)
                    - Math.max(first.left, second.left);
            if (horizontalOverlap > 0f) {
                RectF top = first.centerY() <= second.centerY() ? first : second;
                RectF bottom = top == first ? second : first;
                float gap = bottom.top - top.bottom;
                if (gap >= 0f && gap <= threshold) {
                    float cx = (Math.max(first.left, second.left)
                            + Math.min(first.right, second.right)) * 0.5f;
                    float half = Math.min(horizontalOverlap * 0.22f, 5f
                            * getResources().getDisplayMetrics().density);
                    return new RectF(cx - half, top.bottom - half,
                            cx + half, bottom.top + half);
                }
            }
            return null;
        }

        private RectF animatedBounds(DrawRegion region, long now, float density) {
            if (region.kind == KIND_INPUT
                    || region.kind == KIND_USER_BUBBLE
                    || region.kind == KIND_ASSISTANT_BUBBLE
                    || region.kind == KIND_SETTINGS_CARD
                    || region.kind == KIND_DIVIDER
                    || region.kind == KIND_FULLSCREEN_EDGE
                    || region.kind == KIND_MODE_ITEM
                    || region.kind == KIND_MODE_SELECTED
                    || region.kind == KIND_SIDEBAR_SEARCH) {
                // These surfaces must stay pixel-aligned with their real Compose nodes. Motion
                // elasticity belongs in the optical sampling, not in a second visible outline
                // that lags behind the control.
                return new RectF(region.bounds);
            }
            float progress = appearProgress(region, now);
            float ease = 1f - (float) Math.pow(1f - progress, 3f);
            float initial = region.isSmall() ? 0.955f : 0.982f;
            float scale = initial + (1f - initial) * ease;
            float velocityLife = clamp(
                    1f - (now - region.lastBoundsAt) / 260f, 0f, 1f);
            velocityLife *= velocityLife;
            float vx = clamp(
                    region.velocityX * velocityLife + sceneVelocityX * 0.18f,
                    -3.2f * density, 3.2f * density);
            float vy = clamp(region.velocityY * velocityLife,
                    -2.2f * density, 2.2f * density);
            float stretchX = Math.abs(vx)
                    * (0.72f + (1f - ease) * 0.55f);
            float stretchY = Math.abs(vy)
                    * (0.72f + (1f - ease) * 0.55f);
            RectF source = region.bounds;
            float halfWidth = source.width() * scale * 0.5f + stretchX;
            float halfHeight = source.height() * scale * 0.5f + stretchY;
            float cx = source.centerX()
                    - vx * (0.22f + (1f - ease) * 0.28f);
            float cy = source.centerY()
                    - vy * (0.22f + (1f - ease) * 0.28f);
            return new RectF(
                    cx - halfWidth, cy - halfHeight,
                    cx + halfWidth, cy + halfHeight);
        }

        private float appearProgress(DrawRegion region, long now) {
            if (region.appearAt <= 0L) return 1f;
            return clamp((now - region.appearAt) / (float) APPEAR_DURATION_MS, 0f, 1f);
        }

        private float pressFor(DrawRegion region) {
            if (press <= 0f || !region.bounds.contains(touchX, touchY)) return 0f;
            return press;
        }

        private boolean isDark() {
            int mode = getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            return mode == Configuration.UI_MODE_NIGHT_YES;
        }

        private static RectF insetCopy(RectF source, float amount) {
            RectF copy = new RectF(source);
            copy.inset(amount, amount);
            return copy;
        }
    }

    private static final class Profile {
        static final int BACKEND_STATIC = 1;
        static final int BACKEND_CACHED = 2;
        static final int BACKEND_SHADER = 3;

        final int backend;
        final int level;
        final float sourceScale;
        final String label;
        final String englishLabel;

        Profile(
                int backend, int level, float sourceScale,
                String label, String englishLabel) {
            this.backend = backend;
            this.level = level;
            this.sourceScale = sourceScale;
            this.label = label;
            this.englishLabel = englishLabel;
        }

        boolean sameRendering(Profile other) {
            return other != null && backend == other.backend && level == other.level
                    && Math.abs(sourceScale - other.sourceScale) < 0.001f;
        }

        static Profile resolve(Context context, ChatAppearance.Config config) {
            String requested = config == null ? "auto" : config.glassQuality;
            boolean saver = powerSaver(context);
            int thermal = thermalStatus(context);
            boolean automatic = "auto".equals(requested);
            int memoryClass = 128;
            long pixels = 0L;
            try {
                ActivityManager manager = (ActivityManager) context.getSystemService(
                        Context.ACTIVITY_SERVICE);
                if (manager != null) memoryClass = manager.getMemoryClass();
                android.util.DisplayMetrics metrics =
                        context.getResources().getDisplayMetrics();
                pixels = (long) metrics.widthPixels * (long) metrics.heightPixels;
            } catch (Throwable ignored) {}

            int requestedLevel;
            if ("high".equals(requested)) requestedLevel = 3;
            else if ("balanced".equals(requested)) requestedLevel = 2;
            else if ("saver".equals(requested)) requestedLevel = 1;
            else {
                if (Build.VERSION.SDK_INT >= 33 && memoryClass >= 256
                        && pixels <= 4_500_000L) {
                    requestedLevel = 3;
                } else if (Build.VERSION.SDK_INT >= 31) {
                    requestedLevel = 2;
                } else {
                    requestedLevel = 1;
                }
            }
            if (automatic) {
                // Automatic mode may reduce sampling work, but API 33+ devices keep the shader
                // backend so enabling liquid glass never silently turns into a static mask.
                if ((saver || thermal >= 3) && Build.VERSION.SDK_INT >= 33) {
                    requestedLevel = Math.min(requestedLevel, 2);
                } else if (saver || thermal >= 3) {
                    requestedLevel = Math.min(requestedLevel, 1);
                } else if (thermal >= 2) {
                    requestedLevel = Math.min(requestedLevel, 2);
                }
            } else if (!"saver".equals(requested) && thermal >= 4) {
                // Respect an explicit High/Balanced selection. Only critical thermal pressure
                // lowers High to Balanced; it still retains real-time refraction.
                requestedLevel = Math.min(requestedLevel, 2);
            }

            if (requestedLevel >= 3 && Build.VERSION.SDK_INT >= 33) {
                return new Profile(
                        BACKEND_SHADER, 3, 0.62f, "高画质", "High quality");
            }
            if (requestedLevel >= 2 && Build.VERSION.SDK_INT >= 33) {
                return new Profile(
                        BACKEND_SHADER, 2, 0.48f, "均衡", "Balanced");
            }
            if (requestedLevel >= 2 && Build.VERSION.SDK_INT >= 31) {
                return new Profile(
                        BACKEND_CACHED, 2, 0.44f,
                        "兼容均衡", "Compatibility balanced");
            }
            return new Profile(
                    BACKEND_STATIC, 1, 0.34f, "省电", "Battery saver");
        }

        private static boolean powerSaver(Context context) {
            try {
                PowerManager manager = (PowerManager) context.getSystemService(
                        Context.POWER_SERVICE);
                return manager != null && manager.isPowerSaveMode();
            } catch (Throwable ignored) {
                return false;
            }
        }

        private static int thermalStatus(Context context) {
            if (Build.VERSION.SDK_INT < 29) return 0;
            try {
                PowerManager manager = (PowerManager) context.getSystemService(
                        Context.POWER_SERVICE);
                return manager == null ? 0 : Api29.thermalStatus(manager);
            } catch (Throwable ignored) {
                return 0;
            }
        }
    }

    private static final class Api29 {
        static int thermalStatus(PowerManager manager) {
            return manager.getCurrentThermalStatus();
        }
    }

    private static final class Api33 {
        private static final String SHADER_BALANCED =
                "uniform shader backdrop;\n"
                + "uniform float2 origin;\n"
                + "uniform float2 extent;\n"
                + "uniform float radius;\n"
                + "uniform float refraction;\n"
                + "uniform float blur;\n"
                + "uniform float2 velocity;\n"
                + "uniform float2 sceneShift;\n"
                + "uniform float press;\n"
                + "uniform float2 touch;\n"
                + "uniform float darkMode;\n"
                + "uniform float edgeOnly;\n"
                + "uniform float seamMode;\n"
                + "uniform float neutralize;\n"
                + "uniform float textSafe;\n"
                + "float roundedSdf(float2 p, float2 halfSize, float r) {\n"
                + "  float2 q = abs(p) - halfSize + r;\n"
                + "  return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;\n"
                + "}\n"
                + "half4 main(float2 coord) {\n"
                + "  float2 halfSize = extent * 0.5;\n"
                + "  float2 center = origin + halfSize;\n"
                + "  float2 local = coord - center;\n"
                + "  float sd = roundedSdf(local, halfSize, radius);\n"
                + "  float depth = max(1.0, min(halfSize.x, halfSize.y));\n"
                + "  float edgeRange = max(16.0, min(depth * 1.36, 144.0));\n"
                // Edge proximity from the true signed distance: 1 deep inside, 0 at the rim.
                // Rounded corners and straight sides share the same falloff, so a curved edge
                // gets the identical cling band on both of its flanks.
                + "  float inside = clamp(-sd / edgeRange, 0.0, 1.0);\n"
                + "  float edge = 1.0 - inside;\n"
                + "  float clingBand = pow(edge, 2.2);\n"
                // SDF gradient = the real outward surface normal. Refracting strictly along it
                // (never along the tangent) is what removes the chaotic diagonal stripes: the
                // background is only ever squeezed straight into the rim, so both sides of an
                // arc pull symmetrically toward each other like two adjacent shadows.
                + "  float dx = roundedSdf(local + float2(1.0, 0.0), halfSize, radius)\n"
                + "           - roundedSdf(local - float2(1.0, 0.0), halfSize, radius);\n"
                + "  float dy = roundedSdf(local + float2(0.0, 1.0), halfSize, radius)\n"
                + "           - roundedSdf(local - float2(0.0, 1.0), halfSize, radius);\n"
                + "  float2 nrm = normalize(float2(dx, dy) + float2(0.0001));\n"
                + "  float seamSign = step(0.0, local.x) * 2.0 - 1.0;\n"
                + "  nrm = mix(nrm, float2(seamSign, 0.0), seamMode);\n"
                + "  float touchDistance = length(coord - touch);\n"
                + "  float touchLens = press * (1.0 - smoothstep(0.0, depth * 1.8,"
                + " touchDistance));\n"
                // Gentle overall magnification that swells toward the rim so the material reads
                // like a thick bevelled lens: content passing behind it is enlarged a touch.
                + "  float bevel = clingBand;\n"
                + "  float mag = 1.0 + 0.05 + bevel * 0.26 + touchLens * 0.05;\n"
                + "  float2 sampleAt = center + local / mag - sceneShift;\n"
                // The cling: shove the sampled background outward along the normal near the rim,
                // compressing a thin ring of surroundings into the edge on every side.
                + "  sampleAt += nrm * refraction * clingBand;\n"
                + "  sampleAt -= velocity * edge * 0.28;\n"
                + "  sampleAt = mix(sampleAt, center + (sampleAt - center) * 0.95, touchLens);\n"
                // Blur only along the normal (the squeeze direction) — no tangential smear.
                + "  float2 softOffset = nrm * blur * (0.4 + clingBand * 0.6);\n"
                + "  half4 base = backdrop.eval(sampleAt) * 0.7;\n"
                + "  base += backdrop.eval(sampleAt + softOffset) * 0.15;\n"
                + "  base += backdrop.eval(sampleAt - softOffset) * 0.15;\n"
                + "  half3 rgb = base.rgb;\n"
                + "  half luminance = dot(rgb, half3(0.299, 0.587, 0.114));\n"
                + "  rgb = mix(rgb, half3(luminance), half(neutralize * 0.72));\n"
                + "  float shineGain = mix(0.105, 0.085, darkMode);\n"
                + "  rgb += half3(shineGain * clingBand + touchLens * 0.008);\n"
                // Text is still rendered by DeepSeek below this overlay. For text-bearing
                // controls confine the material to a tight outer ring so native glyphs stay
                // crisp through the centre.
                + "  float tightRange = max(6.0, min(edgeRange * 0.40, 32.0));\n"
                + "  float tightInside = clamp(-sd / tightRange, 0.0, 1.0);\n"
                + "  float glyphSafeEdge = pow(1.0 - tightInside, 2.0);\n"
                + "  float visibleEdge = mix(edge, glyphSafeEdge, textSafe);\n"
                + "  float touchVisibility = mix(0.18, 0.03, textSafe);\n"
                + "  float edgeMask = clamp(visibleEdge"
                + " + touchLens * touchVisibility, 0.0, 1.0);\n"
                + "  half materialMask = half(mix(1.0, edgeMask, edgeOnly));\n"
                + "  half alpha = half(0.92 + clingBand * 0.06"
                + " + touchLens * 0.03) * materialMask;\n"
                + "  return half4(rgb * alpha, alpha);\n"
                + "}\n";

        // High quality uses the same optical model as balanced. Its sharper 0.62-scale shared
        // backdrop already provides the extra fidelity without changing the material's shape.
        private static final String SHADER_HIGH = SHADER_BALANCED;

        static RuntimeShader createShader(boolean high) {
            return new RuntimeShader(high ? SHADER_HIGH : SHADER_BALANCED);
        }

        static void draw(
                LayerView layer, Canvas canvas, ArrayList<DrawRegion> regions,
                RuntimeShader shader, BitmapShader input, Paint glassPaint,
                Paint edgePaint, float density, long now) {
            boolean dark = layer.isDark();
            shader.setFloatUniform("darkMode", dark ? 1f : 0f);
            shader.setFloatUniform(
                    "sceneShift", layer.sceneX - layer.capturedSceneX, 0f);
            shader.setFloatUniform("touch", layer.touchX, layer.touchY);
            for (DrawRegion region : regions) {
                RectF rect = layer.animatedBounds(region, now, density);
                float radius = Math.min(region.radiusPx,
                        Math.min(rect.width(), rect.height()) * 0.5f);
                if (region.isDivider()) {
                    // A wide, highly transparent lens follows the real drawer edge. It has no
                    // velocity stretch, so its width remains stable throughout the transition.
                    shader.setFloatUniform("origin", rect.left, rect.top);
                    shader.setFloatUniform("extent", rect.width(), rect.height());
                    shader.setFloatUniform("radius", Math.max(0.5f, radius));
                    shader.setFloatUniform("refraction", 18.0f * density);
                    shader.setFloatUniform("blur", 0.95f * density);
                    shader.setFloatUniform("velocity", 0f, 0f);
                    shader.setFloatUniform("press", 0f);
                    shader.setFloatUniform("seamMode", 1f);
                    shader.setFloatUniform("neutralize", 0f);
                    shader.setFloatUniform("textSafe", 0f);
                    shader.setFloatUniform("edgeOnly", 1f);
                    glassPaint.setShader(shader);
                    glassPaint.setAlpha(94);
                    canvas.drawRoundRect(rect, radius, radius, glassPaint);
                    glassPaint.setShader(null);
                    glassPaint.setAlpha(255);
                    layer.drawTintAndEdge(canvas, region, rect, radius, density);
                    continue;
                }
                float small = region.isSmall() ? 1f : 0f;
                float speedX = clamp(
                        region.velocityX + layer.sceneVelocityX * 0.18f,
                        -3.2f * density, 3.2f * density);
                float speedY = clamp(
                        region.velocityY, -2.2f * density, 2.2f * density);
                shader.setFloatUniform("origin", rect.left, rect.top);
                shader.setFloatUniform("extent", rect.width(), rect.height());
                shader.setFloatUniform("radius", Math.max(0.5f, radius));
                shader.setFloatUniform(
                        "refraction", (small > 0f ? 15.5f : 24.0f) * density);
                shader.setFloatUniform(
                        "blur", (small > 0f ? 1.9f : 1.15f) * density);
                shader.setFloatUniform("velocity", speedX, speedY);
                shader.setFloatUniform("press", layer.pressFor(region));
                shader.setFloatUniform("seamMode", 0f);
                boolean neutralControl = region.kind == KIND_INPUT
                        || region.kind == KIND_MODE_ITEM
                        || region.kind == KIND_MODE_SELECTED
                        || region.kind == KIND_SIDEBAR_SEARCH;
                shader.setFloatUniform("neutralize", neutralControl ? 1f : 0f);
                shader.setFloatUniform("textSafe", region.isTextBearing() ? 1f : 0f);
                // Text-bearing surfaces keep an edge-only material so DeepSeek's native glyphs
                // stay crisp through the centre. Purely decorative surfaces (buttons, attachments,
                // settings cards) fill solid so the refracted, magnified backdrop actually
                // replaces the flat background beneath them and reads as one piece of glass
                // instead of stripes floating over the untouched wallpaper.
                boolean solidGlass = !region.isTextBearing()
                        && region.kind != KIND_SIDEBAR_PANEL;
                shader.setFloatUniform("edgeOnly", solidGlass ? 0f : 1f);
                glassPaint.setShader(shader);
                // The whole screen already has one continuous transparent material. The shader's
                // smooth mask leaves only each node's curved optical falloff above that base.
                int materialAlpha;
                if (region.isFullscreenEdge()) {
                    materialAlpha = 138;
                } else if (region.isTextBearing()) {
                    materialAlpha =
                            (region.kind == KIND_MODE_ITEM
                                    || region.kind == KIND_MODE_SELECTED)
                            ? 156 : (region.isSmall() ? 164 : 156);
                } else if (region.kind == KIND_SIDEBAR_PANEL) {
                    materialAlpha = 132;
                } else {
                    materialAlpha = 240;
                }
                glassPaint.setAlpha(materialAlpha);
                canvas.drawRoundRect(rect, radius, radius, glassPaint);
                glassPaint.setShader(null);
                glassPaint.setAlpha(255);
                layer.drawTintAndEdge(canvas, region, rect, radius, density);
            }
        }
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
