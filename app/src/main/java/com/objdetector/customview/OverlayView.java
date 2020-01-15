package com.objdetector.customview;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.MediaPlayer;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.Toast;

import com.objdetector.deepmodel.DetectionResult;

import java.util.LinkedList;
import java.util.List;


public class OverlayView extends View {
    private static int INPUT_SIZE = 416;

    private final Paint paint;
    private final List<DrawCallback> callbacks = new LinkedList();
    private List<DetectionResult> results;
    private List<Integer> colors;
    private float resultsViewHeight;
    private Context context;

    public OverlayView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                40, getResources().getDisplayMetrics()));
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
                    String title = results.get(i).getTitle() + String.format(" %.2f", results.get(i).getConfidence());
                    paint.setColor(Color.RED);
                    paint.setStyle(Paint.Style.STROKE);
                    canvas.drawRect(box, paint);
                    paint.setStrokeWidth(3.0f);
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
        float overlayViewHeight = this.getHeight() - resultsViewHeight;
        float sizeMultiplier = Math.min((float) this.getWidth() / (float) INPUT_SIZE,
                overlayViewHeight / (float) INPUT_SIZE);

        float offsetX = (this.getWidth() - INPUT_SIZE * sizeMultiplier) / 2;
        float offsetY = (overlayViewHeight - INPUT_SIZE * sizeMultiplier) / 2 + resultsViewHeight;

        float left = Math.max(padding, sizeMultiplier * rect.left + offsetX);
        float top = Math.max(offsetY + padding, sizeMultiplier * rect.top + offsetY);

        float right = Math.min(rect.right * sizeMultiplier, this.getWidth() - padding);
        float bottom = Math.min(rect.bottom * sizeMultiplier + offsetY, this.getHeight() - padding);

        return new RectF(left, top, right, bottom);
    }

    public static void playAssetSound(Context context, String soundFileName) {
        try {
            MediaPlayer mediaPlayer = new MediaPlayer();

            AssetFileDescriptor descriptor = context.getAssets().openFd(soundFileName);
            mediaPlayer.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength());
            descriptor.close();

            mediaPlayer.prepare();
            mediaPlayer.setVolume(1f, 1f);
            mediaPlayer.setLooping(false);
            mediaPlayer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}