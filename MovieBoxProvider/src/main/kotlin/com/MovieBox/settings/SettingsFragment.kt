package com.MovieBox

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.phisher98.BuildConfig

class SettingsFragment(
    private val plugin: MovieBoxProviderPlugin,
    private val sharedPref: SharedPreferences,
) : BottomSheetDialogFragment() {

    private val res = plugin.resources ?: throw Exception("Unable to access plugin resources")

    private val HOST_POOL = listOf(
        "https://api6.aoneroom.com",
        "https://api5.aoneroom.com",
        "https://api4.aoneroom.com",
        "https://api4sg.aoneroom.com",
        "https://api3.aoneroom.com",
    )

    private fun getDrawable(name: String): Drawable {
        val id = res.getIdentifier(name, "drawable", "com.phisher98")
        return res.getDrawable(id, null) ?: throw Exception("Drawable $name not found")
    }

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", "com.phisher98")
        if (id == 0) throw Exception("View ID $name not found.")
        return this.findViewById(id)
    }

    private fun View.makeTvCompatible() {
        val outlineId = res.getIdentifier("outline", "drawable", "com.phisher98")
        this.background = res.getDrawable(outlineId, null)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            val displayMetrics = resources.displayMetrics
            val maxDialogWidth = (500 * displayMetrics.density).toInt()
            val width = if (displayMetrics.widthPixels > 0 && displayMetrics.widthPixels > maxDialogWidth) {
                maxDialogWidth
            } else {
                (displayMetrics.widthPixels * 0.9f).toInt()
            }
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
            val layoutId = res.getIdentifier("fragment_moviebox_settings", "layout", "com.phisher98")
            val layout = res.getLayout(layoutId)
            val view = inflater.inflate(layout, container, false)
            
            val drawableId = res.getIdentifier("dialog_background", "drawable", "com.phisher98")
        if (drawableId != 0) {
            view.background = res.getDrawable(drawableId, null)
        }

        val saveIcon: ImageView = view.findView("saveIcon")
        val hostIcon: ImageView = view.findView("hostIcon")
        val hostRow: View = view.findView("hostRow")
        val hostSubtitle: android.widget.TextView = view.findView("hostSubtitle")

        saveIcon.setImageDrawable(getDrawable("save_icon"))
        hostIcon.setImageDrawable(getDrawable("settings_icon"))
        hostRow.background = getDrawable("settings_item_background")
        
        saveIcon.makeTvCompatible()

        val hostNames = HOST_POOL.map { it.removePrefix("https://") }.toTypedArray()
        var currentHostIndex = HOST_POOL.indexOf(sharedPref.getString("moviebox_host", HOST_POOL[4])).coerceAtLeast(0)

        // Show current host as subtitle
        hostSubtitle.text = "Current: ${hostNames[currentHostIndex]}"

        hostRow.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Select API Host")
                .setSingleChoiceItems(hostNames, currentHostIndex) { dialog, which ->
                    currentHostIndex = which
                    val selected = HOST_POOL[which]
                    sharedPref.edit().putString("moviebox_host", selected).apply()
                    hostSubtitle.text = "Current: ${hostNames[which]}"
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        saveIcon.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Save & Reload")
                .setMessage("Changes have been saved. Do you want to restart the app to apply them?")
                .setPositiveButton("Yes") { _, _ ->
                    dismiss()
                    restartApp()
                }
                .setNegativeButton("No", null)
                .show()
        }

        return view
    }

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
}
