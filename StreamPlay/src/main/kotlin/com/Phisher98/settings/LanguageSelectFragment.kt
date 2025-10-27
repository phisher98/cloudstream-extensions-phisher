package com.Phisher98

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.phisher98.BuildConfig
import com.phisher98.StreamPlayPlugin
import androidx.core.content.edit
import androidx.core.widget.addTextChangedListener

class LanguageSelectFragment(
    plugin: StreamPlayPlugin,
    private val sharedPref: SharedPreferences
) : BottomSheetDialogFragment() {

    private val res = plugin.resources ?: throw Exception("Unable to access plugin resources")

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun View.makeTvCompatible() {
        val outlineId = res.getIdentifier("outline", "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        this.background = res.getDrawable(outlineId, null)
    }

    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = res.getIdentifier(name, "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        val layout = res.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        if (id == 0) throw Exception("View ID $name not found.")
        return this.findViewById(id)
    }

    // Language Display List
    private val languages = listOf(
        "South Africa (Afrikaans)" to "af-ZA",
        "United Arab Emirates (Arabic)" to "ar-AE",
        "Saudi Arabia (Arabic)" to "ar-SA",
        "Azerbaijan (Azerbaijani)" to "az-AZ",
        "Bulgaria (Bulgarian)" to "bg-BG",
        "India (Bengali)" to "bn-IN",
        "Spain (Catalan)" to "ca-ES",
        "Czech Republic (Czech)" to "cs-CZ",
        "United Kingdom (Welsh)" to "cy-GB",
        "Denmark (Danish)" to "da-DK",
        "Germany (German)" to "de-DE",
        "Greece (Greek)" to "el-GR",
        "United States (English)" to "en-US",
        "United Kingdom (English)" to "en-GB",
        "Spain (Spanish)" to "es-ES",
        "Latin America (Spanish)" to "es-419",
        "Estonia (Estonian)" to "et-EE",
        "Spain (Basque)" to "eu-ES",
        "Iran (Persian)" to "fa-IR",
        "Finland (Finnish)" to "fi-FI",
        "Philippines (Filipino)" to "fil-PH",
        "France (French)" to "fr-FR",
        "Spain (Galician)" to "gl-ES",
        "India (Gujarati)" to "gu-IN",
        "Israel (Hebrew)" to "he-IL",
        "India (Hindi)" to "hi-IN",
        "Croatia (Croatian)" to "hr-HR",
        "Hungary (Hungarian)" to "hu-HU",
        "Indonesia (Indonesian)" to "id-ID",
        "Iceland (Icelandic)" to "is-IS",
        "Italy (Italian)" to "it-IT",
        "Japan (Japanese)" to "ja-JP",
        "India (Kannada)" to "kn-IN",
        "South Korea (Korean)" to "ko-KR",
        "Lithuania (Lithuanian)" to "lt-LT",
        "Latvia (Latvian)" to "lv-LV",
        "India (Malayalam)" to "ml-IN",
        "Malaysia (Malay)" to "ms-MY",
        "Norway (Norwegian)" to "no-NO",
        "Netherlands (Dutch)" to "nl-NL",
        "Poland (Polish)" to "pl-PL",
        "Brazil (Portuguese)" to "pt-BR",
        "Portugal (Portuguese)" to "pt-PT",
        "Romania (Romanian)" to "ro-RO",
        "Russia (Russian)" to "ru-RU",
        "Slovakia (Slovak)" to "sk-SK",
        "Slovenia (Slovenian)" to "sl-SI",
        "Serbia (Serbian)" to "sr-RS",
        "Sweden (Swedish)" to "sv-SE",
        "India (Tamil)" to "ta-IN",
        "India (Telugu)" to "te-IN",
        "Thailand (Thai)" to "th-TH",
        "Turkey (Turkish)" to "tr-TR",
        "Ukraine (Ukrainian)" to "uk-UA",
        "Vietnam (Vietnamese)" to "vi-VN",
        "China (Chinese Simplified)" to "zh-CN",
        "Taiwan (Chinese Traditional)" to "zh-TW"
    ).sortedBy { it.first.lowercase() }


    private lateinit var adapter: LanguageAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {

        val root = getLayout("fragment_language_select", inflater, container)

        val recycler: RecyclerView = root.findView("languageRecycler")
        val search: EditText = root.findView("searchLanguage")
        recycler.makeTvCompatible()
        search.makeTvCompatible()

        recycler.layoutManager = LinearLayoutManager(requireContext())

        val savedCode = sharedPref.getString("tmdb_language_code", "en-US") ?: "en-US"

        adapter = LanguageAdapter(
            languages.sortedBy { it.first.lowercase() }, // << SORT HERE
            savedCode
        ) { code ->
            sharedPref.edit { putString("tmdb_language_code", code) }
            Toast.makeText(requireContext(), "Language set to $code", Toast.LENGTH_SHORT).show()
            dismiss()
        }


        recycler.adapter = adapter

        search.addTextChangedListener { text ->
            adapter.filter(text.toString())
        }

        return root
    }


    // ---------------------------------------------------------------------- ADAPTER ------------------ //

    inner class LanguageAdapter(
        private val originalList: List<Pair<String, String>>,
        private val selectedCode: String,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<LanguageAdapter.VH>() {

        private var filteredList = originalList.toMutableList()

        inner class VH(val v: View) : RecyclerView.ViewHolder(v) {
            val radio: RadioButton = v.findView("radio_language")
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = getLayout("item_language", LayoutInflater.from(parent.context), parent)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (name, code) = filteredList[position]

            holder.radio.text = name
            holder.radio.isChecked = code == selectedCode

            holder.radio.setOnClickListener {
                onClick(code)
            }
        }

        override fun getItemCount() = filteredList.size
        @SuppressLint("NotifyDataSetChanged")
        fun filter(query: String) {
            filteredList = if (query.isBlank()) {
                originalList.toMutableList()
            } else {
                originalList.filter { it.first.contains(query, ignoreCase = true) }.toMutableList()
            }
            notifyDataSetChanged()
        }
    }

}
