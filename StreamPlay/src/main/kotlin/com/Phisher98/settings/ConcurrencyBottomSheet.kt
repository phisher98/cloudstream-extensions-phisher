package com.phisher98

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment

class ConcurrencyBottomSheet(
    plugin: StreamPlayPlugin,
    private val sharedPref: SharedPreferences,
    private val onDismissCallback: (() -> Unit)? = null
) : DialogFragment() {

    private val res = plugin.resources ?: throw Exception("Unable to access plugin resources")

    private fun View.makeTvCompatible() {
        val outlineId = res.getIdentifier("outline", "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        this.background = res.getDrawable(outlineId, null)
    }

    // Single source of truth with proper clamp
    private var currentValue = sharedPref
        .getInt("provider_concurrency", 20)
        .coerceIn(8, 50)

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        if (id == 0) throw Exception("View ID $name not found.")
        return this.findViewById(id)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val layoutId = res.getIdentifier(
            "concurrency_bottom_sheet",
            "layout",
            BuildConfig.LIBRARY_PACKAGE_NAME
        )
        val layout = res.getLayout(layoutId)
        val view = inflater.inflate(layout, container, false)
        val drawableId = res.getIdentifier("dialog_background", "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        if (drawableId != 0) {
            view.background = res.getDrawable(drawableId, null)
        }
        return view
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            val displayMetrics = resources.displayMetrics
            val maxDialogWidth = (500 * displayMetrics.density).toInt()
            val width = if (displayMetrics.widthPixels > 0 && displayMetrics.widthPixels > maxDialogWidth) {
                maxDialogWidth
            } else {
                (displayMetrics.widthPixels * 0.9f).toInt()
            }
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvValue = view.findView<TextView>("tv_value")
        val btnDecrease = view.findView<Button>("btn_decrease")
        val btnIncrease = view.findView<Button>("btn_increase")
        val btnClose = view.findView<Button>("btn_close")

        btnDecrease.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#D32F2F"))
        btnDecrease.setTextColor(android.graphics.Color.WHITE)
        btnDecrease.makeTvCompatible()

        btnIncrease.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#1E88E5"))
        btnIncrease.setTextColor(android.graphics.Color.WHITE)
        btnIncrease.makeTvCompatible()

        btnClose.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2196F3"))
        btnClose.setTextColor(android.graphics.Color.WHITE)
        btnClose.makeTvCompatible()
        fun updateUI() {
            tvValue.text = currentValue.toString()
            btnDecrease.isEnabled = currentValue > 1
            btnIncrease.isEnabled = currentValue < 50
        }

        btnDecrease.setOnClickListener {
            if (currentValue > 1) {
                currentValue--
                saveValue()
                updateUI()
            }
        }

        btnIncrease.setOnClickListener {
            if (currentValue < 50) {
                currentValue++
                saveValue()
                updateUI()
            }
        }

        btnClose.setOnClickListener {
            dismiss()
        }

        // Initial UI state
        updateUI()
    }

    private fun saveValue() {
        sharedPref.edit {
            putInt("provider_concurrency", currentValue)
        }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        onDismissCallback?.invoke()
    }
}