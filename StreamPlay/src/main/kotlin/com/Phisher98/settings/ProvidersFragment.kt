package com.phisher98

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.core.view.isNotEmpty

class ProvidersFragment(
    private val plugin: StreamPlayPlugin,
    private val sharedPref: SharedPreferences
) : BottomSheetDialogFragment() {

    private val res = plugin.resources ?: throw Exception("Unable to access plugin resources")
    private lateinit var btnSave: ImageButton
    private lateinit var btnSelectAll: Button
    private lateinit var btnDeselectAll: Button
    private lateinit var adapter: ProviderAdapter
    private lateinit var container: LinearLayout
    private var providers: List<Provider> = emptyList()
    private val PREFS_DISABLED = "disabled_providers"

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        if (id == 0) throw Exception("View ID $name not found.")
        return this.findViewById(id)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun getDrawable(name: String): Drawable {
        val id = res.getIdentifier(name, "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        return res.getDrawable(id, null) ?: throw Exception("Drawable $name not found")
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun View.makeTvCompatible() {
        val outlineId = res.getIdentifier("outline", "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        this.background = res.getDrawable(outlineId, null)
    }

    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = res.getIdentifier(name, "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        val layout = res.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return getLayout("fragment_providers", inflater, container)
    }

    override fun onStart() {
        super.onStart()
        dialog?.let { dlg ->
            val bottomSheet = dlg.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.isDraggable = true
                behavior.skipCollapsed = true
                sheet.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnSave = view.findView("btn_save")
        btnSave.setImageDrawable(getDrawable("save_icon"))
        btnSave.makeTvCompatible()

        btnSelectAll = view.findView("btn_select_all")
        btnDeselectAll = view.findView("btn_deselect_all")
        //btnSelectAll.makeTvCompatible()
        //btnDeselectAll.makeTvCompatible()
        container = view.findView("list_container")
        container.makeTvCompatible()
        providers = buildProviders().sortedBy { it.name.lowercase() }

        // --- Load disabled providers ---
        val savedDisabled = sharedPref.getStringSet(PREFS_DISABLED, emptySet()) ?: emptySet()

        adapter = ProviderAdapter(providers, savedDisabled) { disabled ->
            sharedPref.edit { putStringSet(PREFS_DISABLED, disabled) }
            updateUI()
        }

        val chkId = res.getIdentifier("chk_provider", "id", BuildConfig.LIBRARY_PACKAGE_NAME)

        // --- Add provider items ---
        providers.forEach { provider ->
            val item = getLayout("item_provider_checkbox", layoutInflater, container)
            val chk = item.findViewById<CheckBox>(chkId)
            item.makeTvCompatible()
            chk.makeTvCompatible()
            chk.text = provider.name
            // Enabled if NOT in disabled list
            chk.isChecked = !adapter.isDisabled(provider.id)

            item.setOnClickListener { chk.toggle() }

            chk.setOnCheckedChangeListener { _, isChecked ->
                // Checked → remove from disabled, Unchecked → add to disabled
                adapter.setDisabled(provider.id, !isChecked)
            }

            container.addView(item)
        }
        container.post {
            if (container.isNotEmpty()) {
                val firstItem = container.getChildAt(0)
                firstItem.isFocusable = true
                firstItem.requestFocusFromTouch()
                firstItem.nextFocusUpId = btnSave.id
            }
        }

        btnSelectAll.setOnClickListener { adapter.setAll(true) }
        btnDeselectAll.setOnClickListener { adapter.setAll(false) }
        btnSave.setOnClickListener { dismissFragment() }
    }

    private fun updateUI() {
        val chkId = res.getIdentifier("chk_provider", "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        for (i in 0 until container.childCount) {
            val chk = container.getChildAt(i).findViewById<CheckBox>(chkId)
            chk.isChecked = !adapter.isDisabled(providers[i].id)
        }
    }

    private fun dismissFragment() {
        parentFragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
    }

    inner class ProviderAdapter(
        private val items: List<Provider>,
        initiallyDisabled: Set<String>,
        private val onChange: (Set<String>) -> Unit
    ) {
        private val disabled = initiallyDisabled.toMutableSet()

        fun isDisabled(id: String) = id in disabled

        fun setDisabled(id: String, value: Boolean) {
            if (value) disabled.add(id) else disabled.remove(id)
            onChange(disabled)
        }

        fun setAll(value: Boolean) {
            disabled.clear()
            if (!value) disabled.addAll(items.map { it.id })
            onChange(disabled)
        }
    }
}
