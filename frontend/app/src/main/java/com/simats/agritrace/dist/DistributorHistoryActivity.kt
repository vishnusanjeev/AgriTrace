package com.simats.agritrace.dist

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
import com.simats.agritrace.HistoryEventDto
import com.simats.agritrace.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class DistributorHistoryActivity : AppCompatActivity() {

    private enum class HistoryStatus { BLUE, GREEN }
    private enum class ChipIcon { LOCATION, PERSON }

    private data class ChipItem(val icon: ChipIcon, val text: String)

    private data class HistoryItem(
        val timeLabel: String,
        val title: String,
        val desc: String,
        val statusColor: HistoryStatus,
        val chips: List<ChipItem>
    )

    private inner class HistoryAdapter(private val items: List<HistoryItem>) :
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

        override fun onBindViewHolder(h: VH, position: Int) {
            val ctx = h.itemView.context
            val item = items[position]

            h.tvTime.text = item.timeLabel
            h.tvTitle.text = item.title
            h.tvDesc.text = item.desc

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

        override fun getItemCount(): Int = items.size
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_distributor_history)

        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.ivFilter).setOnClickListener {
            Toast.makeText(this, "Filter clicked", Toast.LENGTH_SHORT).show()
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
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
        val rv = findViewById<RecyclerView>(R.id.rvHistory)
        val progress = findViewById<ProgressBar>(R.id.progress)
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)
        val tvError = findViewById<TextView>(R.id.tvError)
        rv.layoutManager = LinearLayoutManager(this)

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
                    ApiClient.distributorApi.getHistory(
                        if (batchId > 0) batchId else null,
                        batchCode.ifBlank { null }
                    )
                }
                if (!resp.ok) {
                    rv.adapter = HistoryAdapter(emptyList())
                    tvError.text = resp.error ?: "Failed to load history"
                    tvError.visibility = View.VISIBLE
                    return@launch
                }

                val items = mapHistory(resp.items.orEmpty())
                rv.adapter = HistoryAdapter(items)
                if (items.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                }
            } catch (_: Exception) {
                rv.adapter = HistoryAdapter(emptyList())
                tvError.visibility = View.VISIBLE
            } finally {
                progress.visibility = View.GONE
            }
        }
    }

    private fun mapHistory(events: List<HistoryEventDto>): List<HistoryItem> {
        return events.map { e ->
            val timeLabel = formatTime(e.time)
            val source = e.source?.uppercase(Locale.US).orEmpty()
            val eventType = e.event_type?.uppercase(Locale.US).orEmpty()
            val result = e.result?.uppercase(Locale.US).orEmpty()
            val status = e.status?.uppercase(Locale.US).orEmpty()

            val title = when {
                source == "CHAIN" && eventType == "BATCHCREATED" -> "Batch created on chain"
                source == "CHAIN" && eventType == "TRANSFERACCEPTED" -> "Transfer accepted on chain"
                source == "CHAIN" && eventType == "RECEIVEDBYRETAILER" -> "Received by retailer on chain"
                source == "SCAN" && eventType == "QR_VERIFY" && result == "SUCCESS" -> "QR verified"
                source == "SCAN" && eventType == "QR_VERIFY" -> "QR verification failed"
                source == "SCAN" && eventType == "TRANSFER_ACCEPTED" -> "Transfer Accepted"
                source == "SCAN" && eventType == "PICKUP_CONFIRMED" -> "Pickup from Farm"
                source == "SCAN" && eventType == "TRANSPORT_UPDATE" -> "In Transit - Checkpoint"
                source == "SCAN" && eventType == "RETAILER_ASSIGNED" -> "Retailer assigned"
                source == "SCAN" && eventType == "RECEIVED_BY_RETAILER" -> "Received by retailer"
                source == "SCAN" && eventType == "DISTRIBUTOR_UPDATE_LOCATION" -> "Location updated"
                source == "SCAN" && eventType == "LOCATION_UPDATE" -> "Location updated"
                source == "SCAN" && eventType == "RETAILER_CONFIRM_RECEIPT" -> "Received by retailer"
                else -> if (eventType.isNotBlank()) eventType.replace('_', ' ').lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) } else "Update"
            }

            val meta = e.meta
            val loc = meta?.location_text?.trim().orEmpty()
            val temp = meta?.temperature_c
            val tempText = if (temp != null) String.format(Locale.US, "%.1f°C", temp) else ""
            val desc = when {
                source == "CHAIN" -> buildChainDesc(e)
                tempText.isNotBlank() && loc.isNotBlank() -> "Temp: $tempText • $loc"
                tempText.isNotBlank() -> "Temperature: $tempText"
                loc.isNotBlank() -> "Location: $loc"
                result.isNotBlank() && source == "SCAN" -> "Result: $result"
                else -> "Update recorded"
            }

            val chips = ArrayList<ChipItem>()
            if (loc.isNotBlank()) {
                chips.add(ChipItem(ChipIcon.LOCATION, loc))
            }
            val role = e.actor_role?.trim().orEmpty()
            if (role.isNotBlank()) {
                chips.add(ChipItem(ChipIcon.PERSON, role.replace('_', ' ').lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }))
            } else if (source == "CHAIN") {
                chips.add(ChipItem(ChipIcon.PERSON, "Blockchain"))
            }

            val ok = when {
                source == "CHAIN" && status == "CONFIRMED" -> true
                source == "SCAN" && (result == "OK" || result == "SUCCESS") -> true
                else -> false
            }

            HistoryItem(
                timeLabel = timeLabel,
                title = title,
                desc = desc,
                statusColor = if (ok) HistoryStatus.GREEN else HistoryStatus.BLUE,
                chips = chips
            )
        }
    }

    private fun buildChainDesc(e: HistoryEventDto): String {
        val tx = e.tx_hash?.trim().orEmpty()
        val block = e.block_number
        val shortTx = if (tx.length > 12) "${tx.take(8)}…${tx.takeLast(4)}" else tx
        return when {
            shortTx.isNotBlank() && block != null -> "Tx: $shortTx • Block $block"
            shortTx.isNotBlank() -> "Tx: $shortTx"
            block != null -> "Block $block"
            else -> "On-chain update"
        }
    }

    private fun formatTime(raw: String?): String {
        if (raw.isNullOrBlank()) return "--"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val dt = parser.parse(raw) ?: return raw
            val fmt = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.US)
            fmt.format(dt)
        } catch (_: Exception) {
            raw
        }
    }
}
