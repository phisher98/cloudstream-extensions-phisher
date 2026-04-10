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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ConcurrencyBottomSheet(
    plugin: StreamPlayPlugin,
    private val sharedPref: SharedPreferences
) : BottomSheetDialogFragment() {

    private val res = plugin.resources ?: throw Exception("Unable to access plugin resources")

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
        return inflater.inflate(layout, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.let { dlg ->
            val bottomSheet = dlg.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.isDraggable = true
                behavior.skipCollapsed = true
                sheet.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvValue = view.findView<TextView>("tv_value")
        val btnDecrease = view.findView<Button>("btn_decrease")
        val btnIncrease = view.findView<Button>("btn_increase")
        val btnClose = view.findView<Button>("btn_close")
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
}