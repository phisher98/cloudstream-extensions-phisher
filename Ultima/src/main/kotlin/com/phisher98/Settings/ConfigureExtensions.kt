package com.phisher98

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.api.Log

private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

class UltimaConfigureExtensions(val plugin: UltimaPlugin) : BottomSheetDialogFragment() {
    private var param1: String? = null
    private var param2: String? = null
    private val sm = UltimaStorageManager
    private val res: Resources = plugin.resources ?: throw Exception("Unable to read resources")
    private val extensions = sm.fetchExtensions().also {
        Log.d("UltimaDebug", "Fetched ${it.size} extensions.")
        it.forEach { ext ->
            Log.d("UltimaDebug", "→ Extension: ${ext.name}")
            ext.sections?.forEach { sec ->
                Log.d("UltimaDebug", " - Section: ${sec.name}, enabled=${sec.enabled}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    // #region - Utility functions
    @SuppressLint("DiscouragedApi")
    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = res.getIdentifier(name, "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        val layout = res.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun getDrawable(name: String): Drawable {
        val id = res.getIdentifier(name, "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        return res.getDrawable(id, null) ?: throw Exception("Drawable $name not found")
    }

    private fun getString(name: String): String {
        val id = res.getIdentifier(name, "string", BuildConfig.LIBRARY_PACKAGE_NAME)
        return res.getString(id)
    }

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        return this.findViewById(id)
    }

    private fun View.makeTvCompatible() {
        val outlineId = res.getIdentifier("outline", "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        this.background = res.getDrawable(outlineId, null)
    }
    // #endregion

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val settings = getLayout("configure_extensions", inflater, container)

        // Save button
        val saveBtn = settings.findView<ImageView>("save")
        saveBtn.setImageDrawable(getDrawable("save_icon"))
        saveBtn.makeTvCompatible()
        saveBtn.setOnClickListener {
            // Debug log all selected sections
            extensions.forEach { ext ->
                Log.d("UltimaDebug", "Saving Extension: ${ext.name}")
                ext.sections?.forEach { sec ->
                    Log.d("UltimaDebug", "-- Section: ${sec.name} enabled=${sec.enabled}")
                }
            }

            sm.currentExtensions = extensions
            plugin.reload(context)
            showToast("Saved")
            dismiss()
        }

        // Toggle switch: Show extension name on home
        val extNameOnHomeBtn = settings.findView<Switch>("ext_name_on_home_toggle")
        extNameOnHomeBtn.makeTvCompatible()
        extNameOnHomeBtn.isChecked = sm.extNameOnHome
        extNameOnHomeBtn.setOnClickListener {
            sm.extNameOnHome = extNameOnHomeBtn.isChecked
        }

        // Extensions list
        val extensionsListLayout = settings.findView<LinearLayout>("extensions_list")
        extensions.forEach { extension ->
            val extensionLayoutView = buildExtensionView(extension, inflater, container)
            extensionsListLayout.addView(extensionLayoutView)
        }

        return settings
    }

    // Create one full view for each extension
    private fun buildExtensionView(
        extension: UltimaUtils.ExtensionInfo,
        inflater: LayoutInflater,
        container: ViewGroup?
    ): View {

        fun buildSectionView(
            section: UltimaUtils.SectionInfo,
            inflater: LayoutInflater,
            container: ViewGroup?
        ): View {
            val sectionView = getLayout("list_section_item", inflater, container)
            val checkBox = sectionView.findView<CheckBox>("section_checkbox")
            checkBox.text = section.name
            checkBox.makeTvCompatible()

            // Auto-enable sections by default if not already toggled
            if (section.enabled == null) section.enabled = true

            checkBox.isChecked = section.enabled == true
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                section.enabled = isChecked
                Log.d("UltimaDebug", "Section '${section.name}' in '${extension.name}' set to $isChecked")
            }

            return sectionView
        }

        val extView = getLayout("list_extension_item", inflater, container)
        val extensionDataBtn = extView.findView<LinearLayout>("extension_data")
        val expandImage = extView.findView<ImageView>("expand_icon")
        val extensionNameBtn = extensionDataBtn.findView<TextView>("extension_name")
        val childList = extView.findView<LinearLayout>("sections_list")

        expandImage.setImageDrawable(getDrawable("triangle"))
        expandImage.rotation = 90f

        extensionNameBtn.text = extension.name
        extensionDataBtn.makeTvCompatible()
        extensionDataBtn.setOnClickListener {
            val isVisible = childList.isVisible
            childList.visibility = if (isVisible) View.GONE else View.VISIBLE
            expandImage.rotation = if (isVisible) 90f else 180f
        }

        // Add sections
        extension.sections?.forEach { section ->
            val sectionView = buildSectionView(section, inflater, container)
            childList.addView(sectionView)
        }

        return extView
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {}

    override fun onDetach() {
        val settings = UltimaSettings(plugin)
        settings.show(
            activity?.supportFragmentManager ?: throw Exception("Unable to open configure settings"),
            ""
        )
        super.onDetach()
    }
}
