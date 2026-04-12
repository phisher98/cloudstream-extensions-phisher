package com.Aniworld

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.api.Log
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.phisher98.BuildConfig

class SettingsFragment(
    plugin: AniworldPlugin,
    private val sharedPref: SharedPreferences,
) : BottomSheetDialogFragment() {
    private val res = plugin.resources ?: throw Exception("Unable to read resources")

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        return this.findViewById(id)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val id = res.getIdentifier("settings_fragment", "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        val layout = res.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
            isDraggable = false // optional: prevent dragging at all
        }
    }


    @SuppressLint("SetJavaScriptEnabled", "SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tokenInput = view.findView<EditText>("tokenInput")
        val addButton = view.findView<Button>("addButton")
        val resetButton = view.findView<Button>("resetButton")
        val loginButton = view.findView<Button>("loginButton")
        val webView = view.findView<WebView>("authWebView")
        val savedToken = sharedPref.getString("serienstream_token", null)
        if (!savedToken.isNullOrEmpty()) {
            tokenInput.setText(savedToken)
        }

        setupWebView(webView)

        loginButton.setOnClickListener {
            webView.visibility = View.VISIBLE
            webView.loadUrl("https://serienstream.to/login")
        }

        addButton.setOnClickListener {
            val token = tokenInput.text.toString().trim()
            if (token.isNotEmpty()) {
                sharedPref.edit { putString("serienstream_token", token) }
                val ctx = context ?: run {
                    showToast("Error: Context is null")
                    return@setOnClickListener
                }

                AlertDialog.Builder(ctx)
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
            } else {
                showToast("Please enter a valid token")
            }
        }

        resetButton.setOnClickListener {
            sharedPref.edit {
                remove("serienstream_token")
            }
            tokenInput.setText("")
            showToast("Token reset successfully. Restart the app.")
            dismiss()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.90 Mobile Safari/537.36"

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                // Detect login success (not on login page anymore)
                if (url != null && !url.contains("/login") && view != null) {
                    // Give page time to fully load and set cookies
                    view.postDelayed({
                        extractAndSaveCookie(view)
                    }, 1500)
                }
            }
        }
    }

    private fun extractAndSaveCookie(webView: WebView) {
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie("https://serienstream.to")

        if (!cookies.isNullOrEmpty()) {
            try {
                // Check if cookies contain laravel_session or remember_token
                if (cookies.contains("laravel_session") || cookies.contains("remember_token")) {
                    activity?.runOnUiThread {
                        try {
                            val tokenInput = requireView().findView<EditText>("tokenInput")
                            // Save the entire full cookie string
                            tokenInput.setText(cookies)

                            sharedPref.edit {
                                putString("serienstream_token", cookies)
                            }

                            showToast("✓ Login successful! Cookie saved.")
                            webView.visibility = View.GONE
                            webView.clearHistory()
                            webView.clearCache(true)
                        } catch (e: Exception) {
                            showToast("Error saving cookie: ${e.message}")
                        }
                    }
                } else {
                    showToast("Required cookies not found (need laravel_session or remember_token)")
                }
            } catch (e: Exception) {
                showToast("Error extracting cookies: ${e.message}")
            }
        } else {
            showToast("No cookies found. Try logging in again.")
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