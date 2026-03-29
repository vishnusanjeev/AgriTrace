package com.simats.agritrace.retailer

import android.os.Bundle
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.simats.agritrace.ApiClient
import com.simats.agritrace.R
import com.simats.agritrace.RetailerBatchVerification
import com.simats.agritrace.RetailerInventoryItemDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class RetailerReceivedInventoryActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var etSearch: EditText
    private lateinit var btnFilter: View
    private lateinit var rvInventory: RecyclerView
    private lateinit var tvEmpty: TextView

    private val allItems = mutableListOf<Row>()
    private val adapter = InventoryAdapter { row -> openDetails(row) }
    private var currentFilter = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_retailer_received_inventory)

        // Apply window insets to root container, preserving original padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootRetailInv)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val originalPaddingTop = v.paddingTop
            val originalPaddingBottom = v.paddingBottom
            val originalPaddingLeft = v.paddingLeft
            val originalPaddingRight = v.paddingRight
            
            v.setPadding(
                maxOf(originalPaddingLeft, systemBars.left),
                maxOf(originalPaddingTop, systemBars.top),
                maxOf(originalPaddingRight, systemBars.right),
                maxOf(originalPaddingBottom, systemBars.bottom)
            )
            insets
        }

        btnBack = findViewById(R.id.btnBack)
        etSearch = findViewById(R.id.etSearch)
        btnFilter = findViewById(R.id.btnFilter)
        rvInventory = findViewById(R.id.rvInventory)
        tvEmpty = findViewById(R.id.tvEmpty)

        btnBack.setOnClickListener { finish() }

        btnFilter.setOnClickListener {
            showRetailerFilterSheet(
                context = this,
                title = "Filter Received Inventory",
                hint = "Show inventory by status",
                options = listOf(
                    FilterSheetOption("All"),
                    FilterSheetOption("In Stock"),
                    FilterSheetOption("Sold Out")
                ),
                selectedLabel = currentFilter
            ) { selected ->
                currentFilter = selected
                applyFilters(etSearch.text?.toString().orEmpty())
            }
        }

        rvInventory.layoutManager = LinearLayoutManager(this)
        rvInventory.adapter = adapter
        rvInventory.setHasFixedSize(true)

        loadInventory()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                applyFilters(s?.toString().orEmpty())
            }
        })
    }

    private fun loadInventory() {
        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.retailerApi.getReceivedInventory()
                }
                if (!resp.ok) {
                    allItems.clear()
                    adapter.submitList(emptyList())
                    showEmpty(resp.error ?: "Unable to load inventory")
                    return@launch
                }
                allItems.clear()
                allItems.addAll(resp.items.orEmpty().map { it.toRow() })
                applyFilters(etSearch.text?.toString().orEmpty())
            } catch (_: Exception) {
                allItems.clear()
                adapter.submitList(emptyList())
                showEmpty("Unable to load inventory")
            }
        }
    }

    private fun applyFilters(raw: String) {
        val q = raw.trim().lowercase()
        val filtered = allItems.filter { r ->
            matchesStatus(r.status) &&
                (q.isEmpty() ||
                    r.name.lowercase().contains(q) ||
                    r.batchId.lowercase().contains(q) ||
                    r.from.lowercase().contains(q) ||
                    r.status.lowercase().contains(q))
        }
        adapter.submitList(filtered)
        updateEmpty(filtered.isEmpty())
    }

    private fun matchesStatus(status: String): Boolean {
        return when (currentFilter) {
            "In Stock" -> status == "In Stock"
            "Sold Out" -> status == "Sold Out"
            else -> true
        }
    }

    private fun updateEmpty(isEmpty: Boolean) {
        tvEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        rvInventory.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showEmpty(message: String) {
        tvEmpty.text = message
        updateEmpty(true)
    }

    data class Row(
        val batchIdRaw: Long,
        val batchCodeRaw: String,
        val name: String,
        val batchId: String,
        val from: String,
        val acceptedAt: String,
        val qty: String,
        val receivedAt: String,
        val status: String,
        val statusDot: StatusDot
    )

    enum class StatusDot { GREEN }

    private class InventoryAdapter(
        private val onClick: (Row) -> Unit
    ) : ListAdapter<Row, InventoryAdapter.VH>(DIFF) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_retailer_received_inventory, parent, false)
            return VH(v, onClick)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
        }

        class VH(itemView: View, private val onClick: (Row) -> Unit) :
            RecyclerView.ViewHolder(itemView) {

            private val card: CardView = itemView.findViewById(R.id.card)
            private val tvName: TextView = itemView.findViewById(R.id.tvName)
            private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
            private val statusDot: View = itemView.findViewById(R.id.statusDot)
            private val tvBatch: TextView = itemView.findViewById(R.id.tvBatch)
            private val tvFrom: TextView = itemView.findViewById(R.id.tvFrom)
            private val tvAcceptedPill: TextView = itemView.findViewById(R.id.tvAcceptedPill)
            private val tvQty: TextView = itemView.findViewById(R.id.tvQty)
            private val tvReceived: TextView = itemView.findViewById(R.id.tvReceived)

            fun bind(row: Row) {
                tvName.text = row.name
                tvStatus.text = row.status
                tvBatch.text = row.batchId
                tvFrom.text = "From:  ${row.from}"
                tvAcceptedPill.text = "Accepted:   ${row.acceptedAt}"
                tvQty.text = "Qty:  ${row.qty}"
                tvReceived.text = "Received:  ${row.receivedAt}"

                // statusDot uses drawable bg_status_dot_green already; keep as-is.
                statusDot.visibility = View.VISIBLE

                card.setOnClickListener { onClick(row) }
            }
        }

        companion object {
            private val DIFF = object : DiffUtil.ItemCallback<Row>() {
                override fun areItemsTheSame(oldItem: Row, newItem: Row): Boolean =
                    oldItem.batchId == newItem.batchId

                override fun areContentsTheSame(oldItem: Row, newItem: Row): Boolean =
                    oldItem == newItem
            }
        }
    }

    private fun openDetails(row: Row) {
        val intent = Intent(this, RetailerBatchVerification::class.java).apply {
            if (row.batchIdRaw > 0) {
                putExtra("batch_id", row.batchIdRaw.toInt())
            }
            if (row.batchCodeRaw.isNotBlank()) {
                putExtra("batch_code", row.batchCodeRaw)
            }
        }
        startActivity(intent)
    }

    private fun RetailerInventoryItemDto.toRow(): Row {
        val code = batch_code?.trim().orEmpty()
        val qtyRaw = quantity_kg?.trim().orEmpty()
        val from = from_name?.trim().orEmpty().ifBlank { "--" }
        val farmer = farmer_name?.trim().orEmpty()
        val fromText = if (farmer.isNotBlank()) "$from • $farmer" else from
        val statusRaw = batch_status?.trim().orEmpty().uppercase(Locale.US)
        val statusText = if (statusRaw == "SOLD") "Sold Out" else "In Stock"
        val acceptedAt = formatTime(transfer_created_at)
        return Row(
            batchIdRaw = batch_id,
            batchCodeRaw = code,
            name = crop_name?.trim().orEmpty().ifBlank { "Batch" },
            batchId = if (code.isNotBlank()) code else batch_id.toString(),
            from = fromText,
            acceptedAt = acceptedAt,
            qty = if (qtyRaw.isNotBlank()) "$qtyRaw kg" else "--",
            receivedAt = formatDateOnly(transfer_created_at),
            status = statusText,
            statusDot = StatusDot.GREEN
        )
    }

    private fun formatTime(raw: String?): String {
        if (raw.isNullOrBlank()) return "--"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val dt = parser.parse(raw) ?: return raw
            val fmt = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.US)
            fmt.format(dt)
        } catch (_: Exception) {
            raw ?: "--"
        }
    }

    private fun formatDateOnly(raw: String?): String {
        if (raw.isNullOrBlank()) return "--"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val dt = parser.parse(raw) ?: return raw
            val fmt = SimpleDateFormat("MMM dd", Locale.US)
            fmt.format(dt)
        } catch (_: Exception) {
            raw ?: "--"
        }
    }
}
