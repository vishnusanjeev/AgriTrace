package com.simats.agritrace.customer

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.simats.agritrace.ApiClient
import com.simats.agritrace.CustomerBatchVerification
import com.simats.agritrace.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CustomerHistoryFragment : Fragment() {

    private var searchJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_customer_history, container, false)

        val etSearch = view.findViewById<EditText>(R.id.etSearch)
        val list = view.findViewById<LinearLayout>(R.id.historyList)
        val progress = view.findViewById<ProgressBar>(R.id.progressHistory)
        val empty = view.findViewById<TextView>(R.id.tvHistoryEmpty)

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchJob?.cancel()
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(350)
                    loadHistory(s?.toString()?.trim().orEmpty(), list, progress, empty)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        viewLifecycleOwner.lifecycleScope.launch {
            loadHistory("", list, progress, empty)
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        view?.let { root ->
            val list = root.findViewById<LinearLayout>(R.id.historyList)
            val progress = root.findViewById<ProgressBar>(R.id.progressHistory)
            val empty = root.findViewById<TextView>(R.id.tvHistoryEmpty)
            viewLifecycleOwner.lifecycleScope.launch {
                loadHistory("", list, progress, empty)
            }
        }
    }

    private suspend fun loadHistory(
        query: String,
        list: LinearLayout,
        progress: ProgressBar,
        empty: TextView
    ) {
        progress.isVisible = true
        empty.isVisible = false
        list.removeAllViews()

        try {
            val resp = withContext(Dispatchers.IO) {
                ApiClient.consumerApi.scanHistory(query.ifBlank { null })
            }
            progress.isVisible = false

            if (!resp.ok) {
                toast(resp.error ?: "Failed to load history")
                empty.isVisible = true
                return
            }

            val items = resp.items ?: emptyList()
            empty.isVisible = items.isEmpty()
            val inflater = LayoutInflater.from(requireContext())
            for (item in items) {
                val row = inflater.inflate(R.layout.item_customer_history, list, false)
                row.findViewById<TextView>(R.id.tvHistoryName).text = item.crop_name ?: "Unknown"
                row.findViewById<TextView>(R.id.tvHistoryTime).text = formatTime(item.scanned_at)
                bindStatus(row, item.result)
                row.setOnClickListener {
                    val intent = Intent(requireContext(), CustomerBatchVerification::class.java).apply {
                        putExtra("batch_id", item.batch_id)
                        putExtra("batch_code", item.batch_code ?: "")
                    }
                    startActivity(intent)
                }
                list.addView(row)
            }
        } catch (_: Exception) {
            progress.isVisible = false
            empty.isVisible = true
            toast("Failed to load history")
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    private fun bindStatus(row: View, result: String?) {
        val pill = row.findViewById<View>(R.id.statusPill)
        val icon = row.findViewById<android.widget.ImageView>(R.id.statusIcon)
        val isTampered = result?.uppercase(Locale.US)?.contains("TAMPER") == true ||
            result?.uppercase(Locale.US)?.contains("MISMATCH") == true

        if (isTampered) {
            pill.setBackgroundResource(R.drawable.bg_pill_orange)
            icon.setImageResource(R.drawable.ic_alert)
            icon.setColorFilter(android.graphics.Color.parseColor("#B45309"))
        } else {
            pill.setBackgroundResource(R.drawable.bg_verified_pill_green)
            icon.setImageResource(R.drawable.ic_shield_check)
            icon.setColorFilter(android.graphics.Color.parseColor("#0F7A57"))
        }
    }

    private fun formatTime(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val dt = parser.parse(raw) ?: return raw
            val cal = Calendar.getInstance()
            val now = Calendar.getInstance()
            cal.time = dt
            val timePart = SimpleDateFormat("hh:mm a", Locale.US).format(dt)
            when {
                now.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR) ->
                    "Today, $timePart"
                now.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) - cal.get(Calendar.DAY_OF_YEAR) == 1 ->
                    "Yesterday, $timePart"
                else ->
                    SimpleDateFormat("MMM dd, yyyy", Locale.US).format(dt)
            }
        } catch (_: Exception) {
            raw ?: ""
        }
    }
}
