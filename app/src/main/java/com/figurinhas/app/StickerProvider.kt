package com.figurinhas.app

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * ContentProvider no formato que o WhatsApp exige para ler pacotes de figurinhas.
 * Rotas: metadata, metadata/{id}, stickers/{id} e stickers_asset/{id}/{arquivo}.
 */
class StickerProvider : ContentProvider() {

    private val matcher = UriMatcher(UriMatcher.NO_MATCH)

    override fun onCreate(): Boolean {
        val authority = StickerStore.authority(context!!)
        matcher.addURI(authority, "metadata", CODE_METADATA)
        matcher.addURI(authority, "metadata/*", CODE_METADATA_SINGLE)
        matcher.addURI(authority, "stickers/*", CODE_STICKERS)
        matcher.addURI(authority, "stickers_asset/*/*", CODE_ASSET)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        return when (matcher.match(uri)) {
            CODE_METADATA -> metadataCursor(uri, null)
            CODE_METADATA_SINGLE -> metadataCursor(uri, uri.lastPathSegment)
            CODE_STICKERS -> stickersCursor(uri)
            else -> throw IllegalArgumentException("URI desconhecida: $uri")
        }
    }

    private fun metadataCursor(uri: Uri, onlyId: String?): Cursor {
        val cursor = MatrixCursor(
            arrayOf(
                "sticker_pack_identifier",
                "sticker_pack_name",
                "sticker_pack_publisher",
                "sticker_pack_icon",
                "android_play_store_link",
                "ios_app_download_link",
                "sticker_pack_publisher_email",
                "sticker_pack_publisher_website",
                "sticker_pack_privacy_policy_website",
                "sticker_pack_license_agreement_website",
                "image_data_version",
                "whatsapp_will_not_cache_stickers",
                "animated_sticker_pack"
            )
        )
        if (onlyId == null || onlyId == StickerStore.PACK_ID) {
            val data = StickerStore(context!!).load()
            cursor.addRow(
                arrayOf<Any>(
                    StickerStore.PACK_ID,
                    data.shownName,
                    data.shownPublisher,
                    StickerStore.TRAY_FILE,
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    data.version.toString(),
                    0,
                    0
                )
            )
        }
        cursor.setNotificationUri(context!!.contentResolver, uri)
        return cursor
    }

    private fun stickersCursor(uri: Uri): Cursor {
        val cursor = MatrixCursor(
            arrayOf("sticker_file_name", "sticker_emoji", "sticker_accessibility_text")
        )
        if (uri.lastPathSegment == StickerStore.PACK_ID) {
            StickerStore(context!!).stickersForWhatsApp().forEach {
                cursor.addRow(arrayOf<Any>(it.file, it.emoji, "Figurinha"))
            }
        }
        cursor.setNotificationUri(context!!.contentResolver, uri)
        return cursor
    }

    private fun resolveAsset(uri: Uri): File? {
        if (matcher.match(uri) != CODE_ASSET) return null
        val segments = uri.pathSegments
        if (segments.size < 3 || segments[1] != StickerStore.PACK_ID) return null
        return StickerStore(context!!).fileFor(segments[2])
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val file = resolveAsset(uri) ?: return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val file = resolveAsset(uri) ?: return null
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(fd, 0, file.length())
    }

    override fun getType(uri: Uri): String? {
        val authority = StickerStore.authority(context!!)
        return when (matcher.match(uri)) {
            CODE_METADATA -> "vnd.android.cursor.dir/vnd.$authority.metadata"
            CODE_METADATA_SINGLE -> "vnd.android.cursor.item/vnd.$authority.metadata"
            CODE_STICKERS -> "vnd.android.cursor.dir/vnd.$authority.stickers"
            CODE_ASSET ->
                if (uri.lastPathSegment?.endsWith(".png") == true) "image/png" else "image/webp"
            else -> throw IllegalArgumentException("URI desconhecida: $uri")
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0

    private companion object {
        const val CODE_METADATA = 1
        const val CODE_METADATA_SINGLE = 2
        const val CODE_STICKERS = 3
        const val CODE_ASSET = 4
    }
}
