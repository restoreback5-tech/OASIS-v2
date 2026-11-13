package com.oasis.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.Executors

/**
 * "Leer en voz alta": toma una foto con la cámara del teléfono, reconoce el texto en el propio teléfono
 * (ML Kit, sin internet) y lo entrega para que el orbe lo lea. Sirve para carteles, recetas, medicinas, cartas.
 * Debe crearse en onCreate() de la actividad.
 */
class ReadAloud(
    private val act: AppCompatActivity,
    private val onStatus: (String) -> Unit,
    private val onText: (String) -> Unit
) {
    private val io = Executors.newSingleThreadExecutor()
    private val file = File(File(act.cacheDir, "images").also { it.mkdirs() }, "leer.jpg")

    private val launcher = act.registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) recognize() else onStatus("Listo, cancelé la foto.")
    }

    fun start() {
        try {
            if (file.exists()) file.delete()
            val uri: Uri = FileProvider.getUriForFile(act, "${act.packageName}.files", file)
            onStatus("Apunta al texto y toma la foto.")
            launcher.launch(uri)
        } catch (e: Exception) {
            onStatus("No pude abrir la cámara.")
        }
    }

    private fun recognize() {
        onStatus("Leyendo…")
        io.execute {
            val bmp = decode()
            if (bmp == null) { act.runOnUiThread { onStatus("No pude ver la foto. Intenta de nuevo.") }; return@execute }
            val rotation = try {
                when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } catch (e: Exception) { 0 }
            try {
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(InputImage.fromBitmap(bmp, rotation))
                    .addOnSuccessListener { result ->
                        val text = result.text.replace(Regex("\\s+"), " ").trim()
                        if (text.isEmpty()) onStatus("No encontré texto. Acerca más la cámara y con buena luz.")
                        else onText(text.take(1500))
                    }
                    .addOnFailureListener { onStatus("No pude leer el texto de la foto.") }
            } catch (e: Exception) {
                act.runOnUiThread { onStatus("No pude leer el texto de la foto.") }
            }
        }
    }

    private fun decode(): Bitmap? = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        var sample = 1
        while (opts.outWidth / sample > 2200 || opts.outHeight / sample > 2200) sample *= 2
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Exception) { null }

    fun destroy() { io.shutdownNow() }
}
