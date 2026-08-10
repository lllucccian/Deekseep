package com.dsmod.probe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Original line icons used by the standalone module UI. */
final class ModuleGlyphView extends View {
    static final int HOME = 1;
    static final int SETTINGS = 2;
    static final int SPONSOR = 3;
    static final int REPOSITORY = 4;
    static final int LICENSE = 5;
    static final int EXPORT = 6;
    static final int IMPORT = 7;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final int type;
    private int color;

    ModuleGlyphView(Context context, int type, int color) {
        super(context);
        this.type = type;
        this.color = color;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    void setGlyphColor(int value) { color = value; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight(), s = Math.min(w, h);
        float cx = w / 2f, cy = h / 2f;
        paint.setColor(color);
        paint.setStrokeWidth(Math.max(1.6f, s * 0.075f));
        paint.setStyle(Paint.Style.STROKE);
        path.reset();
        if (type == HOME) {
            path.moveTo(cx - s * .32f, cy - s * .02f);
            path.lineTo(cx, cy - s * .30f);
            path.lineTo(cx + s * .32f, cy - s * .02f);
            path.moveTo(cx - s * .24f, cy - s * .08f);
            path.lineTo(cx - s * .24f, cy + s * .28f);
            path.lineTo(cx + s * .24f, cy + s * .28f);
            path.lineTo(cx + s * .24f, cy - s * .08f);
            canvas.drawPath(path, paint);
        } else if (type == SETTINGS) {
            canvas.drawCircle(cx, cy, s * .13f, paint);
            canvas.drawCircle(cx, cy, s * .31f, paint);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * i / 4d;
                canvas.drawLine(cx + (float) Math.cos(a) * s * .31f,
                        cy + (float) Math.sin(a) * s * .31f,
                        cx + (float) Math.cos(a) * s * .39f,
                        cy + (float) Math.sin(a) * s * .39f, paint);
            }
        } else if (type == SPONSOR) {
            path.moveTo(cx, cy + s * .30f);
            path.cubicTo(cx - s * .43f, cy + s * .04f, cx - s * .35f, cy - s * .30f,
                    cx - s * .13f, cy - s * .28f);
            path.cubicTo(cx, cy - s * .27f, cx, cy - s * .16f, cx, cy - s * .16f);
            path.cubicTo(cx, cy - s * .16f, cx + s * .05f, cy - s * .28f,
                    cx + s * .18f, cy - s * .28f);
            path.cubicTo(cx + s * .40f, cy - s * .28f, cx + s * .43f, cy + s * .04f,
                    cx, cy + s * .30f);
            canvas.drawPath(path, paint);
        } else if (type == REPOSITORY) {
            canvas.drawCircle(cx, cy, s * .32f, paint);
            path.moveTo(cx - s * .17f, cy + s * .29f);
            path.cubicTo(cx - s * .18f, cy + s * .10f, cx - s * .28f, cy + s * .10f,
                    cx - s * .28f, cy - s * .03f);
            path.cubicTo(cx - s * .28f, cy - s * .15f, cx - s * .19f, cy - s * .22f,
                    cx, cy - s * .22f);
            path.cubicTo(cx + s * .19f, cy - s * .22f, cx + s * .28f, cy - s * .15f,
                    cx + s * .28f, cy - s * .03f);
            path.cubicTo(cx + s * .28f, cy + s * .10f, cx + s * .18f, cy + s * .10f,
                    cx + s * .17f, cy + s * .29f);
            canvas.drawPath(path, paint);
        } else if (type == LICENSE) {
            RectF r = new RectF(cx - s * .25f, cy - s * .33f,
                    cx + s * .25f, cy + s * .33f);
            canvas.drawRoundRect(r, s * .04f, s * .04f, paint);
            canvas.drawLine(cx - s * .14f, cy - s * .12f,
                    cx + s * .14f, cy - s * .12f, paint);
            canvas.drawLine(cx - s * .14f, cy,
                    cx + s * .14f, cy, paint);
            canvas.drawLine(cx - s * .14f, cy + s * .12f,
                    cx + s * .05f, cy + s * .12f, paint);
        } else {
            RectF tray = new RectF(cx - s * .28f, cy + s * .12f,
                    cx + s * .28f, cy + s * .31f);
            canvas.drawRoundRect(tray, s * .04f, s * .04f, paint);
            float direction = type == EXPORT ? -1f : 1f;
            canvas.drawLine(cx, cy + direction * s * .18f,
                    cx, cy - direction * s * .25f, paint);
            path.moveTo(cx - s * .13f, cy - direction * s * .10f);
            path.lineTo(cx, cy - direction * s * .25f);
            path.lineTo(cx + s * .13f, cy - direction * s * .10f);
            canvas.drawPath(path, paint);
        }
    }
}
