package com.example.licznikusmiechow

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Build
import android.os.Bundle
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

    private lateinit var previewView: PreviewView
    private lateinit var overlay: OverlayView
    private lateinit var smileText: TextView

    private lateinit var smile: SmileInterpreter
    private lateinit var yuv: YuvToRgbConverter
    private lateinit var faceCascade: CascadeClassifier

    private val executor = Executors.newSingleThreadExecutor()
    private var frameCount = 0



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

        smile = SmileInterpreter(this)
        yuv = YuvToRgbConverter(this)


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
                    // co 3 klatkę
                    frameCount = (frameCount + 1) % 5
                    if (frameCount != 0) { proxy.close(); return@setAnalyzer }

                    // konwersja do bitmapy
                    var bmp = proxy.toBitmap(yuv) ?: run { proxy.close(); return@setAnalyzer }

                    // obrót zgodnie z rotacją z sensora
                    val rot = proxy.imageInfo.rotationDegrees
                    bmp = bmp.rotate(rot)

                    // konwersja do Mat i gray
                    val mat = Mat().also { Utils.bitmapToMat(bmp, it) }
                    val gray = Mat().also {
                        Imgproc.cvtColor(mat, it, Imgproc.COLOR_RGBA2GRAY)
                        Imgproc.equalizeHist(it, it)
                    }

                    // detekcja twarzy
                    val faces = MatOfRect()
                    faceCascade.detectMultiScale(
                        gray, faces,
                        1.1, 5, 0,
                        org.opencv.core.Size(80.0, 80.0),
                        org.opencv.core.Size()
                    )

                    // skalowanie współrzędnych do rozmiaru PreviewView
                    val scaleX = previewView.width.toFloat() / gray.width()
                    val scaleY = previewView.height.toFloat() / gray.height()

                    val boxes = mutableListOf<OverlayView.Box>()
                    var status = "Brak twarzy"

                    for (r in faces.toArray()) {
                        val rectOnOverlay = RectF(
                            r.x * scaleX,
                            r.y * scaleY,
                            (r.x + r.width) * scaleX,
                            (r.y + r.height) * scaleY
                        )

                        // dla przedniej kamery – lustrzane odbicie
                        val mirroredLeft = previewView.width - rectOnOverlay.right
                        val mirroredRight = previewView.width - rectOnOverlay.left
                        rectOnOverlay.set(mirroredLeft, rectOnOverlay.top, mirroredRight, rectOnOverlay.bottom)

                        // wycięcie twarzy do predykcji
                        val faceBmp = Bitmap.createBitmap(
                            bmp,
                            r.x.coerceAtLeast(0),
                            r.y.coerceAtLeast(0),
                            r.width.coerceAtMost(bmp.width - r.x),
                            r.height.coerceAtMost(bmp.height - r.y)
                        )

                        val grayFace = faceBmp.toGrayscale()
                        val (pNot, pYes) = smile.predictSmile(grayFace)
                        val smiling = pYes > pNot
                        val label = if (smiling) "Smiling" else "Not Smiling"
                        status = "$label (pSmile=${String.format("%.2f", pYes)})"

                        boxes.add(OverlayView.Box(rectOnOverlay, label, smiling))
                    }

                    runOnUiThread {
                        overlay.setBoxes(boxes)
                        smileText.text = status
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
