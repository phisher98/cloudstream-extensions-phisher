package com.Phisher98.settings

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Button
import android.widget.EditText
import androidx.annotation.RequiresApi
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.Phisher98.BuildConfig
import com.Phisher98.SuperStreamPlugin
import com.lagradost.cloudstream3.CommonActivity.showToast

class SettingsFragment(
    private val plugin: SuperStreamPlugin,
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
        val id = res.getIdentifier(
            "settings_fragment",
            "layout",
            BuildConfig.LIBRARY_PACKAGE_NAME
        )
        val layout = res.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    @SuppressLint("SetJavaScriptEnabled")
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tokenInput = view.findView<EditText>("tokenInput")
        val addButton = view.findView<Button>("addButton")
        val resetButton = view.findView<Button>("resetButton")
        val loginButton = view.findView<Button>("loginButton")
        val webView = view.findView<WebView>("authWebView")

        setupWebView(webView) // ✅ FIX: Setup WebView properly

        loginButton.setOnClickListener {
            webView.visibility = View.VISIBLE
            webView.loadUrl("https://www.febbox.com/login/google?jump=%2F")
        }

        addButton.setOnClickListener {
            val token = tokenInput.text.toString().trim()
            if (token.isNotEmpty()) {
                sharedPref.edit()?.apply {
                    putString("token", token)
                    apply()
                }
                showToast("Token saved successfully. Restart the app.")
                dismiss()
            } else {
                showToast("Please enter a valid token")
            }
        }

        resetButton.setOnClickListener {
            sharedPref.edit()?.apply {
                remove("token")
                apply()
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true) // ✅ Fix for Android 9+
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val cookieManager = CookieManager.getInstance()
                val cookies = cookieManager.getCookie(url ?: "")
                val token = extractUICookie(cookies)

                if (!token.isNullOrEmpty() && view != null) {
                    activity?.runOnUiThread {
                        val tokenInput = requireView().findViewById<EditText>(
                            res.getIdentifier("tokenInput", "id", BuildConfig.LIBRARY_PACKAGE_NAME)
                        )
                        tokenInput.setText(token)

                        sharedPref.edit()?.apply {
                            putString("token", token)
                            apply()
                        }

                        showToast("Login successful! Token retrieved.")
                        webView.visibility = View.GONE // Hide WebView after login
                    }
                }
            }
        }
    }

    private fun extractUICookie(cookies: String?): String? {
        if (cookies.isNullOrEmpty()) return null
        val cookieList = cookies.split(";").map { it.trim() }
        return cookieList.find { it.startsWith("ui=") }
    }
}
