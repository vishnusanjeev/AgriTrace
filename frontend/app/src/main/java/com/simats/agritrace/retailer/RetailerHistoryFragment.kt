package com.simats.agritrace.retailer

import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.simats.agritrace.ApiClient
import com.simats.agritrace.R
import com.simats.agritrace.RetailerSaleItemDto
import com.simats.agritrace.RetailerBatchVerification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class RetailerHistoryFragment : Fragment() {

    private lateinit var rv: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: RetailerSalesAdapter

    private val allRows = mutableListOf<SaleRow>()
    private var visibleRows: List<SaleRow> = emptyList()
    private var currentFilter = "All"

    private var didInitialLoad = false
    private var lastLoadAtMs: Long = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_retailerhistory, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        view.findViewById<ImageView>(R.id.btnFilter).setOnClickListener {
            showRetailerFilterSheet(
                context = requireContext(),
                title = "Filter Sales History",
                hint = "Show sales by time",
                options = listOf(
                    FilterSheetOption("All"),
                    FilterSheetOption("Today"),
                    FilterSheetOption("This Week"),
                    FilterSheetOption("This Month")
                ),
                selectedLabel = currentFilter
            ) { selected ->
                currentFilter = selected
                applyFilters()
            }
        }

        rv = view.findViewById(R.id.rvSales)
        tvEmpty = view.findViewById(R.id.tvEmpty)

        adapter = RetailerSalesAdapter(mutableListOf())
        rv.adapter = adapter

        attachRowClickOpenBatchDetails()

        loadSales(force = true)
    }

    override fun onResume() {
        super.onResume()
        val now = System.currentTimeMillis()
        if (!didInitialLoad || (now - lastLoadAtMs) > 1500L) {
            loadSales(force = false)
        }
    }

    private fun attachRowClickOpenBatchDetails() {
        val detector = GestureDetector(
            requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean = true
            }
        )

        rv.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (!detector.onTouchEvent(e)) return false
                val child = rv.findChildViewUnder(e.x, e.y) ?: return false
                val pos = rv.getChildAdapterPosition(child)
                if (pos == RecyclerView.NO_POSITION) return false

                val row = visibleRows.getOrNull(pos) ?: return false
                openBatchDetails(row)
                return true
            }
        })
    }

    private fun openBatchDetails(row: SaleRow) {
        val batchId = parseBatchIdFromRow(row)
        val batchCode = row.batchCode.trim()

        if ((batchId ?: 0L) <= 0L && batchCode.isBlank()) return

        val i = Intent(requireContext(), RetailerBatchVerification::class.java).apply {
            if ((batchId ?: 0L) > 0L) putExtra("batch_id", batchId!!.toInt())
            if (batchCode.isNotBlank()) putExtra("batch_code", batchCode)
        }
        startActivity(i)
    }

    private fun parseBatchIdFromRow(row: SaleRow): Long? {
        val s = row.saleId.trim()

        // Our code sets saleId like "S-<batch_id>"
        if (s.startsWith("S-", ignoreCase = true)) {
            val idPart = s.substringAfter("S-", "").trim()
            idPart.toLongOrNull()?.let { if (it > 0) return it }
        }

        // Fallback: sometimes batchCode might be numeric like "123"
        row.batchCode.trim().toLongOrNull()?.let { if (it > 0) return it }

        return null
    }

    private fun bind(rows: List<SaleRow>) {
        if (!isAdded) return
        visibleRows = rows

        if (rows.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            rv.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            rv.visibility = View.VISIBLE
            adapter.setAll(rows)
        }
    }

    private fun loadSales(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastLoadAtMs) < 1000L) return

        lastLoadAtMs = now
        didInitialLoad = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { ApiClient.retailerApi.getSalesHistory() }
                if (!resp.ok) {
                    allRows.clear()
                    bind(emptyList())
                    return@launch
                }

                val mapped = resp.items.orEmpty().map { it.toRow() }
                allRows.clear()
                allRows.addAll(mapped)
                applyFilters()
            } catch (_: Exception) {
                allRows.clear()
                bind(emptyList())
            }
        }
    }

    private fun applyFilters() {
        val filtered = allRows.filter { matchesRange(it.soldAtMillis) }
        bind(filtered)
    }

    private fun matchesRange(millis: Long): Boolean {
        if (currentFilter == "All") return true
        if (millis <= 0L) return false

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = millis }

        return when (currentFilter) {
            "Today" ->
                now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                        now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)

            "This Week" ->
                now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                        now.get(Calendar.WEEK_OF_YEAR) == target.get(Calendar.WEEK_OF_YEAR)

            "This Month" ->
                now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                        now.get(Calendar.MONTH) == target.get(Calendar.MONTH)

            else -> true
        }
    }

    private fun RetailerSaleItemDto.toRow(): SaleRow {
        val title = crop_name?.trim().orEmpty().ifBlank { "Batch" }
        val batch = batch_code?.trim().orEmpty().ifBlank { batch_id.toString() }

        // Prefer created_at for non-sold, sold_at for sold (for your filter + whenText)
        val rawStatus = status?.trim()?.uppercase(Locale.US).orEmpty()
        val isSold = rawStatus == "SOLD"

        val timeRaw = if (isSold) sold_at else (created_at ?: sold_at)
        val whenText = formatTime(timeRaw)
        val millis = parseMillis(timeRaw)

        val saleId = "S-$batch_id"

        val amountText = when (rawStatus) {
            "SOLD" -> "Sold"
            "PENDING" -> "Pending"
            "ACTIVE", "AVAILABLE", "IN_STOCK", "RECEIVED_BY_RETAILER" -> "Available"
            else -> if (isSold) "Sold" else "Available"
        }

        return SaleRow(
            title = title,
            batchCode = batch,
            saleId = saleId,
            whenText = whenText,
            amountText = amountText,
            soldAtMillis = millis
        )
    }


    private fun formatTime(raw: String?): String {
        if (raw.isNullOrBlank()) return "--"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val dt = parser.parse(raw) ?: return raw
            val cal = Calendar.getInstance().apply { time = dt }
            val now = Calendar.getInstance()
            val timeFmt = SimpleDateFormat("hh:mm a", Locale.US)
            val dateFmt = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            when {
                isSameDay(now, cal) -> "Today, ${timeFmt.format(dt)}"
                isYesterday(now, cal) -> "Yesterday, ${timeFmt.format(dt)}"
                else -> dateFmt.format(dt)
            }
        } catch (_: Exception) {
            raw
        }
    }

    private fun formatPrice(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return "$--"
        if (trimmed.startsWith("$")) return trimmed
        return try {
            val value = trimmed.toDouble()
            String.format(Locale.US, "$%.2f", value)
        } catch (_: Exception) {
            "$trimmed"
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(now: Calendar, target: Calendar): Boolean {
        val yesterday = now.clone() as Calendar
        yesterday.add(Calendar.DAY_OF_YEAR, -1)
        return isSameDay(yesterday, target)
    }

    private fun parseMillis(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            parser.parse(raw)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
