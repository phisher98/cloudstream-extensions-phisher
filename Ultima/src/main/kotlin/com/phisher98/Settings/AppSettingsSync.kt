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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                            plugin.pushAllCategories(context)
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
                            showToast("Pushing all categories...")
                            plugin.pushAllCategories(context)
                            showToast("Push complete!")
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
                            showToast("Pulling from cloud...")
                            val restored = plugin.forcePullAllCategories(context)
                            showToast(if (restored) "Restored from cloud" else "Already up to date")
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

                        // 1. Pull all categories from cloud (force)
                        plugin.forcePullAllCategories(context)

                        // 2. Push all categories to cloud
                        plugin.pushAllCategories(context)

                        showToast("Force sync complete!")
                        updateLastSyncInfo(settings)
                    } catch (e: Exception) {
                        showToast("Sync failed: ${e.message}")
                    }
                }
            }
        }
        // #endregion

        // #region - unified sync category checkboxes
        val syncExtensionsCb = settings.findView<CheckBox>("sync_extensions")
        val syncBookmarksCb = settings.findView<CheckBox>("sync_bookmarks")
        val syncResumeWatchingCb = settings.findView<CheckBox>("sync_resume_watching")
        val syncSearchHistoryCb = settings.findView<CheckBox>("sync_search_history")
        val syncSettingsCb = settings.findView<CheckBox>("sync_settings")

        val currentCreds = sm.appSettingsSyncCreds ?: AppSettingsSyncCreds()

        syncExtensionsCb.isChecked = currentCreds.syncExtensions
        syncBookmarksCb.isChecked = currentCreds.syncBookmarks
        syncResumeWatchingCb.isChecked = currentCreds.syncResumeWatching
        syncSearchHistoryCb.isChecked = currentCreds.syncSearchHistory
        syncSettingsCb.isChecked = currentCreds.syncSettings

        val checkboxListener = CompoundButton.OnCheckedChangeListener { _, _ ->
            val creds = sm.appSettingsSyncCreds ?: AppSettingsSyncCreds()

            creds.syncExtensions = syncExtensionsCb.isChecked
            creds.syncBookmarks = syncBookmarksCb.isChecked
            creds.syncResumeWatching = syncResumeWatchingCb.isChecked
            creds.syncSearchHistory = syncSearchHistoryCb.isChecked
            creds.syncSettings = syncSettingsCb.isChecked

            sm.appSettingsSyncCreds = creds
        }

        syncExtensionsCb.setOnCheckedChangeListener(checkboxListener)
        syncBookmarksCb.setOnCheckedChangeListener(checkboxListener)
        syncResumeWatchingCb.setOnCheckedChangeListener(checkboxListener)
        syncSearchHistoryCb.setOnCheckedChangeListener(checkboxListener)
        syncSettingsCb.setOnCheckedChangeListener(checkboxListener)
        // #endregion

        // Load devices list + last sync info
        activity?.lifecycle?.coroutineScope?.launch {
            refreshDevicesList(settings, inflater, container)
            updateLastSyncInfo(settings)
        }

        return settings
    }

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

    private suspend fun refreshDevicesList(rootView: View, inflater: LayoutInflater, container: ViewGroup?) {
        val devicesListLayout = rootView.findView<LinearLayout>("devices_list")
        devicesListLayout.removeAllViews()

        val creds = sm.appSettingsSyncCreds ?: return
        val devices = UltimaSettingsSyncUtils.fetchDevices(requireContext()) ?: return

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
