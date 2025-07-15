package com.Phisher98.settings

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.phisher98.R

class ExtractorAdapter(
    private val extractors: List<ExtractorToggleEntry>,
    private val onToggleChanged: (ExtractorToggleEntry) -> Unit
) : RecyclerView.Adapter<ExtractorAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.extractorName)
        @SuppressLint("UseSwitchCompatOrMaterialCode")
        val toggle: Switch = view.findViewById(R.id.extractorToggle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_extractor, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = extractors.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = extractors[position]
        holder.name.text = entry.name
        holder.toggle.isChecked = entry.enabled

        holder.toggle.setOnCheckedChangeListener { _, isChecked ->
            entry.enabled = isChecked
            onToggleChanged(entry)
        }
    }
}

data class ExtractorToggleEntry(
    val name: String,
    var enabled: Boolean
)
