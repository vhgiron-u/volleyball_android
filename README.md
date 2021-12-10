# Pose Estimation on Android
Modifications to the TensorFlow Lite PoseNet example. Main goals:

- Use a model different from the default PoseNet. In this case, [Luvizon's 2D Human Pose Estimation model](https://github.com/dluvizon/pose-regression).

The app currently has two modes: Server (default, main page) and Local. Since the Server mode requires authentication to a remote server for additional process, it is preferred to test the Local mode because no internet connection nor authentication will be required.

Results of Pose Estimation on a single image:

<img src="/results_demo/before.jpg" width="250"> <img src="/results_demo/after.jpg" width="250">

## Demo

### Getting Started

We will be testing the Local mode, since it will not require access to a remote server.

- Download the model weights: [Link](https://drive.google.com/drive/folders/1yMA1972YNS2zXgyzCa2vWvWv7gRceVTU?usp=sharing). Or convert the keras model of [Luvizon](https://github.com/dluvizon/pose-regression/releases) to Tensorflow Lite.
- Move the weights to the folder `app/src/main/assets`
- Clone our repository: `git clone https://github.com/vhgiron-u/volleyball_android.git`
- Open the project in Android Studio and Run, wait for the app to start
- Tap on "Try Local Processing"
- Tap on "Browse"
- Select the image that you want to test. It is required that there is only one person in the image. The image will be displayed on the screen.
- Tap on "Predict". Now the skeleton is added to the image.


(Video demo available soon)
