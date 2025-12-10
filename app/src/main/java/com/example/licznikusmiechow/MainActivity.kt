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
import android.util.Log
import android.util.Size
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import org.opencv.core.Core
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfRect
import org.opencv.core.Size as CvSize
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.min
import android.widget.ImageView
import androidx.camera.core.ImageProxy
import androidx.camera.view.transform.CoordinateTransform
class MainActivity : ComponentActivity() {

    private lateinit var faceLandmarker: FaceLandmarker
    private lateinit var svm: SmileSvmInterpreter

    private lateinit var previewView: PreviewView
    private lateinit var overlay: OverlayView
    private lateinit var smileText: TextView

    private lateinit var smile: SmileInterpreter
    private lateinit var yuv: YuvToRgbConverter
    private lateinit var faceCascade: CascadeClassifier

    private val executor = Executors.newSingleThreadExecutor()
    private var frameCount = 0

    private lateinit var smileEmoji: TextView
    private var lastSmilingState: Boolean = false



    private fun initFaceLandmarker() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("face_landmarker.task") // plik w assets
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

        check(OpenCVLoader.initDebug()) { "OpenCV init failed" }

        previewView = findViewById(R.id.previewView)
        overlay = findViewById(R.id.overlay)
        smileText = findViewById(R.id.smileText)
        smileEmoji = findViewById(R.id.smileEmoji)

        //smile = SmileInterpreter(this)
        yuv = YuvToRgbConverter(this)

        svm = SmileSvmInterpreter(this)
        initFaceLandmarker()


        val cascadeFile: File = AssetUtils.copyAssetToCache(this, "haarcascade_frontalface_default.xml")
        faceCascade = CascadeClassifier(cascadeFile.absolutePath)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) startCamera()
        else askCamera.launch(Manifest.permission.CAMERA)



    }

    override fun onResume() {
        super.onResume()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        super.onPause()
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
                    // ewentualne rzadsze klatki (chcesz -> zostaw)
                    frameCount = (frameCount + 1) % 3
                    if (frameCount != 0) { proxy.close(); return@setAnalyzer }

                    var bmp = proxy.toBitmap(yuv) ?: run { proxy.close(); return@setAnalyzer }
                    val rot = proxy.imageInfo.rotationDegrees
                    bmp = bmp.rotate(rot)

                    // Z BItmapy robimy MPImage
                    val mpImage = BitmapImageBuilder(bmp).build()
                    val timestampMs = SystemClock.uptimeMillis()

                    // FaceLandmarker w trybie VIDEO
                    val result: FaceLandmarkerResult =
                        faceLandmarker.detectForVideo(mpImage, timestampMs)

                    val faceLandmarksList = result.faceLandmarks()
                    if (faceLandmarksList.isEmpty()) {
                        runOnUiThread {
                            overlay.setBoxes(emptyList())
                            smileText.text = "Brak twarzy"
                        }
                        return@setAnalyzer
                    }

                    // Bierzemy pierwszą twarz
                    val landmarks = faceLandmarksList[0]  // lista 468 (lub 478) punktów

                    // wyciągamy tylko usta, tak jak w Pythonie
                    val lipsPoints = LipsFeatureExtractor.LIPS_INDICES.map { idx ->
                        val lm = landmarks[idx]
                        PointF(lm.x(), lm.y())
                    }

                    // liczymy cechy
                    val features = LipsFeatureExtractor.computeFeaturesFromLips(lipsPoints)

                    // predykcja SVM
                    val smiling = svm.isSmiling(features)
                    val label = if (smiling) "Smiling" else "Not Smiling"
                    val status = label

                    // prostokąt na overlayu: bierzemy min/max ust (normalizowane) i skaluje do PreviewView
                    val minX = lipsPoints.minOf { it.x }
                    val maxX = lipsPoints.maxOf { it.x }
                    val minY = lipsPoints.minOf { it.y }
                    val maxY = lipsPoints.maxOf { it.y }

                    val w = previewView.width.toFloat()
                    val h = previewView.height.toFloat()

                    // jeśli preview jest lustrzane dla front kamerki, możemy odbić X:
                    val leftPx = w * (1f - maxX)  // lustrzane odbicie
                    val rightPx = w * (1f - minX)
                    val topPx = h * minY
                    val bottomPx = h * maxY
                    Log.d("LipsFeatures", "features=${features.joinToString()}")
                    val rectOnOverlay = android.graphics.RectF(
                        leftPx,
                        topPx,
                        rightPx,
                        bottomPx
                    )

                    val boxes = listOf(
                        OverlayView.Box(rectOnOverlay, label, smiling)
                    )

                    runOnUiThread {
                        overlay.setBoxes(boxes)
                        smileText.text = status

                        if (smiling && !lastSmilingState) {
                            // przejście z brak uśmiechu -> uśmiech : zrób "pop" animację
                            smileEmoji.animate()
                                .alpha(1f)
                                .scaleX(1.2f)
                                .scaleY(1.2f)
                                .setDuration(200)
                                .withEndAction {
                                    // delikatne cofnięcie do 1.0, żeby nie była za duża
                                    smileEmoji.animate()
                                        .scaleX(1f)
                                        .scaleY(1f)
                                        .setDuration(150)
                                        .start()
                                }
                                .start()
                        } else if (!smiling && lastSmilingState) {
                            // przejście z uśmiechu -> brak uśmiechu: wygaszamy emotkę
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
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

}

// ---------- helpery ----------

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

private fun Bitmap.mirrorHorizontally(): Bitmap {
    val m = Matrix().apply { preScale(-1f, 1f) }
    return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
}

private fun Bitmap.toGrayscale(): Bitmap {
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val cm = ColorMatrix().apply { setSaturation(0f) }
    val p = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
    c.drawBitmap(this, 0f, 0f, p)
    return out
}