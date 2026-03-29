package com.simats.agritrace.retailer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.simats.agritrace.ApiClient
import com.simats.agritrace.LoginActivity
import com.simats.agritrace.R
import com.simats.agritrace.RetailerIncomingItemDto
import com.simats.agritrace.RetailerBatchVerification
import com.simats.agritrace.Session
import com.simats.agritrace.TransferActionReq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.Locale

class RetailerIncomingStockActivity : AppCompatActivity() {

    data class IncomingStock(
        val transferId: Long,
        val batchNumericId: Long,
        val batchCodeRaw: String,
        val product: String,
        val batchId: String,
        val fromDistributor: String,
        val quantity: String,
        val transferDate: String,
        val txHash: String
    )

    private lateinit var tvPending: TextView
    private lateinit var etSearch: EditText
    private lateinit var rv: RecyclerView
    private lateinit var tvEmpty: TextView

    private val allItems = mutableListOf<IncomingStock>()
    private val shownItems = mutableListOf<IncomingStock>()

    private lateinit var adapter: IncomingAdapter
    private var currentFilter = "All"

    private val uiHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    private var pendingAccept: IncomingStock? = null

    private val scanForAcceptLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val item = pendingAccept
            pendingAccept = null

            if (item == null) return@registerForActivityResult

            if (res.resultCode != RESULT_OK || res.data == null) {
                return@registerForActivityResult
            }

            val verified = res.data?.getBooleanExtra("verified_ok", false) == true
            if (!verified) {
                Toast.makeText(this, "Verification failed", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }

            doAccept(item)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check session validity before proceeding
        if (!Session.ensureValidOrClear(this)) {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            finish()
            return
        }
        
        enableEdgeToEdge()
        setContentView(R.layout.activity_retailer_incoming_stock)
        window.statusBarColor = android.graphics.Color.WHITE
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

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
                title = "Filter Incoming Stock",
                hint = "Show incoming by status",
                options = listOf(
                    FilterSheetOption("All"),
                    FilterSheetOption("Pending")
                ),
                selectedLabel = currentFilter
            ) { selected ->
                currentFilter = selected
                applyFilter(etSearch.text?.toString().orEmpty())
            }
        }

        tvPending = findViewById(R.id.tvPending)
        etSearch = findViewById(R.id.etSearch)
        rv = findViewById(R.id.rvIncoming)
        tvEmpty = findViewById(R.id.tvEmpty)

        adapter = IncomingAdapter(
            shownItems,
            onAccept = { item -> startScanBeforeAccept(item) },
            onReject = { item -> onDecision(item, accepted = false) },
            onCopyTx = { fullHash -> copyToClipboard(fullHash) },
            onOpen = { openDetails(it) }
        )
        rv.adapter = adapter

        loadIncoming()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString().orEmpty()
                searchRunnable?.let { uiHandler.removeCallbacks(it) }
                searchRunnable = Runnable { applyFilter(q) }
                uiHandler.postDelayed(searchRunnable!!, 180)
            }
        })
    }

    private fun loadIncoming() {
        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.retailerApi.getIncomingStock()
                }
                if (!resp.ok) {
                    // Check if it's an unauthorized error
                    if (resp.error?.contains("Unauthorized", ignoreCase = true) == true) {
                        handleUnauthorized()
                        return@launch
                    }
                    allItems.clear()
                    shownItems.clear()
                    adapter.notifyDataSetChanged()
                    showEmpty(resp.error ?: "Unable to load incoming stock")
                    return@launch
                }
                allItems.clear()
                allItems.addAll(resp.items.orEmpty().map { it.toUi() })
                applyFilter(etSearch.text?.toString().orEmpty())
            } catch (e: HttpException) {
                // Handle HTTP errors, especially 401 Unauthorized
                if (e.code() == 401) {
                    handleUnauthorized()
                    return@launch
                }
                allItems.clear()
                shownItems.clear()
                adapter.notifyDataSetChanged()
                showEmpty("Unable to load incoming stock")
            } catch (_: Exception) {
                allItems.clear()
                shownItems.clear()
                adapter.notifyDataSetChanged()
                showEmpty("Unable to load incoming stock")
            }
        }
    }
    
    private fun handleUnauthorized() {
        Session.clear(this)
        startActivity(Intent(this, LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        finish()
    }

    private fun applyFilter(queryRaw: String) {
        val q = queryRaw.trim().lowercase()
        shownItems.clear()

        shownItems.addAll(
            allItems.filter {
                matchesStatus() &&
                        (q.isEmpty() ||
                                it.batchId.lowercase().contains(q) ||
                                it.product.lowercase().contains(q))
            }
        )

        adapter.notifyDataSetChanged()
        updatePendingPill()
        updateEmpty(shownItems.isEmpty())
    }

    private fun matchesStatus(): Boolean {
        return currentFilter == "All" || currentFilter == "Pending"
    }

    private fun updatePendingPill() {
        val n = allItems.size
        tvPending.text = "$n Pending"
    }

    private fun updateEmpty(isEmpty: Boolean) {
        tvEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        rv.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showEmpty(message: String) {
        tvEmpty.text = message
        updateEmpty(true)
    }

    private fun openDetails(item: IncomingStock) {
        val intent = Intent(this, RetailerBatchVerification::class.java).apply {
            if (item.batchNumericId > 0) {
                putExtra("batch_id", item.batchNumericId.toInt())
            }
            if (item.batchCodeRaw.isNotBlank()) {
                putExtra("batch_code", item.batchCodeRaw)
            }
        }
        startActivity(intent)
    }

    private fun startScanBeforeAccept(item: IncomingStock) {
        pendingAccept = item
        val i = Intent(this, RetailerScanQrActivity::class.java).apply {
            putExtra("mode", "ACCEPT_STOCK")
            putExtra("expected_batch_id", item.batchNumericId)
            putExtra("expected_batch_code", item.batchCodeRaw)
            putExtra("expected_transfer_id", item.transferId)
        }
        scanForAcceptLauncher.launch(i)
    }

    private fun doAccept(item: IncomingStock) {
        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.retailerApi.acceptStock(TransferActionReq(transfer_id = item.transferId))
                }
                if (!resp.ok) {
                    if (resp.error?.contains("Unauthorized", ignoreCase = true) == true) {
                        handleUnauthorized()
                        return@launch
                    }
                    Toast.makeText(
                        this@RetailerIncomingStockActivity,
                        resp.error ?: "Accept failed",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                Toast.makeText(this@RetailerIncomingStockActivity, "Stock accepted", Toast.LENGTH_SHORT).show()
                loadIncoming()
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    handleUnauthorized()
                    return@launch
                }
                Toast.makeText(this@RetailerIncomingStockActivity, "Accept failed", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this@RetailerIncomingStockActivity, "Accept failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onDecision(item: IncomingStock, accepted: Boolean) {
        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    if (accepted) {
                        ApiClient.retailerApi.acceptStock(TransferActionReq(transfer_id = item.transferId))
                    } else {
                        ApiClient.retailerApi.rejectStock(TransferActionReq(transfer_id = item.transferId))
                    }
                }
                if (!resp.ok) {
                    if (resp.error?.contains("Unauthorized", ignoreCase = true) == true) {
                        handleUnauthorized()
                        return@launch
                    }
                    Toast.makeText(
                        this@RetailerIncomingStockActivity,
                        resp.error ?: if (accepted) "Accept failed" else "Reject failed",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                Toast.makeText(
                    this@RetailerIncomingStockActivity,
                    if (accepted) "Stock accepted" else "Stock rejected",
                    Toast.LENGTH_SHORT
                ).show()
                loadIncoming()
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    handleUnauthorized()
                    return@launch
                }
                Toast.makeText(
                    this@RetailerIncomingStockActivity,
                    if (accepted) "Accept failed" else "Reject failed",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (_: Exception) {
                Toast.makeText(
                    this@RetailerIncomingStockActivity,
                    if (accepted) "Accept failed" else "Reject failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("tx_hash", text))
        Toast.makeText(this, "Transaction copied", Toast.LENGTH_SHORT).show()
    }

    private class IncomingAdapter(
        private val items: List<IncomingStock>,
        private val onAccept: (IncomingStock) -> Unit,
        private val onReject: (IncomingStock) -> Unit,
        private val onCopyTx: (String) -> Unit,
        private val onOpen: (IncomingStock) -> Unit
    ) : RecyclerView.Adapter<IncomingVH>() {

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): IncomingVH {
            val v = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_retailer_incoming_stock_card, parent, false)
            return IncomingVH(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(h: IncomingVH, position: Int) {
            val transfer = items[position]

            h.tvProduct.text = transfer.product
            h.tvBatchId.text = transfer.batchId
            h.tvFromValue.text = transfer.fromDistributor
            h.tvQtyValue.text = transfer.quantity
            h.tvDateValue.text = transfer.transferDate

            val full = transfer.txHash
            if (full.isBlank()) {
                h.boxTx.visibility = View.GONE
            } else {
                h.boxTx.visibility = View.VISIBLE
                val shown = if (full.length > 68) full.substring(0, 68) + "..." else full
                h.tvTxHash.text = shown
            }

            h.btnAccept.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check_white, 0, 0, 0)
            h.btnReject.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_close_red, 0, 0, 0)

            h.btnAccept.setOnClickListener { onAccept(transfer) }
            h.btnReject.setOnClickListener { onReject(transfer) }
            h.boxTx.setOnClickListener { onCopyTx(full) }
            h.card.setOnClickListener { onOpen(transfer) }
        }
    }

    private class IncomingVH(v: android.view.View) : RecyclerView.ViewHolder(v) {
        val tvProduct: TextView = v.findViewById(R.id.tvProduct)
        val tvBatchId: TextView = v.findViewById(R.id.tvBatchId)
        val tvFromValue: TextView = v.findViewById(R.id.tvFromValue)
        val tvQtyValue: TextView = v.findViewById(R.id.tvQtyValue)
        val tvDateValue: TextView = v.findViewById(R.id.tvDateValue)
        val tvTxHash: TextView = v.findViewById(R.id.tvTxHash)
        val boxTx: com.google.android.material.card.MaterialCardView = v.findViewById(R.id.boxTx)
        val btnAccept: androidx.appcompat.widget.AppCompatButton = v.findViewById(R.id.btnAccept)
        val btnReject: androidx.appcompat.widget.AppCompatButton = v.findViewById(R.id.btnReject)
        val card: com.google.android.material.card.MaterialCardView = v.findViewById(R.id.card)
    }

    private fun RetailerIncomingItemDto.toUi(): IncomingStock {
        val code = batch_code?.trim().orEmpty()
        val qty = quantity_kg?.trim().orEmpty()
        val batchLabel = if (code.isNotBlank()) code else batch_id.toString()
        return IncomingStock(
            transferId = transfer_id,
            batchNumericId = batch_id,
            batchCodeRaw = code,
            product = crop_name?.trim().orEmpty().ifBlank { "Batch" },
            batchId = batchLabel,
            fromDistributor = from_name?.trim().orEmpty().ifBlank { "--" },
            quantity = if (qty.isNotBlank()) "$qty kg" else "--",
            transferDate = formatTime(created_at),
            txHash = tx_hash?.trim().orEmpty()
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
}
