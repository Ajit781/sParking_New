package com.innovus.sparkingnew.scanner;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;

/**
 * Custom Viewfinder overlay matching the style of text_reader_scanner.
 * Displays a darkened scrim, rounded rectangular scanning cutout,
 * emerald green corner brackets, and a smooth animated laser beam.
 */
public class ScannerOverlayView extends View {

    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint laserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF frameRect = new RectF();
    private float laserPositionFraction = 0.0f;
    private ValueAnimator laserAnimator;
    private boolean isMatchFound = false;

    private static final int COLOR_PRIMARY_GREEN = 0xFF10B981;
    private static final int COLOR_SUCCESS_GREEN = 0xFF059669;
    private static final int COLOR_SCRIM = 0x99000000;

    public ScannerOverlayView(Context context) {
        super(context);
        init();
    }

    public ScannerOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ScannerOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        scrimPaint.setColor(COLOR_SCRIM);
        scrimPaint.setStyle(Paint.Style.FILL);

        clearPaint.setColor(Color.TRANSPARENT);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        borderPaint.setColor(COLOR_PRIMARY_GREEN);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dpToPx(2));

        cornerPaint.setColor(COLOR_PRIMARY_GREEN);
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(dpToPx(4));
        cornerPaint.setStrokeCap(Paint.Cap.ROUND);

        laserPaint.setColor(COLOR_PRIMARY_GREEN);
        laserPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        laserPaint.setStrokeWidth(dpToPx(3));

        startLaserAnimation();
    }

    private void startLaserAnimation() {
        laserAnimator = ValueAnimator.ofFloat(0.05f, 0.95f);
        laserAnimator.setDuration(2000);
        laserAnimator.setRepeatMode(ValueAnimator.REVERSE);
        laserAnimator.setRepeatCount(ValueAnimator.INFINITE);
        laserAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        laserAnimator.addUpdateListener(animation -> {
            laserPositionFraction = (float) animation.getAnimatedValue();
            invalidate();
        });
        laserAnimator.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // Aspect ratio appropriate for vehicle number plates (around 2.2 : 1)
        float maxAllowedWidth = w * 0.86f;
        float targetWidth = Math.min(maxAllowedWidth, dpToPx(340));
        float targetHeight = targetWidth * 0.48f; // ~160dp height

        float left = (w - targetWidth) / 2f;
        float top = (h - targetHeight) / 2f - dpToPx(30); // slightly higher than true center for ergonomics
        float right = left + targetWidth;
        float bottom = top + targetHeight;

        frameRect.set(left, top, right, bottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (frameRect.isEmpty()) return;

        float cornerRadius = dpToPx(16);

        // 1. Draw dark translucent scrim over the entire canvas
        canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);

        // 2. Clear out the transparent viewfinder rectangle
        canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, clearPaint);

        // 3. Draw border
        int activeColor = isMatchFound ? COLOR_SUCCESS_GREEN : COLOR_PRIMARY_GREEN;
        borderPaint.setColor(activeColor);
        borderPaint.setAlpha(isMatchFound ? 255 : 180);
        canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, borderPaint);

        // 4. Draw stylish corner brackets
        cornerPaint.setColor(activeColor);
        float cornerLen = dpToPx(24);
        drawCornerBrackets(canvas, frameRect, cornerLen, cornerRadius);

        // 5. Draw animated laser line
        if (!isMatchFound) {
            float laserY = frameRect.top + (frameRect.height() * laserPositionFraction);
            float laserPadding = dpToPx(8);
            float laserLeft = frameRect.left + laserPadding;
            float laserRight = frameRect.right - laserPadding;

            // Gradient shader for laser line glow
            LinearGradient gradient = new LinearGradient(
                    laserLeft, laserY, laserRight, laserY,
                    new int[]{0x0010B981, COLOR_PRIMARY_GREEN, 0x0010B981},
                    new float[]{0.0f, 0.5f, 1.0f},
                    Shader.TileMode.CLAMP
            );
            laserPaint.setShader(gradient);
            canvas.drawLine(laserLeft, laserY, laserRight, laserY, laserPaint);
        }
    }

    private void drawCornerBrackets(Canvas canvas, RectF rect, float len, float radius) {
        // Top-Left
        canvas.drawLine(rect.left, rect.top + len, rect.left, rect.top + radius, cornerPaint);
        canvas.drawArc(rect.left, rect.top, rect.left + radius * 2, rect.top + radius * 2, 180, 90, false, cornerPaint);
        canvas.drawLine(rect.left + radius, rect.top, rect.left + len, rect.top, cornerPaint);

        // Top-Right
        canvas.drawLine(rect.right - len, rect.top, rect.right - radius, rect.top, cornerPaint);
        canvas.drawArc(rect.right - radius * 2, rect.top, rect.right, rect.top + radius * 2, 270, 90, false, cornerPaint);
        canvas.drawLine(rect.right, rect.top + radius, rect.right, rect.top + len, cornerPaint);

        // Bottom-Left
        canvas.drawLine(rect.left, rect.bottom - len, rect.left, rect.bottom - radius, cornerPaint);
        canvas.drawArc(rect.left, rect.bottom - radius * 2, rect.left + radius * 2, rect.bottom, 90, 90, false, cornerPaint);
        canvas.drawLine(rect.left + radius, rect.bottom, rect.left + len, rect.bottom, cornerPaint);

        // Bottom-Right
        canvas.drawLine(rect.right - len, rect.bottom, rect.right - radius, rect.bottom, cornerPaint);
        canvas.drawArc(rect.right - radius * 2, rect.bottom - radius * 2, rect.right, rect.bottom, 0, 90, false, cornerPaint);
        canvas.drawLine(rect.right, rect.bottom - radius, rect.right, rect.bottom - len, cornerPaint);
    }

    public void setMatchFound(boolean matchFound) {
        this.isMatchFound = matchFound;
        if (matchFound && laserAnimator != null) {
            laserAnimator.cancel();
        }
        invalidate();
    }

    public RectF getFrameRect() {
        return frameRect;
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (laserAnimator != null) {
            laserAnimator.cancel();
        }
    }
}
