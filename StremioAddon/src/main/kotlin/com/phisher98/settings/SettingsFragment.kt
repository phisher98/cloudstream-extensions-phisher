package com.phisher98.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.phisher98.BuildConfig
import com.phisher98.StremioAddonProvider

class SettingsFragment(
    plugin: StremioAddonProvider,
    private val sharedPref: SharedPreferences
) : BottomSheetDialogFragment() {

    private val res: Resources = plugin.resources ?: throw Exception("Unable to read resources")

    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = res.getIdentifier(name, "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        return inflater.inflate(res.getLayout(id), container, false)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun getDrawable(name: String): Drawable {
        val id = res.getIdentifier(name, "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        return res.getDrawable(id, null)
    }

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        return findViewById(id)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun View?.makeTvCompatible() {
        if (this == null) return
        val outlineId = res.getIdentifier("outline", "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        if (outlineId != 0) {
            val drawable = res.getDrawable(outlineId, null)
            if (drawable != null) background = drawable
        }
    }

    /** Load all stremio_addon* keys as a list */
    private fun loadAddonsFromPrefs(): MutableList<String> {
        val addons = mutableListOf<String>()
        var index = 0

        while (true) {
            val key = if (index == 0) "stremio_addon" else "stremio_addon${index + 1}"
            if (!sharedPref.contains(key)) break

            val value = sharedPref.getString(key, "")?.trim().orEmpty()
            if (value.isNotEmpty()) {
                addons.add(value)
            }
            index++
        }

        return addons
    }

    /** Save list back to stremio_addon, stremio_addon2, stremio_addon3, ... */
    private fun saveAddonsToPrefs(addons: List<String>) {
        sharedPref.edit {
            // remove existing stremio_addon* keys
            sharedPref.all.keys
                .filter { it.startsWith("stremio_addon") }
                .forEach { remove(it) }

            // write current list sequentially
            addons.forEachIndexed { index, url ->
                val key = if (index == 0) "stremio_addon" else "stremio_addon${index + 1}"
                putString(key, url)
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = getLayout("settings", inflater, container)

        val stremioAddonInput = root.findView<EditText>("stremio_addon_input")
        val addAddonButton = root.findView<Button>("add_addon_button")
        stremioAddonInput.makeTvCompatible()
        addAddonButton.makeTvCompatible()

        val addonList = loadAddonsFromPrefs()
        val addonRecyclerView = root.findView<RecyclerView>("stremio_addon_list")
        addonRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        val addonAdapter = AddonAdapter(addonList)
        addonRecyclerView.adapter = addonAdapter

        addAddonButton.setOnClickListener {
            val text = stremioAddonInput.text.toString().trim()
            if (text.isNotEmpty()) {
                addonList.add(text)
                addonAdapter.notifyItemInserted(addonList.size - 1)
                stremioAddonInput.text.clear()
            }
        }

        val saveBtn = root.findView<ImageView>("save")
        saveBtn.setImageDrawable(getDrawable("save_icon"))
        saveBtn.makeTvCompatible()
        saveBtn.setOnClickListener {
            saveAddonsToPrefs(addonList)

            AlertDialog.Builder(requireContext())
                .setTitle("Restart Required")
                .setMessage("Changes have been saved. Do you want to restart the app to apply them?")
                .setPositiveButton("Yes") { _, _ ->
                    showToast("Saved and Restarting...")
                    dismiss()
                    restartApp()
                }
                .setNegativeButton("No") { dialog, _ ->
                    showToast("Saved. Restart later to apply changes.")
                    dialog.dismiss()
                    dismiss()
                }
                .show()
        }

        val resetBtn = root.findView<View>("delete_img")
        resetBtn.makeTvCompatible()
        resetBtn.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Reset")
                .setMessage("This will delete all saved addons.")
                .setPositiveButton("Reset") { _, _ ->

                    sharedPref.edit(commit = true) {
                        sharedPref.all.keys
                            .filter { it.startsWith("stremio_addon") }
                            .forEach { remove(it) }
                    }
                    val size = addonList.size
                    if (size > 0) {
                        addonList.clear()
                        addonAdapter.notifyItemRangeRemoved(0, size)
                    }
                    stremioAddonInput.text.clear()
                    restartApp()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        return root
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {}

    private fun restartApp() {
        val context = requireContext().applicationContext
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component
        if (componentName != null) {
            val restartIntent = Intent.makeRestartActivityTask(componentName)
            context.startActivity(restartIntent)
            Runtime.getRuntime().exit(0)
        }
    }

    inner class AddonAdapter(
        private val items: MutableList<String>
    ) : RecyclerView.Adapter<AddonAdapter.AddonViewHolder>() {

        inner class AddonViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val urlText: TextView = view.findViewById(
                res.getIdentifier("addon_url_text", "id", BuildConfig.LIBRARY_PACKAGE_NAME)
            )
            val deleteButton: Button = view.findViewById(
                res.getIdentifier("delete_addon_button", "id", BuildConfig.LIBRARY_PACKAGE_NAME)
            )
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AddonViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val view = getLayout("item_stremio_addon", inflater, parent)
            view.makeTvCompatible()
            return AddonViewHolder(view)
        }

        override fun onBindViewHolder(holder: AddonViewHolder, position: Int) {
            val url = items[position]
            holder.urlText.text = url

            holder.deleteButton.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    items.removeAt(pos)
                    notifyItemRemoved(pos)
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
