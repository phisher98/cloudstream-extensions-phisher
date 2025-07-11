package com.phisher98

import android.content.DialogInterface
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.coroutineScope
import com.phisher98.WatchSyncUtils.WatchSyncCreds
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.phisher98.BuildConfig
import kotlinx.coroutines.*

private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

class UltimaConfigureWatchSync(val plugin: UltimaPlugin) : BottomSheetDialogFragment() {
    private var param1: String? = null
    private var param2: String? = null
    private val sm = UltimaStorageManager
    private val deviceData = sm.deviceSyncCreds
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
        val settings = getLayout("configure_watch_sync", inflater, container)

        // #region - building save button and its click listener
        val saveBtn = settings.findView<ImageView>("save")
        saveBtn.setImageDrawable(getDrawable("save_icon"))
        saveBtn.makeTvCompatible()
        saveBtn.setOnClickListener(
                object : OnClickListener {
                    override fun onClick(btn: View) {
                        sm.deviceSyncCreds = deviceData
                        plugin.reload(context)
                        showToast("Saved")
                        dismiss()
                    }
                }
        )
        // #endregion - building save button and its click listener

        // #region - building watch sync creds and its click listener
        val CredsBtn = settings.findView<ImageView>("watch_sync_creds_btn")
        CredsBtn.setImageDrawable(getDrawable("edit_icon"))
        CredsBtn.makeTvCompatible()
        CredsBtn.setOnClickListener(
                object : OnClickListener {
                    override fun onClick(btn: View) {
                        val credsView = getLayout("watch_sync_creds", inflater, container)
                        val tokenInput = credsView.findView<EditText>("token")
                        tokenInput.setText(sm.deviceSyncCreds?.token)
                        val prNumInput = credsView.findView<EditText>("project_num")
                        prNumInput.setText(sm.deviceSyncCreds?.projectNum?.toString())
                        val deviceNameInput = credsView.findView<EditText>("device_name")
                        deviceNameInput.setText(sm.deviceSyncCreds?.deviceName)

                        AlertDialog.Builder(
                                        context ?: throw Exception("Unable to build alert dialog")
                                )
                                .setTitle("Set your creds")
                                .setView(credsView)
                                .setPositiveButton(
                                        "Save",
                                        object : DialogInterface.OnClickListener {
                                            override fun onClick(p0: DialogInterface, p1: Int) {
                                                var token = tokenInput.text.trim().toString()
                                                var prNum = prNumInput.text.toString().toIntOrNull()
                                                var deviceName =
                                                        deviceNameInput.text.trim().toString()
                                                if (token.isNullOrEmpty() ||
                                                                prNum == null ||
                                                                deviceName.isNullOrEmpty()
                                                )
                                                        showToast("Invalid details")
                                                else {
                                                    activity?.lifecycle?.coroutineScope?.launch {
                                                        sm.deviceSyncCreds =
                                                                WatchSyncCreds(
                                                                        token,
                                                                        prNum,
                                                                        deviceName
                                                                )

                                                        showToast(
                                                                sm.deviceSyncCreds
                                                                        ?.syncProjectDetails()
                                                                        ?.second
                                                        )
                                                    }
                                                }
                                                dismiss()
                                            }
                                        }
                                )
                                .setNegativeButton(
                                        "Reset",
                                        object : DialogInterface.OnClickListener {
                                            override fun onClick(p0: DialogInterface, p1: Int) {
                                                sm.deviceSyncCreds = WatchSyncCreds()
                                                showToast("Credentials removed")
                                                dismiss()
                                            }
                                        }
                                )
                                .show()
                                .setDefaultFocus()
                    }
                }
        )
        // #endregion - building toggle for extension_name_on_home and its click listener

        // #region - building toggle for sync this device and its click listener
        val syncThisDeviceBtn = settings.findView<Switch>("sync_this_device")
        syncThisDeviceBtn.makeTvCompatible()
        syncThisDeviceBtn.isChecked = sm.deviceSyncCreds?.isThisDeviceSync ?: false
        syncThisDeviceBtn.setOnClickListener(
                object : OnClickListener {
                    override fun onClick(btn: View) {
                        activity?.lifecycle?.coroutineScope?.launch {
                            sm.deviceSyncCreds?.let {
                                val res =
                                        if (syncThisDeviceBtn.isChecked) it.registerThisDevice()
                                        else it.deregisterThisDevice()
                                showToast(res.second)
                                if (res.first) dismiss()
                            }
                        }
                    }
                }
        )
        // #endregion - building toggle for sync this device and its click listener

        // #region - building list of devices with its click listener
        val devicesListLayout = settings.findView<LinearLayout>("devices_list")
        val activeDevices = deviceData?.enabledDevices ?: mutableListOf<String>()
        activity?.lifecycle?.coroutineScope?.launch {
            val devices = deviceData?.fetchDevices()
            devices?.forEach { device ->
                val currentDevice = sm.deviceSyncCreds?.deviceId.equals(device.deviceId)
                val syncDeviceView = getLayout("watch_sync_device", inflater, container)
                val deviceName = syncDeviceView.findView<Switch>("watch_sync_device_name")
                deviceName.text = device.name + if (currentDevice) " (current device)" else ""
                deviceName.isChecked =
                        sm.deviceSyncCreds?.enabledDevices?.contains(device.deviceId) ?: false
                deviceName.setOnClickListener(
                        object : OnClickListener {
                            override fun onClick(btn: View) {
                                if (deviceName.isChecked) {
                                    if (currentDevice) deviceName.isChecked = false
                                    else activeDevices.add(device.deviceId)
                                } else activeDevices.remove(device.deviceId)
                                deviceData?.enabledDevices = activeDevices
                            }
                        }
                )
                devicesListLayout.addView(syncDeviceView)
            }
        }
        // #endregion - building list of devices with its click listener

        return settings
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
