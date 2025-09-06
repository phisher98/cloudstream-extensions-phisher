package com.phisher98.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.phisher98.BuildConfig
import com.phisher98.TorraStreamProvider

class SettingsFragment(
    private val plugin: TorraStreamProvider,
    private val sharedPref: SharedPreferences
) : BottomSheetDialogFragment() {

    private val res: Resources = plugin.resources ?: throw Exception("Unable to read resources")

    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = res.getIdentifier(name, "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        return inflater.inflate(res.getLayout(id), container, false)
    }

    private fun getDrawable(name: String): Drawable {
        val id = res.getIdentifier(name, "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        return res.getDrawable(id, null)
    }

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        return findViewById(id)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun View?.makeTvCompatible() {
        if (this == null) return
        val outlineId = res.getIdentifier("outline", "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        if (outlineId != 0) {
            val drawable = res.getDrawable(outlineId, null)
            if (drawable != null) background = drawable
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = getLayout("settings", inflater, container)

        // ===== PROVIDERS =====
        val providerTextView = root.findView<TextView>("providers_spinner")
        val providers = listOf(
            "YTS",
            "EZTV",
            "RARBG",
            "1337x",
            "ThePirateBay",
            "KickassTorrents",
            "TorrentGalaxy",
            "MagnetDL",
            "HorribleSubs",
            "NyaaSi",
            "TokyoTosho",
            "AniDex",
            "Rutor",
            "RuTracker",
            "Comando",
            "BluDV",
            "Torrent9",
            "ilCorSaRoNeRo",
            "MejorTorrent",
            "Wolfmax4k",
            "Cinecalidad",
            "BestTorrents"
        )
        val selectedProviders = BooleanArray(providers.size)

        val updateProviderText = {
            val selected = providers.filterIndexed { index, _ -> selectedProviders[index] }
            providerTextView.text = if (selected.isEmpty()) "Select Providers" else selected.joinToString(", ")
        }
        updateProviderText()

        providerTextView.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Select Providers")
                .setMultiChoiceItems(providers.toTypedArray(), selectedProviders) { _, which, isChecked ->
                    selectedProviders[which] = isChecked
                }
                .setPositiveButton("OK") { _, _ ->
                    updateProviderText()
                    sharedPref.edit { putString("provider", providers.filterIndexed { i,_ -> selectedProviders[i] }.joinToString(",")) }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        providerTextView.makeTvCompatible()

        // ===== SORT SPINNER =====
        val sortSpinner = root.findView<Spinner>("sort_spinner")
        val sortOptions = listOf(
            "Quality",
            "Qualitysize",
            "Seeders",
            "Size"
        )
        sortSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sortOptions).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        sortSpinner.makeTvCompatible()

        // ===== LANGUAGES (Multi-select) =====
        val languageTextView = root.findView<TextView>("language_spinner")
        val languages = listOf(
            "Japanese",   // 🇯🇵 Japanese
            "Russian",    // 🇷🇺 Russian
            "Italian",    // 🇮🇹 Italian
            "Portuguese", // 🇵🇹 Portuguese
            "Spanish",    // 🇪🇸 Spanish
            "Latino",     // 🇲🇽 Latino
            "Korean",     // 🇰🇷 Korean
            "Chinese",    // 🇨🇳 Chinese
            "Taiwanese",  // 🇹🇼 Taiwanese
            "French",     // 🇫🇷 French
            "German",     // 🇩🇪 German
            "Dutch",      // 🇳🇱 Dutch
            "Hindi",      // 🇮🇳 Hindi
            "Telugu",     // 🇮🇳 Telugu
            "Tamil",      // 🇮🇳 Tamil
            "Polish",     // 🇵🇱 Polish
            "Lithuanian", // 🇱🇹 Lithuanian
            "Latvian",    // 🇱🇻 Latvian
            "Estonian",   // 🇪🇪 Estonian
            "Czech",      // 🇨🇿 Czech
            "Slovakian",  // 🇸🇰 Slovakian
            "Slovenian",  // 🇸🇮 Slovenian
            "Hungarian",  // 🇭🇺 Hungarian
            "Romanian",   // 🇷🇴 Romanian
            "Bulgarian",  // 🇧🇬 Bulgarian
            "Serbian",    // 🇷🇸 Serbian
            "Croatian",   // 🇭🇷 Croatian
            "Ukrainian",  // 🇺🇦 Ukrainian
            "Greek",      // 🇬🇷 Greek
            "Danish",     // 🇩🇰 Danish
            "Finnish",    // 🇫🇮 Finnish
            "Swedish",    // 🇸🇪 Swedish
            "Norwegian",  // 🇳🇴 Norwegian
            "Turkish",    // 🇹🇷 Turkish
            "Arabic",     // 🇸🇦 Arabic
            "Persian",    // 🇮🇷 Persian
            "Hebrew",     // 🇮🇱 Hebrew
            "Vietnamese", // 🇻🇳 Vietnamese
            "Indonesian", // 🇮🇩 Indonesian
            "Malay",      // 🇲🇾 Malay
            "Thai"        // 🇹🇭 Thai
        )
        val selectedLanguages = BooleanArray(languages.size) { false }

        val updateLanguageText = {
            val selected = languages.filterIndexed { index, _ -> selectedLanguages[index] }
            languageTextView.text = if (selected.isEmpty()) "Select Languages"
            else selected.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
        updateLanguageText()

        languageTextView.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Select Languages")
                .setMultiChoiceItems(languages.toTypedArray(), selectedLanguages) { _, which, isChecked ->
                    selectedLanguages[which] = isChecked
                }
                .setPositiveButton("OK") { _, _ ->
                    updateLanguageText()
                    // Save selected languages in shared preferences
                    sharedPref.edit {
                        putString("language", languages.filterIndexed { index, _ -> selectedLanguages[index] }.joinToString(","))
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        languageTextView.makeTvCompatible()

        // ===== QUALITY FILTER =====
        val qualityTextView = root.findView<TextView>("quality_spinner")
        val qualities = listOf(
            "Brremux",             // BluRay REMUX
            "Hdrall",              // HDR/HDR10+/Dolby Vision
            "Dolbyvision",         // Dolby Vision
            "Dolbyvisionwithhdr",  // Dolby Vision + HDR
            "Threed",              // 3D
            "Nonthreed",           // Non 3D (DO NOT SELECT IF NOT SURE)
            "4k",                  // 4k
            "1080p",               // 1080p
            "720p",                // 720p
            "480p",                // 480p
            "Other",               // Other (DVDRip/HDRip/BDRip...)
            "Scr",                 // Screener
            "Cam",                 // Cam
            "Unknown"              // Unknown
        )

        val selectedQualities = BooleanArray(qualities.size)
        val updateQualityText = {
            val selected = qualities.filterIndexed { i,_ -> selectedQualities[i] }
            qualityTextView.text = if (selected.isEmpty()) "Select Qualities" else selected.joinToString(", ")
        }
        updateQualityText()
        qualityTextView.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Select Qualities")
                .setMultiChoiceItems(qualities.toTypedArray(), selectedQualities) { _, which, isChecked ->
                    selectedQualities[which] = isChecked
                }
                .setPositiveButton("OK") { _, _ ->
                    updateQualityText()
                    sharedPref.edit { putString("qualityfilter", qualities.filterIndexed { i,_ -> selectedQualities[i] }.joinToString(",")) }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        qualityTextView.makeTvCompatible()

        // ===== LIMIT =====
        val limitInput = root.findView<EditText>("limit_input")
        limitInput.makeTvCompatible()

        // ===== SIZE =====
        val sizeInput = root.findView<EditText>("size_filter_input")
        sizeInput.makeTvCompatible()

        // ===== DEBRID PROVIDERS =====
        val debridSpinner = root.findView<Spinner>("debrid_provider_spinner")
        val debridProviders = listOf(
            "None",
            "RealDebrid",
            "Premiumize",
            "AllDebrid",
            "DebridLink",
            "EasyDebrid",
            "Offcloud",
            "TorBox",
            "Put.io",
            "AIO Streams"
        )
        debridSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, debridProviders).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        debridSpinner.makeTvCompatible()

        val debridKeyInput = root.findView<EditText>("debrid_key_input")
        debridKeyInput.makeTvCompatible()

        // ===== SAVE =====
        val saveBtn = root.findView<ImageView>("save")
        saveBtn.setImageDrawable(getDrawable("save_icon"))
        saveBtn.makeTvCompatible()
        saveBtn.setOnClickListener {
            sharedPref.edit {
                putString("provider", providers.filterIndexed { i,_ -> selectedProviders[i] }.joinToString(","))
                putString("language", languages.filterIndexed { i,_ -> selectedLanguages[i] }.joinToString(","))
                putString("qualityfilter", qualities.filterIndexed { i,_ -> selectedQualities[i] }.joinToString(","))
                putString("sort", sortSpinner.selectedItem?.toString() ?: "")
                putString("limit", limitInput.text.toString())
                putString("sizefilter", sizeInput.text.toString())
                putString("debrid_provider", debridSpinner.selectedItem?.toString() ?: "")
                putString("debrid_key", debridKeyInput.text.toString())
            }

            AlertDialog.Builder(requireContext())
                .setTitle("Restart Required")
                .setMessage("Changes have been saved. Do you want to restart the app to apply them?")
                .setPositiveButton("Yes") { _, _ ->
                    showToast("Saved and Restarting...")
                    dismiss()
                    restartApp()
                }
                .setNegativeButton("No") { dialog, _ ->
                    showToast("Saved. Restart later to apply changes.")
                    dialog.dismiss()
                    dismiss()
                }
                .show()
        }

        // ===== RESET =====
        val resetBtn = root.findView<View>("delete_img")
        resetBtn.makeTvCompatible()
        resetBtn.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Reset")
                .setMessage("This will delete all saved settings.")
                .setPositiveButton("Reset") { _, _ ->
                    sharedPref.edit { clear() }
                    selectedProviders.fill(false)
                    updateProviderText()
                    selectedLanguages.fill(false)
                    updateLanguageText()
                    selectedQualities.fill(false)
                    updateQualityText()
                    sortSpinner.setSelection(0, false)
                    debridSpinner.setSelection(0, false)
                    limitInput.text.clear()
                    sizeInput.text.clear()
                    debridKeyInput.text.clear()
                    restartApp()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        return root
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
