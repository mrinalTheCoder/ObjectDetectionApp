package com.objdetector.deepmodel;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public class MobileNetObjDetector {
    private static final String MODEL_FILENAME = "detect.tflite";
    private static final String LABEL_FILENAME = "labelmap.txt";
    private static final int INPUT_SIZE = 300;
    private static final int NUM_BYTES_PER_CHANNEL = 1;
    private static final float IMAGE_MEAN = 128.0f;
    private static final float IMAGE_STD = 128.0f;
    private static final int NUM_DETECTIONS = 10;
    private static final String LOGGING_TAG = MobileNetObjDetector.class.getName();

    private ByteBuffer imgData;
    private Interpreter tfLite;
    private int[] intValues;
    private float[][][] outputLocations;
    private float[][] outputClasses;
    private float[][] outputScores;
    private float[] numDetections;
    private Vector<String> labels = new Vector<String>();

    private MobileNetObjDetector(final AssetManager assetManager) throws IOException {
        init(assetManager);
    }

    private void init(final AssetManager assetManager) throws IOException {
        imgData = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * NUM_BYTES_PER_CHANNEL);
        imgData.order(ByteOrder.nativeOrder());
        intValues = new int[INPUT_SIZE * INPUT_SIZE];
        outputLocations = new float[1][NUM_DETECTIONS][4];
        outputClasses = new float[1][NUM_DETECTIONS];
        outputScores = new float[1][NUM_DETECTIONS];
        numDetections = new float[1];

        InputStream labelsInput = assetManager.open(LABEL_FILENAME);
        BufferedReader br = new BufferedReader(new InputStreamReader(labelsInput));
        String line;
        while ((line = br.readLine()) != null) {
            labels.add(line);
        }
        br.close();

        try {
            tfLite = new Interpreter(loadModelFile(assetManager));
            Log.i(LOGGING_TAG, "Input tensor shapes:");
            for (int i=0; i<tfLite.getInputTensorCount(); i++) {
                int[] shape = tfLite.getInputTensor(i).shape();
                String stringShape = "";
                for(int j = 0; j < shape.length; j++) {
                    stringShape = stringShape + ", " + shape[j];
                }
                Log.i(LOGGING_TAG, "Shape of input tensor " + i + ": " + stringShape);
            }
            Log.i(LOGGING_TAG, "Output tensor shapes:");
            for (int i=0; i<tfLite.getOutputTensorCount(); i++) {
                int[] shape = tfLite.getOutputTensor(i).shape();
                String stringShape = "";
                for(int j = 0; j < shape.length; j++) {
                    stringShape = stringShape + ", " + shape[j];
                }
                Log.i(LOGGING_TAG, "Shape of output tensor " + i + ": " + tfLite.getOutputTensor(i).name() + " " + stringShape);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

    }

    public static MobileNetObjDetector create(final AssetManager assetManager) throws IOException {
        return new MobileNetObjDetector(assetManager);
    }

    private static MappedByteBuffer loadModelFile(AssetManager assets)
            throws IOException {
        AssetFileDescriptor fileDescriptor = assets.openFd(MODEL_FILENAME);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public void close() {
        tfLite.close();
    }

    public List<DetectionResult> detectObjects(final Bitmap bitmap) {
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        imgData.rewind();
        for (int i = 0; i < INPUT_SIZE; ++i) {
            for (int j = 0; j < INPUT_SIZE; ++j) {
                int pixelValue = intValues[i * INPUT_SIZE + j];
                imgData.put((byte) ((pixelValue >> 16) & 0xFF));
                imgData.put((byte) ((pixelValue >> 8) & 0xFF));
                imgData.put((byte) (pixelValue & 0xFF));
            }
        }

        Object[] inputArray = {imgData};
        Map<Integer, Object> outputMap = new HashMap<>();
        outputMap.put(0, outputLocations);
        outputMap.put(1, outputClasses);
        outputMap.put(2, outputScores);
        outputMap.put(3, numDetections);
        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);

        final ArrayList<DetectionResult> recognitions = new ArrayList<>(NUM_DETECTIONS);
        for (int i = 0; i < NUM_DETECTIONS; ++i) {
            final RectF detection =
                    new RectF(
                            outputLocations[0][i][1] * INPUT_SIZE,
                            outputLocations[0][i][0] * INPUT_SIZE,
                            outputLocations[0][i][3] * INPUT_SIZE,
                            outputLocations[0][i][2] * INPUT_SIZE);
            int labelOffset = 1;
            recognitions.add(
                    new DetectionResult(
                            i,
                            labels.get((int) outputClasses[0][i] + labelOffset),
                            outputScores[0][i],
                            detection));
        }
        return recognitions;
    }
}
