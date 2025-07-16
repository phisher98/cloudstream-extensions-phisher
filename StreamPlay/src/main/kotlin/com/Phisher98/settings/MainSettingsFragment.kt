package com.Phisher98.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.api.Log
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.phisher98.BuildConfig
import com.phisher98.StreamPlayPlugin
import com.phisher98.settings.SettingsFragment
import com.phisher98.settings.ToggleFragment


class MainSettingsFragment(
    private val plugin: StreamPlayPlugin,
    private val sharedPref: android.content.SharedPreferences
) : BottomSheetDialogFragment() {

    private val res = plugin.resources ?: throw Exception("Unable to read resources")

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        return this.findViewById(id)
    }


    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = res.getIdentifier(name, "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        val layout = res.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val id = res.getIdentifier("fragment_main_settings", "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        val layout = res.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val loginGear: ImageView = view.findView("loginGear")
        val featureGear: ImageView = view.findView("featureGear")
        val saveIcon: ImageView = view.findView("saveIcon")
        loginGear.setOnClickListener {
            val loginSettings = SettingsFragment(plugin, sharedPref)
            loginSettings.show(
                activity?.supportFragmentManager ?: throw Exception("No FragmentManager"),
                "settings_fragment"
            )
        }

        featureGear.setOnClickListener {
            val toggleFragment = ToggleFragment(plugin, sharedPref)
            val fragmentManager = activity?.supportFragmentManager ?: return@setOnClickListener
            toggleFragment.show(fragmentManager, "fragment_toggle_extensions")
        }

        saveIcon.setOnClickListener {
            showToast("Settings Saved. Please restart the app to apply changes.")
            dismiss()
        }

    }
}
