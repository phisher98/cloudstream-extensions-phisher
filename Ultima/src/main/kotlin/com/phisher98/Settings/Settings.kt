package com.phisher98

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.api.Log
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.plugins.PluginManager.loadSinglePlugin
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.phisher98.BuildConfig
import kotlinx.coroutines.launch

private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

class UltimaSettings(val plugin: UltimaPlugin) : BottomSheetDialogFragment() {
    private var param1: String? = null
    private var param2: String? = null
    private val sm = UltimaStorageManager
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

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val settings = getLayout("settings", inflater, container)

        // #region - building save button and its click listener
        val saveBtn = settings.findView<ImageView>("save")
        saveBtn.setImageDrawable(getDrawable("save_icon"))
        saveBtn.makeTvCompatible()
        saveBtn.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Restart Required")
                .setMessage("Changes have been saved. Do you want to restart the app to apply them ?")
                .setPositiveButton("Yes") { _, _ ->
                    plugin.reload(requireContext())
                    showToast("Saved and Restarting...")
                    dismiss()
                    restartApp()
                }
                .setNegativeButton("No") { dialog, _ ->
                    showToast("Saved. Restart later to apply changes.")
                    dialog.dismiss()
                    dismiss()
                }.show()
        }
        // #endregion - building save button and its click listener

        // #region - building meta providers button and its click listener
        val metaProvidersBtn = settings.findView<ImageView>("meta_providers_img")
        metaProvidersBtn.setImageDrawable(getDrawable("edit_icon"))
        metaProvidersBtn.makeTvCompatible()
        metaProvidersBtn.setOnClickListener {
            val configure = UltimaMetaProviders(plugin)
            configure.show(
                activity?.supportFragmentManager
                    ?: throw Exception(
                        "Unable to open meta providers settings"
                    ),
                ""
            )
            dismiss()
        }
        // #endregion - building meta providers button and its click listener

        // #region - building config extensions button and its click listener
        val configBtn = settings.findView<ImageView>("config_img")
        configBtn.setImageDrawable(getDrawable("edit_icon"))
        configBtn.makeTvCompatible()
        configBtn.setOnClickListener {
            val configure = UltimaConfigureExtensions(plugin)
            configure.show(
                activity?.supportFragmentManager
                    ?: throw Exception(
                        "Unable to open configure extensions settings"
                    ),
                ""
            )
            dismiss()
        }
        // #endregion - building config extensions button and its click listener

        // #region - building reorder button and its click listener
        val reorderBtn = settings.findView<ImageView>("reorder_img")
        reorderBtn.setImageDrawable(getDrawable("edit_icon"))
        reorderBtn.makeTvCompatible()
        reorderBtn.setOnClickListener {
            val reorder = UltimaReorder(plugin)
            reorder.show(
                activity?.supportFragmentManager
                    ?: throw Exception("Unable to open reorder settings"),
                ""
            )
            dismiss()
        }
        // #endregion - building reorder button and its click listener

        // #region - building reorder button and its click listener
        val watchSyncBtn = settings.findView<ImageView>("watch_sync_img")
        watchSyncBtn.setImageDrawable(getDrawable("edit_icon"))
        watchSyncBtn.makeTvCompatible()
        watchSyncBtn.setOnClickListener {
            val reorder = UltimaConfigureWatchSync(plugin)
            reorder.show(
                activity?.supportFragmentManager
                    ?: throw Exception("Unable to open reorder settings"),
                ""
            )
            dismiss()
        }
        // #endregion - building reorder button and its click listener

        // #region - building delete button with its click listener
        val deleteBtn = settings.findView<TextView>("delete_img")

        deleteBtn.text = "Reset"
        deleteBtn.makeTvCompatible()

        deleteBtn.setOnClickListener {
            AlertDialog.Builder(
                context ?: throw Exception("Unable to build alert dialog")
            )
                .setTitle("Reset Ultima")
                .setMessage("This will delete all selected sections.")
                .setPositiveButton("Reset") { _, _ ->
                    sm.deleteAllData()
                    plugin.reload(context)
                    showToast("Sections cleared")
                    dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
                .setDefaultFocus()
        }
        // #endregion - building delete button with its click listener

        return settings
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {}


    private fun restartApp() {
        val context = requireContext().applicationContext
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component

        if (componentName != null) {
            val restartIntent = Intent.makeRestartActivityTask(componentName)
            context.startActivity(restartIntent)
            Runtime.getRuntime().exit(0)
        }
    }
}
