package com.phisher98

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.phisher98.*
import androidx.core.view.isVisible

private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

class UltimaConfigureExtensions(val plugin: UltimaPlugin) : BottomSheetDialogFragment() {
    private var param1: String? = null
    private var param2: String? = null
    private val sm = UltimaStorageManager
    private val extensions = sm.fetchExtensions()
    private val res: Resources = plugin.resources ?: throw Exception("Unable to read resources")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    // #region - necessary functions
    @SuppressLint("DiscouragedApi")
    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = res.getIdentifier(name, "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        val layout = res.getLayout(id)
        return inflater.inflate(layout, container, false)
    }




    @SuppressLint("UseCompatLoadingForDrawables")
    private fun getDrawable(name: String): Drawable {
        val id = res.getIdentifier(name, "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        return res.getDrawable(id, null) ?: throw Exception("Unable to find drawable $name")
    }

    private fun getString(name: String): String {
        val id = res.getIdentifier(name, "string", BuildConfig.LIBRARY_PACKAGE_NAME)
        return res.getString(id)
    }

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        return this.findViewById(id)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun View.makeTvCompatible() {
        val outlineId = res.getIdentifier("outline", "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        this.background = res.getDrawable(outlineId, null)
    }
    // #endregion - necessary functions

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val settings = getLayout("configure_extensions", inflater, container)

        // #region - building save button and its click listener
        val saveBtn = settings.findView<ImageView>("save")
        saveBtn.setImageDrawable(getDrawable("save_icon"))
        saveBtn.makeTvCompatible()
        saveBtn.setOnClickListener {
            plugin.reload(context)
            sm.currentExtensions = extensions
            plugin.reload(context)
            showToast("Saved")
            dismiss()
        }
        // #endregion - building save button and its click listener

        // #region - building toggle for extension_name_on_home and its click listener
        val extNameOnHomeBtn = settings.findView<Switch>("ext_name_on_home_toggle")
        extNameOnHomeBtn.makeTvCompatible()
        extNameOnHomeBtn.isChecked = sm.extNameOnHome
        extNameOnHomeBtn.setOnClickListener { sm.extNameOnHome = extNameOnHomeBtn.isChecked }
        // #endregion - building toggle for extension_name_on_home and its click listener

        // #region - building list of extensions and its sections with its click listener
        val extensionsListLayout = settings.findView<LinearLayout>("extensions_list")
        extensions.forEach { extension ->
            val extensionLayoutView = buildExtensionView(extension, inflater, container)
            extensionsListLayout.addView(extensionLayoutView)
        }
        // #endregion - building list of extensions and its sections with its click listener

        return settings
    }

    // #region - functions which lists extensions and its sections with counters
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

            // collecting required resources
            val sectionView = getLayout("list_section_item", inflater, container)
            val childCheckBoxBtn = sectionView.findView<CheckBox>("section_checkbox")

            // building section checkbox and its click listener
            childCheckBoxBtn.text = section.name
            childCheckBoxBtn.makeTvCompatible()
            childCheckBoxBtn.isChecked = section.enabled
            childCheckBoxBtn.setOnCheckedChangeListener { buttonView, isChecked ->
                section.enabled = isChecked
            }

            return sectionView
        }

        // collecting required resources
        val extensionLayoutView = getLayout("list_extension_item", inflater, container)
        val extensionDataBtn = extensionLayoutView.findView<LinearLayout>("extension_data")
        val expandImage = extensionLayoutView.findView<ImageView>("expand_icon")
        expandImage.setImageDrawable(getDrawable("triangle"))
        val extensionNameBtn = extensionDataBtn.findView<TextView>("extension_name")
        val childList = extensionLayoutView.findView<LinearLayout>("sections_list")

        // building extension textview and its click listener
        expandImage.rotation = 90f
        extensionNameBtn.text = extension.name
        extensionDataBtn.makeTvCompatible()

        extensionDataBtn.setOnClickListener(
                object : OnClickListener {
                    override fun onClick(btn: View) {
                        if (childList.isVisible) {
                            childList.visibility = View.GONE
                            expandImage.rotation = 90f
                        } else {
                            childList.visibility = View.VISIBLE
                            expandImage.rotation = 180f
                        }
                    }
                }
        )

        // building list of sections of current extnesion with its click listener
        extension.sections?.forEach { section ->
            val newSectionView = buildSectionView(section, inflater, container)
            childList.addView(newSectionView)
        }
        return extensionLayoutView
    }
    // #endregion - functions which lists extensions and its sections with counters

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {}

    override fun onDetach() {
        val settings = UltimaSettings(plugin)
        settings.show(
                activity?.supportFragmentManager
                        ?: throw Exception("Unable to open configure settings"),
                ""
        )
        super.onDetach()
    }
}
