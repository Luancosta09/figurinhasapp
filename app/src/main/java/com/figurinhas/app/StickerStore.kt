package com.figurinhas.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Sticker(val file: String, val emoji: String)

data class PackData(
    val name: String,
    val publisher: String,
    val version: Int,
    val stickers: List<Sticker>
)

/**
 * Guarda o pacote (nome, criador e figurinhas) na pasta interna do app.
 * É usado tanto pela tela quanto pelo ContentProvider que o WhatsApp consulta.
 */
class StickerStore(context: Context) {

    private val dir: File = File(context.applicationContext.filesDir, "pack").apply { mkdirs() }
    private val metaFile = File(dir, "pack.json")

    fun load(): PackData = synchronized(LOCK) { read() }

    fun saveInfo(name: String, publisher: String) {
        synchronized(LOCK) {
            write(read().copy(name = name, publisher = publisher))
        }
    }

    fun addSticker(webp: ByteArray) {
        synchronized(LOCK) {
            val data = read()
            val fileName = "s_${System.currentTimeMillis()}.webp"
            File(dir, fileName).writeBytes(webp)
            commit(
                data.copy(
                    version = data.version + 1,
                    stickers = data.stickers + Sticker(fileName, DEFAULT_EMOJI)
                )
            )
        }
    }

    fun removeSticker(fileName: String) {
        synchronized(LOCK) {
            val data = read()
            val remaining = data.stickers.filterNot { it.file == fileName }
            if (remaining.size == data.stickers.size) return@synchronized
            File(dir, fileName).delete()
            commit(data.copy(version = data.version + 1, stickers = remaining))
        }
    }

    /** Devolve o arquivo de uma figurinha (ou do ícone) se o nome for seguro e existir. */
    fun fileFor(name: String): File? {
        if (name.isEmpty() || name.contains('/') || name.contains('\\') || name.contains("..")) {
            return null
        }
        val file = File(dir, name)
        return if (file.isFile) file else null
    }

    private fun commit(data: PackData) {
        val tray = File(dir, TRAY_FILE)
        val first = data.stickers.firstOrNull()
        if (first != null) {
            ImageUtils.makeTray(File(dir, first.file), tray)
        } else {
            tray.delete()
        }
        write(data)
    }

    private fun read(): PackData {
        if (!metaFile.exists()) return EMPTY
        return try {
            val obj = JSONObject(metaFile.readText())
            val arr = obj.optJSONArray("stickers") ?: JSONArray()
            val list = (0 until arr.length()).map { i ->
                val s = arr.getJSONObject(i)
                Sticker(s.getString("file"), s.optString("emoji", DEFAULT_EMOJI))
            }
            PackData(
                name = obj.optString("name"),
                publisher = obj.optString("publisher"),
                version = obj.optInt("version", 1),
                stickers = list
            )
        } catch (e: Exception) {
            EMPTY
        }
    }

    private fun write(data: PackData) {
        val arr = JSONArray()
        data.stickers.forEach {
            arr.put(JSONObject().put("file", it.file).put("emoji", it.emoji))
        }
        val text = JSONObject()
            .put("name", data.name)
            .put("publisher", data.publisher)
            .put("version", data.version)
            .put("stickers", arr)
            .toString()
        val tmp = File(dir, "pack.json.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(metaFile)) {
            metaFile.writeText(text)
            tmp.delete()
        }
    }

    companion object {
        const val PACK_ID = "figurinhas_pacote_1"
        const val TRAY_FILE = "tray.png"
        const val MIN_STICKERS = 3
        const val MAX_STICKERS = 30
        const val DEFAULT_EMOJI = "😀"

        private val LOCK = Any()
        private val EMPTY = PackData("", "", 1, emptyList())

        fun authority(context: Context): String = context.packageName + ".stickercontentprovider"
    }
}
