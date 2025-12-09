package com.example.licznikusmiechow

import android.content.Context
import android.util.Log
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
    val classes: Array<String>
)

class SmileSvmInterpreter(private val context: Context) {

    companion object {
        private const val TAG = "SmileSvmInterpreter"

        // nazwa pliku i klucza w SharedPreferences
        private const val PREFS_NAME = "smile_settings"
        private const val KEY_THRESHOLD = "smile_threshold"
        private const val DEFAULT_THRESHOLD = 0.0f  // to, co wcześniej (f >= 0)
    }

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

        Log.d(TAG, "Loaded SVM model: features=${mean.size}, SVs=${supportVectors.size}, classes=${classes.toList()}")

        return SvmModel(mean, scale, supportVectors, dualCoef, intercept, gamma, classes)
    }

    /** odczyt aktualnego progu z SharedPreferences */
    private fun getThreshold(): Double {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val t = prefs.getFloat(KEY_THRESHOLD, DEFAULT_THRESHOLD)
        return t.toDouble()
    }

    /** decyzja SVM – jak w sklearn decision_function */
    fun decisionScore(features: FloatArray): Double {
        require(features.size == model.mean.size) {
            "Zły rozmiar wektora cech: ${features.size}, oczekiwano ${model.mean.size}"
        }

        // 1) StandardScaler
        val x = DoubleArray(features.size) { i ->
            (features[i].toDouble() - model.mean[i]) / model.scale[i]
        }

        // 2) RBF SVM
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
        Log.d(TAG, "features=${features.joinToString()}, score=$f")
        return f
    }

    fun isSmiling(features: FloatArray): Boolean {
        val f = decisionScore(features)
        val threshold = getThreshold()
        val smiling = f >= threshold
        Log.d(TAG, "isSmiling=$smiling (f=$f, threshold=$threshold)")
        return smiling
    }
}
