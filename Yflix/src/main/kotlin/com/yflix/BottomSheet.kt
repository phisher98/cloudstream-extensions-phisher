package com.yflix

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.phisher98.BuildConfig

class BottomFragment(private val plugin: YflixPlugin) : BottomSheetDialogFragment() {

    @SuppressLint("UseCompatLoadingForDrawables", "SetTextI18n", "DiscouragedApi")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val id = plugin.resources!!.getIdentifier(
            "bottom_sheet_layout",
            "layout",
            BuildConfig.LIBRARY_PACKAGE_NAME
        )
        val layout = plugin.resources!!.getLayout(id)
        val view = inflater.inflate(layout, container, false)

        val outlineId = plugin.resources!!.getIdentifier(
            "outline",
            "drawable",
            BuildConfig.LIBRARY_PACKAGE_NAME
        )

        // Save button
        val saveIconId = plugin.resources!!.getIdentifier(
            "save_icon",
            "drawable",
            BuildConfig.LIBRARY_PACKAGE_NAME
        )
        val saveBtn = view.findView<ImageView>("save")
        saveBtn.setImageDrawable(plugin.resources!!.getDrawable(saveIconId, null))
        saveBtn.background = plugin.resources!!.getDrawable(outlineId, null)
        saveBtn.setOnClickListener {
            context?.let { ctx ->
                AlertDialog.Builder(ctx)
                    .setTitle("Restart App?")
                    .setMessage("Save changes and restart the app?")
                    .setPositiveButton("Yes") { _, _ ->
                        restartApp(ctx)
                    }
                    .setNegativeButton("No") { dialog, _ ->
                        dialog.dismiss()
                        Toast.makeText(ctx, "Changes saved", Toast.LENGTH_SHORT).show()
                        dismiss()
                    }
                    .show()
            }
        }

        // Server selection radio buttons
        val serverGroup = view.findView<RadioGroup>("server_group")
        val radioBtnId = plugin.resources!!.getIdentifier(
            "radio_button",
            "layout",
            BuildConfig.LIBRARY_PACKAGE_NAME
        )

        ServerList.entries.forEach { server ->
            val radioBtnLayout = plugin.resources!!.getLayout(radioBtnId)
            val radioBtnView = inflater.inflate(radioBtnLayout, container, false)
            val radioBtn = radioBtnView.findView<RadioButton>("radio_button")

            val name = YflixPlugin.getServerName(server.link.first)
            val url = server.link.first

            radioBtn.text = "$name [$url]"

            radioBtn.isEnabled = server.link.second

            val newId = View.generateViewId()
            radioBtn.id = newId
            radioBtn.background = plugin.resources!!.getDrawable(outlineId, null)

            radioBtn.setOnClickListener {
                YflixPlugin.currentYflixServer = server.link.first
                serverGroup.check(newId)
            }

            serverGroup.addView(radioBtnView)

            if (YflixPlugin.currentYflixServer == server.link.first) {
                serverGroup.check(newId)
            }
        }


        return view
    }

    private fun <T : View> View.findView(name: String): T {
        val id = plugin.resources!!.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        return findViewById(id)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
        return dialog
    }

    private fun restartApp(context: Context) {
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
