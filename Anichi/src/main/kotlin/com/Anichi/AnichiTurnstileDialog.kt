package com.Anichi

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.api.Log

/**
 * Full-screen BottomSheet that loads [targetUrl] in a real WebView to solve Anichi's
 * Cloudflare Turnstile challenge and extracts the `cf-turnstile-response` token.
 */
class AnichiTurnstileDialog(
    private val targetUrl: String,
    /** Called with the Turnstile token when found, or null if dismissed/failed. */
    private val onFinished: ((String?) -> Unit)? = null
) : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "AnichiTurnstileDialog"
        private const val POLL_INTERVAL_MS = 2_000L
        private const val POLL_TIMEOUT_MS  = 120_000L

        private val CHALLENGE_TITLES = listOf(
            "just a moment",
            "just a moment...",
            "checking your browser",
            "attention required",
            "ddos-guard",
            "one more step"
        )

        fun isChallengeTitle(title: String): Boolean =
            CHALLENGE_TITLES.any { title.lowercase().contains(it) }
    }

    private var webView: WebView? = null
    private var statusText: TextView? = null
    private var progressBar: ProgressBar? = null

    private val handler = Handler(Looper.getMainLooper())
    private var tokenSaved = false
    private var pollElapsedMs = 0L

    private val tokenPollRunnable = object : Runnable {
        override fun run() {
            if (tokenSaved || !isAdded) return

            pollElapsedMs += POLL_INTERVAL_MS
            updateStatus("⏳ Waiting for Turnstile token… (${pollElapsedMs / 1000}s)")

            if (pollElapsedMs == 4000L) {
                (dialog as? BottomSheetDialog)?.behavior?.apply {
                    skipCollapsed = true
                    peekHeight = android.view.WindowManager.LayoutParams.MATCH_PARENT
                    state = BottomSheetBehavior.STATE_EXPANDED
                }
            }

            if (pollElapsedMs >= POLL_TIMEOUT_MS) {
                updateStatus("⏱️ Timed out. Try solving the CAPTCHA then tap Bypass again.")
                return
            }

            // Inject JS to grab the Turnstile response token from the hidden input field
            webView?.evaluateJavascript(
                "(function() { " +
                "  var tokenInput = document.querySelector('[name=\"cf-turnstile-response\"]'); " +
                "  if (tokenInput && tokenInput.value) return tokenInput.value; " +
                "  return null; " +
                "})();"
            ) { result ->
                val cleanResult = result?.trim('"', '\'')
                if (!cleanResult.isNullOrBlank() && cleanResult != "null") {
                    saveTokenAndDismiss(cleanResult)
                } else {
                    handler.postDelayed(this, POLL_INTERVAL_MS)
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            // Start hidden, slide up after 4s if interaction is needed
            state = BottomSheetBehavior.STATE_COLLAPSED
            skipCollapsed = false
            peekHeight = 0
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        val bottomSheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )
        bottomSheet?.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
        bottomSheet?.requestLayout()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val screenH = requireContext().resources.displayMetrics.heightPixels
        val webViewHeight = (screenH * 0.70).toInt()

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        root.addView(TextView(requireContext()).apply {
            text = "🛡️ Anichi Cloudflare Bypass"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 8)
        })

        statusText = TextView(requireContext()).apply {
            text = "Loading challenge page…"
            textSize = 13f
            setTextColor(Color.parseColor("#A0A0B0"))
            setPadding(0, 0, 0, 4)
        }
        root.addView(statusText)

        root.addView(TextView(requireContext()).apply {
            text = "Solve the Turnstile CAPTCHA. The dialog will close automatically."
            textSize = 11f
            setTextColor(Color.parseColor("#707080"))
            setPadding(0, 0, 0, 12)
        })

        progressBar = ProgressBar(
            requireContext(), null, android.R.attr.progressBarStyleHorizontal
        ).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = 12 }
        }
        root.addView(progressBar)

        val wvContainer = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                webViewHeight
            )
        }
        webView = buildWebView()
        wvContainer.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(wvContainer)

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
            flush()
        }
        webView?.loadUrl(targetUrl)
        handler.postDelayed(tokenPollRunnable, POLL_INTERVAL_MS)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(): WebView {
        val wv = WebView(requireContext())
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            allowContentAccess = true
            allowFileAccess = true
            loadsImagesAutomatically = true
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (!tokenSaved) updateStatus("Loading… $newProgress%")
            }
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (tokenSaved) return
                val title = view?.title ?: ""
                if (isChallengeTitle(title)) {
                    updateStatus("🔄 Challenge active – solve the CAPTCHA above")
                } else {
                    updateStatus("✏️ Page loaded – extracting Turnstile token…")
                }
            }
        }
        return wv
    }

    private fun saveTokenAndDismiss(token: String) {
        if (tokenSaved) return
        tokenSaved = true
        handler.removeCallbacks(tokenPollRunnable)

        Log.d(TAG, "✅ Extracted Turnstile Token: $token")
        updateStatus("✅ Done! Token extracted.")

        webView?.postDelayed({
            if (isAdded) {
                onFinished?.invoke(token)
                dismissAllowingStateLoss()
            }
        }, 1500)
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        if (!tokenSaved) {
            handler.removeCallbacks(tokenPollRunnable)
            onFinished?.invoke(null)
        }
    }

    private fun updateStatus(msg: String) {
        activity?.runOnUiThread {
            statusText?.text = msg
            if (msg.startsWith("✅")) {
                progressBar?.visibility = View.GONE
                statusText?.setTextColor(Color.parseColor("#4CAF50"))
            } else {
                progressBar?.visibility = View.VISIBLE
                statusText?.setTextColor(Color.parseColor("#A0A0B0"))
            }
        }
    }

    override fun onDestroyView() {
        handler.removeCallbacks(tokenPollRunnable)
        webView?.apply {
            stopLoading()
            destroy()
        }
        webView = null
        super.onDestroyView()
    }
}
