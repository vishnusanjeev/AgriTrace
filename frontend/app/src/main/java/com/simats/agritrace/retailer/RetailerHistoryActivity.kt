package com.simats.agritrace.retailer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.simats.agritrace.ApiClient
import com.simats.agritrace.R
import com.simats.agritrace.models.RetailerHistoryResp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class RetailerHistoryActivity : AppCompatActivity() {

    private enum class HistoryStatus { BLUE, GREEN }
    private enum class ChipIcon { LOCATION, PERSON }
    private data class ChipItem(val icon: ChipIcon, val text: String)

    private data class UiItem(
        val timeLabel: String,
        val title: String,
        val desc: String,
        val statusColor: HistoryStatus,
        val chips: List<ChipItem>
    )

    private inner class HistoryAdapter(private val items: List<UiItem>) :
        RecyclerView.Adapter<HistoryAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val statusOuter: View = v.findViewById(R.id.viewStatusOuter)
            val statusInner: View = v.findViewById(R.id.viewStatusInner)
            val statusCheck: ImageView = v.findViewById(R.id.ivStatusCheck)

            val tvTime: TextView = v.findViewById(R.id.tvTime)
            val tvTitle: TextView = v.findViewById(R.id.tvItemTitle)
            val tvDesc: TextView = v.findViewById(R.id.tvDesc)
            val chipsRow: LinearLayout = v.findViewById(R.id.llChipsRow)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history_entry, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val ctx = h.itemView.context
            val item = items[position]

            h.tvTime.text = item.timeLabel
            h.tvTitle.text = item.title

            // ✅ Remove blank dash lines completely
            if (item.desc.isBlank() || item.desc == "—") {
                h.tvDesc.visibility = View.GONE
            } else {
                h.tvDesc.visibility = View.VISIBLE
                h.tvDesc.text = item.desc
            }

            val outerBgRes = if (item.statusColor == HistoryStatus.GREEN) {
                R.drawable.bg_status_outer_green
            } else {
                R.drawable.bg_status_outer_blue
            }
            val innerBgRes = if (item.statusColor == HistoryStatus.GREEN) {
                R.drawable.bg_status_inner_green
            } else {
                R.drawable.bg_status_inner_blue
            }

            h.statusOuter.setBackgroundResource(outerBgRes)
            h.statusInner.setBackgroundResource(innerBgRes)
            h.statusCheck.setImageResource(R.drawable.ic_check_white_16)

            h.chipsRow.removeAllViews()
            if (item.chips.isEmpty()) {
                h.chipsRow.visibility = View.GONE
                return
            }
            h.chipsRow.visibility = View.VISIBLE

            for (chip in item.chips) {
                val chipView = LayoutInflater.from(ctx)
                    .inflate(R.layout.item_chip, h.chipsRow, false)

                val iv = chipView.findViewById<ImageView>(R.id.ivChipIcon)
                val tv = chipView.findViewById<TextView>(R.id.tvChipText)

                val iconRes = if (chip.icon == ChipIcon.LOCATION) {
                    R.drawable.ic_pin_24
                } else {
                    R.drawable.ic_user_outline
                }

                iv.setImageResource(iconRes)
                tv.text = chip.text
                h.chipsRow.addView(chipView)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ✅ FIRST set content view (must be before findViewById)
        setContentView(R.layout.activity_distributor_history) // keep as you said

        // ✅ THEN apply status bar appearance + insets
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        // ✅ safest: use android.R.id.content so it NEVER becomes null
        val insetTarget = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(insetTarget) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.ivFilter)?.visibility = View.GONE

        val rv = findViewById<RecyclerView>(R.id.rvHistory)
        val progress = findViewById<ProgressBar>(R.id.progress)
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)
        val tvError = findViewById<TextView>(R.id.tvError)

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = HistoryAdapter(emptyList())

        val batchId = intent.getIntExtra("batch_id", -1)
        val batchCode = intent.getStringExtra("batch_code").orEmpty().trim()

        if (batchId <= 0 && batchCode.isBlank()) {
            Toast.makeText(this, "Batch ID missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        progress.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        tvError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.retailerApi.getHistory(
                        if (batchId > 0) batchId.toLong() else null,
                        batchCode.ifBlank { null }
                    )
                }

                if (resp.ok != true) {
                    rv.adapter = HistoryAdapter(emptyList())
                    tvError.text = resp.error ?: "Failed to load history"
                    tvError.visibility = View.VISIBLE
                    return@launch
                }

                val sourceItems = resp.items ?: emptyList()
                val mapped = mapHistory(sourceItems)

                rv.adapter = HistoryAdapter(mapped)
                tvEmpty.visibility = if (mapped.isEmpty()) View.VISIBLE else View.GONE

            } catch (_: Exception) {
                rv.adapter = HistoryAdapter(emptyList())
                tvError.text = "Failed to load history"
                tvError.visibility = View.VISIBLE
            } finally {
                progress.visibility = View.GONE
            }
        }
    }


    private fun mapHistory(events: List<RetailerHistoryResp.HistoryItem>): List<UiItem> {
        return events.mapNotNull { e ->
            val source = e.source?.trim().orEmpty().lowercase(Locale.US)
            val isChain = source == "chain"

            val rawTime = if (isChain) (e.confirmedAt ?: e.time) else e.time
            val timeLabel = formatTime(rawTime)

            val eventType = e.eventType?.trim().orEmpty().uppercase(Locale.US)
            val actorRoleRaw = e.actorRole?.trim().orEmpty()
            val actorRolePretty = actorRoleRaw.replace('_', ' ')
                .lowercase(Locale.US)
                .replaceFirstChar { it.titlecase(Locale.US) }

            val loc = e.meta?.locationText?.trim().orEmpty()
            val temp = e.meta?.temperatureC
            val remarks = e.meta?.remarks?.trim().orEmpty()

            val result = e.result?.trim()?.uppercase(Locale.US).orEmpty()
            val chainStatus = e.chainStatus?.trim()?.uppercase(Locale.US).orEmpty()

            val title = when {
                isChain && eventType == "BATCHCREATED" -> "Batch Registered"
                eventType == "TRANSFER_ACCEPTED" -> "Transfer Accepted"
                eventType == "PICKUP_CONFIRMED" -> "Pickup from Farm"
                eventType == "RETAILER_ASSIGNED" -> "Retailer Assigned"
                eventType == "TRANSPORT_UPDATE" -> "In Transit - Checkpoint"
                eventType == "QR_VERIFY" -> "QR Verified"
                eventType == "DISTRIBUTOR_SCAN_VERIFY" -> "Distributor Verified"
                eventType == "RETAILER_ACCEPTED_STOCK" -> "Stock Accepted"
                eventType == "RETAILER_REJECTED_STOCK" -> "Stock Rejected"
                else -> eventType.replace('_', ' ')
                    .lowercase(Locale.US)
                    .replaceFirstChar { it.titlecase(Locale.US) }
                    .ifBlank { "Update" }
            }

            val desc = when {
                isChain -> {
                    val tx = e.txHash?.trim().orEmpty()
                    val bn = e.blockNumber?.toString().orEmpty()
                    val st = chainStatus.ifBlank { "CONFIRMED" }
                    buildString {
                        append("Blockchain: ").append(st)
                        if (bn.isNotBlank()) append(" • Block ").append(bn)
                        if (tx.isNotBlank()) append("\nTX: ").append(tx.take(18)).append("...")
                    }
                }

                remarks.isNotBlank() -> remarks

                eventType == "TRANSPORT_UPDATE" -> {
                    when {
                        temp != null && loc.isNotBlank() ->
                            "Temp: " + String.format(Locale.US, "%.1f°C", temp) + " - " + loc
                        temp != null ->
                            "Temperature: " + String.format(Locale.US, "%.1f°C", temp)
                        loc.isNotBlank() ->
                            "Location: $loc"
                        else -> "Transport checkpoint updated"
                    }
                }

                eventType == "TRANSFER_ACCEPTED" ->
                    "Distributor accepted the transfer"

                eventType == "PICKUP_CONFIRMED" ->
                    "Pickup confirmed by distributor"

                eventType == "RETAILER_ASSIGNED" ->
                    "Retailer assigned for delivery"

                eventType == "DISTRIBUTOR_SCAN_VERIFY" ->
                    "Distributor scanned and verified the QR"

                eventType == "QR_VERIFY" -> {
                    when (actorRoleRaw.uppercase(Locale.US)) {
                        "DISTRIBUTOR" -> "Distributor scanned and verified the QR"
                        "RETAILER" -> "Retailer scanned and verified the QR"
                        else -> "QR verified successfully"
                    }
                }

                eventType == "RETAILER_ACCEPTED_STOCK" ->
                    "Retailer accepted stock and confirmed receipt"

                eventType == "RETAILER_REJECTED_STOCK" ->
                    "Retailer rejected stock"

                else -> "—"
            }

            val chips = ArrayList<ChipItem>()
            if (loc.isNotBlank()) chips.add(ChipItem(ChipIcon.LOCATION, loc))
            if (actorRolePretty.isNotBlank() && !isChain) chips.add(ChipItem(ChipIcon.PERSON, actorRolePretty))

            val ok = if (isChain) {
                chainStatus == "CONFIRMED" || chainStatus == "OK" || chainStatus == "SUCCESS"
            } else {
                result.isBlank() || result == "OK" || result == "SUCCESS"
            }

            // drop totally useless placeholder-only rows
            val isUseless =
                (title == "Update" && (desc == "—" || desc.isBlank()) && chips.isEmpty() && timeLabel == "--")
            if (isUseless) return@mapNotNull null

            UiItem(
                timeLabel = timeLabel,
                title = title,
                desc = desc,
                statusColor = if (ok) HistoryStatus.GREEN else HistoryStatus.BLUE,
                chips = chips
            )
        }
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
}
