package com.example.volleyball00

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.exp
import kotlin.math.roundToInt


enum class BodyPart {
    PA16J_00,
    PA16J_01,
    PA16J_02,
    PA16J_03,
    PA16J_04,
    PA16J_05,
    PA16J_06,
    PA16J_07,
    PA16J_08,
    PA16J_09,
    PA16J_10,
    PA16J_11,
    PA16J_12,
    PA16J_13,
    PA16J_14,
    PA16J_15
}

class Position {
    var x: Int = 0
    var y: Int = 0
}

class KeyPoint {
    var bodyPart: BodyPart = BodyPart.PA16J_00 //BodyPart.NOSE
    var position: Position = Position()
    var score: Float = 0.0f
}

class Person {
    var keyPoints = listOf<KeyPoint>()
    var score: Float = 0.0f
}

enum class Device {
    CPU,
    NNAPI,
    GPU
}

class DLuvizon2D(
    val context: Context,
    //val filename: String = "posenet_model.tflite",
    val filename: String = "modelo2D.tflite",
    val device: Device = Device.CPU
    ) : AutoCloseable {
        var lastInferenceTimeNanos: Long = -1
        private set

                /** An Interpreter for the TFLite model.   */
                private var interpreter: Interpreter? = null
        private var gpuDelegate: GpuDelegate? = null
        private val NUM_LITE_THREADS = 4

    private fun getInterpreter(): Interpreter {
        if (interpreter != null) {
            return interpreter!!
        }
        val options = Interpreter.Options()
        options.setNumThreads(NUM_LITE_THREADS)
        when (device) {
            Device.CPU -> { }
            Device.GPU -> {
                gpuDelegate = GpuDelegate()
                options.addDelegate(gpuDelegate)
            }
            Device.NNAPI -> options.setUseNNAPI(true)
        }
        interpreter = Interpreter(loadModelFile(filename, context), options)
        //Log.i("model", interpreter.toString()) //:deb:
        //exitProcess(0) //:deb:
        return interpreter!!
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
    }

    /** Returns value within [0,1].   */
    private fun sigmoid(x: Float): Float {
        return (1.0f / (1.0f + exp(-x)))
    }

    /**
     * Scale the image to a byteBuffer of [-1,1] values.
     */
    private fun initInputArray(bitmap: Bitmap): ByteBuffer {
        val bytesPerChannel = 4
        val inputChannels = 3
        val batchSize = 1
        val inputBuffer = ByteBuffer.allocateDirect(
                batchSize * bytesPerChannel * bitmap.height * bitmap.width * inputChannels
        )
        inputBuffer.order(ByteOrder.nativeOrder())
        inputBuffer.rewind()

        val mean = 128.0f
        val std = 128.0f
        val intValues = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (pixelValue in intValues) {
            inputBuffer.putFloat(((pixelValue shr 16 and 0xFF) - mean) / std)
            inputBuffer.putFloat(((pixelValue shr 8 and 0xFF) - mean) / std)
            inputBuffer.putFloat(((pixelValue and 0xFF) - mean) / std)
        }
        return inputBuffer
    }

    /** Preload and memory map the model file, returning a MappedByteBuffer containing the model. */
    private fun loadModelFile(path: String, context: Context): MappedByteBuffer {

        Log.i("chiri", Arrays.toString(context.assets.list("")));
        Log.i("path", path);
        val fileDescriptor = context.assets.openFd(path)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        return inputStream.channel.map(
                FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength
        )
    }

    /**
     * Initializes an outputMap of 1 * x * y * z FloatArrays for the model processing to populate.
     */
    private fun initOutputMap(interpreter: Interpreter): HashMap<Int, Any> {
        val outputMap = HashMap<Int, Any>()
        val iblockHm = 15
        val hmShape = interpreter.getOutputTensor(iblockHm).shape()
        outputMap[iblockHm] = Array(hmShape[0]) {
            Array(hmShape[1]) {
                Array(hmShape[2]) { FloatArray(hmShape[3])}}
        }


        val iblockPose = 7
        val poseShape = interpreter.getOutputTensor(iblockPose).shape()
        outputMap[iblockPose] = Array(poseShape[0]) {
            Array(poseShape[1]) { FloatArray(poseShape[2])}
        }

        val iblockV = 23
        val vShape = interpreter.getOutputTensor(iblockV).shape()
        outputMap[iblockV] = Array(vShape[0]) {
            Array(vShape[1]) { FloatArray(vShape[2])}
        }

        return outputMap
    }

    /**
     * Estimates the pose for a single person.
     * args:
     *      bitmap: image bitmap of frame that should be processed
     * returns:
     *      person: a Person object containing data about keypoint locations and confidence scores
     *
     * Modif by vhgiron-u
     */
    @Suppress("UNCHECKED_CAST")
    fun estimateSinglePose(bitmap: Bitmap): Person {
        val estimationStartTimeNanos = SystemClock.elapsedRealtimeNanos()
        val inputArray = arrayOf(initInputArray(bitmap))
        Log.i(
                "posenet",
                String.format(
                        "Scaling to [-1,1] took %.2f ms",
                        1.0f * (SystemClock.elapsedRealtimeNanos() - estimationStartTimeNanos) / 1_000_000
                )
        )
        //:deb: getInterpreter() corre ok

        //Log.i("estimatePose", "t odo ok hasta aqui") //:deb:
        //if(true)  exitProcess(0) //:deb:

        val outputMap = initOutputMap(getInterpreter())


        val inferenceStartTimeNanos = SystemClock.elapsedRealtimeNanos()



        getInterpreter().runForMultipleInputsOutputs(inputArray, outputMap)



        lastInferenceTimeNanos = SystemClock.elapsedRealtimeNanos() - inferenceStartTimeNanos


        Log.i(
                "posenet",
                String.format("Interpreter took %.2f ms", 1.0f * lastInferenceTimeNanos / 1_000_000)
        )


        val iblockPose = 7
//    Log.i("outputMap", "indices de outputMap:") //:deb:
//    outputMap.forEach { (k, v) ->
//      Log.i(
//        k.toString(),
//        Arrays.deepToString(outputMap[k] as Array<Any>)
//      )
//    }

        val pose = (outputMap[iblockPose] as Array<Array<FloatArray>>)[0]
//    Log.i("pose", Arrays.deepToString(pose))

        val iblockProb = 23
        val probs = (outputMap[iblockProb] as Array<Array<FloatArray>>)[0]


        //comentar
//    val heatmaps = outputMap[0] as Array<Array<Array<FloatArray>>>
//    val offsets = outputMap[1] as Array<Array<Array<FloatArray>>>
//
//    val height = heatmaps[0].size
//    val width = heatmaps[0][0].size
        //comentar


        val numKeypoints = pose.size

        // Finds the (row, col) locations of where the keypoints are most likely to be.
        val keypointPositions = Array(numKeypoints) { Pair(0, 0) }


        val xCoords = IntArray(numKeypoints)
        val yCoords = IntArray(numKeypoints)
        for (keypoint in 0 until numKeypoints) {
            val currJoint = pose[keypoint]
            val row = (currJoint[0] * 256).roundToInt()
            val col = (currJoint[1] * 256).roundToInt()
            keypointPositions[keypoint] = Pair(row, col)
            yCoords[keypoint] = col
            xCoords[keypoint] = row

        }


        val confidenceScores = FloatArray(numKeypoints)
        for (keypoint in 0 until numKeypoints) {
            confidenceScores[keypoint] = probs[keypoint][0]
        }




        val person = Person()
        val keypointList = Array(numKeypoints) { KeyPoint() }
        var totalScore = 0.0f
        enumValues<BodyPart>().forEachIndexed { idx, it ->
            keypointList[idx].bodyPart = it
            keypointList[idx].position.x = xCoords[idx]
            keypointList[idx].position.y = yCoords[idx]
            keypointList[idx].score = confidenceScores[idx]
            totalScore += confidenceScores[idx]
        }

        person.keyPoints = keypointList.toList()
        person.score = totalScore / numKeypoints

        return person
    }



}