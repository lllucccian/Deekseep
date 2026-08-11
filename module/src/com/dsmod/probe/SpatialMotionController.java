package com.dsmod.probe;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Choreographer;
import android.view.Surface;
import android.view.WindowManager;

import java.lang.ref.WeakReference;

/**
 * One camera, one sensor listener and one display-frame clock for the complete spatial scene.
 *
 * <p>The rotation-vector callback only updates primitive target values. Every consumer
 * notification happens from {@link #doFrame(long)}, so a 200 Hz sensor cannot make the host
 * Compose tree render more often than a 60/120 Hz display.</p>
 */
final class SpatialMotionController implements Choreographer.FrameCallback {
    static final float DEAD_ZONE_RADIANS = 0f;
    // Spherical-camera projection reference: a five-degree move on the virtual camera hemisphere
    // produces one optical unit. sin(angle) matches its lateral displacement and, unlike tan(),
    // stays continuous when the phone passes a side-on pose instead of flying across the screen.
    static final float OPTICAL_REFERENCE_TILT_RADIANS =
            (float) Math.toRadians(5d);
    static final float OPTICAL_REFERENCE_SINE =
            (float) Math.sin(OPTICAL_REFERENCE_TILT_RADIANS);
    // Last-resort invalid-input guard. The spherical projection itself is naturally bounded to
    // +/-1/sin(5 degrees), so ordinary sensor poses never approach this value.
    static final float MAX_NUMERICAL_PROJECTION = 128f;
    // Rotation-vector delivery plus waiting for the next VSYNC costs roughly one frame. Predict
    // only that short display horizon from consecutive stable poses; this is not gyro integration
    // and can never accumulate drift because every sample replaces the absolute target.
    static final float DISPLAY_PREDICTION_LEAD_SECONDS = 0.008f;
    static final float MAX_DISPLAY_PREDICTION_SECONDS = 0.024f;
    static final float MAX_PREDICTION_SPEED_UNITS_PER_SECOND = 60f;

    static final long STABLE_BEFORE_RECENTER_NANOS = 650_000_000L;
    static final float STABLE_ANGULAR_RATE_RADIANS =
            (float) Math.toRadians(1.1d);
    static final float SENSOR_NOISE_ALLOWANCE_RADIANS =
            (float) Math.toRadians(0.025d);
    static final float AUTO_RECENTER_WINDOW_RADIANS =
            (float) Math.toRadians(0.05d);
    static final float REFERENCE_RESPONSE_PER_SECOND = 0.55f;
    static final float MAX_REFERENCE_SPEED_RADIANS =
            (float) Math.toRadians(0.45d);

    interface FrameListener {
        void onSpatialFrame(boolean active, float cameraX, float cameraY);
    }

    private final Context applicationContext;
    private final FrameListener frameListener;
    private final Choreographer choreographer;
    private final SensorPoseProvider poseProvider;

    private WeakReference<Activity> activityRef = new WeakReference<>(null);
    private boolean attached;
    private boolean resumed;
    private boolean frameScheduled;
    private boolean dynamicAllowed;
    private boolean sensorRunning;
    private boolean policyReceiverRegistered;
    private boolean listenerFailureLogged;

    private float strength = 1f;
    private float directionMultiplier = 1f;
    private boolean reduceMotion;
    private boolean autoRecenter = true;

    // Written by the sensor callback, consumed only by the display-frame callback.
    private volatile float rawTargetX;
    private volatile float rawTargetY;
    private volatile float rawVelocityX;
    private volatile float rawVelocityY;
    private volatile long latestPoseTimestampNanos;

    private boolean haveReference;
    private boolean recenterPending = true;
    private int referenceDisplayRotation = -1;
    private final float[] referenceRotation = new float[9];
    private final float[] previousRotation = new float[9];
    private final float[] relativeRotation = new float[9];
    private final float[] sampleDeltaRotation = new float[9];
    private final float[] referenceStepRotation = new float[9];
    private final float[] updatedReferenceRotation = new float[9];
    private long previousSampleNanos;
    private long stableSinceNanos;
    private float lastDispatchedX = Float.NaN;
    private float lastDispatchedY = Float.NaN;
    private boolean lastDispatchedActive;
    private final BroadcastReceiver policyReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!attached || !resumed) return;
            updateRuntimePolicy();
            startSensorIfNeeded();
            scheduleFrame();
        }
    };

    SpatialMotionController(Context context, FrameListener listener) {
        Context app = context == null ? null : context.getApplicationContext();
        applicationContext = app == null ? context : app;
        frameListener = listener;
        choreographer = Choreographer.getInstance();
        poseProvider = new SensorPoseProvider(
                applicationContext, new SensorPoseProvider.PoseListener() {
                    @Override public void onPose(
                            float[] screenRotation, long timestampNanos,
                            int displayRotation) {
                        updateSensorTarget(
                                screenRotation, timestampNanos,
                                displayRotation);
                    }
                });
    }

    void attach(Activity activity) {
        boolean shouldResume = !attached || !resumed;
        attached = true;
        if (activity != null) {
            activityRef = new WeakReference<>(activity);
            poseProvider.setActivity(activity);
        }
        if (shouldResume) {
            resume(activity);
        } else {
            updateRuntimePolicy();
            startSensorIfNeeded();
            scheduleFrame();
        }
    }

    void resume(Activity activity) {
        if (!attached) attached = true;
        if (activity != null) {
            activityRef = new WeakReference<>(activity);
            poseProvider.setActivity(activity);
        }
        if (resumed) {
            updateRuntimePolicy();
            startSensorIfNeeded();
            scheduleFrame();
            return;
        }
        resumed = true;
        startPolicyReceiver();
        // A new foreground session uses the way the user is currently holding the phone as zero.
        requestReferencePose();
        updateRuntimePolicy();
        startSensorIfNeeded();
        scheduleFrame();
    }

    void pause() {
        resumed = false;
        stopPolicyReceiver();
        stopSensor();
        resetCamera();
        cancelScheduledFrame();
        scheduleFrame();
    }

    void detach() {
        attached = false;
        resumed = false;
        stopPolicyReceiver();
        stopSensor();
        poseProvider.setActivity(null);
        activityRef.clear();
        resetCamera();
        cancelScheduledFrame();
        scheduleFrame();
    }

    /** Final teardown for an Activity that is being destroyed; leaves no queued frame callback. */
    void dispose() {
        attached = false;
        resumed = false;
        stopPolicyReceiver();
        stopSensor();
        poseProvider.setActivity(null);
        activityRef.clear();
        resetCamera();
        cancelScheduledFrame();
    }

    void configure(
            float strengthMultiplier, boolean reduceMotionEnabled,
            boolean autoRecenterEnabled, float direction) {
        strength = clamp(strengthMultiplier, 0.55f, 1.25f);
        reduceMotion = reduceMotionEnabled;
        autoRecenter = autoRecenterEnabled;
        directionMultiplier = direction < 0f ? -1f : 1f;
        updateRuntimePolicy();
        if (resumed && attached) startSensorIfNeeded();
        scheduleFrame();
    }

    void recenter() {
        requestReferencePose();
        scheduleFrame();
    }

    boolean isSensorAvailable() {
        return poseProvider.isAvailable();
    }

    private void updateRuntimePolicy() {
        boolean allowed = !reduceMotion
                && !systemRequestsReducedMotion(applicationContext)
                && !isPowerSaveMode(applicationContext)
                && poseProvider.isAvailable();
        if (allowed != dynamicAllowed) {
            requestReferencePose();
        }
        dynamicAllowed = allowed;
        if (!dynamicAllowed) {
            stopSensor();
            rawTargetX = 0f;
            rawTargetY = 0f;
        }
    }

    private void startSensorIfNeeded() {
        if (!attached || !resumed || !dynamicAllowed || sensorRunning) return;
        sensorRunning = poseProvider.start();
        if (!sensorRunning) {
            dynamicAllowed = false;
            rawTargetX = 0f;
            rawTargetY = 0f;
            Main.log("spatial motion sensor unavailable");
        } else {
            Main.log("spatial motion sensor started");
        }
    }

    private void stopSensor() {
        if (sensorRunning) {
            poseProvider.stop();
            Main.log("spatial motion sensor stopped");
        }
        sensorRunning = false;
    }

    private void startPolicyReceiver() {
        if (policyReceiverRegistered || applicationContext == null) return;
        try {
            applicationContext.registerReceiver(
                    policyReceiver,
                    new IntentFilter(
                            PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));
            policyReceiverRegistered = true;
        } catch (Throwable t) {
            Main.log("spatial power-policy receiver unavailable: " + t);
        }
    }

    private void stopPolicyReceiver() {
        if (!policyReceiverRegistered || applicationContext == null) return;
        try {
            applicationContext.unregisterReceiver(policyReceiver);
        } catch (Throwable ignored) {}
        policyReceiverRegistered = false;
    }

    private void requestReferencePose() {
        recenterPending = true;
        haveReference = false;
        referenceDisplayRotation = -1;
        previousSampleNanos = 0L;
        stableSinceNanos = 0L;
        rawTargetX = 0f;
        rawTargetY = 0f;
        rawVelocityX = 0f;
        rawVelocityY = 0f;
        latestPoseTimestampNanos = 0L;
    }

    private void resetCamera() {
        requestReferencePose();
    }

    /**
     * Sensor-thread work deliberately stops at target calculation. It never touches a View,
     * Compose state, Drawable or listener owned by the host UI.
     */
    private void updateSensorTarget(
            float[] screenRotation, long timestampNanos,
            int displayRotation) {
        if (!attached || !resumed || !dynamicAllowed
                || screenRotation == null || screenRotation.length < 9) {
            return;
        }
        long now = timestampNanos > 0L ? timestampNanos : System.nanoTime();
        if (recenterPending || !haveReference
                || displayRotation != referenceDisplayRotation) {
            recenterPending = false;
            haveReference = true;
            referenceDisplayRotation = displayRotation;
            copyRotation(screenRotation, referenceRotation);
            copyRotation(screenRotation, previousRotation);
            previousSampleNanos = now;
            stableSinceNanos = now;
            rawTargetX = 0f;
            rawTargetY = 0f;
            rawVelocityX = 0f;
            rawVelocityY = 0f;
            latestPoseTimestampNanos = now;
            return;
        }

        float sampleDt = previousSampleNanos <= 0L
                ? 1f / 60f
                : clamp((now - previousSampleNanos) / 1_000_000_000f,
                        1f / 1000f, 0.05f);
        relativeRotation(
                previousRotation, screenRotation, sampleDeltaRotation);
        float sampleMovement = rotationAngle(sampleDeltaRotation);
        float stableAllowance = SENSOR_NOISE_ALLOWANCE_RADIANS
                + STABLE_ANGULAR_RATE_RADIANS * sampleDt;
        if (sampleMovement <= stableAllowance) {
            if (stableSinceNanos <= 0L) stableSinceNanos = now;
        } else {
            stableSinceNanos = now;
        }

        // Express the current pose in the reference device's local screen axes. Unlike subtracting
        // two absolute Euler angles, reference^T * current cannot turn a forward tilt into sideways
        // motion merely because the phone started at an arbitrary yaw or non-level attitude.
        relativeRotation(
                referenceRotation, screenRotation, relativeRotation);
        float referenceError = rotationAngle(relativeRotation);
        if (autoRecenter
                && now - stableSinceNanos >= STABLE_BEFORE_RECENTER_NANOS
                && referenceError <= AUTO_RECENTER_WINDOW_RADIANS) {
            // Correct only the small zero-point error range. Recentring a deliberate 6-8 degree
            // tilt would look like the scene drifting by itself and would create a reverse jump
            // when the phone returns to its original reading angle.
            nudgeReferenceTowardCurrent(
                    relativeRotation, referenceError, sampleDt);
            relativeRotation(
                    referenceRotation, screenRotation, relativeRotation);
        }

        float relativePitch = pitchFromRotation(relativeRotation);
        float relativeRoll = rollFromRotation(relativeRotation);
        float nextTargetX = cameraTargetXFromRoll(relativeRoll);
        float nextTargetY = cameraTargetYFromPitch(relativePitch);
        rawVelocityX = targetVelocity(
                rawTargetX, nextTargetX, sampleDt);
        rawVelocityY = targetVelocity(
                rawTargetY, nextTargetY, sampleDt);
        rawTargetX = nextTargetX;
        rawTargetY = nextTargetY;
        latestPoseTimestampNanos = now;
        copyRotation(screenRotation, previousRotation);
        previousSampleNanos = now;
    }

    private void nudgeReferenceTowardCurrent(
            float[] relative, float relativeAngle, float dt) {
        if (relativeAngle <= 0.000001f) return;
        float responseStep = relativeAngle
                * (1f - (float) Math.exp(
                        -REFERENCE_RESPONSE_PER_SECOND * dt));
        float stepAngle = Math.min(
                responseStep, MAX_REFERENCE_SPEED_RADIANS * dt);
        if (!fractionalRotation(
                relative, relativeAngle, stepAngle,
                referenceStepRotation)) {
            return;
        }
        multiplyRotation(
                referenceRotation, referenceStepRotation,
                updatedReferenceRotation);
        copyRotation(updatedReferenceRotation, referenceRotation);
    }

    @Override public void doFrame(long frameTimeNanos) {
        frameScheduled = false;
        // A retained controller on a hidden route is not an active spatial scene. Reduced-motion
        // mode remains active while resumed, so it keeps static depth without running a sensor.
        boolean active = attached && resumed;
        if (!active || !resumed || !dynamicAllowed || !sensorRunning) {
            dispatchFrame(active, 0f, 0f);
            return;
        }

        // Direct spherical-camera tracking: the newest rotation-vector pose is rendered on the
        // next display frame. There is deliberately no spring, target-rate limiter or low-pass.
        long displayNow = System.nanoTime();
        float predictedX = predictForDisplay(
                rawTargetX, rawVelocityX,
                latestPoseTimestampNanos, displayNow);
        float predictedY = predictForDisplay(
                rawTargetY, rawVelocityY,
                latestPoseTimestampNanos, displayNow);
        float outputScale = strength * directionMultiplier;
        float outputX = finiteProjection(predictedX * outputScale);
        float outputY = finiteProjection(predictedY * outputScale);
        dispatchFrame(true, outputX, outputY);
        scheduleFrame();
    }

    private void dispatchFrame(boolean active, float x, float y) {
        if (frameListener == null) return;
        if (active == lastDispatchedActive
                && nearlyEqual(x, lastDispatchedX)
                && nearlyEqual(y, lastDispatchedY)) {
            return;
        }
        lastDispatchedActive = active;
        lastDispatchedX = x;
        lastDispatchedY = y;
        try {
            frameListener.onSpatialFrame(active, x, y);
        } catch (Throwable t) {
            if (!listenerFailureLogged) {
                listenerFailureLogged = true;
                Main.log("spatial frame consumer failed: " + t);
            }
        }
    }

    private void scheduleFrame() {
        if (frameScheduled) return;
        frameScheduled = true;
        choreographer.postFrameCallback(this);
    }

    private void cancelScheduledFrame() {
        if (!frameScheduled) return;
        choreographer.removeFrameCallback(this);
        frameScheduled = false;
    }

    static float normalizeTilt(float deltaRadians) {
        if (!isFinite(deltaRadians)) return 0f;
        float angle = wrappedRadians(deltaRadians);
        if (angle == 0f) return 0f;
        return finiteProjection(
                (float) Math.sin(angle) / OPTICAL_REFERENCE_SINE);
    }

    static float cameraTargetXFromRoll(float relativeRoll) {
        // Device preview calibration: moving/tilting the phone left should carry the rear image
        // left as well. Keep this axis correction in one place so every depth plane stays coherent.
        return -normalizeTilt(relativeRoll);
    }

    static float cameraTargetYFromPitch(float relativePitch) {
        // The target device reports the user's forward/faceward local pitch with the sign that
        // already maps to upward View translation. Preserve it; the previous inverse was wrong.
        return normalizeTilt(relativePitch);
    }

    static float targetVelocity(
            float previousTarget, float currentTarget, float dt) {
        if (!isFinite(previousTarget) || !isFinite(currentTarget)
                || !isFinite(dt) || dt <= 0f) {
            return 0f;
        }
        return clamp(
                (currentTarget - previousTarget) / dt,
                -MAX_PREDICTION_SPEED_UNITS_PER_SECOND,
                MAX_PREDICTION_SPEED_UNITS_PER_SECOND);
    }

    static float predictForDisplay(
            float target, float velocity,
            long sampleTimestampNanos, long displayTimestampNanos) {
        if (!isFinite(target) || !isFinite(velocity)) return 0f;
        float sampleAge = sampleTimestampNanos > 0L
                ? Math.max(0f, (displayTimestampNanos
                        - sampleTimestampNanos) / 1_000_000_000f)
                : 0f;
        float horizon = clamp(
                sampleAge + DISPLAY_PREDICTION_LEAD_SECONDS,
                0f, MAX_DISPLAY_PREDICTION_SECONDS);
        return finiteProjection(target + velocity * horizon);
    }

    private static float finiteProjection(float value) {
        if (!isFinite(value)) {
            return Math.copySign(MAX_NUMERICAL_PROJECTION, value);
        }
        return clamp(
                value, -MAX_NUMERICAL_PROJECTION,
                MAX_NUMERICAL_PROJECTION);
    }

    static int axisXForRotation(int rotation) {
        if (rotation == Surface.ROTATION_90) return SensorManager.AXIS_Y;
        if (rotation == Surface.ROTATION_180) return SensorManager.AXIS_MINUS_X;
        if (rotation == Surface.ROTATION_270) return SensorManager.AXIS_MINUS_Y;
        return SensorManager.AXIS_X;
    }

    static int axisYForRotation(int rotation) {
        if (rotation == Surface.ROTATION_90) return SensorManager.AXIS_MINUS_X;
        if (rotation == Surface.ROTATION_180) return SensorManager.AXIS_MINUS_Y;
        if (rotation == Surface.ROTATION_270) return SensorManager.AXIS_X;
        return SensorManager.AXIS_Y;
    }

    static void relativeRotation(
            float[] reference, float[] current, float[] out) {
        if (reference == null || current == null || out == null
                || reference.length < 9 || current.length < 9
                || out.length < 9) {
            return;
        }
        // out = transpose(reference) * current. Inputs and output must be distinct.
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                out[row * 3 + column] =
                        reference[row] * current[column]
                        + reference[3 + row] * current[3 + column]
                        + reference[6 + row] * current[6 + column];
            }
        }
    }

    static float pitchFromRotation(float[] rotation) {
        if (rotation == null || rotation.length < 9) return 0f;
        return (float) Math.asin(clamp(-rotation[7], -1f, 1f));
    }

    static float rollFromRotation(float[] rotation) {
        if (rotation == null || rotation.length < 9) return 0f;
        return (float) Math.atan2(-rotation[6], rotation[8]);
    }

    static float rotationAngle(float[] rotation) {
        if (rotation == null || rotation.length < 9) return 0f;
        float cosine = (rotation[0] + rotation[4] + rotation[8] - 1f) * 0.5f;
        return (float) Math.acos(clamp(cosine, -1f, 1f));
    }

    private static boolean fractionalRotation(
            float[] rotation, float fullAngle, float stepAngle,
            float[] out) {
        if (rotation == null || rotation.length < 9
                || out == null || out.length < 9
                || fullAngle <= 0.000001f || stepAngle <= 0f) {
            return false;
        }
        float denominator = 2f * (float) Math.sin(fullAngle);
        if (Math.abs(denominator) < 0.000001f) return false;
        float axisX = (rotation[7] - rotation[5]) / denominator;
        float axisY = (rotation[2] - rotation[6]) / denominator;
        float axisZ = (rotation[3] - rotation[1]) / denominator;
        float axisLength = (float) Math.sqrt(
                axisX * axisX + axisY * axisY + axisZ * axisZ);
        if (axisLength < 0.000001f) return false;
        axisX /= axisLength;
        axisY /= axisLength;
        axisZ /= axisLength;
        float cosine = (float) Math.cos(stepAngle);
        float sine = (float) Math.sin(stepAngle);
        float oneMinusCosine = 1f - cosine;
        out[0] = cosine + axisX * axisX * oneMinusCosine;
        out[1] = axisX * axisY * oneMinusCosine - axisZ * sine;
        out[2] = axisX * axisZ * oneMinusCosine + axisY * sine;
        out[3] = axisY * axisX * oneMinusCosine + axisZ * sine;
        out[4] = cosine + axisY * axisY * oneMinusCosine;
        out[5] = axisY * axisZ * oneMinusCosine - axisX * sine;
        out[6] = axisZ * axisX * oneMinusCosine - axisY * sine;
        out[7] = axisZ * axisY * oneMinusCosine + axisX * sine;
        out[8] = cosine + axisZ * axisZ * oneMinusCosine;
        return true;
    }

    private static void multiplyRotation(
            float[] left, float[] right, float[] out) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                out[row * 3 + column] =
                        left[row * 3] * right[column]
                        + left[row * 3 + 1] * right[3 + column]
                        + left[row * 3 + 2] * right[6 + column];
            }
        }
    }

    private static void copyRotation(float[] source, float[] destination) {
        System.arraycopy(source, 0, destination, 0, 9);
    }

    private static boolean nearlyEqual(float first, float second) {
        return isFinite(first) && isFinite(second)
                && Math.abs(first - second) < 0.00025f;
    }

    private static float hypot(float x, float y) {
        return (float) Math.sqrt(x * x + y * y);
    }

    private static float wrappedRadians(float value) {
        if (!isFinite(value)) return 0f;
        float circle = (float) (Math.PI * 2d);
        float out = value % circle;
        if (out > Math.PI) out -= circle;
        if (out < -Math.PI) out += circle;
        return out;
    }

    private static boolean systemRequestsReducedMotion(Context context) {
        if (context == null) return false;
        try {
            return Settings.Global.getFloat(
                    context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean isPowerSaveMode(Context context) {
        if (context == null) return false;
        try {
            PowerManager manager = (PowerManager)
                    context.getSystemService(Context.POWER_SERVICE);
            return manager != null && manager.isPowerSaveMode();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static float clamp(float value, float min, float max) {
        if (!isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    /** Stable screen-space pose provider. It never integrates gyroscope angular velocity. */
    private static final class SensorPoseProvider implements SensorEventListener {
        interface PoseListener {
            void onPose(
                    float[] screenRotation, long timestampNanos,
                    int displayRotation);
        }

        private final Context context;
        private final PoseListener listener;
        private final SensorManager sensorManager;
        private final Sensor rotationVectorSensor;
        private final float[] rawMatrix = new float[9];
        private final float[] screenMatrix = new float[9];
        private WeakReference<Activity> activityRef = new WeakReference<>(null);
        private boolean registered;
        private boolean unavailableLogged;
        private boolean sampleFailureLogged;

        SensorPoseProvider(Context context, PoseListener listener) {
            this.context = context;
            this.listener = listener;
            SensorManager manager = context == null ? null
                    : (SensorManager) context.getSystemService(
                            Context.SENSOR_SERVICE);
            sensorManager = manager;
            Sensor sensor = manager == null ? null : manager.getDefaultSensor(
                    Sensor.TYPE_GAME_ROTATION_VECTOR);
            if (sensor == null && manager != null) {
                sensor = manager.getDefaultSensor(
                        Sensor.TYPE_ROTATION_VECTOR);
            }
            rotationVectorSensor = sensor;
        }

        void setActivity(Activity activity) {
            activityRef = new WeakReference<>(activity);
        }

        boolean isAvailable() {
            return sensorManager != null && rotationVectorSensor != null;
        }

        boolean start() {
            if (registered) return true;
            if (!isAvailable()) {
                if (!unavailableLogged) {
                    unavailableLogged = true;
                    Main.log("spatial motion static fallback: "
                            + "rotation-vector sensor unavailable");
                }
                return false;
            }
            registered = sensorManager.registerListener(
                    this, rotationVectorSensor,
                    SensorManager.SENSOR_DELAY_FASTEST);
            if (registered) {
                Main.log("spatial pose provider registered: "
                        + rotationVectorSensor.getName());
            }
            return registered;
        }

        void stop() {
            if (registered && sensorManager != null) {
                sensorManager.unregisterListener(this);
            }
            registered = false;
        }

        @Override public void onSensorChanged(SensorEvent event) {
            if (!registered || event == null || event.values == null
                    || listener == null) {
                return;
            }
            try {
                SensorManager.getRotationMatrixFromVector(
                        rawMatrix, event.values);
                int rotation = currentDisplayRotation();
                boolean remapped = SensorManager.remapCoordinateSystem(
                        rawMatrix, axisXForRotation(rotation),
                        axisYForRotation(rotation), screenMatrix);
                if (!remapped) return;
                listener.onPose(
                        screenMatrix,
                        event.timestamp > 0L
                                ? event.timestamp : System.nanoTime(),
                        rotation);
            } catch (Throwable t) {
                if (!sampleFailureLogged) {
                    sampleFailureLogged = true;
                    Main.log("spatial pose sample ignored: " + t);
                }
            }
        }

        private int currentDisplayRotation() {
            Activity activity = activityRef.get();
            try {
                if (activity != null && activity.getWindowManager() != null
                        && activity.getWindowManager().getDefaultDisplay() != null) {
                    return activity.getWindowManager()
                            .getDefaultDisplay().getRotation();
                }
                WindowManager manager = context == null ? null
                        : (WindowManager) context.getSystemService(
                                Context.WINDOW_SERVICE);
                if (manager != null && manager.getDefaultDisplay() != null) {
                    return manager.getDefaultDisplay().getRotation();
                }
            } catch (Throwable ignored) {}
            return Surface.ROTATION_0;
        }

        @Override public void onAccuracyChanged(
                Sensor sensor, int accuracy) {}
    }
}
