package com.figurinhas.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var store: StickerStore
    private lateinit var stickerAdapter: StickerAdapter
    private lateinit var packNameInput: TextInputEditText
    private lateinit var creatorInput: TextInputEditText
    private lateinit var countText: TextView
    private lateinit var emptyText: TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var addStickerButton: MaterialButton
    private lateinit var addToWhatsAppButton: MaterialButton

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) importImage(uri)
        }

    private val whatsappLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            onWhatsAppResult(result)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = StickerStore(this)

        packNameInput = findViewById(R.id.packNameInput)
        creatorInput = findViewById(R.id.creatorInput)
        countText = findViewById(R.id.countText)
        emptyText = findViewById(R.id.emptyText)
        progress = findViewById(R.id.progress)
        addStickerButton = findViewById(R.id.addStickerButton)
        addToWhatsAppButton = findViewById(R.id.addToWhatsAppButton)

        stickerAdapter = StickerAdapter(store) { fileName -> confirmRemove(fileName) }
        val list = findViewById<RecyclerView>(R.id.stickerList)
        list.layoutManager = GridLayoutManager(this, 3)
        list.adapter = stickerAdapter

        val data = store.load()
        packNameInput.setText(data.name)
        creatorInput.setText(data.publisher)

        addStickerButton.setOnClickListener { onAddStickerClicked() }
        addToWhatsAppButton.setOnClickListener { addToWhatsApp() }

        refresh()
        showPendingCrash()
    }

    override fun onPause() {
        super.onPause()
        saveInfo()
    }

    private fun saveInfo() {
        store.saveInfo(
            packNameInput.text?.toString().orEmpty().trim(),
            creatorInput.text?.toString().orEmpty().trim()
        )
    }

    private fun refresh() {
        val data = store.load()
        stickerAdapter.submit(data.stickers.map { it.file })
        countText.text = getString(
            R.string.count_format,
            data.stickers.size,
            StickerStore.MAX_STICKERS,
            StickerStore.MIN_STICKERS
        )
        emptyText.visibility = if (data.stickers.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        addStickerButton.isEnabled = !busy
        addToWhatsAppButton.isEnabled = !busy
    }

    private fun toast(text: String, length: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, text, length).show()
    }

    /** Se o app fechou por erro da última vez, mostra o erro para poder copiar e enviar. */
    private fun showPendingCrash() {
        val file = File(filesDir, App.CRASH_FILE)
        if (!file.exists()) return
        val text = try {
            file.readText().take(4000)
        } catch (e: Exception) {
            ""
        }
        file.delete()
        if (text.isBlank()) return

        val label = TextView(this).apply {
            this.text = text
            setTextIsSelectable(true)
            textSize = 11f
            setPadding(48, 24, 48, 24)
        }
        val scroll = ScrollView(this).apply { addView(label) }

        AlertDialog.Builder(this)
            .setTitle(R.string.crash_title)
            .setView(scroll)
            .setPositiveButton(R.string.crash_copy) { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("erro", text))
                toast(getString(R.string.crash_copied))
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    // ---- Adicionar figurinha: escolher foto -> converter -> adicionar ------------------------

    private fun onAddStickerClicked() {
        if (store.load().stickers.size >= StickerStore.MAX_STICKERS) {
            toast(getString(R.string.err_max, StickerStore.MAX_STICKERS))
            return
        }
        pickImage.launch("image/*")
    }

    /** Copia a foto escolhida, converte para figurinha 512x512 e adiciona ao pacote. */
    private fun importImage(source: Uri) {
        setBusy(true)
        Thread {
            val copy = File(cacheDir, "source_${System.currentTimeMillis()}")
            var message: Int? = null
            try {
                val input = contentResolver.openInputStream(source)
                if (input == null) {
                    message = R.string.err_load_image
                } else {
                    input.use { stream -> copy.outputStream().use { stream.copyTo(it) } }
                    store.addSticker(ImageUtils.toStickerWebp(copy))
                }
            } catch (e: Throwable) {
                message = R.string.err_process
            } finally {
                copy.delete()
            }
            runOnUiThread {
                setBusy(false)
                refresh()
                message?.let { toast(getString(it)) }
            }
        }.start()
    }

    private fun confirmRemove(fileName: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.remove_title)
            .setPositiveButton(R.string.remove_confirm) { _, _ ->
                store.removeSticker(fileName)
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---- Adicionar o pacote ao WhatsApp --------------------------------------------------------

    private fun addToWhatsApp() {
        saveInfo()
        val data = store.load()

        if (data.name.isBlank()) {
            toast(getString(R.string.err_name))
            packNameInput.requestFocus()
            return
        }
        if (data.publisher.isBlank()) {
            toast(getString(R.string.err_creator))
            creatorInput.requestFocus()
            return
        }
        if (data.stickers.size < StickerStore.MIN_STICKERS) {
            toast(getString(R.string.err_min, StickerStore.MIN_STICKERS))
            return
        }

        val intent = Intent(ACTION_ENABLE_STICKER_PACK).apply {
            putExtra("sticker_pack_id", StickerStore.PACK_ID)
            putExtra("sticker_pack_authority", StickerStore.authority(this@MainActivity))
            putExtra("sticker_pack_name", data.name)
        }
        try {
            whatsappLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.err_whatsapp_missing), Toast.LENGTH_LONG)
        }
    }

    private fun onWhatsAppResult(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            toast(getString(R.string.msg_added))
            return
        }
        val error = result.data?.getStringExtra("validation_error")
        if (!error.isNullOrBlank()) {
            toast(getString(R.string.err_rejected, error), Toast.LENGTH_LONG)
        }
    }

    private companion object {
        const val ACTION_ENABLE_STICKER_PACK = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
    }
}
