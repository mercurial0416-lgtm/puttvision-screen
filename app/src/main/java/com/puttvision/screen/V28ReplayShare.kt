package com.puttvision.screen

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object V28ReplayShare {
    fun share(context: Context, view: View) {
        if (view.width <= 0 || view.height <= 0) return
        runCatching {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "PuttVision_Replay_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            bitmap.recycle()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "PuttVision HFR Replay")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "리플레이 이미지 공유"))
        }.onFailure {
            Toast.makeText(context, "리플레이 공유 실패 · ${it.message ?: "이미지 오류"}", Toast.LENGTH_LONG).show()
        }
    }
}
