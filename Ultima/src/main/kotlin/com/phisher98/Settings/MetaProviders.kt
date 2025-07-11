package com.phisher98

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.phisher98.UltimaUtils.MediaProviderState
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.phisher98.BuildConfig
import kotlin.collections.toList

private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

class UltimaMetaProviders(val plugin: UltimaPlugin) : BottomSheetDialogFragment() {
    private var param1: String? = null
    private var param2: String? = null
    private var sm = UltimaStorageManager
    private var metaProviders = sm.currentMetaProviders.toList()
    private var mediaProviders = sm.currentMediaProviders.toList()
    private val res: Resources = plugin.resources ?: throw Exception("Unable to read resources")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    // #region - necessary functions
    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = res.getIdentifier(name, "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        val layout = res.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

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

    private fun View.makeTvCompatible() {
        val outlineId = res.getIdentifier("outline", "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        this.background = res.getDrawable(outlineId, null)
    }
    // #endregion - necessary functions

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val settings = getLayout("meta_providers", inflater, container)

        // #region - building save button and its click listener
        val saveBtn = settings.findView<ImageView>("save")
        saveBtn.setImageDrawable(getDrawable("save_icon"))
        saveBtn.makeTvCompatible()
        saveBtn.setOnClickListener(
                object : OnClickListener {
                    override fun onClick(btn: View) {
                        sm.currentMetaProviders = metaProviders.toTypedArray()
                        sm.currentMediaProviders = mediaProviders.toTypedArray()
                        plugin.reload(context)
                        showToast("Saved")
                        dismiss()
                    }
                }
        )
        // #endregion - building save button and its click listener

        // #region - building list of meta providers and its sections with its click listener
        val metaProvidersListLayout = settings.findView<LinearLayout>("meta_providers_list")
        metaProviders.forEach { metaProvider ->
            val extensionLayoutView = buildMetaProviderView(metaProvider, inflater, container)
            metaProvidersListLayout.addView(extensionLayoutView)
        }
        // #endregion - building list of meta providers and its sections with its click listener

        // #region - building list of media providers and its sections with its click listener
        val mediaProvidersListLayout = settings.findView<LinearLayout>("media_providers_list")
        mediaProviders.forEach { mediaProvider ->
            val extensionLayoutView = buildMediaProviderView(mediaProvider, inflater, container)
            mediaProvidersListLayout.addView(extensionLayoutView)
        }
        // #endregion - building list of media providers and its sections with its click listener

        return settings
    }

    fun buildMetaProviderView(
            metaProvider: Pair<String, Boolean>,
            inflater: LayoutInflater,
            container: ViewGroup?
    ): View {

        // collecting required resources
        val metaProviderLayoutView = getLayout("list_meta_provider_item", inflater, container)
        val metaProviderNameBtn = metaProviderLayoutView.findView<Switch>("meta_provider_name")

        // building extension textview and its click listener
        metaProviderNameBtn.text = metaProvider.first
        metaProviderNameBtn.isChecked = metaProvider.second
        metaProviderNameBtn.makeTvCompatible()
        metaProviderNameBtn.setOnClickListener(
                object : OnClickListener {
                    override fun onClick(btn: View) {
                        metaProviders =
                                metaProviders.map {
                                    if (it.first.equals(metaProvider.first))
                                            it.first to metaProviderNameBtn.isChecked
                                    else it
                                }
                    }
                }
        )

        return metaProviderLayoutView
    }

    fun buildMediaProviderView(
            mediaProvider: MediaProviderState,
            inflater: LayoutInflater,
            container: ViewGroup?
    ): View {
        val mediaProviderLayoutView = getLayout("list_media_provider_item", inflater, container)
        val providerCheckBox = mediaProviderLayoutView.findView<CheckBox>("provider")
        providerCheckBox.makeTvCompatible()

        val domainEdit = mediaProviderLayoutView.findView<ImageView>("domain_edit")
        domainEdit.setImageDrawable(getDrawable("edit_icon"))
        domainEdit.makeTvCompatible()

        providerCheckBox.text =
                mediaProvider.name + if (mediaProvider.customDomain.isNullOrBlank()) "" else "*"
        providerCheckBox.isChecked = mediaProvider.enabled
        providerCheckBox.setOnCheckedChangeListener(
                object : OnCheckedChangeListener {
                    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
                        mediaProvider.enabled = isChecked
                    }
                }
        )

        // #region - Set domain and its edit + reset buttons with listeners
        val domain = mediaProviderLayoutView.findView<TextView>("domain")
        domain.text = mediaProvider.customDomain ?: mediaProvider.getDomain()
        domainEdit.setOnClickListener(
                object : OnClickListener {
                    override fun onClick(btn: View) {
                        val editText = EditText(context)
                        editText.setText(mediaProvider.getDomain())
                        editText.setInputType(InputType.TYPE_CLASS_TEXT)

                        AlertDialog.Builder(
                                        context ?: throw Exception("Unable to build alert dialog")
                                )
                                .setTitle("Update Domain")
                                .setView(editText)
                                .setPositiveButton(
                                        "Save",
                                        object : DialogInterface.OnClickListener {
                                            override fun onClick(p0: DialogInterface, p1: Int) {
                                                mediaProvider.customDomain =
                                                        editText.text.toString()
                                                domain.text = mediaProvider.getDomain()
                                                providerCheckBox.text = mediaProvider.name + "*"
                                            }
                                        }
                                )
                                .setNegativeButton(
                                        "Reset",
                                        object : DialogInterface.OnClickListener {
                                            override fun onClick(p0: DialogInterface, p1: Int) {
                                                mediaProvider.customDomain = null
                                                domain.text = mediaProvider.getDomain()
                                                providerCheckBox.text = mediaProvider.name
                                            }
                                        }
                                )
                                .show()
                    }
                }
        )
        // #endregion - Set domain and its edit + reset buttons with listeners

        return mediaProviderLayoutView
    }

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
