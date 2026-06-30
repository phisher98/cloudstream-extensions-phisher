package com.phisher98

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
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.api.Log

/**
 * Full-screen BottomSheet that loads [targetUrl] in a real WebView so Cloudflare's
 * JS challenge / Turnstile CAPTCHA can run in a genuine browser environment.
 *
 * Detection strategy:
 *  - A background Handler polls for `cf_clearance` in the CookieManager every 2 s.
 *  - This avoids false positives from page-title checks during CF's multi-redirect flow.
 *  - Once found the cookies are persisted via [AnimePaheProviderPlugin] and the dialog
 *    closes automatically.
 */
class CloudflareWebViewDialog(
    private val targetUrl: String,
    /** Called with true when cf_clearance was saved, false if the user dismissed without solving. */
    private val onFinished: ((Boolean) -> Unit)? = null
) : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "CFWebViewDialog"
        private const val POLL_INTERVAL_MS = 2_000L   // check cookies every 2 s
        private const val POLL_TIMEOUT_MS  = 120_000L // give up after 2 minutes

        /**
         * Page-title strings that definitively indicate a CF challenge is still active.
         * NOTE: "please wait" is intentionally excluded – it appears on many normal pages
         * and was causing false reload loops.
         */
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

    // ── UI refs ──────────────────────────────────────────────────────────────
    private var webView: WebView? = null
    private var statusText: TextView? = null
    private var progressBar: ProgressBar? = null

    // ── Polling state ────────────────────────────────────────────────────────
    private val handler = Handler(Looper.getMainLooper())
    private var cookiesSaved = false
    private var pollElapsedMs = 0L

    // The host origin from which we read cookies (derived from targetUrl)
    private val targetHost: String by lazy {
        try {
            val uri = android.net.Uri.parse(targetUrl)
            "${uri.scheme}://${uri.host}"
        } catch (_: Exception) {
            targetUrl
        }
    }

    // ── Polling runnable ─────────────────────────────────────────────────────
    private val cookiePollRunnable = object : Runnable {
        override fun run() {
            if (cookiesSaved || !isAdded) return

            // Flush to make sure JS-written cookies are visible to Java
            CookieManager.getInstance().flush()

            val cookieStr = CookieManager.getInstance().getCookie(targetHost) ?: ""
            Log.d(TAG, "Poll [$pollElapsedMs ms] cookies for $targetHost → $cookieStr")

            when {
                cookieStr.contains("cf_clearance") -> {
                    // ✅ Full Cloudflare clearance cookie found
                    saveCookiesAndDismiss(cookieStr)
                }
                cookieStr.contains("__ddg2_") || cookieStr.contains("__ddg1_") -> {
                    // DDG cookies present – might be enough for some servers; keep polling
                    // for cf_clearance but also accept if timeout is near
                    if (pollElapsedMs >= POLL_TIMEOUT_MS / 2) {
                        saveCookiesAndDismiss(cookieStr)
                    } else {
                        scheduleNextPoll()
                    }
                }
                pollElapsedMs >= POLL_TIMEOUT_MS -> {
                    // Timed out
                    updateStatus("⏱️ Timed out. Try solving the CAPTCHA then tap Bypass again.")
                }
                else -> {
                    scheduleNextPoll()
                }
            }
        }
    }

    private fun scheduleNextPoll() {
        pollElapsedMs += POLL_INTERVAL_MS
        updateStatus("⏳ Waiting for cookies… (${pollElapsedMs / 1000}s)")

        // If the 0-click Turnstile hasn't solved after the first poll (2 s), it likely needs
        // a manual interaction. Expand the BottomSheet so the user can see and tap the CAPTCHA.
        // (pollElapsedMs == 3500L could never be reached since steps are 2000 ms apart.)
        if (pollElapsedMs >= POLL_INTERVAL_MS) {
            (dialog as? com.google.android.material.bottomsheet.BottomSheetDialog)?.behavior?.apply {
                skipCollapsed = true
                peekHeight = android.view.WindowManager.LayoutParams.MATCH_PARENT
                state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            }
        }

        handler.postDelayed(cookiePollRunnable, POLL_INTERVAL_MS)
    }

    // ── Dialog / View lifecycle ───────────────────────────────────────────────

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        (dialog as? com.google.android.material.bottomsheet.BottomSheetDialog)?.behavior?.apply {
            // Start completely hidden (background mode). 
            // It will only slide up if it takes longer than 3.5s.
            state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
            skipCollapsed = false
            peekHeight = 0
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        // Force the BottomSheet container window to MATCH_PARENT height.
        // Without this the inner LinearLayout with weight-based children collapses.
        dialog?.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
        // Also expand the BottomSheet frame itself
        val bottomSheet = dialog?.findViewById<android.view.View>(
            com.google.android.material.R.id.design_bottom_sheet
        )
        bottomSheet?.layoutParams?.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        bottomSheet?.requestLayout()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Use 70% of real screen height for the WebView so it is always visible.
        // Using weight=1f/height=0 inside a BottomSheet collapses to 0 on many devices.
        val screenH = requireContext().resources.displayMetrics.heightPixels
        val webViewHeight = (screenH * 0.70).toInt()

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT  // let content define height
            )
        }

        // Header label
        root.addView(TextView(requireContext()).apply {
            text = "🛡️ Cloudflare Bypass"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 8)
        })

        // Status line
        statusText = TextView(requireContext()).apply {
            text = "Loading challenge page…"
            textSize = 13f
            setTextColor(Color.parseColor("#A0A0B0"))
            setPadding(0, 0, 0, 4)
        }
        root.addView(statusText)

        // Hint
        root.addView(TextView(requireContext()).apply {
            text = "Solve any CAPTCHA shown below. The dialog will close automatically once done."
            textSize = 11f
            setTextColor(Color.parseColor("#707080"))
            setPadding(0, 0, 0, 12)
        })

        // Indeterminate progress bar
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

        // WebView container – explicit pixel height so it is never collapsed
        val wvContainer = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                webViewHeight          // concrete height – weight=1f collapses in BottomSheet
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

        // Accept all cookies (do NOT clear cf_clearance here – clearing breaks CF's
        // own redirect chain and causes Turnstile to loop infinitely)
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
            flush()
        }

        webView?.loadUrl(targetUrl)

        // Start polling for cookies in background
        handler.postDelayed(cookiePollRunnable, POLL_INTERVAL_MS)
    }

    // ── WebView builder ───────────────────────────────────────────────────────

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


        // WebChromeClient is required for Turnstile (JS dialog / progress)
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (!cookiesSaved) {
                    updateStatus("Loading… $newProgress%")
                }
            }
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?, request: WebResourceRequest?
            ): Boolean = false   // follow all CF redirects inside the WebView

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (cookiesSaved) return

                val title = view?.title ?: ""
                Log.d(TAG, "onPageFinished  title='$title'  url=$url")

                if (isChallengeTitle(title)) {
                    updateStatus("🔄 Challenge active – solve the CAPTCHA above")
                } else {
                    updateStatus("✏️ Page loaded – checking cookies…")
                    CookieManager.getInstance().flush()

                    // Check cookies from both the target host and the current page URL
                    // (CF sometimes sets them on the redirect destination, not the origin)
                    val cookiesFromTarget = CookieManager.getInstance().getCookie(targetHost) ?: ""
                    val cookiesFromUrl = url?.let {
                        runCatching {
                            val uri = android.net.Uri.parse(it)
                            CookieManager.getInstance().getCookie("${uri.scheme}://${uri.host}")
                        }.getOrNull()
                    } ?: ""

                    val bestCookies = when {
                        cookiesFromTarget.contains("cf_clearance") -> cookiesFromTarget
                        cookiesFromUrl.contains("cf_clearance")    -> cookiesFromUrl
                        else                                        -> null
                    }

                    if (bestCookies != null) {
                        handler.removeCallbacks(cookiePollRunnable)
                        saveCookiesAndDismiss(bestCookies)
                    }
                }
            }
        }

        return wv
    }

    // ── Cookie save & dismiss ────────────────────────────────────────────────

    private fun saveCookiesAndDismiss(cookieStr: String) {
        if (cookiesSaved) return
        cookiesSaved = true

        handler.removeCallbacks(cookiePollRunnable)

        AnimePaheProviderPlugin.cfCookies    = cookieStr
        AnimePaheProviderPlugin.cfCookieHost = targetHost
        webView?.settings?.userAgentString?.let { ua ->
            AnimePaheProviderPlugin.cfUserAgent = ua
        }

        Log.d(TAG, "✅ Saved cookies: $cookieStr")

        updateStatus("✅ Done! Cookies saved.")

        // Auto-dismiss after a brief moment so user can see the success message
        webView?.postDelayed({
            if (isAdded) {
                onFinished?.invoke(true)
                dismissAllowingStateLoss()
            }
        }, 1500)
    }

    /** Called when the dialog is dismissed without saving (user pressed Back / outside). */
    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        if (!cookiesSaved) {
            handler.removeCallbacks(cookiePollRunnable)
            onFinished?.invoke(false)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
        handler.removeCallbacks(cookiePollRunnable)
        webView?.apply {
            stopLoading()
            destroy()
        }
        webView = null
        super.onDestroyView()
    }
}
