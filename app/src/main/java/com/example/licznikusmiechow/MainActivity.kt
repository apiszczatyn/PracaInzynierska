package com.example.licznikusmiechow

import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.core.BaseOptions

import android.graphics.PointF
import android.os.SystemClock

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.widget.TextView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import kotlin.math.min
import android.view.animation.DecelerateInterpolator
import androidx.camera.core.ImageProxy

class MainActivity : ComponentActivity() {

    private lateinit var faceLandmarker: FaceLandmarker
    private lateinit var svm: SmileSvmInterpreter

    private lateinit var previewView: PreviewView
    private lateinit var overlay: OverlayView
    private lateinit var smileText: TextView
    private lateinit var smileEmoji: TextView
    private lateinit var rocketContainer: FrameLayout

    private lateinit var yuv: YuvToRgbConverter

    private val executor = Executors.newSingleThreadExecutor()
    private var frameCount = 0
    private var lastSmilingState = false
    private var rocketAnimated = false   // zabezpieczenie: animacja tylko raz

    private fun initFaceLandmarker() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("face_landmarker.task")
            .build()

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setMinFaceDetectionConfidence(0.3f)
            .setMinFacePresenceConfidence(0.3f)
            .setMinTrackingConfidence(0.3f)
            .setRunningMode(RunningMode.VIDEO)
            .setNumFaces(1)
            .build()

        faceLandmarker = FaceLandmarker.createFromOptions(this, options)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private val askCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { if (it) startCamera() else finish() }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        overlay = findViewById(R.id.overlay)
/*        smileText = findViewById(R.id.smileText)
        smileEmoji = findViewById(R.id.smileEmoji)*/
        rocketContainer = findViewById(R.id.rocketContainer)

        yuv = YuvToRgbConverter(this)
        svm = SmileSvmInterpreter(this)
        initFaceLandmarker()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) startCamera()
        else askCamera.launch(Manifest.permission.CAMERA)
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (!rocketAnimated) {
            rocketAnimated = true
            startRocketZoom()
        }
    }

    override fun onPause() {
        super.onPause()
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun startRocketZoom() {
        rocketContainer.post {
            rocketContainer.animate()
                .setStartDelay(1000)
                .scaleX(1.8f)
                .scaleY(1.8f)
                .translationY(300f) // dodatnie = w dół
                .setDuration(700)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
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
                .setOutputImageRotationEnabled(true)
                .build()

            analysis.setAnalyzer(executor) { proxy ->
                try {
                    frameCount = (frameCount + 1) % 3
                    if (frameCount != 0) { proxy.close(); return@setAnalyzer }

                    var bmp = proxy.toBitmap(yuv) ?: run { proxy.close(); return@setAnalyzer }
                    val rot = proxy.imageInfo.rotationDegrees
                    bmp = bmp.rotate(rot)

                    val mpImage = BitmapImageBuilder(bmp).build()
                    val timestampMs = SystemClock.uptimeMillis()
                    val result = faceLandmarker.detectForVideo(mpImage, timestampMs)

                    val faces = result.faceLandmarks()
                    if (faces.isEmpty()) {
                        runOnUiThread {
                            overlay.setBoxes(emptyList())
                            smileText.text = "Brak twarzy"
                        }
                        return@setAnalyzer
                    }

                    val landmarks = faces[0]
                    val lipsPoints = LipsFeatureExtractor.LIPS_INDICES.map {
                        val lm = landmarks[it]
                        PointF(lm.x(), lm.y())
                    }

                    val features = LipsFeatureExtractor.computeFeaturesFromLips(lipsPoints)
                    val smiling = svm.isSmiling(features)
                    val score = svm.decisionScore(features)
                    val label = if (smiling) "Smiling" else "Not Smiling"

                    runOnUiThread {
                        smileText.text = "$label (${String.format("%.2f", score)})"

                        if (smiling && !lastSmilingState) {
                            smileEmoji.animate()
                                .alpha(1f)
                                .scaleX(1.2f)
                                .scaleY(1.2f)
                                .setDuration(200)
                                .withEndAction {
                                    smileEmoji.animate()
                                        .scaleX(1f)
                                        .scaleY(1f)
                                        .setDuration(150)
                                        .start()
                                }
                                .start()
                        } else if (!smiling && lastSmilingState) {
                            smileEmoji.animate()
                                .alpha(0f)
                                .scaleX(0.8f)
                                .scaleY(0.8f)
                                .setDuration(200)
                                .start()
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

/* ===== helpery ===== */

private fun ImageProxy.toBitmap(conv: YuvToRgbConverter): Bitmap? {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    conv.yuvToRgb(this, bmp)
    return bmp
}

private fun Bitmap.rotate(deg: Int): Bitmap {
    if (deg == 0) return this
    val m = Matrix().apply { postRotate(deg.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
}
