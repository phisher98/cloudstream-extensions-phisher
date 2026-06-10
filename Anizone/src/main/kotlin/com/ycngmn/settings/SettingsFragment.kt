package com.ycngmn

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.core.content.edit
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.phisher98.BuildConfig
import android.content.Intent

class SettingsFragment(
    private val plugin: AnizonePlugin,
    private val sharedPref: SharedPreferences,
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val res = plugin.resources ?: return null
        val id = res.getIdentifier("anizone_settings_fragment", "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        val layout = res.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val res = plugin.resources ?: return
        val spinnerId = res.getIdentifier("language_spinner", "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        val saveBtnId = res.getIdentifier("save_button", "id", BuildConfig.LIBRARY_PACKAGE_NAME)

        val spinner = view.findViewById<Spinner>(spinnerId)
        val saveBtn = view.findViewById<Button>(saveBtnId)

        // Options
        val options = listOf("Default (0)", "English (1)", "Romaji (5)", "Japanese (8)", "Chinese Simplified (9)", "Chinese Traditional (38)")
        val values = listOf("0", "1", "5", "8", "9", "38")
        
        // Setup spinner adapter
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        // Set current selection
        val currentPref = sharedPref.getString("anizone_title_language", "1")
        val currentIndex = values.indexOf(currentPref).takeIf { it != -1 } ?: 0
        spinner.setSelection(currentIndex)

        saveBtn.setOnClickListener {
            val selectedIndex = spinner.selectedItemPosition
            val selectedValue = values[selectedIndex]
            sharedPref.edit(commit = true) {
                putString("anizone_title_language", selectedValue)
            }
            dismiss()
            restartApp()
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
