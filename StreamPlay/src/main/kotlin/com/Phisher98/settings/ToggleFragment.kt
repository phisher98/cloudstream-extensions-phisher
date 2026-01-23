package com.phisher98.settings

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import androidx.core.content.edit
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.phisher98.BuildConfig
import com.phisher98.StreamPlay
import com.phisher98.StreamPlayAnime
import com.phisher98.StreamPlayPlugin
import com.phisher98.StreamPlayStremioCatelog

class ToggleFragment(
    plugin: StreamPlayPlugin,
    private val sharedPref: SharedPreferences
) : BottomSheetDialogFragment() {

    private val res: Resources = plugin.resources ?: throw Exception("Unable to access plugin resources")

    @SuppressLint("DiscouragedApi")
    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = res.getIdentifier(name, "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        if (id == 0) throw Exception("Layout $name not found.")
        val layout = res.getLayout(id)
        return inflater.inflate(layout, container, false)
    }


    @SuppressLint("UseCompatLoadingForDrawables")
    private fun getDrawable(name: String): Drawable {
        val id = res.getIdentifier(name, "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        return res.getDrawable(id, null)
            ?: throw Resources.NotFoundException("Drawable $name not found.")
    }

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        if (id == 0) throw Resources.NotFoundException("View ID $name not found.")
        return this.findViewById(id)
    }

    private fun View.makeTvCompatible() {
        val outlineId = res.getIdentifier("outline", "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        this.background = res.getDrawable(outlineId, null)
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = getLayout("fragment_toggle_extensions", inflater, container)
        val extensionList = root.findView<LinearLayout>("toggle_list_container")

        val apis = listOf(
            StreamPlay(sharedPref),
            //StreamPlayLite(),
            //StreamPlayTorrent(),
            StreamPlayAnime(),
            StreamPlayStremioCatelog("", "", sharedPref)
            //StreamplayTorrentAnime()
        )

        val savedKey = "enabled_plugins_saved"
        val savedSet = sharedPref.getStringSet(savedKey, null)
        val defaultEnabled = apis.map { it.name }.toSet()
        val currentSet = savedSet?.toSet() ?: defaultEnabled


        for (api in apis) {
            val toggleItem = getLayout("list_toggle_item", inflater, container)
            val toggleSwitch = toggleItem.findView<Switch>("toggle_item")
            toggleItem.makeTvCompatible()
            toggleSwitch.text = api.name
            toggleSwitch.isChecked = currentSet.contains(api.name)

            toggleSwitch.makeTvCompatible()

            toggleItem.setOnClickListener {
                toggleSwitch.isChecked = !toggleSwitch.isChecked
            }

            extensionList.addView(toggleItem)
        }


        val saveBtn = root.findView<ImageView>("saveIcon")
        saveBtn.setImageDrawable(getDrawable("save_icon"))
        saveBtn.makeTvCompatible()

        saveBtn.setOnClickListener {
            val enabledPluginNames = mutableListOf<String>()

            for (i in 0 until extensionList.childCount) {
                val toggleItem = extensionList.getChildAt(i)
                val toggleSwitch = toggleItem.findViewById<Switch>(
                    res.getIdentifier("toggle_item", "id", BuildConfig.LIBRARY_PACKAGE_NAME)
                )
                if (toggleSwitch.isChecked) {
                    enabledPluginNames.add(toggleSwitch.text.toString())
                }
            }

            if (enabledPluginNames.isEmpty()) {
                showToast("At least one extension must stay enabled")
                return@setOnClickListener
            }

            sharedPref.edit {
                putStringSet(savedKey, enabledPluginNames.toSet())
                commit()
            }

            showToast("Settings saved")
            dismiss()
        }
        return root
    }
}
