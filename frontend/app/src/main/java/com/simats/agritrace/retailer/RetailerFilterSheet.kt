package com.simats.agritrace.retailer

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.simats.agritrace.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

data class FilterSheetOption(val label: String)

fun showRetailerFilterSheet(
    context: Context,
    title: String,
    hint: String,
    options: List<FilterSheetOption>,
    selectedLabel: String?,
    onApply: (String) -> Unit
) {
    if (options.isEmpty()) return

    val dialog = BottomSheetDialog(context)
    val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_products_filter, null, false)
    dialog.setContentView(view)

    val tvTitle = view.findViewById<TextView>(R.id.tvFilterTitle)
    val tvHint = view.findViewById<TextView>(R.id.tvFilterHint)
    tvTitle.text = title
    tvHint.text = hint

    val row1 = view.findViewById<View>(R.id.statusRow1)
    val row2 = view.findViewById<View>(R.id.statusRow2)

    val btnAll = view.findViewById<MaterialButton>(R.id.btnFilterAll)
    val btnActive = view.findViewById<MaterialButton>(R.id.btnFilterActive)
    val btnPending = view.findViewById<MaterialButton>(R.id.btnFilterPending)
    val btnSold = view.findViewById<MaterialButton>(R.id.btnFilterSold)
    val btnReset = view.findViewById<MaterialButton>(R.id.btnFilterReset)
    val btnApply = view.findViewById<MaterialButton>(R.id.btnFilterApply)

    val buttons = listOf(btnAll, btnActive, btnPending, btnSold)
    val optionLabels = options.map { it.label }
    buttons.forEachIndexed { idx, btn ->
        if (idx < optionLabels.size) {
            btn.visibility = View.VISIBLE
            btn.text = optionLabels[idx]
        } else {
            btn.visibility = View.GONE
        }
    }

    row1.visibility = if (optionLabels.isEmpty()) View.GONE else View.VISIBLE
    row2.visibility = if (optionLabels.size > 2) View.VISIBLE else View.GONE

    val selectedBg = ColorStateList.valueOf(Color.parseColor("#DCFCE7"))
    val selectedStroke = ColorStateList.valueOf(Color.parseColor("#0B8B5B"))
    val normalBg = ColorStateList.valueOf(Color.parseColor("#F9FAFB"))
    val normalStroke = ColorStateList.valueOf(Color.parseColor("#E5E7EB"))

    var selected = selectedLabel?.takeIf { optionLabels.contains(it) } ?: optionLabels.first()

    fun updateButtons() {
        buttons.forEach { btn ->
            if (btn.visibility != View.VISIBLE) return@forEach
            val isSelected = btn.text.toString() == selected
            btn.backgroundTintList = if (isSelected) selectedBg else normalBg
            btn.strokeColor = if (isSelected) selectedStroke else normalStroke
            btn.strokeWidth = 1
        }
    }

    buttons.forEach { btn ->
        btn.setOnClickListener {
            selected = btn.text.toString()
            updateButtons()
        }
    }

    btnReset.setOnClickListener {
        selected = optionLabels.first()
        updateButtons()
    }

    btnApply.setOnClickListener {
        onApply(selected)
        dialog.dismiss()
    }

    updateButtons()
    dialog.show()
}
