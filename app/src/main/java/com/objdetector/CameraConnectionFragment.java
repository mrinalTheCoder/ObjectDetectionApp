package com.objdetector;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.objdetector.customview.AutoFitTextureView;
import com.objdetector.utils.ErrorDialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CameraConnectionFragment extends Fragment {
    private static final int screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
    private static final int screenHeight = Resources.getSystem().getDisplayMetrics().heightPixels;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray(4);

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private static final String LOGGING_TAG = CameraConnectionFragment.class.getName();
    private static final Size DESIRED_PREVIEW_SIZE = new Size(screenWidth, screenHeight);

    private static final int MINIMUM_PREVIEW_SIZE = 500;
    private static final String FRAGMENT_DIALOG = "dialog";
    private final Semaphore cameraOpenCloseLock = new Semaphore(1);
    private OnImageAvailableListener imageListener;
    private ConnectionListener cameraConnectionListener;
    private String cameraId;
    private AutoFitTextureView textureView;
    private CameraCaptureSession captureSession;
    private CameraDevice cameraDevice;
    private Integer sensorOrientation;
    private Size previewSize;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private ImageReader previewReader;
    private CaptureRequest.Builder previewRequestBuilder;
    private CaptureRequest previewRequest;

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.camera_connection_fragment, container, false);
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        textureView = (AutoFitTextureView) view.findViewById(R.id.texture);
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();

        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(final SurfaceTexture texture, final int width, final int height) {
                    openCamera(width, height);
                }

                @Override
                public void onSurfaceTextureSizeChanged(final SurfaceTexture texture, final int width, final int height) {
                    configureTransform(width, height);
                }

                @Override
                public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(final SurfaceTexture texture) {
                }
            });
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    public void addConnectionListener(final ConnectionListener cameraConnectionListener) {
        this.cameraConnectionListener = cameraConnectionListener;
    }

    public void addImageAvailableListener(final OnImageAvailableListener imageListener) {
        this.imageListener = imageListener;
    }

    public interface ConnectionListener {
        void onPreviewSizeChosen(Size size, int cameraRotation);
    }

    private static Size chooseOptimalSize(final Size[] choices) {
        final int minSize = Math.max(Math.min(DESIRED_PREVIEW_SIZE.getWidth(),
                DESIRED_PREVIEW_SIZE.getHeight()), MINIMUM_PREVIEW_SIZE);
        Log.i(LOGGING_TAG, "Min size: " + minSize);

        // Collect the supported resolutions that are at least as big as the preview Surface
        final List<Size> bigEnough = new ArrayList();
        final List<Size> tooSmall = new ArrayList<Size>();
        for (final Size option : choices) {
            if (option.equals(DESIRED_PREVIEW_SIZE)) {
                return DESIRED_PREVIEW_SIZE;
            }

            if (option.getHeight() >= minSize && option.getWidth() >= minSize) {
                bigEnough.add(option);
            } else {
                tooSmall.add(option);
            }
        }
        // Pick the smallest of those, assuming we found any
        Size chosenSize = (bigEnough.size() > 0) ? Collections.min(bigEnough, new CompareSizesByArea(  )) : choices[0];

        Log.i(LOGGING_TAG, "Desired size: " + DESIRED_PREVIEW_SIZE + ", min size: " + minSize + "x" + minSize);
        Log.i(LOGGING_TAG, "Valid preview sizes: [" + TextUtils.join(", ", bigEnough) + "]");
        Log.i(LOGGING_TAG, "Rejected preview sizes: [" + TextUtils.join(", ", tooSmall) + "]");
        Log.i(LOGGING_TAG, "Chosen preview size: " + chosenSize);

        //return new Size(DESIRED_PREVIEW_SIZE.getHeight(), DESIRED_PREVIEW_SIZE.getWidth());
        return chosenSize;
    }

    private void showToast(final String text) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> Toast.makeText(activity, text, Toast.LENGTH_SHORT).show());
        }
    }

    private void setUpCameraOutputs() {
        final CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            for (final String cameraId : manager.getCameraIdList()) {
                final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                final StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                if (map == null) {
                    continue;
                }

                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                Log.i(LOGGING_TAG, "Sensor Orientation: " + sensorOrientation);

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class));


                // We fit the aspect ratio of TextureView to the size of preview we picked.
                final int orientation = getResources().getConfiguration().orientation;
                Log.i(LOGGING_TAG, "Resource Orientation: " + orientation);

                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
                } else {
                    textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
                }

                this.cameraId = cameraId;
            }
        } catch (final CameraAccessException ex) {
            Log.e(LOGGING_TAG, "Exception: " + ex.getMessage());
        } catch (final NullPointerException ex) {
            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            throw new RuntimeException(getString(R.string.camera_error));
        }

        cameraConnectionListener.onPreviewSizeChosen(previewSize, sensorOrientation);
    }

    private void openCamera(final int width, final int height) {
        setUpCameraOutputs();
        configureTransform(width, height);
        final Activity activity = getActivity();

        final CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            if (activity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(final CameraDevice cameraDevice) {
                        // This method is called when the camera is opened.  We start camera preview here.
                        cameraOpenCloseLock.release();
                        CameraConnectionFragment.this.cameraDevice = cameraDevice;
                        createCameraPreviewSession();
                    }

                    @Override
                    public void onDisconnected(final CameraDevice cameraDevice) {
                        cameraOpenCloseLock.release();
                        cameraDevice.close();
                        CameraConnectionFragment.this.cameraDevice = null;
                    }

                    @Override
                    public void onError(final CameraDevice cameraDevice, final int error) {
                        cameraOpenCloseLock.release();
                        cameraDevice.close();
                        CameraConnectionFragment.this.cameraDevice = null;
                        final Activity activity = getActivity();
                        if (null != activity) {
                            activity.finish();
                        }
                    }
                }, backgroundHandler);
            } else {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
            }
        } catch (final CameraAccessException ex) {
            Log.e(LOGGING_TAG, "Exception: " + ex.getMessage());
        } catch (final InterruptedException ex) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", ex);
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            cameraOpenCloseLock.acquire();
            if (null != captureSession) {
                captureSession.close();
                captureSession = null;
            }
            if (null != cameraDevice) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (null != previewReader) {
                previewReader.close();
                previewReader = null;
            }
        } catch (final InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            cameraOpenCloseLock.release();
        }
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("ImageListener");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (final InterruptedException ex) {
            Log.e(LOGGING_TAG, "Exception: " + ex.getMessage());
        }
    }

    private void createCameraPreviewSession() {
        try {
            final SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

            // This is the output Surface we need to start preview.
            final Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            Log.i(LOGGING_TAG, String.format("Opening camera preview: "
                    + previewSize.getWidth() + "x" + previewSize.getHeight()));

            // Create the reader for the preview frames.
            previewReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(),
                    ImageFormat.YUV_420_888, 2);

            previewReader.setOnImageAvailableListener(imageListener, backgroundHandler);
            previewRequestBuilder.addTarget(previewReader.getSurface());

            //fixDeviceCameraOrientation(previewRequestBuilder);

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice.createCaptureSession(Arrays.asList(surface, previewReader.getSurface()),
                    getCaptureSessionStateCallback(), null);
        } catch (final CameraAccessException ex) {
            Log.e(LOGGING_TAG, "Exception: " + ex.getMessage());
        }
    }

    private CameraCaptureSession.StateCallback getCaptureSessionStateCallback() {
        return new CameraCaptureSession.StateCallback() {

            @Override
            public void onConfigured(final CameraCaptureSession cameraCaptureSession) {
                // The camera is already closed
                if (null == cameraDevice) {
                    return;
                }

                // When the session is ready, we start displaying the preview.
                captureSession = cameraCaptureSession;
                try {
                    // Auto focus should be continuous for camera preview.
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    // Flash is automatically enabled when necessary.
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                    //fixDeviceCameraOrientation(previewRequestBuilder);

                    // Finally, we start displaying the camera preview.
                    previewRequest = previewRequestBuilder.build();
                    captureSession.setRepeatingRequest(previewRequest, null, backgroundHandler);
                } catch (final CameraAccessException ex) {
                    Log.e(LOGGING_TAG, "Exception: " + ex.getMessage());
                }
            }

            @Override
            public void onConfigureFailed(final CameraCaptureSession cameraCaptureSession) {
                showToast("Failed");
            }
        };
    }

    private void configureTransform(final int viewWidth, final int viewHeight) {
        final Activity activity = getActivity();
        if (null == textureView || null == previewSize || null == activity) {
            return;
        }
        final int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        final Matrix matrix = new Matrix();
        final RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        final RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        final float centerX = viewRect.centerX();
        final float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            final float scale =
                    Math.max(
                            (float) viewHeight / previewSize.getHeight(),
                            (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        textureView.setTransform(matrix);
    }

    private void fixDeviceCameraOrientation(CaptureRequest.Builder previewRequestBuilder) {
        final int deviceRotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
        int jpegOrientation =
                (ORIENTATIONS.get(deviceRotation) + sensorOrientation + 270) % 360;
        previewRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation);

    }

    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(final Size lhs, final Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight()
                    - (long) rhs.getWidth() * rhs.getHeight());
        }
    }
}