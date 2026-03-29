package com.simats.agritrace.dist

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.simats.agritrace.ApiClient
import com.simats.agritrace.ReceivedProductDto
import com.simats.agritrace.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class DistributorReceivedProductsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var etSearch: EditText
    private lateinit var btnFilter: FrameLayout
    private lateinit var rv: RecyclerView
    private lateinit var emptyState: View
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptySub: TextView

    // Adapter kept inside activity (as you prefer in other screens)
    private lateinit var adapter: ReceivedAdapter

    private val allItems = mutableListOf<ReceivedRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_distributor_received_products)

        btnBack = findViewById(R.id.btnBack)
        etSearch = findViewById(R.id.etSearch)
        btnFilter = findViewById(R.id.btnFilter)
        rv = findViewById(R.id.rvReceived)
        emptyState = findViewById(R.id.emptyState)
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle)
        tvEmptySub = findViewById(R.id.tvEmptySub)

        btnBack.setOnClickListener { finish() }

        adapter = ReceivedAdapter { row -> openDetails(row) }
        rv.adapter = adapter

        loadReceived()

        // Search
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            applyFilter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Filter button (UI only; keep behavior simple, no regressions)
        btnFilter.setOnClickListener {
            // You can open a bottom sheet later. For now just re-apply filter.
            applyFilter(etSearch.text?.toString().orEmpty())
        }
    }

    override fun onResume() {
        super.onResume()
        loadReceived()
    }

    private fun loadReceived() {
        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.distributorApi.getReceivedProducts()
                }
                if (!resp.ok) {
                    Toast.makeText(this@DistributorReceivedProductsActivity, resp.error ?: "Failed to load received products", Toast.LENGTH_SHORT).show()
                    setEmpty(true)
                    return@launch
                }
                allItems.clear()
                allItems.addAll(resp.data.orEmpty().map { it.toUi() })
                applyFilter(etSearch.text?.toString().orEmpty())
            } catch (_: Exception) {
                Toast.makeText(this@DistributorReceivedProductsActivity, "Failed to load received products", Toast.LENGTH_SHORT).show()
                setEmpty(true)
            }
        }
    }

    private fun applyFilter(qRaw: String) {
        val q = qRaw.trim().lowercase(Locale.US)
        val out = if (q.isBlank()) {
            allItems.toList()
        } else {
            allItems.filter {
                it.batch.lowercase(Locale.US).contains(q) ||
                        it.name.lowercase(Locale.US).contains(q) ||
                        it.from.lowercase(Locale.US).contains(q)
            }
        }

        adapter.submit(out)
        setEmpty(out.isEmpty())
    }

    private fun setEmpty(isEmpty: Boolean) {
        if (isEmpty) {
            rv.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            emptyState.visibility = View.GONE
            rv.visibility = View.VISIBLE
        }
    }

    data class ReceivedRow(
        val name: String,
        val batch: String,
        val statusText: String,
        val statusColor: String, // "GREEN" / "ORANGE" etc.
        val from: String,
        val whenText: String,
        val batchId: Int,
        val batchCode: String
    )

    private fun ReceivedProductDto.toUi(): ReceivedRow {
        val statusRaw = transfer_status?.uppercase(Locale.US).orEmpty()
        val statusText = when (statusRaw) {
            "PICKED_UP" -> "In Stock"
            "IN_TRANSIT" -> "In Transit"
            else -> "In Transit"
        }
        val statusColor = if (statusRaw == "PICKED_UP") "GREEN" else "ORANGE"
        val batchCode = batch_code?.trim().orEmpty()
        return ReceivedRow(
            name = crop_name?.trim().orEmpty().ifBlank { "Batch" },
            batch = if (batchCode.isNotBlank()) batchCode else "Batch #$batch_id",
            statusText = statusText,
            statusColor = statusColor,
            from = farmer_name?.trim().orEmpty().ifBlank { "--" },
            whenText = formatTime(transfer_created_at),
            batchId = batch_id.toInt(),
            batchCode = batchCode
        )
    }

    private fun openDetails(row: ReceivedRow) {
        val intent = android.content.Intent(this, DistributorBatchDetailsActivity::class.java).apply {
            if (row.batchId > 0) putExtra("batch_id", row.batchId)
            if (row.batchCode.isNotBlank()) putExtra("batch_code", row.batchCode)
        }
        startActivity(intent)
    }

    private fun formatTime(raw: String?): String {
        if (raw.isNullOrBlank()) return "--"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val dt = parser.parse(raw) ?: return raw
            val fmt = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.US)
            fmt.format(dt)
        } catch (_: Exception) {
            raw
        }
    }

    private class ReceivedAdapter(
        private val onClick: (ReceivedRow) -> Unit
    ) : RecyclerView.Adapter<ReceivedVH>() {

        private val items = ArrayList<ReceivedRow>()

        fun submit(newItems: List<ReceivedRow>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ReceivedVH {
            val v = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_received_product, parent, false)
            return ReceivedVH(v, onClick)
        }

        override fun onBindViewHolder(holder: ReceivedVH, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size
    }

    private class ReceivedVH(
        itemView: View,
        private val onClick: (ReceivedRow) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvBatch: TextView = itemView.findViewById(R.id.tvBatch)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val dot: View = itemView.findViewById(R.id.statusDot)
        private val tvFrom: TextView = itemView.findViewById(R.id.tvFrom)
        private val tvWhen: TextView = itemView.findViewById(R.id.tvWhen)

        fun bind(row: ReceivedRow) {
            tvName.text = row.name
            tvBatch.text = row.batch
            tvStatus.text = row.statusText
            tvFrom.text = row.from
            tvWhen.text = row.whenText

            // Keep it safe: only change tint when drawable is GradientDrawable; otherwise ignore
            val bg = dot.background
            if (bg is android.graphics.drawable.GradientDrawable) {
                val color = when (row.statusColor) {
                    "ORANGE" -> android.graphics.Color.parseColor("#F59E0B")
                    else -> android.graphics.Color.parseColor("#10B981")
                }
                bg.setColor(color)
            }

            itemView.setOnClickListener { onClick(row) }
        }
    }
}
