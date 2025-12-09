package com.example.licznikusmiechow

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.exp

data class SvmModel(
    val mean: DoubleArray,
    val scale: DoubleArray,
    val supportVectors: Array<DoubleArray>,
    val dualCoef: DoubleArray,
    val intercept: Double,
    val gamma: Double,
    val classes: Array<String>  // bo w .joblib masz etykiety stringowe
)

class SmileSvmInterpreter(context: Context) {

    private val model: SvmModel = loadFromAssets(context, "svm_smile_model.json")

    private fun loadFromAssets(context: Context, filename: String): SvmModel {
        val jsonStr = context.assets.open(filename)
            .bufferedReader().use { it.readText() }

        val obj = JSONObject(jsonStr)

        fun jsonArrayToDoubleArray(arr: JSONArray): DoubleArray =
            DoubleArray(arr.length()) { i -> arr.getDouble(i) }

        val mean = jsonArrayToDoubleArray(obj.getJSONArray("mean"))
        val scale = jsonArrayToDoubleArray(obj.getJSONArray("scale"))

        val svJson = obj.getJSONArray("support_vectors")
        val supportVectors = Array(svJson.length()) { i ->
            jsonArrayToDoubleArray(svJson.getJSONArray(i))
        }

        val dualCoef = jsonArrayToDoubleArray(obj.getJSONArray("dual_coef"))
        val intercept = obj.getDouble("intercept")
        val gamma = obj.getDouble("gamma")

        val classesJson = obj.getJSONArray("classes")
        val classes = Array(classesJson.length()) { i ->
            classesJson.get(i).toString()
        }

        return SvmModel(mean, scale, supportVectors, dualCoef, intercept, gamma, classes)
    }

    // features = [mar, smile_curve, asym, spread_x, spread_y]
    fun predictLabel(features: FloatArray): String {
        require(features.size == model.mean.size) {
            "Zły rozmiar wektora cech: ${features.size}, oczekiwano ${model.mean.size}"
        }

        // 1) StandardScaler: (x - mean) / scale
        val x = DoubleArray(features.size) { i ->
            (features[i].toDouble() - model.mean[i]) / model.scale[i]
        }

        // 2) SVM z jądrem RBF
        var sum = 0.0
        for (i in model.supportVectors.indices) {
            val sv = model.supportVectors[i]
            var dist2 = 0.0
            for (d in x.indices) {
                val diff = x[d] - sv[d]
                dist2 += diff * diff
            }
            val k = exp(-model.gamma * dist2)
            sum += model.dualCoef[i] * k
        }
        val f = sum + model.intercept

        // SVC binary: f>=0 -> classes[1], inaczej classes[0]
        return if (f >= 0.0) model.classes[1] else model.classes[0]
    }

    fun isSmiling(features: FloatArray): Boolean {
        val label = predictLabel(features)
        // dostosuj do swoich etykiet z treningu
        return label.contains("smil", ignoreCase = true) || label == "1"
    }
}
