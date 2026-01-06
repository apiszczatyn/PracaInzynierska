package com.example.licznikusmiechow

import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.view.Gravity
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.camera.view.PreviewView
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class SmileEffectsManager(
    private val context: Context,
    private val previewView: PreviewView,
    private val effectsLayer: FrameLayout
) {

    // ===== KONTROLA =====
    private var lastEffectTime = 0L
    private var effectRunning = false

    private val COOLDOWN_MS = 2800L
    private val BASE_DURATION = 3000L

    private val texts = listOf(
        "BRAWO!",
        "SUPER!",
        "WOW!",
        "ŚWIETNIE!",
        "GOOD JOB!"
    )

    fun onSmileDetected(faceX: Float, faceY: Float) {
        if (effectRunning) return
        if (effectsLayer.width == 0 || effectsLayer.height == 0) return

        val now = SystemClock.uptimeMillis()
        if (now - lastEffectTime < COOLDOWN_MS) return
        lastEffectTime = now
        effectRunning = true

        // 👉 NAPIS ZAWSZE
        textPopAtEdge()

        // 👉 JEDNA LOSOWA ANIMACJA GŁÓWNA
        when (Random.nextInt(8)) {
            0 -> starBurstAboveFace(faceX, faceY)
            1 -> starRainCrazy()
            2 -> starSpiral(faceX, faceY)
            3 -> bigPlanetPop()
            4 -> flyingPlanet(false)
            5 -> flyingPlanet(true)
            6 -> rocketRandom()
            else -> orbitingPlanets(faceX, faceY)
        }
    }

    // =====================================================
    // =================== GWIAZDKI ========================
    // =====================================================

    private fun starBurstAboveFace(x: Float, y: Float) {
        val startY = y - 120f
        repeat(20) { i ->
            val angle = Math.PI + i * (Math.PI / 20)
            val star = spawnImage(
                R.drawable.ic_star_space,
                x,
                startY,
                Random.nextInt(56, 90)
            )
            star.animate()
                .translationXBy((cos(angle) * 360).toFloat())
                .translationYBy((sin(angle) * 360).toFloat())
                .rotationBy(Random.nextInt(-360, 360).toFloat())
                .alpha(0f)
                .setDuration(BASE_DURATION + 600)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { remove(star) }
                .start()
        }
        finishLater(BASE_DURATION + 700)
    }

    private fun starRainCrazy() {
        repeat(28) {
            val x = Random.nextInt(0, effectsLayer.width).toFloat()
            val star = spawnImage(
                R.drawable.ic_star_space,
                x,
                -120f,
                Random.nextInt(48, 80)
            )
            star.animate()
                .translationY(effectsLayer.height + 300f)
                .translationXBy(Random.nextInt(-120, 120).toFloat())
                .rotationBy(Random.nextInt(-360, 360).toFloat())
                .alpha(0f)
                .setDuration(BASE_DURATION + Random.nextLong(0, 1200))
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { remove(star) }
                .start()
        }
        finishLater(BASE_DURATION + 1300)
    }

    private fun starSpiral(x: Float, y: Float) {
        repeat(18) { i ->
            val star = spawnImage(
                R.drawable.ic_star_space,
                x,
                y - 80f,
                Random.nextInt(48, 72)
            )
            val angle = i * 0.6
            star.animate()
                .translationXBy((cos(angle) * 320).toFloat())
                .translationYBy((sin(angle) * 320).toFloat())
                .rotationBy(720f)
                .alpha(0f)
                .setDuration(BASE_DURATION + 400)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { remove(star) }
                .start()
        }
        finishLater(BASE_DURATION + 600)
    }

    // =====================================================
    // =================== PLANETY =========================
    // =====================================================

    private fun bigPlanetPop() {
        val size = Random.nextInt(360, 520)
        val x = Random.nextInt(0, effectsLayer.width - size).toFloat()
        val y = Random.nextInt(0, effectsLayer.height - size).toFloat()

        val planet = spawnImage(randomPlanet(), x, y, size)
        planet.alpha = 0f
        planet.scaleX = 0.6f
        planet.scaleY = 0.6f

        planet.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .rotationBy(360f)
            .setDuration(BASE_DURATION + 500)
            .withEndAction {
                planet.animate()
                    .alpha(0f)
                    .setDuration(700)
                    .withEndAction { remove(planet) }
                    .start()
            }
            .start()

        finishLater(BASE_DURATION + 1400)
    }

    private fun flyingPlanet(diagonal: Boolean) {
        val size = Random.nextInt(300, 460)
        val startX = if (Random.nextBoolean()) -size.toFloat() else effectsLayer.width.toFloat()
        val startY = Random.nextInt(100, effectsLayer.height - size - 100).toFloat()

        val endX = if (startX < 0) effectsLayer.width + size.toFloat() else -size.toFloat()
        val endY = if (diagonal)
            Random.nextInt(0, effectsLayer.height - size).toFloat()
        else startY

        val planet = spawnImage(randomPlanet(), startX, startY, size)
        planet.alpha = 1f

        planet.animate()
            .translationX(endX)
            .translationY(endY)
            .rotationBy(Random.nextInt(180, 360).toFloat())
            .setDuration(BASE_DURATION + 1600)
            .withEndAction {
                planet.animate()
                    .alpha(0f)
                    .setDuration(600)
                    .withEndAction { remove(planet) }
                    .start()
            }
            .start()

        finishLater(BASE_DURATION + 2200)
    }

    private fun orbitingPlanets(x: Float, y: Float) {
        repeat(2) { idx ->
            val planet = spawnImage(
                randomPlanet(),
                x - 120f,
                y - 120f,
                240
            )
            val dir = if (idx == 0) 1 else -1
            planet.animate()
                .rotationBy((360 * dir).toFloat())
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(BASE_DURATION + 800)
                .withEndAction { remove(planet) }
                .start()
        }
        finishLater(BASE_DURATION + 1000)
    }

    // =====================================================
    // =================== RAKIETA =========================
    // =====================================================

    private fun rocketRandom() {
        val size = 180
        val mode = Random.nextInt(3)

        val (sx, sy, ex, ey) = when (mode) {
            0 -> listOf(
                effectsLayer.width / 2f - size / 2,
                effectsLayer.height.toFloat(),
                effectsLayer.width / 2f,
                -300f
            )
            1 -> listOf(
                -size.toFloat(),
                effectsLayer.height * 0.6f,
                effectsLayer.width + size.toFloat(),
                effectsLayer.height * 0.3f
            )
            else -> listOf(
                -size.toFloat(),
                effectsLayer.height.toFloat(),
                effectsLayer.width + size.toFloat(),
                -300f
            )
        }

        val rocket = spawnImage(R.drawable.ic_rocket_space, sx, sy, size)

        rocket.animate()
            .translationX(ex)
            .translationY(ey)
            .rotationBy(-25f)
            .setDuration(BASE_DURATION + 1200)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { remove(rocket) }
            .start()

        repeat(12) {
            val star = spawnImage(
                R.drawable.ic_star_space,
                rocket.x + size / 2,
                rocket.y,
                Random.nextInt(48, 64)
            )
            star.animate()
                .translationXBy(Random.nextInt(-220, 220).toFloat())
                .translationYBy(Random.nextInt(200, 420).toFloat())
                .alpha(0f)
                .setDuration(BASE_DURATION)
                .withEndAction { remove(star) }
                .start()
        }

        finishLater(BASE_DURATION + 1600)
    }

    // =====================================================
    // =================== NAPISY ==========================
    // =====================================================

    private fun textPopAtEdge() {
        val top = Random.nextBoolean()
        val tv = TextView(context).apply {
            text = texts.random()
            textSize = 40f
            setTextColor(Color.WHITE)
            setShadowLayer(10f, 0f, 0f, Color.BLACK)
            gravity = Gravity.CENTER
            alpha = 0f
            scaleX = 0.6f
            scaleY = 0.6f
        }

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        lp.gravity = if (top) Gravity.TOP or Gravity.CENTER_HORIZONTAL
        else Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        lp.topMargin = if (top) 48 else 0
        lp.bottomMargin = if (!top) 48 else 0

        effectsLayer.addView(tv, lp)

        tv.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(700)
            .withEndAction {
                tv.animate()
                    .alpha(0f)
                    .setDuration(1400)
                    .withEndAction { effectsLayer.removeView(tv) }
                    .start()
            }
            .start()
    }

    // =====================================================
    // =================== HELPERY =========================
    // =====================================================

    private fun spawnImage(res: Int, x: Float, y: Float, size: Int): ImageView =
        ImageView(context).apply {
            setImageResource(res)
            layoutParams = FrameLayout.LayoutParams(size, size)
            this.x = x
            this.y = y
            effectsLayer.addView(this)
        }

    private fun remove(view: ImageView) {
        effectsLayer.removeView(view)
    }

    private fun finishLater(delay: Long) {
        effectsLayer.postDelayed({
            effectRunning = false
        }, delay)
    }

    private fun randomPlanet(): Int =
        listOf(
            R.drawable.ic_planet_space,
            R.drawable.ic_planet2_space,
            R.drawable.ic_planet3_space
        ).random()
}
