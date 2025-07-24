package com.phisher98.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.fragment.app.DialogFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.phisher98.BuildConfig
import androidx.appcompat.app.AlertDialog
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.phisher98.JellyfinPlugin
import androidx.core.content.edit

class SettingsFragment(
    plugin: JellyfinPlugin,
    private val sharedPref: SharedPreferences,
) : DialogFragment() {

    private val res = plugin.resources ?: throw Exception("Unable to read resources")
    private lateinit var URL: String
    private lateinit var Username: String
    private lateinit var Password: String

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        if (id == 0) throw Resources.NotFoundException("View ID $name not found.")
        return this.findViewById(id)
    }

    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = res.getIdentifier(name, "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        if (id == 0) throw Resources.NotFoundException("Layout $name not found.")
        val layout = res.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    private fun getDrawable(name: String): Drawable {
        val id = res.getIdentifier(name, "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        return res.getDrawable(id, null)
            ?: throw Resources.NotFoundException("Drawable $name not found.")
    }

    private fun View.makeTvCompatible() {
        val outlineId = res.getIdentifier("outline", "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        this.background = res.getDrawable(outlineId, null)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val view = getLayout("settings_fragment", inflater, container)

        val saveButton = view.findView<Button>("saveCredentialsButton")
        val closeButton = view.findView<ImageView>("closeButton")
        val urlInput = view.findView<EditText>("urlInput")
        val usernameInput = view.findView<EditText>("usernameInput")
        val passwordInput = view.findView<EditText>("passwordInput")
        val toggleVisibility = view.findView<ImageView>("togglePasswordVisibility")
        val resetButton = view.findView<Button>("resetCredentialsButton")

        toggleVisibility.setImageDrawable(getDrawable("ic_visibility_off"))

        var isPasswordVisible = false

        toggleVisibility.setOnClickListener {
            isPasswordVisible = !isPasswordVisible

            if (isPasswordVisible) {
                passwordInput.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                toggleVisibility.setImageDrawable(getDrawable("ic_visibility"))
            } else {
                passwordInput.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                toggleVisibility.setImageDrawable(getDrawable("ic_visibility_off"))
            }

            // Preserve cursor position
            passwordInput.setSelection(passwordInput.text?.length ?: 0)
        }

        saveButton.makeTvCompatible()
        resetButton.makeTvCompatible()

        saveButton.setOnClickListener {
            // Get and trim values
            URL = urlInput.text.toString().trim()
            Username = usernameInput.text.toString().trim()
            Password = passwordInput.text.toString().trim()

            if (URL.isEmpty() || Username.isEmpty() || Password.isEmpty()) {
                showToast("Please fill all fields")
            } else {
                sharedPref.edit()?.apply {
                    putString("url", URL)
                    putString("username", Username)
                    putString("password", Password)
                    apply()
                }

                showToast("Credentials Saved")

                AlertDialog.Builder(requireContext())
                    .setTitle("Save & Reload")
                    .setMessage("Changes have been saved. Do you want to restart the app to apply them?")
                    .setPositiveButton("Yes") { _, _ ->
                        dismiss()
                        restartApp()
                    }
                    .setNegativeButton("No") { _, _ ->
                        dismiss()
                    }
                    .show()
            }
        }

        resetButton.setOnClickListener {
            sharedPref.edit { clear() }
            urlInput.setText("")
            usernameInput.setText("")
            passwordInput.setText("")
            showToast("Credentials reset")
            AlertDialog.Builder(requireContext())
                .setTitle("Save & Reload")
                .setMessage("Reset Completed. Do you want to restart the app to apply them?")
                .setPositiveButton("Yes") { _, _ ->
                    dismiss()
                    restartApp()
                }
                .setNegativeButton("No") { _, _ ->
                    dismiss()
                }
                .show()
        }



        closeButton.setOnClickListener {
            dismiss()
        }

        return view
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
            isDraggable = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Material_Light_Dialog_Alert)
    }

    @SuppressLint("SetJavaScriptEnabled", "SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // no-op
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
