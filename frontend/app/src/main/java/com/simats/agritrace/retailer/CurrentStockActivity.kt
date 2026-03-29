package com.simats.agritrace

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.simats.agritrace.ApiClient
import com.simats.agritrace.RetailerInventoryItemDto
import com.simats.agritrace.retailer.FilterSheetOption
import com.simats.agritrace.retailer.showRetailerFilterSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class CurrentStockActivity : AppCompatActivity() {

    data class StockItem(
        val product: String,
        val batchId: String,
        val qtyText: String,
        val status: Status
    )

    enum class Status { IN_STOCK, OUT_OF_STOCK, LOW_STOCK }

    private lateinit var etSearch: EditText
    private lateinit var rv: RecyclerView
    private lateinit var tvEmpty: android.widget.TextView
    private lateinit var tvError: android.widget.TextView
    private lateinit var progress: android.widget.ProgressBar

    private val allItems = mutableListOf<StockItem>()
    private val shownItems = mutableListOf<StockItem>()

    private lateinit var adapter: StockAdapter

    private val uiHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var currentFilter = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_current_stock)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val originalPaddingTop = v.paddingTop
            val originalPaddingBottom = v.paddingBottom
            val originalPaddingLeft = v.paddingLeft
            val originalPaddingRight = v.paddingRight
            
            v.setPadding(
                maxOf(originalPaddingLeft, sys.left),
                maxOf(originalPaddingTop, sys.top),
                maxOf(originalPaddingRight, sys.right),
                maxOf(originalPaddingBottom, sys.bottom)
            )
            insets
        }

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.ivFilter).setOnClickListener {
            showRetailerFilterSheet(
                context = this,
                title = "Filter Current Stock",
                hint = "Show stock by status",
                options = listOf(
                    FilterSheetOption("All"),
                    FilterSheetOption("In Stock"),
                    FilterSheetOption("Low Stock"),
                    FilterSheetOption("Out of Stock")
                ),
                selectedLabel = currentFilter
            ) { selected ->
                currentFilter = selected
                applyFilter(etSearch.text?.toString().orEmpty())
            }
        }

        etSearch = findViewById(R.id.etSearch)
        rv = findViewById(R.id.rvStock)
        tvEmpty = findViewById(R.id.tvEmpty)
        tvError = findViewById(R.id.tvError)
        progress = findViewById(R.id.progress)

        adapter = StockAdapter(shownItems)
        rv.adapter = adapter
        loadStock()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString().orEmpty()
                searchRunnable?.let { uiHandler.removeCallbacks(it) }
                searchRunnable = Runnable { applyFilter(q) }
                uiHandler.postDelayed(searchRunnable!!, 160)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        loadStock()
    }

    private fun loadStock() {
        progress.visibility = android.view.View.VISIBLE
        tvError.visibility = android.view.View.GONE
        tvEmpty.visibility = android.view.View.GONE
        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.retailerApi.getReceivedInventory()
                }
                if (!resp.ok) {
                    tvError.text = resp.error ?: "Failed to load stock"
                    tvError.visibility = android.view.View.VISIBLE
                    return@launch
                }
                allItems.clear()
                val filtered = resp.items.orEmpty().filter { it.batch_status?.uppercase(Locale.US) != "SOLD" }
                allItems.addAll(filtered.map { it.toStockItem() })
                applyFilter(etSearch.text?.toString().orEmpty())
            } catch (_: Exception) {
                tvError.visibility = android.view.View.VISIBLE
            } finally {
                progress.visibility = android.view.View.GONE
            }
        }
    }

    private fun applyFilter(queryRaw: String) {
        val q = queryRaw.trim().lowercase()
        shownItems.clear()

        shownItems.addAll(
            allItems.filter {
                matchesStatus(it.status) &&
                    (q.isEmpty() ||
                        it.product.lowercase().contains(q) ||
                        it.batchId.lowercase().contains(q))
            }
        )

        adapter.notifyDataSetChanged()
        tvEmpty.visibility = if (shownItems.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        rv.visibility = if (shownItems.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun matchesStatus(status: Status): Boolean {
        return when (currentFilter) {
            "In Stock" -> status == Status.IN_STOCK
            "Low Stock" -> status == Status.LOW_STOCK
            "Out of Stock" -> status == Status.OUT_OF_STOCK
            else -> true
        }
    }

    private inner class StockAdapter(
        private val items: List<StockItem>
    ) : RecyclerView.Adapter<StockVH>() {

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): StockVH {
            val v = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_current_stock_card, parent, false)
            return StockVH(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(h: StockVH, position: Int) {
            val item = items[position]
            h.tvProduct.text = item.product
            h.tvBatchId.text = item.batchId
            h.tvQty.text = item.qtyText

            val (dotColor, statusText, statusTextColor) = when (item.status) {
                Status.IN_STOCK -> Triple("#10B981", "In Stock", "#374151")
                Status.OUT_OF_STOCK -> Triple("#EF4444", "Out of Stock", "#374151")
                Status.LOW_STOCK -> Triple("#F59E0B", "Low Stock", "#374151")
            }

            h.tvStatus.text = statusText
            h.tvStatus.setTextColor(android.graphics.Color.parseColor(statusTextColor))

            val d = h.dot.background?.mutate()
            if (d != null) {
                DrawableCompat.setTint(d, android.graphics.Color.parseColor(dotColor))
            }
        }
    }

    private fun RetailerInventoryItemDto.toStockItem(): StockItem {
        val name = crop_name?.trim().orEmpty().ifBlank { "Batch" }
        val code = batch_code?.trim().orEmpty().ifBlank { batch_id.toString() }
        val qtyRaw = quantity_kg?.trim().orEmpty()
        val statusRaw = batch_status?.trim().orEmpty().uppercase(Locale.US)
        val status = if (statusRaw == "SOLD") Status.OUT_OF_STOCK else Status.IN_STOCK
        val qtyText = if (qtyRaw.isNotBlank()) "$qtyRaw kg" else "--"
        return StockItem(name, code, qtyText, status)
    }

    private class StockVH(v: android.view.View) : RecyclerView.ViewHolder(v) {
        val tvProduct: android.widget.TextView = v.findViewById(R.id.tvProduct)
        val tvBatchId: android.widget.TextView = v.findViewById(R.id.tvBatchId)
        val tvQty: android.widget.TextView = v.findViewById(R.id.tvQty)
        val dot: android.view.View = v.findViewById(R.id.dot)
        val tvStatus: android.widget.TextView = v.findViewById(R.id.tvStatus)
    }
}
