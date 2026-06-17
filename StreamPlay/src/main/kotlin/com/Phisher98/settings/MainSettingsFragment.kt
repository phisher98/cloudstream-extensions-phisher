package com.phisher98

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.phisher98.settings.SettingsFragment
import com.phisher98.settings.ToggleFragment
import com.phisher98.settings.WyzieSettingsFragment

class MainSettingsFragment(
    private val plugin: StreamPlayPlugin,
    private val sharedPref: android.content.SharedPreferences
) : DialogFragment() {

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

    private val res = plugin.resources ?: throw Exception("Unable to access plugin resources")

    private fun getDrawable(name: String): Drawable {
        val id = res.getIdentifier(name, "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        return res.getDrawable(id, null) ?: throw Exception("Drawable $name not found")
    }

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        if (id == 0) throw Exception("View ID $name not found.")
        return this.findViewById(id)
    }

    private fun View.makeTvCompatible() {
        val outlineId = res.getIdentifier("outline", "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        this.background = res.getDrawable(outlineId, null)
    }

    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = res.getIdentifier(name, "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        val layout = res.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = getLayout("fragment_main_settings", inflater, container)
        val drawableId = res.getIdentifier("dialog_background", "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        if (drawableId != 0) {
            view.background = res.getDrawable(drawableId, null)
        }

        val loginCard: ImageView = view.findView("loginCard")
        val wyzieCard: ImageView = view.findView("wyzieCard")
        val featureCard: ImageView = view.findView("featureCard")
        val toggleproviders: ImageView = view.findView("toggleproviders")
        val languagechange: ImageView = view.findView("languageCard")
        val stremioaddon: ImageView = view.findView("stremioaddons")
        val stremioaddonstreams: ImageView = view.findView("stremioaddonstreams")
        val performance: ImageView = view.findView("performance")

        val loginRow: View = view.findView("loginRow")
        val wyzieRow: View = view.findView("wyzieRow")
        val featureRow: View = view.findView("featureRow")
        val toggleprovidersRow: View = view.findView("toggleprovidersRow")
        val languageRow: View = view.findView("languageRow")
        val stremioaddonsRow: View = view.findView("stremioaddonsRow")
        val stremioaddonstreamsRow: View = view.findView("stremioaddonstreamsRow")
        val performanceRow: View = view.findView("performanceRow")

        val saveIcon: ImageView = view.findView("saveIcon")

        val sourceToggleRow: View = view.findView("sourceToggleRow")
        val sourceToggleTitle: TextView = view.findView("sourceToggleTitle")
        val sourceToggleIcon: ImageView = view.findView("sourceToggleIcon")

        loginCard.setImageDrawable(getDrawable("settings_icon"))
        wyzieCard.setImageDrawable(getDrawable("settings_icon"))
        languagechange.setImageDrawable(getDrawable("settings_icon"))
        featureCard.setImageDrawable(getDrawable("settings_icon"))
        toggleproviders.setImageDrawable(getDrawable("settings_icon"))
        stremioaddon.setImageDrawable(getDrawable("settings_icon"))
        stremioaddonstreams.setImageDrawable(getDrawable("settings_icon"))
        performance.setImageDrawable(getDrawable("settings_icon"))
        saveIcon.setImageDrawable(getDrawable("save_icon"))
        sourceToggleIcon.setImageDrawable(getDrawable("settings_icon"))

        loginRow.background = getDrawable("settings_item_background")
        wyzieRow.background = getDrawable("settings_item_background")
        featureRow.background = getDrawable("settings_item_background")
        toggleprovidersRow.background = getDrawable("settings_item_background")
        languageRow.background = getDrawable("settings_item_background")
        stremioaddonsRow.background = getDrawable("settings_item_background")
        stremioaddonstreamsRow.background = getDrawable("settings_item_background")
        performanceRow.background = getDrawable("settings_item_background")
        sourceToggleRow.background = getDrawable("settings_item_background")

        val isTrakt = sharedPref.getBoolean("use_trakt_source", false)
        sourceToggleTitle.text = if (isTrakt) "Source: Trakt" else "Source: TMDB"

        sourceToggleRow.setOnClickListener {
            val newIsTrakt = !sharedPref.getBoolean("use_trakt_source", false)
            sharedPref.edit { putBoolean("use_trakt_source", newIsTrakt) }
            sourceToggleTitle.text = if (newIsTrakt) "Source: Trakt" else "Source: TMDB"
            showToast("Source changed to ${if (newIsTrakt) "Trakt" else "TMDB"}. Save & Restart to apply.")
        }

        saveIcon.makeTvCompatible()
        val showSubFragment = { fragmentCreator: (() -> Unit) -> DialogFragment, tag: String ->
            val fm = activity?.supportFragmentManager
            if (fm != null) {
                dismiss()
                val subFragment = fragmentCreator {
                    val mainSettings = MainSettingsFragment(plugin, sharedPref)
                    mainSettings.show(fm, "main_settings")
                }
                subFragment.show(fm, tag)
            }
        }

        loginRow.setOnClickListener {
            showSubFragment({ cb -> SettingsFragment(plugin, sharedPref, cb) }, "settings_fragment")
        }

        wyzieRow.setOnClickListener {
            showSubFragment({ cb -> WyzieSettingsFragment(plugin, sharedPref, cb) }, "wyzie_settings_fragment")
        }

        featureRow.setOnClickListener {
            showSubFragment({ cb -> ToggleFragment(plugin, sharedPref, cb) }, "fragment_toggle_extensions")
        }

        toggleprovidersRow.setOnClickListener {
            showSubFragment({ cb -> ProvidersFragment(plugin, sharedPref, cb) }, "fragment_toggle_providers")
        }

        languageRow.setOnClickListener {
            showSubFragment({ cb -> LanguageSelectFragment(plugin, sharedPref, cb) }, "fragment_language_list")
        }

        stremioaddonsRow.setOnClickListener {
            showSubFragment({ cb -> StreamPlayStremioCatelogFrag(plugin, sharedPref, cb) }, "stremio_bottom_sheet_layout")
        }

        stremioaddonstreamsRow.setOnClickListener {
            showSubFragment({ cb -> StreamPlayStremioAddonFrag(plugin, sharedPref, cb) }, "streamplay_stremio_addon_bottom_sheet_layout")
        }

        performanceRow.setOnClickListener {
            showSubFragment({ cb -> ConcurrencyBottomSheet(plugin, sharedPref, cb) }, "concurrency_bottom_sheet")
        }

        saveIcon.setOnClickListener {
            val context = this.context ?: return@setOnClickListener

            AlertDialog.Builder(context)
                .setTitle("Save & Reload")
                .setMessage("Changes have been saved. Do you want to restart the app to apply them?")
                .setPositiveButton("Yes") { _, _ ->
                    dismiss()
                    restartApp()
                }
                .setNegativeButton("No", null)
                .show()
        }
        return view
    }

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
