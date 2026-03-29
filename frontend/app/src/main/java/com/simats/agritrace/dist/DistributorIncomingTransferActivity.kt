package com.simats.agritrace

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.simats.agritrace.ApiClient
import com.simats.agritrace.IncomingTransferDto
import com.simats.agritrace.ScanVerifyReq
import com.simats.agritrace.TransferActionReq
import com.simats.agritrace.dist.DistributorConfirmScanActivity
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class DistributorIncomingTransferActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var tvPendingChip: TextView
    private lateinit var progress: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var tvError: TextView

    private val allItems = mutableListOf<IncomingTransfer>()
    private val filtered = mutableListOf<IncomingTransfer>()
    private lateinit var adapter: IncomingAdapter
    private var statusFilter: String = "ALL"

    private var pendingAcceptItem: IncomingTransfer? = null

    private val scanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val item = pendingAcceptItem
            pendingAcceptItem = null

            if (item == null) return@registerForActivityResult

            if (res.resultCode != RESULT_OK) {
                toast("Scan cancelled")
                return@registerForActivityResult
            }

            val qrPayload = res.data?.getStringExtra(DistributorConfirmScanActivity.EXTRA_QR_PAYLOAD)
                ?.trim().orEmpty()

            if (qrPayload.isBlank()) {
                toast("Invalid scan")
                return@registerForActivityResult
            }

            verifyThenAcceptOrReject(item, qrPayload)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_distributor_incoming_transfers)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        tvPendingChip = findViewById(R.id.tvPendingChip)
        etSearch = findViewById(R.id.etSearch)
        rv = findViewById(R.id.rvTransfers)
        progress = findViewById(R.id.progress)
        tvEmpty = findViewById(R.id.tvEmpty)
        tvError = findViewById(R.id.tvError)

        adapter = IncomingAdapter(
            filtered,
            onAccept = { startScanToAccept(it) },
            onReject = { rejectItem(it) },
            onCopyTx = { copyToClipboard("Blockchain Transaction", it) },
            onOpen = { openDetails(it) }
        )

        rv.layoutManager = NonScrollableLinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<View>(R.id.btnFilter).setOnClickListener { anchor ->
            showFilterMenu(anchor)
        }

        updatePendingChip()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyFilter(s?.toString()?.trim().orEmpty())
            }
        })

        loadIncoming()
    }

    override fun onResume() {
        super.onResume()
        loadIncoming()
    }

    private fun loadIncoming() {
        progress.visibility = View.VISIBLE
        tvError.visibility = View.GONE
        tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.distributorApi.getIncomingTransfers()
                }
                if (!resp.ok) {
                    showError(resp.error ?: "Failed to load incoming transfers")
                    return@launch
                }

                val mapped = resp.data.orEmpty().map { it.toUi() }
                allItems.clear()
                allItems.addAll(mapped)

                applyFilter(etSearch.text?.toString()?.trim().orEmpty())

                if (allItems.isEmpty()) tvEmpty.visibility = View.VISIBLE
            } catch (_: Exception) {
                showError("Failed to load incoming transfers")
            } finally {
                progress.visibility = View.GONE
            }
        }
    }

    private fun applyFilter(q: String) {
        filtered.clear()
        val lower = q.lowercase()
        filtered.addAll(allItems.filter { item ->
            val matchesSearch = if (lower.isBlank()) true else {
                item.batchId.lowercase().contains(lower) ||
                        item.batchCode.lowercase().contains(lower) ||
                        item.product.lowercase().contains(lower) ||
                        item.fromFarmer.lowercase().contains(lower)
            }
            val matchesStatus = statusFilter == "ALL" || normalizeStatus(item.status) == statusFilter
            matchesSearch && matchesStatus
        })

        adapter.notifyDataSetChanged()
        updatePendingChip()

        if (filtered.isEmpty()) {
            tvEmpty.text = if (q.isBlank()) "No incoming transfers yet" else "No matching transfers"
            tvEmpty.visibility = View.VISIBLE
        } else {
            tvEmpty.visibility = View.GONE
        }
    }

    /** NEW: Accept now requires scan+verify+match */
    private fun startScanToAccept(item: IncomingTransfer) {
        pendingAcceptItem = item
        val i = Intent(this, com.simats.agritrace.dist.DistributorConfirmScanActivity::class.java).apply {
            putExtra(com.simats.agritrace.dist.DistributorConfirmScanActivity.EXTRA_EXPECTED_BATCH_CODE, item.batchCode)
            val idLong = item.batchId.toLongOrNull()
            if (idLong != null) putExtra(com.simats.agritrace.dist.DistributorConfirmScanActivity.EXTRA_EXPECTED_BATCH_ID, idLong)
        }
        scanLauncher.launch(i)
    }


    /** NEW: Verify chain + ensure scanned batch matches this transfer, else reject */
    private fun verifyThenAcceptOrReject(item: IncomingTransfer, qrPayload: String) {
        progress.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // 1) Call scan verify
                val verifyResp = withContext(Dispatchers.IO) {
                    ApiClient.distributorApi.scanVerify(ScanVerifyReq(qr_payload = qrPayload))
                }

                // 2) Always log full status for debugging
                android.util.Log.d(
                    "SCAN_VERIFY",
                    "ok=${verifyResp.ok} error=${verifyResp.error} " +
                            "vStatus=${verifyResp.verified?.status} " +
                            "batchId=${verifyResp.batch?.id} batchCode=${verifyResp.batch?.batch_code} " +
                            "payload=$qrPayload"
                )

                // 3) If backend says not ok, show the real error and reject (your rule)
                if (!verifyResp.ok) {
                    val err = verifyResp.error?.trim().orEmpty().ifBlank { "Verification failed" }
                    autoRejectWithMsg(item, err)
                    return@launch
                }

                // 4) Verification status check
                val vStatus = verifyResp.verified?.status?.trim()?.uppercase(Locale.US).orEmpty()
                if (vStatus != "BLOCKCHAIN_VERIFIED") {
                    val msg = when (vStatus) {
                        "DATA_TAMPERED" -> "QR data tampered"
                        "CHAIN_NOT_FOUND" -> "Batch not found on blockchain"
                        "CHAIN_UNAVAILABLE" -> "Blockchain unavailable"
                        else -> "Verification failed"
                    }
                    autoRejectWithMsg(item, msg)
                    return@launch
                }

                // 5) Ensure scanned batch matches THIS transfer
                val scannedId = verifyResp.batch?.id ?: 0L
                val scannedCode = verifyResp.batch?.batch_code?.trim().orEmpty()

                if (!isSameBatch(item, scannedId, scannedCode)) {
                    autoRejectWithMsg(item, "Scanned batch does not match this transfer")
                    return@launch
                }

                // 6) Matched + verified -> ACCEPT
                val acceptResp = withContext(Dispatchers.IO) {
                    ApiClient.distributorApi.acceptTransfer(TransferActionReq(transfer_id = item.transferId))
                }

                if (!acceptResp.ok) {
                    val err = acceptResp.error?.trim().orEmpty().ifBlank { "Accept failed" }
                    Toast.makeText(this@DistributorIncomingTransferActivity, err, Toast.LENGTH_LONG).show()
                    return@launch
                }

                Toast.makeText(
                    this@DistributorIncomingTransferActivity,
                    "Accepted ${item.batchId}",
                    Toast.LENGTH_SHORT
                ).show()

                loadIncoming()
            } catch (e: Exception) {
                android.util.Log.e("SCAN_VERIFY", "Exception", e)
                autoRejectWithMsg(item, "Verification failed")
            } finally {
                progress.visibility = View.GONE
            }
        }
    }


    private fun isSameBatch(item: IncomingTransfer, scannedId: Long, scannedCode: String): Boolean {
        val itemCode = item.batchCode.trim()
        val scanCode = scannedCode.trim()

        // Prefer matching by batch_code if available
        if (itemCode.isNotBlank() && scanCode.isNotBlank()) {
            if (itemCode.equals(scanCode, ignoreCase = true)) return true
        }

        // If item.batchId is numeric, match by id
        val itemIdLong = item.batchId.trim().toLongOrNull()
        if (itemIdLong != null && scannedId > 0L) {
            if (itemIdLong == scannedId) return true
        }

        return false
    }


    /** NEW: reject + show reason */
    private fun autoRejectWithMsg(item: IncomingTransfer, reason: String) {
        lifecycleScope.launch {
            try {
                val rejectResp = withContext(Dispatchers.IO) {
                    ApiClient.distributorApi.rejectTransfer(TransferActionReq(transfer_id = item.transferId))
                }

                if (!rejectResp.ok) {
                    val err = rejectResp.error?.trim().orEmpty().ifBlank { "Reject failed" }
                    Toast.makeText(this@DistributorIncomingTransferActivity, err, Toast.LENGTH_LONG).show()
                    return@launch
                }

                Toast.makeText(
                    this@DistributorIncomingTransferActivity,
                    "Rejected ${item.batchId} • $reason",
                    Toast.LENGTH_LONG
                ).show()

                loadIncoming()
            } catch (e: Exception) {
                android.util.Log.e("SCAN_VERIFY", "Reject exception", e)
                Toast.makeText(this@DistributorIncomingTransferActivity, "Reject failed", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun rejectItem(item: IncomingTransfer) {
        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.distributorApi.rejectTransfer(TransferActionReq(transfer_id = item.transferId))
                }
                if (!resp.ok) {
                    toast(resp.error ?: "Reject failed")
                    return@launch
                }
                toast("Rejected ${item.batchId}")
                loadIncoming()
            } catch (_: Exception) {
                toast("Reject failed")
            }
        }
    }

    private fun updatePendingChip() {
        val label = when (statusFilter) {
            "ASSIGNED" -> "Assigned"
            "IN_TRANSIT" -> "In Transit"
            "PICKED_UP" -> "Picked Up"
            else -> "Pending"
        }
        tvPendingChip.text = "${filtered.size} $label"
    }

    private fun showFilterMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, "All")
        menu.menu.add(0, 2, 1, "Assigned")
        menu.menu.add(0, 3, 2, "In Transit")
        menu.menu.add(0, 4, 3, "Picked Up")
        menu.menu.setGroupCheckable(0, true, true)

        val checkedId = when (statusFilter) {
            "ASSIGNED" -> 2
            "IN_TRANSIT" -> 3
            "PICKED_UP" -> 4
            else -> 1
        }
        menu.menu.findItem(checkedId)?.isChecked = true

        menu.setOnMenuItemClickListener { item ->
            statusFilter = when (item.itemId) {
                2 -> "ASSIGNED"
                3 -> "IN_TRANSIT"
                4 -> "PICKED_UP"
                else -> "ALL"
            }
            applyFilter(etSearch.text?.toString()?.trim().orEmpty())
            true
        }
        menu.show()
    }

    private fun normalizeStatus(raw: String): String {
        return raw.trim().replace(' ', '_').uppercase(Locale.US)
    }

    private fun copyToClipboard(label: String, value: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, value))
        toast("Copied")
    }

    private fun showError(msg: String) {
        tvError.text = msg
        tvError.visibility = View.VISIBLE
    }

    private fun openDetails(item: IncomingTransfer) {
        val intent = Intent(this, com.simats.agritrace.dist.DistributorBatchDetailsActivity::class.java).apply {
            if (item.batchId.toIntOrNull() != null) putExtra("batch_id", item.batchId.toInt())
            if (item.batchCode.isNotBlank()) putExtra("batch_code", item.batchCode)
        }
        startActivity(intent)
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    data class IncomingTransfer(
        val transferId: Long,
        val batchCode: String,
        val product: String,
        val batchId: String,
        val fromFarmer: String,
        val quantity: String,
        val transferDate: String,
        val txHash: String,
        val status: String
    )

    private fun IncomingTransferDto.toUi(): IncomingTransfer {
        val qty = quantity_kg?.trim().orEmpty()
        val tx = tx_hash?.trim().orEmpty()
        return IncomingTransfer(
            transferId = transfer_id,
            batchCode = batch_code?.trim().orEmpty(),
            product = crop_name?.trim().orEmpty().ifBlank { "Batch" },
            batchId = (batch_code?.trim().orEmpty()).ifBlank { batch_id.toString() },
            fromFarmer = farmer_name?.trim().orEmpty().ifBlank { "--" },
            quantity = if (qty.isNotBlank()) "$qty kg" else "--",
            transferDate = formatTime(created_at),
            txHash = tx,
            status = status?.replace('_', ' ')
                ?.lowercase(Locale.US)
                ?.replaceFirstChar { it.titlecase(Locale.US) }
                .orEmpty()
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
            raw
        }
    }

    private class IncomingAdapter(
        private val items: List<IncomingTransfer>,
        private val onAccept: (IncomingTransfer) -> Unit,
        private val onReject: (IncomingTransfer) -> Unit,
        private val onCopyTx: (String) -> Unit,
        private val onOpen: (IncomingTransfer) -> Unit
    ) : RecyclerView.Adapter<IncomingAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvProduct: TextView = v.findViewById(R.id.tvProduct)
            val tvBatchId: TextView = v.findViewById(R.id.tvBatchId)
            val tvFromValue: TextView = v.findViewById(R.id.tvFromValue)
            val tvQtyValue: TextView = v.findViewById(R.id.tvQtyValue)
            val tvDateValue: TextView = v.findViewById(R.id.tvDateValue)
            val tvTxHash: TextView = v.findViewById(R.id.tvTxHash)
            val boxTx: View = v.findViewById(R.id.boxTx)
            val btnAccept: MaterialButton = v.findViewById(R.id.btnAccept)
            val btnReject: MaterialButton = v.findViewById(R.id.btnReject)
            val card: View = v.findViewById(R.id.cardTransfer)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_incoming_transfer_compact, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val transfer = items[position]

            h.tvProduct.text = transfer.product
            val statusLabel = if (transfer.status.isNotBlank()) transfer.status else "Assigned"
            h.tvBatchId.text = "${transfer.batchId} \u2022 $statusLabel"
            h.tvFromValue.text = transfer.fromFarmer
            h.tvQtyValue.text = transfer.quantity
            h.tvDateValue.text = transfer.transferDate

            val full = transfer.txHash
            if (full.isBlank()) {
                h.boxTx.visibility = View.GONE
            } else {
                h.boxTx.visibility = View.VISIBLE
                h.tvTxHash.text = full
            }

            h.btnAccept.setOnClickListener { onAccept(transfer) }
            h.btnReject.setOnClickListener { onReject(transfer) }
            h.boxTx.setOnClickListener { onCopyTx(full) }
            h.card.setOnClickListener { onOpen(transfer) }
        }

        override fun getItemCount(): Int = items.size
    }

    private class NonScrollableLinearLayoutManager(ctx: Context) : LinearLayoutManager(ctx) {
        override fun canScrollVertically(): Boolean = false
    }
}
