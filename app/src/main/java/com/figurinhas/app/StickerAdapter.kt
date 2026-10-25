package com.figurinhas.app

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class StickerAdapter(
    private val store: StickerStore,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<StickerAdapter.Holder>() {

    private var files: List<String> = emptyList()

    class Holder(val image: ImageView) : RecyclerView.ViewHolder(image)

    fun submit(newFiles: List<String>) {
        files = newFiles
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sticker, parent, false) as ImageView
        return Holder(view)
    }

    override fun getItemCount(): Int = files.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val name = files[position]
        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
        val bitmap = store.fileFor(name)?.let { BitmapFactory.decodeFile(it.absolutePath, options) }
        holder.image.setImageBitmap(bitmap)
        holder.itemView.setOnClickListener { onClick(name) }
    }
}
