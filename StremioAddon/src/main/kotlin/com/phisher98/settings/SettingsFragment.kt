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
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.phisher98.BuildConfig
import com.phisher98.StremioAddonProvider

class SettingsFragment(
    private val plugin: StremioAddonProvider,
    private val sharedPref: SharedPreferences
) : BottomSheetDialogFragment() {

    private val res: Resources = plugin.resources ?: throw Exception("Unable to read resources")

    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = res.getIdentifier(name, "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        return inflater.inflate(res.getLayout(id), container, false)
    }

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = getLayout("settings", inflater, container)

        // ===== ADDON 1 =====
        val stremioAddonInput = root.findView<EditText>("stremio_addon_input")
        stremioAddonInput.setText(sharedPref.getString("stremio_addon", ""))
        stremioAddonInput.makeTvCompatible()

        // ===== ADDON 2 =====
        val stremioAddon2Input = root.findView<EditText>("stremio_addon2_input")
        stremioAddon2Input.setText(sharedPref.getString("stremio_addon2", ""))
        stremioAddon2Input.makeTvCompatible()

        // ===== ADDON 3 =====
        val stremioAddon3Input = root.findView<EditText>("stremio_addon3_input")
        stremioAddon3Input.setText(sharedPref.getString("stremio_addon3", ""))
        stremioAddon3Input.makeTvCompatible()

        // ===== ADDON 4 =====
        val stremioAddon4Input = root.findView<EditText>("stremio_addon4_input")
        stremioAddon4Input.setText(sharedPref.getString("stremio_addon4", ""))
        stremioAddon4Input.makeTvCompatible()

        // ===== ADDON 5 =====
        val stremioAddon5Input = root.findView<EditText>("stremio_addon5_input")
        stremioAddon5Input.setText(sharedPref.getString("stremio_addon5", ""))
        stremioAddon5Input.makeTvCompatible()

        // ===== SAVE =====
        val saveBtn = root.findView<ImageView>("save")
        saveBtn.setImageDrawable(getDrawable("save_icon"))
        saveBtn.makeTvCompatible()
        saveBtn.setOnClickListener {
            sharedPref.edit {
                putString("stremio_addon", stremioAddonInput.text.toString())
                putString("stremio_addon2", stremioAddon2Input.text.toString())
                putString("stremio_addon3", stremioAddon3Input.text.toString())
                putString("stremio_addon4", stremioAddon4Input.text.toString())
                putString("stremio_addon5", stremioAddon5Input.text.toString())
            }

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

        // ===== RESET =====
        val resetBtn = root.findView<View>("delete_img")
        resetBtn.makeTvCompatible()
        resetBtn.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Reset")
                .setMessage("This will delete all saved settings.")
                .setPositiveButton("Reset") { _, _ ->
                    sharedPref.edit().clear().commit()
                    stremioAddonInput.text.clear()
                    stremioAddon2Input.text.clear()
                    stremioAddon3Input.text.clear()
                    stremioAddon4Input.text.clear()
                    stremioAddon5Input.text.clear()
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
}
