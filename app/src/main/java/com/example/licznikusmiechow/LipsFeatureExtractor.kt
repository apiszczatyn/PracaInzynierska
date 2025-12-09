package com.example.licznikusmiechow

import android.graphics.PointF
import kotlin.math.hypot
import kotlin.math.sqrt

object LipsFeatureExtractor {

    // Ten sam zestaw indeksów ust co w Pythonie (FACEMESH_LIPS)
    val LIPS_INDICES = intArrayOf(
        61, 146, 91, 181, 84, 17, 314, 405, 321, 375,
        291, 308, 324, 318, 402, 317, 14, 87, 178, 88,
        95, 185, 40, 39, 37, 0, 267, 269, 270, 409,
        415, 310, 311, 312, 13, 82, 81, 42, 183, 78
    )

    private fun List<Float>.std(): Float {
        if (isEmpty()) return 0f
        val mean = this.sum() / size
        var acc = 0f
        for (v in this) {
            val d = v - mean
            acc += d * d
        }
        return sqrt(acc / size)
    }

    /**
     * lips: lista punktów (x,y) w układzie znormalizowanym 0..1, tak jak w MediaPipe
     *
     * Zwraca: [mar, smile_curve, asym, spread_x, spread_y]
     * dokładnie jak compute_features_from_lips w Pythonie.
     */
    fun computeFeaturesFromLips(lips: List<PointF>): FloatArray {
        require(lips.isNotEmpty()) { "Lips list is empty" }

        // szukamy punktów skrajnych w poziomie
        val left = lips.minByOrNull { it.x }!!
        val right = lips.maxByOrNull { it.x }!!

        val width = hypot(
            (right.x - left.x).toDouble(),
            (right.y - left.y).toDouble()
        ).toFloat() + 1e-8f

        // normalizacja względem szerokości ust
        val lipsN = lips.map { p ->
            PointF(
                (p.x - left.x) / width,
                (p.y - left.y) / width
            )
        }

        val leftN = PointF(0f, 0f)
        val rightN = PointF(
            (right.x - left.x) / width,
            (right.y - left.y) / width
        )

        val meanX = lipsN.map { it.x }.average().toFloat()
        val meanY = lipsN.map { it.y }.average().toFloat()

        val ys = lipsN.map { it.y }
        val mar = (ys.maxOrNull()!! - ys.minOrNull()!!)              // otwarcie ust
        val smileCurve = ((leftN.y + rightN.y) / 2f) - meanY         // kąciki vs środek
        val asym = leftN.y - rightN.y                                // asymetria pionowa
        val spreadX = lipsN.map { it.x }.std()                       // rozrzut poziomy
        val spreadY = lipsN.map { it.y }.std()                       // rozrzut pionowy

        return floatArrayOf(mar, smileCurve, asym, spreadX, spreadY)
    }
}
