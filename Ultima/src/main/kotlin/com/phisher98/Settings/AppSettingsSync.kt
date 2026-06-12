package com.phisher98

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.coroutineScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class UltimaConfigureAppSettingsSync(private val plugin: UltimaPlugin) : BottomSheetDialogFragment() {
    private val sm = UltimaStorageManager
    private val res: Resources = plugin.resources ?: throw Exception("Unable to read resources")
    private val packageName = BuildConfig.LIBRARY_PACKAGE_NAME

    // #region - necessary functions
    @SuppressLint("DiscouragedApi")
    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = res.getIdentifier(name, "layout", packageName)
        val layout = res.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun getDrawable(name: String): Drawable {
        val id = res.getIdentifier(name, "drawable", packageName)
        return res.getDrawable(id, null) ?: throw Exception("Unable to find drawable $name")
    }

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", packageName)
        return this.findViewById(id)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun View.makeTvCompatible() {
        val outlineId = res.getIdentifier("outline", "drawable", packageName)
        this.background = res.getDrawable(outlineId, null)
    }
    // #endregion

    @SuppressLint("UseSwitchCompatOrMaterialCode", "SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val settings = getLayout("app_settings_sync", inflater, container)
        val context = requireContext()

        // #region - save button
        settings.findView<ImageView>("save").apply {
            setImageDrawable(getDrawable("save_icon"))
            makeTvCompatible()
            setOnClickListener {
                dismiss()
            }
        }
        // #endregion

        // #region - credentials button
        settings.findView<ImageView>("app_settings_sync_creds_btn").apply {
            setImageDrawable(getDrawable("edit_icon"))
            makeTvCompatible()
            setOnClickListener {
                val credsView = getLayout("app_settings_sync_creds", inflater, container)
                val deviceNameInput = credsView.findView<EditText>("device_name")
                val syncKeyInput = credsView.findView<EditText>("sync_key")
                val customDbSwitch = credsView.findView<Switch>("custom_db_switch")
                val customDbSection = credsView.findView<LinearLayout>("custom_db_section")
        
                credsView.findView<EditText>("firebase_rules_snippet").apply {
                    setOnClickListener {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Firebase Rules", this.text.toString())
                        clipboard.setPrimaryClip(clip)
                        showToast("Copied Firebase Rules to clipboard")
                    }
                }
                val firebaseUrlInput = credsView.findView<EditText>("firebase_url")
                val generateKeyBtn = credsView.findView<Button>("generate_key_btn")

                val currentCreds = sm.appSettingsSyncCreds ?: AppSettingsSyncCreds()

                deviceNameInput.setText(currentCreds.deviceName ?: Build.MODEL)
                syncKeyInput.setText(currentCreds.syncKey)
                customDbSwitch.isChecked = currentCreds.useCustomDatabase
                customDbSection.visibility = if (currentCreds.useCustomDatabase) View.VISIBLE else View.GONE
                firebaseUrlInput.setText(currentCreds.firebaseUrl)

                customDbSwitch.setOnCheckedChangeListener { _, isChecked ->
                    customDbSection.visibility = if (isChecked) View.VISIBLE else View.GONE
                }

                generateKeyBtn.setOnClickListener {
                    val randomKey = UUID.randomUUID().toString().replace("-", "").take(12)
                    syncKeyInput.setText(randomKey)
                }

                AlertDialog.Builder(requireContext())
                    .setTitle("Configure App Sync")
                    .setView(credsView)
                    .setPositiveButton("Save") { _, _ ->
                        val devName = deviceNameInput.text.toString().trim()
                        val key = syncKeyInput.text.toString().trim()
                        val useCustom = customDbSwitch.isChecked
                        val fbUrl = firebaseUrlInput.text.toString().trim()

                        if (key.isEmpty()) {
                            showToast("Sync Key cannot be empty")
                            return@setPositiveButton
                        }

                        val deviceId = currentCreds.deviceId ?: UltimaSettingsSyncUtils.getDeviceId(packageName, context)

                        val newCreds = currentCreds.copy(
                            useCustomDatabase = useCustom,
                            firebaseUrl = if (fbUrl.isEmpty()) null else fbUrl,
                            syncKey = key,
                            deviceName = if (devName.isEmpty()) Build.MODEL else devName,
                            deviceId = deviceId
                        )

                        sm.appSettingsSyncCreds = newCreds

                        activity?.lifecycle?.coroutineScope?.launch {
                            showToast("Credentials saved. Performing initial sync...")
                            UltimaSettingsSyncUtils.registerDevice()
                            plugin.mergeAndSyncAllCategories(context)
                            showToast("Initial sync complete!")
                            refreshDevicesList(settings, inflater, container)
                            updateLastSyncInfo(settings)
                        }
                    }
                    .setNegativeButton("Reset") { _, _ ->
                        activity?.lifecycle?.coroutineScope?.launch {
                            val deleteRes = UltimaSettingsSyncUtils.deregisterThisDevice()
                            sm.appSettingsSyncCreds = null
                            showToast("Sync credentials removed: ${deleteRes.second ?: "Reset successful"}")
                            refreshDevicesList(settings, inflater, container)
                            updateLastSyncInfo(settings)
                        }
                    }
                    .show()
                    .setDefaultFocus()
            }
        }
        // #endregion

        // #region - backup device switch
        settings.findView<Switch>("backup_device").apply {
            val currentCreds = sm.appSettingsSyncCreds
            isChecked = currentCreds?.backupDevice ?: false
            setOnCheckedChangeListener { _, checked ->
                val creds = sm.appSettingsSyncCreds
                if (creds != null) {
                    creds.backupDevice = checked
                    sm.appSettingsSyncCreds = creds
                    if (checked) {
                        activity?.lifecycle?.coroutineScope?.launch {
                            showToast("Syncing all categories...")
                            plugin.mergeAndSyncAllCategories(context)
                            showToast("Sync complete!")
                            updateLastSyncInfo(settings)
                        }
                    }
                } else if (checked) {
                    showToast("Configure credentials first")
                    this.isChecked = false
                }
            }
        }
        // #endregion

        // #region - restore device switch
        settings.findView<Switch>("restore_device").apply {
            val currentCreds = sm.appSettingsSyncCreds
            isChecked = currentCreds?.restoreDevice ?: false
            setOnCheckedChangeListener { _, checked ->
                val creds = sm.appSettingsSyncCreds
                if (creds != null) {
                    creds.restoreDevice = checked
                    sm.appSettingsSyncCreds = creds
                    if (checked) {
                        activity?.lifecycle?.coroutineScope?.launch {
                            showToast("Syncing all categories...")
                            plugin.mergeAndSyncAllCategories(context)
                            showToast("Sync complete!")
                            updateLastSyncInfo(settings)
                        }
                    }
                } else if (checked) {
                    showToast("Configure credentials first")
                    this.isChecked = false
                }
            }
        }
        // #endregion

        // #region - sync now button
        settings.findView<Button>("sync_now_btn").apply {
            setOnClickListener {
                val creds = sm.appSettingsSyncCreds
                if (creds == null || !creds.isLoggedIn()) {
                    showToast("Configure credentials first")
                    return@setOnClickListener
                }
                activity?.lifecycle?.coroutineScope?.launch {
                    try {
                        showToast("Force syncing all categories...")

                        plugin.mergeAndSyncAllCategories(context)

                        showToast("Force sync complete!")
                        updateLastSyncInfo(settings)
                    } catch (e: Exception) {
                        showToast("Sync failed: ${e.message}")
                    }
                }
            }
        }
        // #endregion

        // #region - granular sync category checkboxes
        val backupExtensionsCb = settings.findView<CheckBox>("backup_extensions")
        val restoreExtensionsCb = settings.findView<CheckBox>("restore_extensions")
        
        val backupBookmarksCb = settings.findView<CheckBox>("backup_bookmarks")
        val restoreBookmarksCb = settings.findView<CheckBox>("restore_bookmarks")
        
        val backupResumeWatchingCb = settings.findView<CheckBox>("backup_resume_watching")
        val restoreResumeWatchingCb = settings.findView<CheckBox>("restore_resume_watching")
        
        val backupSearchHistoryCb = settings.findView<CheckBox>("backup_search_history")
        val restoreSearchHistoryCb = settings.findView<CheckBox>("restore_search_history")
        
        val backupPlayerCb = settings.findView<CheckBox>("backup_player")
        val restorePlayerCb = settings.findView<CheckBox>("restore_player")
        
        val backupSubtitlesCb = settings.findView<CheckBox>("backup_subtitles")
        val restoreSubtitlesCb = settings.findView<CheckBox>("restore_subtitles")
        
        val backupThemeCb = settings.findView<CheckBox>("backup_theme")
        val restoreThemeCb = settings.findView<CheckBox>("restore_theme")
        
        val backupLayoutCb = settings.findView<CheckBox>("backup_layout")
        val restoreLayoutCb = settings.findView<CheckBox>("restore_layout")
        
        val backupDownloadsCb = settings.findView<CheckBox>("backup_downloads")
        val restoreDownloadsCb = settings.findView<CheckBox>("restore_downloads")
        
        val backupGeneralCb = settings.findView<CheckBox>("backup_general")
        val restoreGeneralCb = settings.findView<CheckBox>("restore_general")

        listOf(
            backupExtensionsCb, restoreExtensionsCb,
            backupBookmarksCb, restoreBookmarksCb,
            backupResumeWatchingCb, restoreResumeWatchingCb,
            backupSearchHistoryCb, restoreSearchHistoryCb,
            backupPlayerCb, restorePlayerCb,
            backupSubtitlesCb, restoreSubtitlesCb,
            backupThemeCb, restoreThemeCb,
            backupLayoutCb, restoreLayoutCb,
            backupDownloadsCb, restoreDownloadsCb,
            backupGeneralCb, restoreGeneralCb
        ).forEach { it.makeTvCompatible() }

        val currentCreds = sm.appSettingsSyncCreds ?: AppSettingsSyncCreds()

        backupExtensionsCb.isChecked = currentCreds.backupExtensions
        restoreExtensionsCb.isChecked = currentCreds.restoreExtensions
        
        backupBookmarksCb.isChecked = currentCreds.backupBookmarks
        restoreBookmarksCb.isChecked = currentCreds.restoreBookmarks
        
        backupResumeWatchingCb.isChecked = currentCreds.backupResumeWatching
        restoreResumeWatchingCb.isChecked = currentCreds.restoreResumeWatching
        
        backupSearchHistoryCb.isChecked = currentCreds.backupSearchHistory
        restoreSearchHistoryCb.isChecked = currentCreds.restoreSearchHistory
        
        backupPlayerCb.isChecked = currentCreds.backupPlayer
        restorePlayerCb.isChecked = currentCreds.restorePlayer
        
        backupSubtitlesCb.isChecked = currentCreds.backupSubtitles
        restoreSubtitlesCb.isChecked = currentCreds.restoreSubtitles
        
        backupThemeCb.isChecked = currentCreds.backupTheme
        restoreThemeCb.isChecked = currentCreds.restoreTheme
        
        backupLayoutCb.isChecked = currentCreds.backupLayout
        restoreLayoutCb.isChecked = currentCreds.restoreLayout
        
        backupDownloadsCb.isChecked = currentCreds.backupDownloads
        restoreDownloadsCb.isChecked = currentCreds.restoreDownloads
        
        backupGeneralCb.isChecked = currentCreds.backupGeneral
        restoreGeneralCb.isChecked = currentCreds.restoreGeneral

        val checkboxListener = CompoundButton.OnCheckedChangeListener { _, _ ->
            val creds = sm.appSettingsSyncCreds ?: AppSettingsSyncCreds()

            creds.backupExtensions = backupExtensionsCb.isChecked
            creds.restoreExtensions = restoreExtensionsCb.isChecked
            
            creds.backupBookmarks = backupBookmarksCb.isChecked
            creds.restoreBookmarks = restoreBookmarksCb.isChecked
            
            creds.backupResumeWatching = backupResumeWatchingCb.isChecked
            creds.restoreResumeWatching = restoreResumeWatchingCb.isChecked
            
            creds.backupSearchHistory = backupSearchHistoryCb.isChecked
            creds.restoreSearchHistory = restoreSearchHistoryCb.isChecked
            
            creds.backupPlayer = backupPlayerCb.isChecked
            creds.restorePlayer = restorePlayerCb.isChecked
            
            creds.backupSubtitles = backupSubtitlesCb.isChecked
            creds.restoreSubtitles = restoreSubtitlesCb.isChecked
            
            creds.backupTheme = backupThemeCb.isChecked
            creds.restoreTheme = restoreThemeCb.isChecked
            
            creds.backupLayout = backupLayoutCb.isChecked
            creds.restoreLayout = restoreLayoutCb.isChecked
            
            creds.backupDownloads = backupDownloadsCb.isChecked
            creds.restoreDownloads = restoreDownloadsCb.isChecked
            
            creds.backupGeneral = backupGeneralCb.isChecked
            creds.restoreGeneral = restoreGeneralCb.isChecked

            sm.appSettingsSyncCreds = creds
        }

        backupExtensionsCb.setOnCheckedChangeListener(checkboxListener)
        restoreExtensionsCb.setOnCheckedChangeListener(checkboxListener)
        
        backupBookmarksCb.setOnCheckedChangeListener(checkboxListener)
        restoreBookmarksCb.setOnCheckedChangeListener(checkboxListener)
        
        backupResumeWatchingCb.setOnCheckedChangeListener(checkboxListener)
        restoreResumeWatchingCb.setOnCheckedChangeListener(checkboxListener)
        
        backupSearchHistoryCb.setOnCheckedChangeListener(checkboxListener)
        restoreSearchHistoryCb.setOnCheckedChangeListener(checkboxListener)
        
        backupPlayerCb.setOnCheckedChangeListener(checkboxListener)
        restorePlayerCb.setOnCheckedChangeListener(checkboxListener)
        
        backupSubtitlesCb.setOnCheckedChangeListener(checkboxListener)
        restoreSubtitlesCb.setOnCheckedChangeListener(checkboxListener)
        
        backupThemeCb.setOnCheckedChangeListener(checkboxListener)
        restoreThemeCb.setOnCheckedChangeListener(checkboxListener)
        
        backupLayoutCb.setOnCheckedChangeListener(checkboxListener)
        restoreLayoutCb.setOnCheckedChangeListener(checkboxListener)
        
        backupDownloadsCb.setOnCheckedChangeListener(checkboxListener)
        restoreDownloadsCb.setOnCheckedChangeListener(checkboxListener)
        
        backupGeneralCb.setOnCheckedChangeListener(checkboxListener)
        restoreGeneralCb.setOnCheckedChangeListener(checkboxListener)
        // #endregion

        // Load devices list + last sync info
        activity?.lifecycle?.coroutineScope?.launch {
            refreshDevicesList(settings, inflater, container)
            updateLastSyncInfo(settings)
        }

        return settings
    }

    @SuppressLint("SetTextI18n")
    private fun updateLastSyncInfo(rootView: View) {
        val infoView = rootView.findView<TextView>("last_sync_info")
        val sb = StringBuilder()

        for (category in SyncCategory.entries) {
            val ts = sm.getCategoryTimestamp(category)
            if (ts > 0) {
                val time = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(ts))
                sb.appendLine("${category.key}: $time")
            }
        }

        if (sb.isEmpty()) {
            infoView.text = "No sync data yet"
        } else {
            infoView.text = "Last synced:\n$sb"
        }
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode", "SetTextI18n")
    private suspend fun refreshDevicesList(rootView: View, inflater: LayoutInflater, container: ViewGroup?) {
        val devicesListLayout = rootView.findView<LinearLayout>("devices_list")
        devicesListLayout.removeAllViews()

        val creds = sm.appSettingsSyncCreds ?: return
        val devices = UltimaSettingsSyncUtils.fetchDevices() ?: return

        devices.forEach { device ->
            val isCurrent = device.deviceId == creds.deviceId
            val deviceView = getLayout("watch_sync_device", inflater, container)
            val nameSwitch = deviceView.findView<Switch>("watch_sync_device_name")

            nameSwitch.text = device.name + if (isCurrent) " (current device)" else ""
            nameSwitch.isChecked = true
            nameSwitch.isClickable = true
            nameSwitch.isFocusable = true

            nameSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (!isChecked) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Remove Device")
                        .setMessage("Remove '${device.name}' from sync network?")
                        .setPositiveButton("Remove") { _, _ ->
                            activity?.lifecycle?.coroutineScope?.launch {
                                val deleteRes = UltimaSettingsSyncUtils.removeDevice(device.deviceId)
                                if (deleteRes.first) {
                                    showToast("Removed ${device.name}")
                                    if (isCurrent) {
                                        sm.appSettingsSyncCreds = null
                                        updateLastSyncInfo(rootView)
                                    }
                                    refreshDevicesList(rootView, inflater, container)
                                } else {
                                    showToast("Failed: ${deleteRes.second}")
                                    nameSwitch.isChecked = true
                                }
                            }
                        }
                        .setNegativeButton("Cancel") { dialog, _ ->
                            nameSwitch.isChecked = true
                            dialog.dismiss()
                        }
                        .setOnCancelListener {
                            nameSwitch.isChecked = true
                        }
                        .show()
                        .setDefaultFocus()
                }
            }

            devicesListLayout.addView(deviceView)
        }
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        if (bottomSheet != null) {
            com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet).state = 
                com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        }
    }

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
