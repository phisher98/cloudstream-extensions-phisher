package com.phisher98.settings

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Switch
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.phisher98.BuildConfig
import com.phisher98.StreamPlayPlugin


class ToggleFragment(
    private val plugin: StreamPlayPlugin,
    private val sharedPref: SharedPreferences,
    private val reloadPlugin: () -> Unit
) : BottomSheetDialogFragment() {


    private val res: Resources = plugin.resources ?: throw Exception("Unable to access plugin resources")

    @SuppressLint("DiscouragedApi")
    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = res.getIdentifier(name, "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        if (id == 0) throw Resources.NotFoundException("Layout $name not found.")
        return inflater.inflate(id, container, false)
    }

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        if (id == 0) throw Resources.NotFoundException("ID $name not found.")
        return this.findViewById(id)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        println("DEBUG: ToggleFragment onCreateView started")

        try {
            val layoutName = "fragment_toggle_extensions" // or your actual layout file name (no .xml)
            val id = res.getIdentifier(layoutName, "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
            println("DEBUG: Layout ID resolved to: $id")

            if (id == 0) {
                throw Resources.NotFoundException("Layout $layoutName not found in package ${BuildConfig.LIBRARY_PACKAGE_NAME}")
            }

            val layout = res.getLayout(id) // This line will crash if layout is missing
            println("DEBUG: Layout loaded from resources")

            val view = inflater.inflate(layout, container, false)
            println("DEBUG: Layout inflated successfully")
            return view

        } catch (e: Exception) {
            println("ERROR: ToggleFragment crashed in onCreateView: ${e.message}")
            e.printStackTrace()
            return null
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val toggleList: RecyclerView = view.findView("extensionsRecyclerView")
        toggleList.layoutManager = LinearLayoutManager(context)
        toggleList.adapter = ToggleAdapter()
    }

    inner class ToggleAdapter : RecyclerView.Adapter<ToggleAdapter.ToggleViewHolder>() {

        private val toggles = listOf(
            "enabled_StreamPlay",
            "enabled_StreamPlayLite",
            "enabled_StreamPlayTorrent",
            "enabled_StreamPlayAnime",
            "enabled_StreamplayTorrentAnime"
        )

        inner class ToggleViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val label: TextView = view.findViewById(
                res.getIdentifier("toggle_title", "id", BuildConfig.LIBRARY_PACKAGE_NAME)
            )
            val toggleSwitch: Switch = view.findViewById(
                res.getIdentifier("toggle_switch", "id", BuildConfig.LIBRARY_PACKAGE_NAME)
            )
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToggleViewHolder {
            val itemView = getLayout("list_toggle_item", LayoutInflater.from(parent.context), parent)
            return ToggleViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: ToggleViewHolder, position: Int) {
            val key = toggles[position]
            holder.label.text = key.removePrefix("enabled_")
            holder.toggleSwitch.isChecked = sharedPref.getBoolean(key, true)
            holder.toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
                sharedPref.edit().putBoolean(key, isChecked).apply()
                reloadPlugin()
            }
        }

        override fun getItemCount(): Int = toggles.size
    }
}
