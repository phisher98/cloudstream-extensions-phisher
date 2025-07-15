package com.Phisher98.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
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
        val settings = getLayout("fragment_main_settings", inflater, container)

        val id = res.getIdentifier("fragment_main_settings", "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        val layout = res.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val loginGear: ImageView = view.findView("loginGear")
        val featureGear: ImageView = view.findView("featureGear")

        loginGear.setOnClickListener {
            val loginSettings = SettingsFragment(plugin, sharedPref)
            loginSettings.show(
                activity?.supportFragmentManager ?: throw Exception("No FragmentManager"),
                "settings_fragment"
            )
        }

        featureGear.setOnClickListener {
            try {
                println("DEBUG: Toggle gear clicked")

                val toggleFragment = ToggleFragment(plugin, sharedPref)
                toggleFragment.show(
                    activity?.supportFragmentManager ?: throw Exception("No FragmentManager"),
                    "fragment_toggle"
                )

                println("DEBUG: ToggleFragment show() called successfully")
            } catch (e: Exception) {
                println("ERROR: Failed to show ToggleFragment - ${e.message}")
                e.printStackTrace()
            }
        }

    }
}
