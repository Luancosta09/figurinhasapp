package com.figurinhas.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.ExifInterface
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * Converte imagens para o formato exigido pelo WhatsApp:
 * figurinha 512x512 em WebP com menos de 100 KB e ícone do pacote 96x96 em PNG.
 */
object ImageUtils {

    const val STICKER_SIZE = 512
    const val TRAY_SIZE = 96
    private const val MAX_STICKER_BYTES = 100 * 1024

    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }

    /** Lê a imagem inteira (sem cortar), estica para 512x512 e devolve os bytes de um WebP 512x512 com menos de 100 KB. */
    fun toStickerWebp(source: File): ByteArray {
        val src = loadOriented(source.absolutePath, 1536)

        val canvasBitmap = Bitmap.createBitmap(STICKER_SIZE, STICKER_SIZE, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(canvasBitmap)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            canvas.drawBitmap(src, null, RectF(0f, 0f, STICKER_SIZE.toFloat(), STICKER_SIZE.toFloat()), paint)
        } finally {
            src.recycle()
        }

        try {
            var quality = 95
            var bytes = ByteArray(0)
            while (quality >= 5) {
                val out = ByteArrayOutputStream()
                canvasBitmap.compress(webpFormat(), quality, out)
                bytes = out.toByteArray()
                if (bytes.size <= MAX_STICKER_BYTES) return bytes
                quality -= 10
            }
            throw IOException("Não foi possível deixar a figurinha abaixo de 100 KB")
        } finally {
            canvasBitmap.recycle()
        }
    }

    /** Abre a foto, reduzida (até maxSide) e já na orientação correta. */
    fun loadOriented(path: String, maxSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Imagem inválida")

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = BitmapFactory.decodeFile(path, options) ?: throw IOException("Imagem inválida")

        val orientation = try {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return raw
        }
        val fixed = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (fixed !== raw) raw.recycle()
        return fixed
    }

    /** Gera o ícone do pacote (96x96, PNG) a partir da primeira figurinha. */
    fun makeTray(sourceWebp: File, dest: File) {
        val src = BitmapFactory.decodeFile(sourceWebp.absolutePath) ?: return
        val tray = Bitmap.createScaledBitmap(src, TRAY_SIZE, TRAY_SIZE, true)
        dest.outputStream().use { tray.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (tray !== src) tray.recycle()
        src.recycle()
    }
}
