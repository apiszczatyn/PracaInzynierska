package com.example.pozytywneemocje

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.random.Random
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.ImageView
import android.graphics.RectF



class MainMenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)


        val startButton: ImageView = findViewById(R.id.startButton)
        startButton.post {
            val startRect = IntArray(2)
            startButton.getLocationOnScreen(startRect)
            val startX = startRect[0].toFloat()
            val startY = startRect[1].toFloat()
            val startWidth = startButton.width.toFloat()
            val startHeight = startButton.height.toFloat()

            val startArea = RectF(
                startX,
                startY,
                startX + startWidth,
                startY + startHeight
            )
            val exitButton: ImageView = findViewById(R.id.exitButton)
            val settingsButton: ImageView = findViewById(R.id.settingsButton)
            val starsLayer: ViewGroup = findViewById(R.id.starsLayer)
            createFloatingStars(starsLayer)
            createPlanets(starsLayer, startArea)
            animateStartButton(startButton)

            startButton.setOnClickListener {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
            }

            settingsButton.setOnClickListener {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
            }

            exitButton.setOnClickListener {
                finishAffinity()
            }
        }
    }

    private fun createFloatingStars(layer: ViewGroup) {

        layer.post {
            val width = layer.width
            val height = layer.height

            val starCount = 30
            repeat(starCount) {
                val sizeDp = Random.nextInt(12, 16)
                val sizePx = (sizeDp * layer.resources.displayMetrics.density).toInt()

                val star = ImageView(layer.context).apply {
                    setImageResource(R.drawable.ic_star_space)
                    layoutParams = ViewGroup.LayoutParams(sizePx, sizePx)
                }


                val startX = Random.nextInt(0, width - sizePx).toFloat()
                val startY = Random.nextInt(0, height).toFloat()
                star.x = startX
                star.y = startY

                layer.addView(star)

                startStarDriftAnimation(star, width, height)
            }
        }
    }

    private fun startStarDriftAnimation(star: ImageView, width: Int, height: Int) {

        val direction = if (Random.nextBoolean()) 1 else -1

        val startY = star.y
        val endY = if (direction == 1) height + 100f else -100f

        val duration = Random.nextLong(12000L, 22000L)

        val animY = ObjectAnimator.ofFloat(star, "translationY", startY, endY).apply {
            this.duration = duration
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            startDelay = Random.nextLong(0L, 8000L)
        }


        val deltaX = Random.nextInt(-180, 180).toFloat()
        val animX = ObjectAnimator.ofFloat(star, "translationX", star.x, star.x + deltaX).apply {
            this.duration = Random.nextLong(2000L, 2500L)
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }


        animY.start()
        animX.start()
    }

    private fun createPlanets(layer: ViewGroup, startArea: RectF) {
        layer.post {
            val width = layer.width
            val height = layer.height

            val planetDrawables = listOf(
                R.drawable.ic_planet_space,
                R.drawable.ic_planet2_space,
                R.drawable.ic_planet3_space
            )

            val planetSizesDp = listOf(120, 140, 180)

            repeat(planetDrawables.size) { index ->
                val drawableRes = planetDrawables[index]
                val sizeDp = planetSizesDp[index]
                val sizePx = (sizeDp * layer.resources.displayMetrics.density).toInt()


                val (safeX, safeY) = generateNonOverlappingPosition(
                    width, height, sizePx, sizePx, startArea
                )

                val planet = ImageView(layer.context).apply {
                    setImageResource(drawableRes)
                    layoutParams = ViewGroup.LayoutParams(sizePx, sizePx)
                    x = safeX
                    y = safeY
                    alpha = 0.85f
                }

                layer.addView(planet)
                startPlanetDriftAnimation(planet, width, height, index)
            }
        }
    }

    private fun generateNonOverlappingPosition(
        layoutWidth: Int,
        layoutHeight: Int,
        objWidth: Int,
        objHeight: Int,
        forbidden: RectF
    ): Pair<Float, Float> {

        var x: Float
        var y: Float

        do {
            x = Random.nextInt(0, layoutWidth - objWidth).toFloat()
            y = Random.nextInt(0, layoutHeight - objHeight).toFloat()

            val objRect = RectF(
                x,
                y,
                x + objWidth,
                y + objHeight
            )


            if (!RectF.intersects(objRect, forbidden)) {
                break
            }

        } while (true)

        return x to y
    }
}

private fun startPlanetDriftAnimation(planet: ImageView, width: Int, height: Int, index: Int) {

    val direction = if (index % 2 == 0) 1 else -1
    val travelX = width * 0.25f * direction

    val animX = ObjectAnimator.ofFloat(
        planet,
        "translationX",
        planet.x,
        planet.x + travelX
    ).apply {
        duration = 25000L + index * 5000L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = LinearInterpolator()
    }


    val travelY = 40f
    val animY = ObjectAnimator.ofFloat(
        planet,
        "translationY",
        planet.y - travelY / 2,
        planet.y + travelY / 2
    ).apply {
        duration = 16000L + index * 3000L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = LinearInterpolator()
    }

    animX.start()
    animY.start()
}

private fun animateStartButton(button: ImageView) {


    val scaleUp = ObjectAnimator.ofFloat(button, "scaleX", 1f, 1.12f).apply {
        duration = 900L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
    }

    val scaleUpY = ObjectAnimator.ofFloat(button, "scaleY", 1f, 1.12f).apply {
        duration = 900L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
    }

    scaleUp.start()
    scaleUpY.start()
}
