package com.dsmod.probe;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.View;
import android.widget.Switch;

/** KSU-style switch whose thumb remains fully inset inside a slightly larger track. */
final class HubInsetSwitch extends Switch {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ColorStateList moduleThumbTint;
    private ColorStateList moduleTrackTint;
    private float thumbPosition;
    private ValueAnimator positionAnimator;

    HubInsetSwitch(Context context) {
        super(context);
        setShowText(false);
        setSplitTrack(false);
        setMinWidth(0);
        setMinimumWidth(0);
        setMinimumHeight(0);
        setPadding(0, 0, 0, 0);
        thumbPosition = isChecked() ? 1f : 0f;
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    @Override public void setThumbTintList(ColorStateList tint) {
        moduleThumbTint = tint;
        try { super.setThumbTintList(tint); } catch (Throwable ignored) {}
        invalidate();
    }

    @Override public void setTrackTintList(ColorStateList tint) {
        moduleTrackTint = tint;
        try { super.setTrackTintList(tint); } catch (Throwable ignored) {}
        invalidate();
    }

    @Override public void setChecked(boolean checked) {
        boolean changed = checked != isChecked();
        super.setChecked(checked);
        float target = checked ? 1f : 0f;
        if (!changed || getWidth() == 0 || getWindowToken() == null) {
            thumbPosition = target;
            invalidate();
            return;
        }
        if (positionAnimator != null) positionAnimator.cancel();
        positionAnimator = ValueAnimator.ofFloat(thumbPosition, target);
        positionAnimator.setDuration(180L);
        positionAnimator.setInterpolator(
                new android.view.animation.DecelerateInterpolator(1.8f));
        positionAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator animation) {
                thumbPosition = (Float) animation.getAnimatedValue();
                invalidate();
            }
        });
        positionAnimator.start();
    }

    @Override public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = dp(56);
        int desiredHeight = dp(34);
        setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        int stateColorTrack = colorFor(moduleTrackTint,
                isChecked() ? 0xFFADBFFF : 0xFFBFBFBF);
        int stateColorThumb = colorFor(moduleThumbTint,
                isChecked() ? DeekseepUi.BRAND : 0xFFFFFFFF);
        if (!isEnabled()) {
            stateColorTrack = withAlpha(stateColorTrack, 0.45f);
            stateColorThumb = withAlpha(stateColorThumb, 0.55f);
        }

        float trackWidth = Math.min(dp(52), getWidth());
        float trackHeight = Math.min(dp(30), getHeight());
        float left = (getWidth() - trackWidth) * 0.5f;
        float top = (getHeight() - trackHeight) * 0.5f;
        paint.setColor(stateColorTrack);
        canvas.drawRoundRect(left, top, left + trackWidth, top + trackHeight,
                trackHeight * 0.5f, trackHeight * 0.5f, paint);

        float inset = dp(3);
        float diameter = trackHeight - inset * 2f;
        float start = left + inset + diameter * 0.5f;
        float end = left + trackWidth - inset - diameter * 0.5f;
        float visualPosition = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                ? 1f - thumbPosition : thumbPosition;
        float centerX = start + (end - start) * visualPosition;
        paint.setColor(stateColorThumb);
        canvas.drawCircle(centerX, top + trackHeight * 0.5f, diameter * 0.5f, paint);
    }

    @Override protected void onDetachedFromWindow() {
        if (positionAnimator != null) positionAnimator.cancel();
        positionAnimator = null;
        super.onDetachedFromWindow();
    }

    private int colorFor(ColorStateList colors, int fallback) {
        return colors == null ? fallback
                : colors.getColorForState(getDrawableState(), colors.getDefaultColor());
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, getResources().getDisplayMetrics()));
    }

    private static int withAlpha(int color, float multiplier) {
        int alpha = Math.round(android.graphics.Color.alpha(color) * multiplier);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
