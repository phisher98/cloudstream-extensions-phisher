package com.Phisher98.settings

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
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
import androidx.core.content.res.ResourcesCompat
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

    @SuppressLint("UseSwitchCompatOrMaterialCode", "SetJavaScriptEnabled")
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val webView = view.findView<WebView>("febboxWebView")
        val tokenInput = view.findView<EditText>("tokenInput")
        val addButton = view.findView<Button>("addButton")
        val resetButton = view.findView<Button>("resetButton")

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.setSupportMultipleWindows(true)

        // ✅ FIX: Set a custom User-Agent
        webView.settings.userAgentString =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                // ✅ Only capture cookies after authentication
                if (url?.contains("febbox.com/login") == false) {
                    val cookieManager = CookieManager.getInstance()
                    val cookies = cookieManager.getCookie("https://www.febbox.com")

                    if (!cookies.isNullOrEmpty()) {
                        tokenInput.setText(cookies)

                        sharedPref.edit()?.apply {
                            putString("token", cookies)
                            apply()
                        }

                        showToast("Token (Cookie) saved successfully!")
                    }
                }
            }
        }

        webView.loadUrl("https://www.febbox.com/")

        val savedToken = sharedPref.getString("token", "")
        if (savedToken?.isNotEmpty() == true) {
            tokenInput.setText(savedToken)
        }

        addButton.setOnClickListener {
            val superStreamToken = tokenInput.text.toString().trim()

            if (superStreamToken.isNotEmpty()) {
                sharedPref.edit()?.apply {
                    putString("token", superStreamToken)
                    apply()
                }

                tokenInput.setText(superStreamToken)
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
}