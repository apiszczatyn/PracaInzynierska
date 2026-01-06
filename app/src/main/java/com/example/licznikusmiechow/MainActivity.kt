package com.example.licznikusmiechow

import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.core.BaseOptions

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Size
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

@RequiresApi(Build.VERSION_CODES.O)
class MainActivity : ComponentActivity() {

    private lateinit var faceLandmarker: FaceLandmarker
    private lateinit var svm: SmileSvmInterpreter
    private lateinit var yuv: YuvToRgbConverter
    private lateinit var effectsManager: SmileEffectsManager

    private lateinit var previewView: PreviewView
    private lateinit var smileText: TextView

    private val executor = Executors.newSingleThreadExecutor()
    private var frameCount = 0
    private var lastSmilingState = false

    private val askCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { if (it) startCamera() else finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
/*        smileText = findViewById(R.id.smileText)*/

        yuv = YuvToRgbConverter(this)
        svm = SmileSvmInterpreter(this)

        effectsManager = SmileEffectsManager(
            context = this,
            previewView = previewView,
            effectsLayer = findViewById(R.id.starsLayer)
        )

        initFaceLandmarker()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) startCamera()
        else askCamera.launch(Manifest.permission.CAMERA)
    }

    private fun initFaceLandmarker() {
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("face_landmarker.task")
                    .build()
            )
            .setRunningMode(RunningMode.VIDEO)
            .setNumFaces(1)
            .build()

        faceLandmarker = FaceLandmarker.createFromOptions(this, options)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(executor) { proxy ->
                try {
                    frameCount = (frameCount + 1) % 3
                    if (frameCount != 0) return@setAnalyzer

                    var bmp = proxy.toBitmap(yuv)
                    bmp = bmp.rotate(proxy.imageInfo.rotationDegrees)

                    val mpImage = BitmapImageBuilder(bmp).build()
                    val result: FaceLandmarkerResult =
                        faceLandmarker.detectForVideo(mpImage, SystemClock.uptimeMillis())

                    if (result.faceLandmarks().isEmpty()) return@setAnalyzer

                    val landmarks = result.faceLandmarks()[0]
                    val lips = LipsFeatureExtractor.LIPS_INDICES.map {
                        PointF(landmarks[it].x(), landmarks[it].y())
                    }

                    val features = LipsFeatureExtractor.computeFeaturesFromLips(lips)
                    val smiling = svm.isSmiling(features)
                    val score = svm.decisionScore(features) // podgląd score na ekranie / roboczo

                    val minX = lips.minOf { it.x }
                    val maxX = lips.maxOf { it.x }
                    val minY = lips.minOf { it.y }

                    val w = previewView.width.toFloat()
                    val h = previewView.height.toFloat()

                    val loc = IntArray(2)
                    previewView.getLocationOnScreen(loc)

                    val mouthX = loc[0] + w * (1f - (minX + maxX) / 2f)
                    val mouthY = loc[1] + h * minY

                    runOnUiThread {
                        if (smiling && !lastSmilingState) {
                            effectsManager.onSmileDetected(mouthX, mouthY)
                        }
                        lastSmilingState = smiling
                    }

                } finally {
                    proxy.close()
                }
            }

            provider.unbindAll()
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                analysis
            )
        }, ContextCompat.getMainExecutor(this))
    }
}

// ===== helpery =====

private fun ImageProxy.toBitmap(conv: YuvToRgbConverter): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    conv.yuvToRgb(this, bmp)
    return bmp
}

private fun Bitmap.rotate(deg: Int): Bitmap {
    if (deg == 0) return this
    val m = Matrix().apply { postRotate(deg.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
}
