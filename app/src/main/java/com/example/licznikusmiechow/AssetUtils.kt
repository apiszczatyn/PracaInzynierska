package com.example.licznikusmiechow

import android.content.Context
import java.io.File

object AssetUtils {
    fun copyAssetToCache(context: Context, assetName: String): File {
        val out = File(context.cacheDir, assetName)
        if (out.exists()) return out
        context.assets.open(assetName).use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }
}