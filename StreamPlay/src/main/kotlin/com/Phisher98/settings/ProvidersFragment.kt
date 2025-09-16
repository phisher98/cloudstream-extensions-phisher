package com.phisher98.settings

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.content.edit
import com.Phisher98.Provider
import com.Phisher98.buildProviders
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.phisher98.BuildConfig
import com.phisher98.StreamPlayPlugin

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
    private val PREFS_KEY = "enabled_providers"

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

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnSave = view.findView("btn_save")
        btnSave.setImageDrawable(getDrawable("save_icon"))
        btnSave.makeTvCompatible()

        btnSelectAll = view.findView("btn_select_all")
        btnDeselectAll = view.findView("btn_deselect_all")

        // --- Build providers dynamically ---
        providers = buildProviders().sortedBy { it.name.lowercase() }

        // --- Initialize adapter with saved or default all-on ---
        val savedSet = sharedPref.getStringSet(PREFS_KEY, null)
        val initiallyEnabled = savedSet ?: providers.map { it.id }.toSet()
        if (savedSet == null) {
            sharedPref.edit { putStringSet(PREFS_KEY, initiallyEnabled).apply() }
        }

        adapter = ProviderAdapter(providers, initiallyEnabled) { selected ->
            sharedPref.edit { putStringSet(PREFS_KEY, selected).apply() }
            updateUI()
        }

        // --- Add items to container manually ---
        container = view.findView("list_container")
        container.makeTvCompatible()
        val chkId = res.getIdentifier("chk_provider", "id", BuildConfig.LIBRARY_PACKAGE_NAME)

        providers.forEachIndexed { index, provider ->
            val item = getLayout("item_provider_checkbox", layoutInflater, container)
            val chk = item.findViewById<CheckBox>(chkId)
            chk.makeTvCompatible()
            chk.text = provider.name
            chk.isChecked = adapter.isEnabled(provider.id)

            item.setOnClickListener {
                chk.isChecked = !chk.isChecked
                adapter.setEnabled(provider.id, chk.isChecked)
            }

            chk.setOnCheckedChangeListener { _, isChecked ->
                adapter.setEnabled(provider.id, isChecked)
            }

            container.addView(item)
        }

        // --- Buttons ---
        btnSelectAll.setOnClickListener {
            adapter.setAll(true)
        }

        btnDeselectAll.setOnClickListener {
            adapter.setAll(false)
        }

        btnSave.setOnClickListener { dismissFragment() }
    }

    private fun updateUI() {
        val chkId = res.getIdentifier("chk_provider", "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        for (i in 0 until container.childCount) {
            val chk = container.getChildAt(i).findViewById<CheckBox>(chkId)
            val providerId = providers[i].id
            chk.isChecked = adapter.isEnabled(providerId)
        }
    }

    private fun dismissFragment() {
        parentFragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
    }

    inner class ProviderAdapter(
        private val items: List<Provider>,
        initiallyEnabled: Set<String>,
        private val onChange: (Set<String>) -> Unit
    ) {
        private val enabled = initiallyEnabled.toMutableSet()

        fun isEnabled(id: String) = enabled.contains(id)

        fun setEnabled(id: String, value: Boolean) {
            if (value) enabled.add(id) else enabled.remove(id)
            onChange(enabled.toSet())
        }

        fun setAll(value: Boolean) {
            enabled.clear()
            if (value) enabled.addAll(items.map { it.id })
            onChange(enabled.toSet())
        }
    }
}
