package com.Phisher98.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.phisher98.BuildConfig
import com.phisher98.StreamPlayPlugin
import com.phisher98.settings.SettingsFragment
import com.phisher98.settings.ToggleFragment

class MainSettingsFragment(
    private val plugin: StreamPlayPlugin,
    private val sharedPref: android.content.SharedPreferences
) : BottomSheetDialogFragment() {

    private val res = plugin.resources ?: throw Exception("Unable to access plugin resources")

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        if (id == 0) throw Exception("View ID $name not found.")
        return this.findViewById(id)
    }

    private fun View.makeTvCompatible() {
        val id = res.getIdentifier("outline", "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        if (id == 0) throw Exception("Drawable 'outline' not found.")
        this.background = res.getDrawable(id, null)
    }

    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = res.getIdentifier(name, "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        if (id == 0) throw Exception("Layout $name not found.")
        val layout = res.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return getLayout("fragment_main_settings", inflater, container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val loginCard: View = view.findView("loginCard")
        val featureCard: View = view.findView("featureCard")
        val saveIcon: ImageView = view.findView("saveIcon")

        // Optional: highlight save icon on TV
        saveIcon.makeTvCompatible()

        loginCard.setOnClickListener {
            val loginSettings = SettingsFragment(plugin, sharedPref)
            loginSettings.show(
                activity?.supportFragmentManager ?: throw Exception("No FragmentManager"),
                "settings_fragment"
            )
        }

        featureCard.setOnClickListener {
            val toggleFragment = ToggleFragment(plugin, sharedPref)
            toggleFragment.show(
                activity?.supportFragmentManager ?: throw Exception("No FragmentManager"),
                "fragment_toggle_extensions"
            )
        }

        saveIcon.setOnClickListener {
            AlertDialog.Builder(
                context ?: throw Exception("Unable to build alert dialog")
            )
                .setTitle("Save & Reload")
                .setMessage("Changes have been saved. Do you want to restart the app to apply them ?")
                .setPositiveButton("Yes") { _, _ ->
                    restartApp()
                    showToast("Saved and Reloaded")
                    dismiss()
                    restartApp()
                }
                .setNegativeButton("No", null)
                .show()
                .setDefaultFocus()
        }

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
