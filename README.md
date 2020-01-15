# Object detection using TensorFlow Lite on Android
### Overview
This is an android app for object detection using [TensorFlow Lite](https://www.tensorflow.org/lite) on a mobile device. I have used a pretrained MobileNet SSD quantized model from [here](https://storage.googleapis.com/download.tensorflow.org/models/tflite/coco_ssd_mobilenet_v1_1.0_quant_2018_06_29.zip). It is  trained on the ImageNet dataset which can detect about 90 classes including banana, scissors, laptop, remote, vase etc. The main issue faced earlier of object detection on mobile is that the models are often too big to run. Tensorflow Lite solves this problem by providing a lightweight solution to run machine learning models on mobile. You can see [here](https://drive.google.com/file/d/0B_1Jj2xWSEEWd1FTdlkyZkZBYzQtMm82WlpHZGwxYVY5ZG9v/view?usp=sharing) yourself how nicely the app works and detects multiple objects quickly.

### TensorFlow Lite Model
TensorFlow Lite is not designed to train a model, the model can be trained on a higher power device. Then, the pretrained model can be converted to a TensorFlow Lite format (.tflite), which has a smaller footprint than can be easily run on a mobile or other embedded devices for classification, regresion or other such tasks. The model (.tflite) file and the class labels (.txt) file need to be placed in the [assets](https://github.com/mrinalTheCoder/ObjectDetectionApp/tree/master/app/src/main/assets) folder of the android app.

### The Android App for Object Detection
I have followed the [TensorFlow Lite example for Object Detection](https://github.com/tensorflow/examples/tree/master/lite/examples/object_detection).
In this app we will get a running feed from the mobile device camera, then, run object detection on the frame in background, and then overlay the results of object detection on the frame with a bounding box.<br/><br/>
First step here is to create an android app using Android Studio. My main Activity is [MainActivity](https://github.com/mrinalTheCoder/ObjectDetectionApp/blob/master/app/src/main/java/com/objdetector/MainActivity.java) which will invoke the object detector. It extends the [CameraActivity](https://github.com/mrinalTheCoder/ObjectDetectionApp/blob/master/app/src/main/java/com/objdetector/CameraActivity.java) which in turn uses a [CameraConnectionFragment](https://github.com/mrinalTheCoder/ObjectDetectionApp/blob/master/app/src/main/java/com/objdetector/CameraConnectionFragment.java) to manage all camera related stuff.<br/><br/>
The object detector is encapsulated by [MobileNetObjDetector](https://github.com/mrinalTheCoder/ObjectDetectionApp/blob/master/app/src/main/java/com/objdetector/deepmodel/MobileNetObjDetector.java) which uses the [TensorFlow Lite Interpreter](https://www.tensorflow.org/lite/guide/inference#load_and_run_a_model_in_java).<br/><br/>
`import org.tensorflow.lite.Interpreter;`<br/><br/>
Its very easy to initialize the Interpreter with the model:<br/><br/>
`private Interpreter tflite;
tfLite = new Interpreter(loadModelFile(assetManager));`<br/><br/>
For object detection, it feeds an image of size 300x300 to the model and obtains the output as defined by the model.<br/><br/>
`tfLite.runForMultipleInputsOutputs(inputArray, outputMap);`<br/><br/>
Then the `MobileNetObjDetector` convertes the `outputMap` into a List of [DetectionResult](https://github.com/mrinalTheCoder/ObjectDetectionApp/blob/master/app/src/main/java/com/objdetector/deepmodel/DetectionResult.java) which can be easily consumed for painting the overlay. Each `DetectionResult` has the label detected, the confidence score of the detection and the bounding box of the detection.<br/><br/>
The [OverlayView](https://github.com/mrinalTheCoder/ObjectDetectionApp/blob/master/app/src/main/java/com/objdetector/customview/OverlayView.java) takes care of resizing the bounding bozes as per the mobile device screen preview size and render it on top of the camera frame.

### Results
![Dining Table with Cups](https://drive.google.com/file/d/14Ly6jux3l7EWTR5pL1-xID_LPvV7YrQ0/view)
![TV](https://drive.google.com/file/d/14KVNN5wOkQKjW8gmYFKUfi50EkUG0Kcu/view)
![Microwave](https://drive.google.com/file/d/14I64oZUMVdaahYL7jH8lGQepMEvHE4S9/view)

Checkout more in a video [here](https://drive.google.com/file/d/0B_1Jj2xWSEEWd1FTdlkyZkZBYzQtMm82WlpHZGwxYVY5ZG9v/view?usp=sharing)
