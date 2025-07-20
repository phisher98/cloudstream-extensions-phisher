package com.phisher98

import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.coroutineScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.phisher98.BuildConfig
import com.phisher98.WatchSyncUtils.WatchSyncCreds
import kotlinx.coroutines.launch

private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

class UltimaConfigureWatchSync(private val plugin: UltimaPlugin) : BottomSheetDialogFragment() {
    private var param1: String? = null
    private var param2: String? = null
    private val sm = UltimaStorageManager
    private val deviceData = sm.deviceSyncCreds
    private val res: Resources = plugin.resources ?: throw Exception("Unable to read resources")
    private val packageName = BuildConfig.LIBRARY_PACKAGE_NAME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    // #region - necessary functions
    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = res.getIdentifier(name, "layout", packageName)
        val layout = res.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    private fun getDrawable(name: String): Drawable {
        val id = res.getIdentifier(name, "drawable", packageName)
        return res.getDrawable(id, null) ?: throw Exception("Unable to find drawable $name")
    }

    private fun getString(name: String): String {
        val id = res.getIdentifier(name, "string", packageName)
        return res.getString(id)
    }

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", packageName)
        return this.findViewById(id)
    }

    private fun View.makeTvCompatible() {
        val outlineId = res.getIdentifier("outline", "drawable", packageName)
        this.background = res.getDrawable(outlineId, null)
    }
    // #endregion

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val settings = getLayout("configure_watch_sync", inflater, container)

        // #region - save button
        settings.findView<ImageView>("save").apply {
            setImageDrawable(getDrawable("save_icon"))
            makeTvCompatible()
            setOnClickListener {
                sm.deviceSyncCreds = deviceData
                plugin.reload(context)
                showToast("Saved")
                dismiss()
            }
        }
        // #endregion

        // #region - watch sync creds
        settings.findView<ImageView>("watch_sync_creds_btn").apply {
            setImageDrawable(getDrawable("edit_icon"))
            makeTvCompatible()
            setOnClickListener {
                val credsView = getLayout("watch_sync_creds", inflater, container)
                val tokenInput = credsView.findView<EditText>("token")
                val prNumInput = credsView.findView<EditText>("project_num")
                val deviceNameInput = credsView.findView<EditText>("device_name")

                tokenInput.setText(sm.deviceSyncCreds?.token)
                prNumInput.setText(sm.deviceSyncCreds?.projectNum?.toString())
                deviceNameInput.setText(sm.deviceSyncCreds?.deviceName)

                AlertDialog.Builder(requireContext())
                    .setTitle("Set your creds")
                    .setView(credsView)
                    .setPositiveButton("Save") { _, _ ->
                        val token = tokenInput.text.trim().toString()
                        val prNum = prNumInput.text.toString().toIntOrNull()
                        val deviceName = deviceNameInput.text.trim().toString()

                        if (token.isEmpty() || prNum == null || deviceName.isEmpty()) {
                            showToast("Invalid details")
                        } else {
                            activity?.lifecycle?.coroutineScope?.launch {
                                sm.deviceSyncCreds = WatchSyncCreds(token, prNum, deviceName)
                                showToast(
                                    sm.deviceSyncCreds
                                        ?.syncProjectDetails()
                                        ?.second
                                )
                            }
                        }
                        dismiss()
                    }
                    .setNegativeButton("Reset") { _, _ ->
                        sm.deviceSyncCreds = WatchSyncCreds()
                        showToast("Credentials removed")
                        dismiss()
                    }
                    .show()
                    .setDefaultFocus()
            }
        }
        // #endregion

        // #region - toggle for sync this device
        settings.findView<Switch>("sync_this_device").apply {
            makeTvCompatible()
            isChecked = sm.deviceSyncCreds?.isThisDeviceSync ?: false
            setOnClickListener {
                activity?.lifecycle?.coroutineScope?.launch {
                    sm.deviceSyncCreds?.let {
                        val res = if (isChecked) it.registerThisDevice()
                        else it.deregisterThisDevice()
                        showToast(res.second)
                        if (res.first) dismiss()
                    }
                }
            }
        }
        // #endregion

        // #region - list of devices
        val devicesListLayout = settings.findView<LinearLayout>("devices_list")
        val activeDevices = deviceData?.enabledDevices?.toMutableList() ?: mutableListOf()
        activity?.lifecycle?.coroutineScope?.launch {
            val devices = deviceData?.fetchDevices()
            devices?.forEach { device ->
                val currentDevice = sm.deviceSyncCreds?.deviceId == device.deviceId
                val syncDeviceView = getLayout("watch_sync_device", inflater, container)
                val deviceName = syncDeviceView.findView<Switch>("watch_sync_device_name")

                deviceName.apply {
                    text = device.name + if (currentDevice) " (current device)" else ""
                    isChecked = sm.deviceSyncCreds?.enabledDevices?.contains(device.deviceId) ?: false
                    setOnClickListener {
                        if (isChecked) {
                            if (currentDevice) isChecked = false
                            else activeDevices.add(device.deviceId)
                        } else {
                            activeDevices.remove(device.deviceId)
                        }
                        deviceData?.enabledDevices = activeDevices
                    }
                }

                devicesListLayout.addView(syncDeviceView)
            }
        }
        // #endregion

        return settings
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
