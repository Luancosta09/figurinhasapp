package com.figurinhas.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Sticker(val file: String, val emoji: String)

/** Caractere "Braille em branco": ocupa espaço mas não aparece, e o WhatsApp não o considera vazio. */
const val INVISIBLE_TEXT = "\u2800"

data class PackData(
    val name: String,
    val publisher: String,
    val version: Int,
    val stickers: List<Sticker>,
    val hideName: Boolean = false,
    val hidePublisher: Boolean = false
) {
    /** Nome que vai para o WhatsApp. */
    val shownName: String get() = if (hideName) INVISIBLE_TEXT else name
    val shownPublisher: String get() = if (hidePublisher) INVISIBLE_TEXT else publisher
}

/**
 * Guarda o pacote (nome, criador e figurinhas) na pasta interna do app.
 * É usado tanto pela tela quanto pelo ContentProvider que o WhatsApp consulta.
 */
class StickerStore(context: Context) {

    private val dir: File = File(context.applicationContext.filesDir, "pack").apply { mkdirs() }
    private val metaFile = File(dir, "pack.json")

    fun load(): PackData = synchronized(LOCK) { read() }

    fun saveInfo(name: String, publisher: String, hideName: Boolean, hidePublisher: Boolean) {
        synchronized(LOCK) {
            write(
                read().copy(
                    name = name,
                    publisher = publisher,
                    hideName = hideName,
                    hidePublisher = hidePublisher
                )
            )
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

    /**
     * Lista enviada ao WhatsApp. Ele exige no mínimo 3 figurinhas por pacote; se houver menos,
     * as suas são repetidas (nomes "dupN_...") só nessa lista, sem aparecer na tela do app.
     */
    fun stickersForWhatsApp(): List<Sticker> {
        val list = load().stickers
        if (list.isEmpty() || list.size >= MIN_STICKERS) return list
        val out = list.toMutableList()
        var i = 0
        while (out.size < MIN_STICKERS) {
            val base = list[i % list.size]
            out.add(Sticker("dup${out.size}_${base.file}", base.emoji))
            i++
        }
        return out
    }

    /** Devolve o arquivo de uma figurinha (ou do ícone) se o nome for seguro e existir. */
    fun fileFor(rawName: String): File? {
        val name = rawName.replace(DUP_PREFIX, "")
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
                stickers = list,
                hideName = obj.optBoolean("hideName", false),
                hidePublisher = obj.optBoolean("hidePublisher", false)
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
            .put("hideName", data.hideName)
            .put("hidePublisher", data.hidePublisher)
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

        private val DUP_PREFIX = Regex("^dup\\d+_")
        private val LOCK = Any()
        private val EMPTY = PackData("", "", 1, emptyList())

        fun authority(context: Context): String = context.packageName + ".stickercontentprovider"
    }
}
