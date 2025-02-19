package com.Phisher98.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import com.Phisher98.StreamPlayPlugin
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.Phisher98.BuildConfig
import com.lagradost.cloudstream3.CommonActivity.showToast

/**
 * A simple [Fragment] subclass.
 * Use the [SettingsFragment] factory method to
 * create an instance of this fragment.
 */
class SettingsFragment(
    private val plugin: StreamPlayPlugin,
) : BottomSheetDialogFragment() {
    private val res = plugin.resources ?: throw Exception("Unable to read resources")
    private val sharedPref = activity?.getSharedPreferences("StreamPlay", Context.MODE_PRIVATE)

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        return this.findViewById(id)
    }

    private fun View.makeTvCompatible() {
        this.setPadding(
            this.paddingLeft + 10,
            this.paddingTop + 10,
            this.paddingRight + 10,
            this.paddingBottom + 10
        )
        this.background = getDrawable("outline")
    }

    private fun getDrawable(name: String): Drawable? {
        val id =
            res.getIdentifier(name, "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        return ResourcesCompat.getDrawable(res, id, null)
    }

    private fun getString(name: String): String? {
        val id =
            res.getIdentifier(name, "string", BuildConfig.LIBRARY_PACKAGE_NAME)
        return res.getString(id)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        // Inflate the layout for this fragment
        val id = res.getIdentifier(
            "settings_fragment",
            "layout",
            BuildConfig.LIBRARY_PACKAGE_NAME
        )
        val layout = res.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val headerTw = view.findView<TextView>("header")
        headerTw.text = getString("header_tw")

        val tokeninput = view.findView<EditText>("tokenInput")
        tokeninput.hint = getString("text_hint")

        val addButton = view.findView<Button>("addButton")
        addButton.text = getString("button")

        addButton.setOnClickListener {
            val superStreamToken = tokeninput.text.toString().trim()

            // Saves the token in the shared preferences in a "variable" named token
            with(sharedPref?.edit()){
                this?.putString("token", superStreamToken)
                this?.apply()
            }
            showToast("Token saved succesfully")
            tokeninput.text.clear()
        }

        // Your code

    }
}