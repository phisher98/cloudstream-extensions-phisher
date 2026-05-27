package com.phisher98

import android.annotation.SuppressLint
import android.content.Context
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
import com.lagradost.cloudstream3.ui.home.HomeViewModel.Companion.getResumeWatching
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.mapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    ): View? {
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

                        val newCreds = AppSettingsSyncCreds(
                            useCustomDatabase = useCustom,
                            firebaseUrl = if (fbUrl.isEmpty()) null else fbUrl,
                            syncKey = key,
                            deviceName = if (devName.isEmpty()) Build.MODEL else devName,
                            deviceId = deviceId,
                            backupDevice = currentCreds.backupDevice,
                            restoreDevice = currentCreds.restoreDevice,
                            backupBookmarks = currentCreds.backupBookmarks,
                            backupResumeWatching = currentCreds.backupResumeWatching,
                            backupSearchHistory = currentCreds.backupSearchHistory,
                            backupExtensions = currentCreds.backupExtensions,
                            backupPlayer = currentCreds.backupPlayer,
                            backupSubtitles = currentCreds.backupSubtitles,
                            backupTheme = currentCreds.backupTheme,
                            backupLayout = currentCreds.backupLayout,
                            backupDownloads = currentCreds.backupDownloads,
                            backupGeneral = currentCreds.backupGeneral,
                            restoreBookmarks = currentCreds.restoreBookmarks,
                            restoreResumeWatching = currentCreds.restoreResumeWatching,
                            restoreSearchHistory = currentCreds.restoreSearchHistory,
                            restoreExtensions = currentCreds.restoreExtensions,
                            restorePlayer = currentCreds.restorePlayer,
                            restoreSubtitles = currentCreds.restoreSubtitles,
                            restoreTheme = currentCreds.restoreTheme,
                            restoreLayout = currentCreds.restoreLayout,
                            restoreDownloads = currentCreds.restoreDownloads,
                            restoreGeneral = currentCreds.restoreGeneral
                        )

                        sm.appSettingsSyncCreds = newCreds

                        activity?.lifecycle?.coroutineScope?.launch {
                            val data = UltimaBackupUtils.getBackup(context, getResumeWatching())?.toJson() ?: ""
                            val syncRes = UltimaSettingsSyncUtils.syncThisDevice(context, data)
                            showToast(if (syncRes.first) "Device registered and synced" else "Error registering: ${syncRes.second}")
                            refreshDevicesList(settings, inflater, container)
                        }
                    }
                    .setNegativeButton("Reset") { _, _ ->
                        activity?.lifecycle?.coroutineScope?.launch {
                            val deleteRes = UltimaSettingsSyncUtils.deregisterThisDevice()
                            sm.appSettingsSyncCreds = null
                            showToast("Sync credentials removed: ${deleteRes.second ?: "Reset successful"}")
                            refreshDevicesList(settings, inflater, container)
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
                            val data = UltimaBackupUtils.getBackup(context, getResumeWatching())?.toJson() ?: ""
                            val res = UltimaSettingsSyncUtils.syncThisDevice(context, data)
                            showToast(res.second)
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
                            restoreFromCloud(context)
                        }
                    }
                } else if (checked) {
                    showToast("Configure credentials first")
                    this.isChecked = false
                }
            }
        }
        // #endregion

        // #region - selective sync checkboxes
        val backupBookmarksCb = settings.findView<CheckBox>("backup_bookmarks")
        val backupResumeWatchingCb = settings.findView<CheckBox>("backup_resume_watching")
        val backupSearchHistoryCb = settings.findView<CheckBox>("backup_search_history")
        val backupExtensionsCb = settings.findView<CheckBox>("backup_extensions")
        val backupPlayerCb = settings.findView<CheckBox>("backup_player")
        val backupSubtitlesCb = settings.findView<CheckBox>("backup_subtitles")
        val backupThemeCb = settings.findView<CheckBox>("backup_theme")
        val backupLayoutCb = settings.findView<CheckBox>("backup_layout")
        val backupDownloadsCb = settings.findView<CheckBox>("backup_downloads")
        val backupGeneralCb = settings.findView<CheckBox>("backup_general")

        val restoreBookmarksCb = settings.findView<CheckBox>("restore_bookmarks")
        val restoreResumeWatchingCb = settings.findView<CheckBox>("restore_resume_watching")
        val restoreSearchHistoryCb = settings.findView<CheckBox>("restore_search_history")
        val restoreExtensionsCb = settings.findView<CheckBox>("restore_extensions")
        val restorePlayerCb = settings.findView<CheckBox>("restore_player")
        val restoreSubtitlesCb = settings.findView<CheckBox>("restore_subtitles")
        val restoreThemeCb = settings.findView<CheckBox>("restore_theme")
        val restoreLayoutCb = settings.findView<CheckBox>("restore_layout")
        val restoreDownloadsCb = settings.findView<CheckBox>("restore_downloads")
        val restoreGeneralCb = settings.findView<CheckBox>("restore_general")

        val currentCreds = sm.appSettingsSyncCreds ?: AppSettingsSyncCreds()
        
        backupBookmarksCb.isChecked = currentCreds.backupBookmarks
        backupResumeWatchingCb.isChecked = currentCreds.backupResumeWatching
        backupSearchHistoryCb.isChecked = currentCreds.backupSearchHistory
        backupExtensionsCb.isChecked = currentCreds.backupExtensions
        backupPlayerCb.isChecked = currentCreds.backupPlayer
        backupSubtitlesCb.isChecked = currentCreds.backupSubtitles
        backupThemeCb.isChecked = currentCreds.backupTheme
        backupLayoutCb.isChecked = currentCreds.backupLayout
        backupDownloadsCb.isChecked = currentCreds.backupDownloads
        backupGeneralCb.isChecked = currentCreds.backupGeneral

        restoreBookmarksCb.isChecked = currentCreds.restoreBookmarks
        restoreResumeWatchingCb.isChecked = currentCreds.restoreResumeWatching
        restoreSearchHistoryCb.isChecked = currentCreds.restoreSearchHistory
        restoreExtensionsCb.isChecked = currentCreds.restoreExtensions
        restorePlayerCb.isChecked = currentCreds.restorePlayer
        restoreSubtitlesCb.isChecked = currentCreds.restoreSubtitles
        restoreThemeCb.isChecked = currentCreds.restoreTheme
        restoreLayoutCb.isChecked = currentCreds.restoreLayout
        restoreDownloadsCb.isChecked = currentCreds.restoreDownloads
        restoreGeneralCb.isChecked = currentCreds.restoreGeneral

        val checkboxListener = CompoundButton.OnCheckedChangeListener { _, _ ->
            val creds = sm.appSettingsSyncCreds ?: AppSettingsSyncCreds()
            
            creds.backupBookmarks = backupBookmarksCb.isChecked
            creds.backupResumeWatching = backupResumeWatchingCb.isChecked
            creds.backupSearchHistory = backupSearchHistoryCb.isChecked
            creds.backupExtensions = backupExtensionsCb.isChecked
            creds.backupPlayer = backupPlayerCb.isChecked
            creds.backupSubtitles = backupSubtitlesCb.isChecked
            creds.backupTheme = backupThemeCb.isChecked
            creds.backupLayout = backupLayoutCb.isChecked
            creds.backupDownloads = backupDownloadsCb.isChecked
            creds.backupGeneral = backupGeneralCb.isChecked

            creds.restoreBookmarks = restoreBookmarksCb.isChecked
            creds.restoreResumeWatching = restoreResumeWatchingCb.isChecked
            creds.restoreSearchHistory = restoreSearchHistoryCb.isChecked
            creds.restoreExtensions = restoreExtensionsCb.isChecked
            creds.restorePlayer = restorePlayerCb.isChecked
            creds.restoreSubtitles = restoreSubtitlesCb.isChecked
            creds.restoreTheme = restoreThemeCb.isChecked
            creds.restoreLayout = restoreLayoutCb.isChecked
            creds.restoreDownloads = restoreDownloadsCb.isChecked
            creds.restoreGeneral = restoreGeneralCb.isChecked
            
            sm.appSettingsSyncCreds = creds
        }
        
        backupBookmarksCb.setOnCheckedChangeListener(checkboxListener)
        backupResumeWatchingCb.setOnCheckedChangeListener(checkboxListener)
        backupSearchHistoryCb.setOnCheckedChangeListener(checkboxListener)
        backupExtensionsCb.setOnCheckedChangeListener(checkboxListener)
        backupPlayerCb.setOnCheckedChangeListener(checkboxListener)
        backupSubtitlesCb.setOnCheckedChangeListener(checkboxListener)
        backupThemeCb.setOnCheckedChangeListener(checkboxListener)
        backupLayoutCb.setOnCheckedChangeListener(checkboxListener)
        backupDownloadsCb.setOnCheckedChangeListener(checkboxListener)
        backupGeneralCb.setOnCheckedChangeListener(checkboxListener)

        restoreBookmarksCb.setOnCheckedChangeListener(checkboxListener)
        restoreResumeWatchingCb.setOnCheckedChangeListener(checkboxListener)
        restoreSearchHistoryCb.setOnCheckedChangeListener(checkboxListener)
        restoreExtensionsCb.setOnCheckedChangeListener(checkboxListener)
        restorePlayerCb.setOnCheckedChangeListener(checkboxListener)
        restoreSubtitlesCb.setOnCheckedChangeListener(checkboxListener)
        restoreThemeCb.setOnCheckedChangeListener(checkboxListener)
        restoreLayoutCb.setOnCheckedChangeListener(checkboxListener)
        restoreDownloadsCb.setOnCheckedChangeListener(checkboxListener)
        restoreGeneralCb.setOnCheckedChangeListener(checkboxListener)
        // #endregion

        // Load devices list
        activity?.lifecycle?.coroutineScope?.launch {
            refreshDevicesList(settings, inflater, container)
        }

        return settings
    }

    private suspend fun restoreFromCloud(context: Context) {
        val creds = sm.appSettingsSyncCreds ?: return
        try {
            val devices = UltimaSettingsSyncUtils.fetchDevices(context)
            if (!devices.isNullOrEmpty()) {
                val otherDevice = devices.firstOrNull { it.deviceId != creds.deviceId }
                if (otherDevice != null && !otherDevice.syncedData.isNullOrBlank()) {
                    val backupFile = mapper.readValue<BackupFile>(otherDevice.syncedData!!)
                    UltimaBackupUtils.restore(context, backupFile, true, true)
                    showToast("Restored settings from ${otherDevice.name}")
                } else {
                    showToast("No other devices found to restore from")
                }
            } else {
                showToast("No backup devices found")
            }
        } catch (e: Exception) {
            showToast("Restore failed: ${e.message}")
        }
    }

    private suspend fun refreshDevicesList(rootView: View, inflater: LayoutInflater, container: ViewGroup?) {
        val devicesListLayout = rootView.findView<LinearLayout>("devices_list")
        devicesListLayout.removeAllViews()

        val creds = sm.appSettingsSyncCreds ?: return
        val devices = UltimaSettingsSyncUtils.fetchDevices(requireContext()) ?: return

        devices.forEach { device ->
            val isCurrent = device.deviceId == creds.deviceId
            val deviceView = getLayout("watch_sync_device", inflater, container)
            val nameSwitch = deviceView.findView<Switch>("watch_sync_device_name")
            
            // We use the switch layout but disable it since it's read-only for showing registered devices
            nameSwitch.text = device.name + if (isCurrent) " (current device)" else ""
            nameSwitch.isChecked = true
            nameSwitch.isClickable = false
            nameSwitch.focusable = View.NOT_FOCUSABLE
            
            devicesListLayout.addView(deviceView)
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
