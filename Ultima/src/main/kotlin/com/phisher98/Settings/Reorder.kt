package com.phisher98

import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"
private var selectedSection: UltimaUtils.SectionInfo? = null

class UltimaReorder(val plugin: UltimaPlugin) : BottomSheetDialogFragment() {
    private var param1: String? = null
    private var param2: String? = null
    private val sm = UltimaStorageManager
    private val extensions = sm.fetchExtensions()
    private val res: Resources = plugin.resources ?: throw Exception("Unable to read resources")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    // region - resource helpers
    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = res.getIdentifier(name, "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        return inflater.inflate(res.getLayout(id), container, false)
    }

    private fun getDrawable(name: String): Drawable {
        val id = res.getIdentifier(name, "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        return res.getDrawable(id, null)
            ?: throw Exception("Unable to find drawable $name")
    }

    private fun getString(name: String): String {
        val id = res.getIdentifier(name, "string", BuildConfig.LIBRARY_PACKAGE_NAME)
        return res.getString(id)
    }

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        return findViewById(id)
    }

    private fun View.makeTvCompatible() {
        val outlineId = res.getIdentifier("outline", "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        background = res.getDrawable(outlineId, null)
    }
    // endregion

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = getLayout("reorder", inflater, container)

        // Save button
        val saveBtn = root.findView<ImageView>("save")
        saveBtn.setImageDrawable(getDrawable("save_icon"))
        saveBtn.makeTvCompatible()
        saveBtn.setOnClickListener {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    sm.currentExtensions = extensions
                    plugin.reload(context)
                }

                showToast("Saved. Please restart the app to apply changes.")
                dismiss()
            }
        }


        val noSectionWarning = root.findView<TextView>("no_section_warning")
        val sectionsListView = root.findView<LinearLayout>("section_list")
        updateSectionList(sectionsListView, inflater, container, noSectionWarning)

        return root
    }

    private fun updateSectionList(
        sectionsListView: LinearLayout,
        inflater: LayoutInflater,
        container: ViewGroup?,
        noSectionWarning: TextView? = null,
        currentSections: List<UltimaUtils.SectionInfo>? = null
    ) {
        sectionsListView.removeAllViews()

        val sections = currentSections ?: run {
            var freshSections = emptyList<UltimaUtils.SectionInfo>()
            extensions.forEach { ext ->
                ext.sections?.filter { it.enabled }?.let { freshSections += it }
            }
            freshSections
        }

        if (sections.isEmpty()) {
            noSectionWarning?.visibility = View.VISIBLE
            return
        }

        val sortedSections = sections.sortedByDescending { it.priority }
        var counter = sortedSections.size

        val displaySections = (currentSections ?: run {
            val freshSections = emptyList<UltimaUtils.SectionInfo>().toMutableList()
            extensions.forEach { ext ->
                ext.sections?.filter { it.enabled }?.let { freshSections += it }
            }
            freshSections
        }).sortedByDescending { it.priority } // Display order

        if (displaySections.isEmpty()) {
            noSectionWarning?.visibility = View.VISIBLE
            return
        }

        displaySections.forEach { section ->
            val sectionView = getLayout("list_section_reorder_item", inflater, container)
            val sectionName = sectionView.findView<TextView>("section_name")

            if (section.priority == 0) section.priority = counter
            sectionName.text = "${section.pluginName}: ${section.name}"

            sectionView.background = LayerDrawable(
                arrayOf(
                    ColorDrawable(if (section == selectedSection) 0x2200FF00.toInt() else Color.TRANSPARENT),
                    getDrawable("outline")
                )
            )

            sectionView.setOnClickListener {
                val focusedSectionBefore = section  // store the actual section object

                when (selectedSection) {
                    null -> {
                        selectedSection = section
                        showToast("Picked! Now tap a target.")
                        updateSectionList(sectionsListView, inflater, container, noSectionWarning, displaySections)
                    }
                    section -> {
                        selectedSection = null
                        updateSectionList(sectionsListView, inflater, container, noSectionWarning, displaySections)
                    }
                    else -> {
                        val selected = selectedSection!!
                        val sectionsMutable = displaySections.toMutableList()

                        val selectedIndex = sectionsMutable.indexOf(selected)
                        val targetIndex = sectionsMutable.indexOf(section)

                        if (selectedIndex == targetIndex) {
                            showToast("Already in this position")
                            return@setOnClickListener
                        }

                        sectionsMutable.removeAt(selectedIndex)
                        sectionsMutable.add(targetIndex, selected)

                        sectionsMutable.forEachIndexed { index, sec ->
                            sec.priority = sectionsMutable.size - index
                        }

                        selectedSection = null
                        updateSectionList(sectionsListView, inflater, container, noSectionWarning, sectionsMutable)
                        showToast("Moved to position ${targetIndex + 1}")
                    }
                }

                sectionsListView.post {
                    for (i in 0 until sectionsListView.childCount) {
                        val child = sectionsListView.getChildAt(i)
                        val nameView = child.findView<TextView>("section_name")
                        if (nameView.text.contains(focusedSectionBefore.name, ignoreCase = true)) {
                            child.requestFocus()
                            break
                        }
                    }
                }
            }



            counter -= 1
            sectionsListView.addView(sectionView)
        }
    }



    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {}

    override fun onDetach() {
        super.onDetach()
        UltimaSettings(plugin).show(
            activity?.supportFragmentManager
                ?: throw Exception("Unable to open configure settings"),
            ""
        )
    }

    private fun normalizePriorities(sections: List<UltimaUtils.SectionInfo>) {
        sections
            .sortedByDescending { it.priority }
            .forEachIndexed { index, section ->
                section.priority = sections.size - index
            }
    }

}
