package com.phisher98.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.phisher98.BuildConfig
import com.phisher98.TorraStreamProvider

class SettingsFragment(
    private val plugin: TorraStreamProvider,
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
    private fun View.makeTvCompatible() {
        val outlineId = res.getIdentifier("outline", "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        background = res.getDrawable(outlineId, null)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = getLayout("settings", inflater, container)

        val providerSpinner = root.findView<Spinner>("provider_spinner")
        val providers = listOf("Select Service", "TorBox", "Realdebrid","AIO Streams")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, providers)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        providerSpinner.adapter = adapter
        providerSpinner.makeTvCompatible()

        val keyInput = root.findView<EditText>("key_input")
        keyInput.makeTvCompatible()

        val savedProvider = sharedPref.getString("provider", null)
        val savedKey = sharedPref.getString("key", null)

        providerSpinner.setSelection(providers.indexOf(savedProvider).takeIf { it >= 0 } ?: 0)
        keyInput.setText(savedKey ?: "")

        val saveBtn = root.findView<ImageView>("save")
        saveBtn.setImageDrawable(getDrawable("save_icon"))
        saveBtn.makeTvCompatible()
        saveBtn.setOnClickListener {
            val selectedProvider = providerSpinner.selectedItem.toString()
            val key = keyInput.text.toString()

            sharedPref.edit {
                putString("provider", selectedProvider)
                putString("key", key)
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

        keyInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        root.findView<View>("delete_img").requestFocus()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        root.findView<View>("provider_spinner").requestFocus()
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        val resetBtn = root.findView<View>("delete_img")
        resetBtn.makeTvCompatible()
        resetBtn.setOnClickListener {
            AlertDialog.Builder(
                context ?: throw Exception("Unable to build alert dialog")
            )
                .setTitle("Reset")
                .setMessage("This will delete the selected provider and key.")
                .setPositiveButton("Reset") { _, _ ->
                    sharedPref.edit().apply {
                        remove("provider")
                        remove("key")
                        commit()
                    }
                    providerSpinner.setSelection(0)
                    keyInput.text.clear()
                    restartApp()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        return root
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findView<Spinner>("provider_spinner").requestFocus()
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
