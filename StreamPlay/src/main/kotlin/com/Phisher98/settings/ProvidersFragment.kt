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
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import androidx.core.view.isNotEmpty
import androidx.core.widget.addTextChangedListener
import com.lagradost.cloudstream3.CommonActivity.showToast
import androidx.core.view.isVisible


private val PREFS_PROFILES = "provider_profiles"

class ProvidersFragment(
    private val plugin: StreamPlayPlugin,
    private val sharedPref: SharedPreferences,
    private val onDismissCallback: (() -> Unit)? = null
) : DialogFragment() {

    private val res = plugin.resources ?: throw Exception("Unable to access plugin resources")
    private lateinit var btnSave: ImageButton
    private lateinit var btnSelectAll: Button
    private lateinit var btnDeselectAll: Button
    private lateinit var adapter: ProviderAdapter
    private lateinit var container: LinearLayout
    private var providers: List<Provider> = emptyList()
    private val PREFS_DISABLED = "disabled_providers"
    private lateinit var tvProviderCount: TextView

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
        val view = getLayout("fragment_providers", inflater, container)
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
    private fun updateProviderCount() {
        val enabled = providers.count { !adapter.isDisabled(it.id) }
        val total = providers.size
        tvProviderCount.text = "$enabled / $total enabled"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvProviderCount = view.findView("tv_provider_count")
        btnSave = view.findView("btn_save")
        btnSave.setImageDrawable(getDrawable("save_icon"))
        btnSave.makeTvCompatible()

        btnSelectAll = view.findView("btn_select_all")
        btnDeselectAll = view.findView("btn_deselect_all")
        btnSelectAll.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#1F6FEB"))
        btnSelectAll.setTextColor(android.graphics.Color.WHITE)
        btnDeselectAll.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#BB2D3B"))
        btnDeselectAll.setTextColor(android.graphics.Color.WHITE)
        container = view.findView("list_container")
        container.makeTvCompatible()
        providers = buildProviders().sortedBy { it.name.lowercase() }

        //Search

        val etSearch = view.findView<EditText>("ext_search")
        etSearch.addTextChangedListener { text ->
            val query = text.toString().lowercase().trim()
            val chkId = res.getIdentifier("chk_provider", "id", BuildConfig.LIBRARY_PACKAGE_NAME)
            for (i in 0 until container.childCount) {
                val item = container.getChildAt(i)
                val chk = item.findViewById<CheckBox>(chkId)
                item.visibility = if (query.isEmpty() || chk.text.toString().lowercase().contains(query)) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
        }

        etSearch.setOnEditorActionListener { _, _, _ ->
            for (i in 0 until container.childCount) {
                val item = container.getChildAt(i)
                if (item.isVisible) {
                    item.requestFocus()
                    break
                }
            }
            true
        }

        // --- Load disabled providers ---
        val savedDisabled = sharedPref.getStringSet(PREFS_DISABLED, emptySet()) ?: emptySet()

        adapter = ProviderAdapter(providers, savedDisabled) { disabled ->
            sharedPref.edit { putStringSet(PREFS_DISABLED, disabled) }
            updateUI()
            updateProviderCount()
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
            updateProviderCount()
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


        //Profile

        val btnSaveProfile = view.findView<Button>("btn_save_profile")
        val btnLoadProfile = view.findView<Button>("btn_load_profile")
        val btnDeleteProfile = view.findView<Button>("btn_delete_profile")

        btnSaveProfile.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#1E88E5"))
        btnSaveProfile.setTextColor(android.graphics.Color.WHITE)
        btnLoadProfile.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2E7D32"))
        btnLoadProfile.setTextColor(android.graphics.Color.WHITE)
        btnDeleteProfile.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#C62828"))
        btnDeleteProfile.setTextColor(android.graphics.Color.WHITE)

        btnSaveProfile.setOnClickListener {
            val input = android.widget.EditText(requireContext())

            val dialog = android.app.AlertDialog.Builder(requireContext())
                .setTitle("Save Profile")
                .setMessage("Enter a name for your profile:")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isNotEmpty()) {
                        saveProfile(name)
                        showMessage("Profile \"$name\" saved.")
                    } else {
                        showMessage("Profile name cannot be empty.")
                    }
                }
                .setNegativeButton("Cancel", null)
                .create()

            dialog.setOnShowListener {
                input.isFocusableInTouchMode = true
                input.requestFocus()
            }

            dialog.show()
        }


        btnLoadProfile.setOnClickListener {
            val profiles = getAllProfiles().keys.toTypedArray()
            if (profiles.isEmpty()) {
                showToast("No profiles saved.")
                return@setOnClickListener
            }

            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Select Profile")
                .setItems(profiles) { _, which ->
                    loadProfile(profiles[which])
                }
                .show()
        }


        btnDeleteProfile.setOnClickListener {
            val profiles = getAllProfiles().keys.toTypedArray()
            if (profiles.isEmpty()) {
                showToast("No profiles to delete.")
                return@setOnClickListener
            }
            val dialog = android.app.AlertDialog.Builder(requireContext())
                .setTitle("Delete Profile")
                .setItems(profiles) { _, which ->
                    deleteProfile(profiles[which])
                }
                .setNegativeButton("Cancel", null)
                .create()
            dialog.setOnShowListener {
                dialog.listView?.let { list ->
                    if (list.isNotEmpty()) {
                        list.getChildAt(0)?.requestFocus()
                    }
                }
            }
            dialog.show()
        }
    }

    private fun updateUI() {
        val chkId = res.getIdentifier("chk_provider", "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        for (i in 0 until container.childCount) {
            val chk = container.getChildAt(i).findViewById<CheckBox>(chkId)
            chk.isChecked = !adapter.isDisabled(providers[i].id)
        }
        updateProviderCount()
    }

    private fun dismissFragment() {
        dismiss()
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

    private fun saveProfile(name: String) {
        val disabled = sharedPref.getStringSet(PREFS_DISABLED, emptySet()) ?: emptySet()
        val allProfiles = getAllProfiles().toMutableMap()
        allProfiles[name] = disabled

        // Convert to a JSON-like string for compact storage
        val encoded = allProfiles.entries.joinToString("|") { (key, value) ->
            "$key:${value.joinToString(",")}"
        }
        sharedPref.edit { putString(PREFS_PROFILES, encoded) }
    }

    private fun getAllProfiles(): Map<String, Set<String>> {
        val encoded = sharedPref.getString(PREFS_PROFILES, "") ?: return emptyMap()
        if (encoded.isEmpty()) return emptyMap()

        return buildMap {
            encoded.split("|").forEach { entry ->
                val separatorIndex = entry.indexOf(':')
                if (separatorIndex < 0) return@forEach

                val name = entry.substring(0, separatorIndex)
                val idsPart = entry.substring(separatorIndex + 1)
                val ids = if (idsPart.isEmpty()) {
                    emptySet()
                } else {
                    idsPart.split(",").toSet()
                }
                put(name, ids)
            }
        }
    }

    private fun loadProfile(name: String) {
        val profiles = getAllProfiles()
        val disabled = profiles[name] ?: return
        sharedPref.edit { putStringSet(PREFS_DISABLED, disabled) }
        adapter = ProviderAdapter(providers, disabled) { updated ->
            sharedPref.edit { putStringSet(PREFS_DISABLED, updated) }
            updateUI()
        }
        updateUI()
        showMessage("Profile \"$name\" loaded.")
    }

    private fun deleteProfile(name: String) {
        val allProfiles = getAllProfiles().toMutableMap()
        if (allProfiles.remove(name) != null) {
            val encoded = allProfiles.entries.joinToString("|") { (key, value) ->
                "$key:${value.joinToString(",")}"
            }
            sharedPref.edit { putString(PREFS_PROFILES, encoded) }
            showMessage("Profile \"$name\" deleted.")
        } else {
            showMessage("Profile not found.")
        }
    }


    private fun showMessage(msg: String) {
        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        onDismissCallback?.invoke()
    }
}
