package com.phisher98

import android.content.res.Resources
import android.graphics.drawable.Drawable
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
import com.phisher98.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

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
        focusingSection: Int? = null,
        focusOn: String? = null
    ) {
        sectionsListView.removeAllViews()

        var sections = emptyList<UltimaUtils.SectionInfo>()
        extensions.forEach { ext ->
            ext.sections?.filter { it.enabled }?.let { sections += it }
        }

        if (sections.isEmpty()) {
            noSectionWarning?.visibility = View.VISIBLE
            return
        }

        var counter = sections.size
        val sortedSections = sections.sortedByDescending { it.priority }

        sortedSections.forEach { section ->
            val sectionView = getLayout("list_section_reorder_item", inflater, container)
            val sectionName = sectionView.findView<TextView>("section_name")
            val increaseBtn = sectionView.findView<ImageView>("increase")
            val decreaseBtn = sectionView.findView<ImageView>("decrease")

            increaseBtn.setImageDrawable(getDrawable("triangle"))
            decreaseBtn.setImageDrawable(getDrawable("triangle"))
            decreaseBtn.rotation = 180f

            if (section.priority == 0) section.priority = counter

            sectionName.text = "${section.pluginName}: ${section.name}"

            increaseBtn.makeTvCompatible()
            increaseBtn.setOnClickListener {
                if (section.priority < sections.size) {
                    val nextSection = sections.find { it.priority == section.priority + 1 }
                    if (nextSection == null) {
                        showToast("Cannot increase priority further")
                        return@setOnClickListener
                    }

                    // Swap priorities
                    nextSection.priority -= 1
                    section.priority += 1

                    // Normalize priorities to ensure consistency
                    normalizePriorities(sections)

                    // Update UI
                    updateSectionList(
                        sectionsListView,
                        inflater,
                        container,
                        null,
                        section.priority,
                        "increase"
                    )
                }
            }


            decreaseBtn.makeTvCompatible()
            decreaseBtn.setOnClickListener {
                if (section.priority > 1) {
                    val prevSection = sections.find { it.priority == section.priority - 1 }
                    if (prevSection == null) {
                        showToast("Cannot decrease priority further")
                        return@setOnClickListener
                    }

                    // Swap priorities
                    prevSection.priority += 1
                    section.priority -= 1

                    // Re-normalize the full list to keep priorities clean
                    normalizePriorities(sections)

                    // Update UI
                    updateSectionList(
                        sectionsListView,
                        inflater,
                        container,
                        null,
                        section.priority,
                        "decrease"
                    )
                }
            }


            if (counter == focusingSection) {
                when (focusOn) {
                    "increase" -> increaseBtn.requestFocus()
                    "decrease" -> decreaseBtn.requestFocus()
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
