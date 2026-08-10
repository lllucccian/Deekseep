package com.dsmod.probe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import androidx.core.graphics.PathParser;

/**
 * Theme-aware Material glyphs for the UI injected into DeepSeek.
 *
 * <p>Injected code cannot assume that the host may open this module's resource package. Keeping
 * the official 24 dp Material paths in a tiny View makes the icons deterministic in both the
 * mainland and Play builds, while still following the host's light/dark text colour.</p>
 */
final class HubMaterialGlyphView extends View {
    private static final float VIEWPORT = 24f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path source;
    private final Path rendered = new Path();
    private final Matrix transform = new Matrix();

    HubMaterialGlyphView(Context context, String name, int color) {
        super(context);
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        Path parsed = PathParser.createPathFromPathData(pathFor(name));
        source = parsed == null ? new Path() : parsed;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0f || source.isEmpty()) return;
        float scale = size / VIEWPORT;
        float left = (getWidth() - size) * 0.5f;
        float top = (getHeight() - size) * 0.5f;
        transform.reset();
        transform.setScale(scale, scale);
        transform.postTranslate(left, top);
        rendered.reset();
        source.transform(transform, rendered);
        canvas.drawPath(rendered, paint);
    }

    private static String pathFor(String name) {
        if ("ds_category_chat".equals(name)) {
            return "M20,2H4C2.9,2 2,2.9 2,4v18l4,-4h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM6,9h12v2H6V9zM6,5h12v2H6V5zM6,13h8v2H6v-2z";
        }
        if ("ds_category_account".equals(name)) {
            return "M12,1 3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12V5l-9,-4zM12,5c1.66,0 3,1.34 3,3s-1.34,3 -3,3 -3,-1.34 -3,-3 1.34,-3 3,-3zM12,19.3c-2.5,-0.8 -4.58,-2.67 -5.69,-5.07C7.62,13.45 9.58,13 12,13s4.38,0.45 5.69,1.23C16.58,16.63 14.5,18.5 12,19.3z";
        }
        if ("ds_category_appearance".equals(name)) {
            return "M12,3C7.03,3 3,6.58 3,11c0,3.87 3.13,7 7,7h1.65c0.82,0 1.35,-0.88 0.96,-1.6 -0.29,-0.54 0.1,-1.2 0.71,-1.2H15c3.31,0 6,-2.69 6,-6 0,-3.42 -4.03,-6.2 -9,-6.2zM7.5,12C6.67,12 6,11.33 6,10.5S6.67,9 7.5,9 9,9.67 9,10.5 8.33,12 7.5,12zM10,7.5C9.17,7.5 8.5,6.83 8.5,6S9.17,4.5 10,4.5s1.5,0.67 1.5,1.5S10.83,7.5 10,7.5zM14,7.5c-0.83,0 -1.5,-0.67 -1.5,-1.5S13.17,4.5 14,4.5s1.5,0.67 1.5,1.5S14.83,7.5 14,7.5zM17,11c-0.83,0 -1.5,-0.67 -1.5,-1.5S16.17,8 17,8s1.5,0.67 1.5,1.5S17.83,11 17,11z";
        }
        if ("ds_category_debug".equals(name)) {
            return "M20,8h-2.81c-0.45,-0.78 -1.07,-1.45 -1.82,-1.96L17,4.41 15.59,3l-2.17,2.17C12.96,5.06 12.49,5 12,5s-0.96,0.06 -1.42,0.17L8.41,3 7,4.41l1.62,1.63C7.88,6.55 7.26,7.22 6.81,8H4v2h2.09C6.03,10.33 6,10.66 6,11v1H4v2h2v1c0,0.34 0.03,0.67 0.09,1H4v2h2.81c1.04,1.79 2.97,3 5.19,3s4.15,-1.21 5.19,-3H20v-2h-2.09c0.06,-0.33 0.09,-0.66 0.09,-1v-1h2v-2h-2v-1c0,-0.34 -0.03,-0.67 -0.09,-1H20V8zM14,16h-4v-2h4v2zM14,12h-4v-2h4v2z";
        }
        if ("ds_category_engineering".equals(name)) {
            return "M9.4,16.6 4.8,12l4.6,-4.6L8,6l-6,6 6,6 1.4,-1.4zM14.6,16.6 19.2,12l-4.6,-4.6L16,6l6,6 -6,6 -1.4,-1.4z";
        }
        if ("ds_category_help".equals(name)) {
            return "M11,18h2v-2h-2v2zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8zM12,6c-2.21,0 -4,1.79 -4,4h2c0,-1.1 0.9,-2 2,-2s2,0.9 2,2c0,2 -3,1.75 -3,5h2c0,-2.25 3,-2.5 3,-5 0,-2.21 -1.79,-4 -4,-4z";
        }
        if ("ds_project_license".equals(name)) {
            return "M14,2H6c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V8l-6,-6zM13,3.5L18.5,9H13V3.5zM8,13h8v2H8v-2zM8,17h8v2H8v-2z";
        }
        if ("ds_project_sponsor".equals(name)) {
            return "M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z";
        }
        if ("ds_action_execute".equals(name)) {
            return "M8,5.18v13.64c0,0.78 0.86,1.25 1.52,0.83l10.72,-6.82c0.61,-0.39 0.61,-1.27 0,-1.66L9.52,4.35C8.86,3.93 8,4.4 8,5.18z";
        }
        if ("ds_action_save".equals(name)) {
            return "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z";
        }
        if ("ds_action_back".equals(name)) {
            return "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z";
        }
        if ("ds_action_close".equals(name)) {
            return "M18.3,5.71 12,12l6.3,6.29 -1.41,1.42L10.59,13.41 4.29,19.71 2.88,18.29 9.17,12 2.88,5.71 4.29,4.29 10.59,10.59 16.89,4.29z";
        }
        if ("ds_action_settings".equals(name)) {
            return "M19.43,12.98c0.04,-0.32 0.07,-0.65 0.07,-0.98s-0.02,-0.66 -0.07,-0.98l2.11,-1.65c0.19,-0.15 0.24,-0.42 0.12,-0.64l-2,-3.46c-0.12,-0.22 -0.37,-0.31 -0.6,-0.22l-2.49,1c-0.52,-0.4 -1.08,-0.73 -1.69,-0.98L14.5,2.42C14.47,2.18 14.25,2 14,2h-4c-0.25,0 -0.46,0.18 -0.5,0.42L9.12,5.07c-0.61,0.25 -1.18,0.59 -1.69,0.98l-2.49,-1c-0.23,-0.08 -0.48,0 -0.6,0.22l-2,3.46c-0.13,0.22 -0.07,0.49 0.12,0.64l2.11,1.65c-0.04,0.32 -0.08,0.66 -0.08,0.98s0.03,0.66 0.08,0.98l-2.11,1.65c-0.19,0.15 -0.24,0.42 -0.12,0.64l2,3.46c0.12,0.22 0.37,0.31 0.6,0.22l2.49,-1c0.52,0.4 1.08,0.73 1.69,0.98l0.38,2.65c0.04,0.24 0.25,0.42 0.5,0.42h4c0.25,0 0.46,-0.18 0.5,-0.42l0.38,-2.65c0.61,-0.25 1.18,-0.58 1.69,-0.98l2.49,1c0.23,0.08 0.48,0 0.6,-0.22l2,-3.46c0.12,-0.22 0.07,-0.49 -0.12,-0.64l-2.11,-1.65zM12,15.5A3.5,3.5 0,1 1,12,8a3.5,3.5 0,0 1,0,7.5z";
        }
        return "";
    }
}
