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
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    private lateinit var faceLandmarker: FaceLandmarker
    private lateinit var svm: SmileSvmInterpreter
    private lateinit var yuv: YuvToRgbConverter

    private lateinit var previewView: PreviewView
    private lateinit var overlay: OverlayView
    private lateinit var smileText: TextView
    private lateinit var smileEmoji: TextView
    private lateinit var effectsLayer: FrameLayout

    private val executor = Executors.newSingleThreadExecutor()
    private var frameCount = 0
    private var lastSmilingState = false

    // cooldown animacji
    private var lastEffectTime = 0L
    private val EFFECT_COOLDOWN_MS = 1200L

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
        smileText = findViewById(R.id.smileText)
        smileEmoji = findViewById(R.id.smileEmoji)
        effectsLayer = findViewById(R.id.starsLayer)

        yuv = YuvToRgbConverter(this)
        svm = SmileSvmInterpreter(this)
        initFaceLandmarker()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) startCamera() else askCamera.launch(Manifest.permission.CAMERA)
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
                .build()

            analysis.setAnalyzer(executor) { proxy ->
                try {
                    frameCount = (frameCount + 1) % 3
                    if (frameCount != 0) return@setAnalyzer

                    var bmp = proxy.toBitmap(yuv) ?: return@setAnalyzer
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

                    val minX = lips.minOf { it.x }
                    val maxX = lips.maxOf { it.x }
                    val minY = lips.minOf { it.y }

                    val w = previewView.width.toFloat()
                    val h = previewView.height.toFloat()

                    val mouthCenterX = w * (1f - (minX + maxX) / 2f)
                    val mouthTopY = h * minY

                    runOnUiThread {
                        if (smiling && !lastSmilingState) {
                            triggerRandomEffect(mouthCenterX, mouthTopY)
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

    // ====================== EFEKTY ======================

    private fun triggerRandomEffect(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        if (now - lastEffectTime < EFFECT_COOLDOWN_MS) return
        lastEffectTime = now

        when (Random.nextInt(10)) {
            0 -> starBurstFromMouth(x, y)
            1 -> haloOverHead(x, y - 200)
            2 -> flyRocket()
            3 -> flyPlanet()
            4 -> starRain()
            5 -> orbitPlanet(x, y - 220)
            6 -> sideStarBurst(x, y)
            7 -> rocketFromMouth(x, y)
            8 -> planetPop()
            else -> faceStarPop(x, y)
        }
    }

    private fun starBurstFromMouth(x: Float, y: Float) =
        repeatStars(12, R.drawable.ic_star_space, x, y, -400..400, -600..-200)

    private fun haloOverHead(x: Float, y: Float) =
        repeatStars(8, R.drawable.ic_star_space, x, y, -120..120, -80..80, 1200)

    private fun flyRocket() = flyAcross(R.drawable.ic_rocket_space)

    private fun flyPlanet() =
        flyAcross(
            listOf(
                R.drawable.ic_planet_space,
                R.drawable.ic_planet2_space,
                R.drawable.ic_planet3_space
            ).random()
        )

    private fun starRain() =
        repeatStars(15, R.drawable.ic_star_space,
            Random.nextFloat() * effectsLayer.width,
            -50f, -50..50, 800..1200
        )

    private fun orbitPlanet(x: Float, y: Float) {
        val planet = spawnImage(R.drawable.ic_planet2_space, x - 80, y - 80, 160)
        planet.animate().rotationBy(360f).setDuration(1200)
            .withEndAction { effectsLayer.removeView(planet) }.start()
    }

    private fun sideStarBurst(x: Float, y: Float) =
        repeatStars(10, R.drawable.ic_star_space, x, y, -600..600, -50..50)

    private fun rocketFromMouth(x: Float, y: Float) {
        val rocket = spawnImage(R.drawable.ic_rocket_space, x - 70, y, 140)
        rocket.animate().translationYBy(-800f).alpha(0f).setDuration(1000)
            .withEndAction { effectsLayer.removeView(rocket) }.start()
    }

    private fun planetPop() {
        val p = spawnImage(
            R.drawable.ic_planet3_space,
            effectsLayer.width / 2f - 100,
            effectsLayer.height / 2f - 100,
            200
        )
        p.scaleX = 0f; p.scaleY = 0f
        p.animate().scaleX(1f).scaleY(1f).alpha(0f).setDuration(900)
            .withEndAction { effectsLayer.removeView(p) }.start()
    }

    private fun faceStarPop(x: Float, y: Float) {
        val s = spawnImage(R.drawable.ic_star_space, x - 20, y - 60, 40)
        s.animate().scaleX(2f).scaleY(2f).alpha(0f).setDuration(600)
            .withEndAction { effectsLayer.removeView(s) }.start()
    }

    // ====================== HELPERY ======================

    private fun spawnImage(res: Int, x: Float, y: Float, size: Int): ImageView =
        ImageView(this).apply {
            setImageResource(res)
            layoutParams = FrameLayout.LayoutParams(size, size)
            this.x = x; this.y = y
            effectsLayer.addView(this)
        }

    private fun repeatStars(
        count: Int,
        res: Int,
        x: Float,
        y: Float,
        dxRange: IntRange,
        dyRange: IntRange,
        duration: Long = 900
    ) {
        repeat(count) {
            val s = spawnImage(res, x, y, Random.nextInt(20, 40))
            s.animate()
                .translationXBy(dxRange.random().toFloat())
                .translationYBy(dyRange.random().toFloat())
                .alpha(0f)
                .setDuration(duration)
                .withEndAction { effectsLayer.removeView(s) }
                .start()
        }
    }

    private fun flyAcross(res: Int) {
        val size = 160
        val v = spawnImage(res, -size.toFloat(), effectsLayer.height * 0.5f, size)
        v.animate().translationX(effectsLayer.width + size.toFloat())
            .setDuration(1200)
            .withEndAction { effectsLayer.removeView(v) }
            .start()
    }
}

// ====================== EXTENSIONS ======================

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
