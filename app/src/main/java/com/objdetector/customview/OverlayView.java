package com.objdetector.customview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import com.objdetector.deepmodel.DetectionResult;

import java.util.LinkedList;
import java.util.List;


public class OverlayView extends View {
    private static int INPUT_SIZE = 300;

    private final Paint paint;
    private final List<DrawCallback> callbacks = new LinkedList();
    private List<DetectionResult> results;
    private List<Integer> colors;
    private float resultsViewHeight;

    public OverlayView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                20, getResources().getDisplayMetrics()));
        resultsViewHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                112, getResources().getDisplayMetrics());
    }

    public void addCallback(final DrawCallback callback) {
        callbacks.add(callback);
    }

    @Override
    public synchronized void onDraw(final Canvas canvas) {
        for (final DrawCallback callback : callbacks) {
            callback.drawCallback(canvas);
        }

        if (results != null) {
            for (int i = 0; i < results.size(); i++) {
                if (results.get(i).getConfidence() > 0.5) {
                    RectF box = reCalcSize(results.get(i).getLocation());
                    String title = results.get(i).getTitle() + String.format(" %2.2f", results.get(i).getConfidence()*100) + "%";
                    paint.setColor(Color.RED);
                    paint.setStyle(Paint.Style.STROKE);
                    canvas.drawRect(box, paint);
                    paint.setStrokeWidth(2.0f);
                    paint.setStyle(Paint.Style.FILL_AND_STROKE);
                    canvas.drawText(title, box.left, box.top, paint);
                }
            }
        }
    }

    public void setResults(final List<DetectionResult> results) {
        this.results = results;
        postInvalidate();
    }

    public interface DrawCallback {
        void drawCallback(final Canvas canvas);
    }

    private RectF reCalcSize(RectF rect) {
        int padding = 5;
        float overlayViewHeight = getHeight() - resultsViewHeight;
        float sizeMultiplier = Math.min((float) getWidth() / (float) INPUT_SIZE,
                overlayViewHeight / (float) INPUT_SIZE);

        float offsetX = (getWidth() - INPUT_SIZE * sizeMultiplier) / 2;
        float offsetY = (overlayViewHeight - INPUT_SIZE * sizeMultiplier) / 2 + resultsViewHeight;

        float left = Math.max(padding, sizeMultiplier * rect.left + offsetX);
        float top = Math.max(offsetY + padding, sizeMultiplier * rect.top + offsetY);

        float right = Math.min(rect.right * sizeMultiplier, getWidth() - padding);
        float bottom = Math.min(rect.bottom * sizeMultiplier + offsetY, getHeight() - padding);

        RectF newRect = new RectF(left, top, right, bottom);
        return newRect;
    }

}