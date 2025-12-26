package com.phisher98

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.Phisher98.LanguageSelectFragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.phisher98.settings.SettingsFragment
import com.phisher98.settings.ToggleFragment

class MainSettingsFragment(
    private val plugin: StreamPlayPlugin,
    private val sharedPref: android.content.SharedPreferences
) : BottomSheetDialogFragment() {

    private val res = plugin.resources ?: throw Exception("Unable to access plugin resources")

    private fun getDrawable(name: String): Drawable {
        val id = res.getIdentifier(name, "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        return res.getDrawable(id, null) ?: throw Exception("Drawable $name not found")
    }

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        if (id == 0) throw Exception("View ID $name not found.")
        return this.findViewById(id)
    }

    private fun View.makeTvCompatible() {
        val outlineId = res.getIdentifier("outline", "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        this.background = res.getDrawable(outlineId, null)
    }

    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = res.getIdentifier(name, "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        val layout = res.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = getLayout("fragment_main_settings", inflater, container)

        val loginCard: ImageView = view.findView("loginCard")
        val featureCard: ImageView = view.findView("featureCard")
        val toggleproviders: ImageView = view.findView("toggleproviders")
        val languagechange: ImageView = view.findView("languageCard")
        val stremioaddon: ImageView = view.findView("stremioaddons")

        val saveIcon: ImageView = view.findView("saveIcon")

        loginCard.setImageDrawable(getDrawable("settings_icon"))
        languagechange.setImageDrawable(getDrawable("settings_icon"))
        featureCard.setImageDrawable(getDrawable("settings_icon"))
        toggleproviders.setImageDrawable(getDrawable("settings_icon"))
        stremioaddon.setImageDrawable(getDrawable("settings_icon"))
        saveIcon.setImageDrawable(getDrawable("save_icon"))

        loginCard.makeTvCompatible()
        featureCard.makeTvCompatible()
        toggleproviders.makeTvCompatible()
        languagechange.makeTvCompatible()
        stremioaddon.makeTvCompatible()

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

        toggleproviders.setOnClickListener {
            val providersFragment = ProvidersFragment(plugin, sharedPref)
            providersFragment.show(
                activity?.supportFragmentManager ?: throw Exception("No FragmentManager"),
                "fragment_toggle_providers"
            )
        }

        languagechange.setOnClickListener {
            LanguageSelectFragment(plugin, sharedPref).show(
                activity?.supportFragmentManager!!,
                "fragment_language_list"
            )
        }

        stremioaddon.setOnClickListener {
            val providersFragment = StreamPlayStremioCatelogFrag(plugin, sharedPref)
            providersFragment.show(
                activity?.supportFragmentManager ?: throw Exception("No FragmentManager"),
                "stremio_bottom_sheet_layout"
            )
        }

        saveIcon.setOnClickListener {
            val context = this.context ?: return@setOnClickListener

            AlertDialog.Builder(context)
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
