package com.simats.agritrace.dist

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.simats.agritrace.ApiClient
import com.simats.agritrace.DistributorIncomingTransferActivity
import com.simats.agritrace.Farmer.distributor.DistributorDashboardActivity
import com.simats.agritrace.R
import com.simats.agritrace.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DistributorHomeFragment : Fragment() {

    private lateinit var item1: CardView
    private lateinit var btnScan: Button
    private lateinit var tvViewAll: TextView

    private lateinit var tvBatchName1: TextView
    private lateinit var tvBatchCode1: TextView
    private lateinit var pill1: TextView
    private lateinit var tvTime1: TextView
    private lateinit var tvHandledValue: TextView
    private lateinit var tvTransitValue: TextView
    private lateinit var tvDeliveredValue: TextView
    private lateinit var tvIncomingBadge: TextView
    private lateinit var tvInventoryBadge: TextView
    private lateinit var tvTitle: TextView
    private lateinit var cardIncoming: androidx.cardview.widget.CardView
    private lateinit var cardInventory: androidx.cardview.widget.CardView

    private lateinit var recentList: LinearLayout
    private lateinit var emptyRecent: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_dist__dashboards, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        item1 = view.findViewById(R.id.item1)
        btnScan = view.findViewById(R.id.btnScan)
        tvViewAll = view.findViewById(R.id.tvViewAll)

        tvBatchName1 = view.findViewById(R.id.tvBatchName1)
        tvBatchCode1 = view.findViewById(R.id.tvBatchCode1)
        pill1 = view.findViewById(R.id.pill1)
        tvTime1 = view.findViewById(R.id.tvTime1)
        tvHandledValue = view.findViewById(R.id.tvHandledValue)
        tvTransitValue = view.findViewById(R.id.tvTransitValue)
        tvDeliveredValue = view.findViewById(R.id.tvDeliveredValue)
        tvIncomingBadge = view.findViewById(R.id.badge)
        tvInventoryBadge = view.findViewById(R.id.badgeInventory)
        tvTitle = view.findViewById(R.id.tvTitle)
        cardIncoming = view.findViewById(R.id.cardIncoming)
        cardInventory = view.findViewById(R.id.cardInventory)

        recentList = view.findViewById(R.id.list)
        emptyRecent = view.findViewById(R.id.emptyRecent)

        btnScan.setOnClickListener {
            (activity as? DistributorDashboardActivity)?.openScanTab()
        }

        tvViewAll.setOnClickListener {
            (activity as? DistributorDashboardActivity)?.openProductsTab()
        }

        cardIncoming.setOnClickListener {
            val intent = Intent(requireContext(), DistributorIncomingTransferActivity::class.java)
            startActivity(intent)
        }

        cardInventory.setOnClickListener {
            val intent = Intent(requireContext(), DistributorReceivedProductsActivity::class.java)
            startActivity(intent)
        }

        val name = Session.name(requireContext())?.trim().orEmpty()
        tvTitle.text = if (name.isNotBlank()) name else "Distributor"

        loadDashboardStats()
        loadRecentBatches()
    }

    override fun onResume() {
        super.onResume()
        loadDashboardStats()
        loadRecentBatches()
    }

    private fun loadDashboardStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.distributorApi.getHomeStats()
                }
                if (!resp.ok || resp.stats == null) {
                    return@launch
                }

                val stats = resp.stats
                tvHandledValue.text = stats.handled.toString()
                tvTransitValue.text = stats.in_transit.toString()
                tvDeliveredValue.text = stats.delivered.toString()
                if (stats.incoming > 0) {
                    tvIncomingBadge.visibility = View.VISIBLE
                    tvIncomingBadge.text = stats.incoming.toString()
                } else {
                    tvIncomingBadge.visibility = View.GONE
                }
                if (stats.inventory > 0) {
                    tvInventoryBadge.visibility = View.VISIBLE
                    tvInventoryBadge.text = stats.inventory.toString()
                } else {
                    tvInventoryBadge.visibility = View.GONE
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun loadRecentBatches() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.distributorApi.getAssignedBatches()
                }
                if (!resp.ok) {
                    showEmptyRecent(true)
                    return@launch
                }

                val items = resp.items.orEmpty().take(3)
                renderRecent(items)
            } catch (_: Exception) {
                showEmptyRecent(true)
            }
        }
    }

    private fun renderRecent(items: List<com.simats.agritrace.DistributorBatchItemDto>) {
        clearDynamicRecentCards()
        if (items.isEmpty()) {
            item1.visibility = View.GONE
            showEmptyRecent(true)
            return
        }

        showEmptyRecent(false)
        item1.visibility = View.VISIBLE
        bindRecentCard(
            item1,
            tvBatchName1,
            tvBatchCode1,
            pill1,
            tvTime1,
            items.first()
        )

        val insertAt = recentList.indexOfChild(emptyRecent)
        for (i in 1 until items.size) {
            val row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_dist_recent_batch, recentList, false)
            bindRecentCard(
                row,
                row.findViewById(R.id.tvBatchName),
                row.findViewById(R.id.tvBatchCode),
                row.findViewById(R.id.pillStatus),
                row.findViewById(R.id.tvTime),
                items[i]
            )
            recentList.addView(row, insertAt)
        }
    }

    private fun bindRecentCard(
        root: View,
        tvName: TextView,
        tvCode: TextView,
        pill: TextView,
        tvTime: TextView,
        item: com.simats.agritrace.DistributorBatchItemDto
    ) {
        val crop = item.crop_name?.trim().orEmpty().ifBlank { "Batch" }
        val code = item.batch_code?.trim().orEmpty().ifBlank { item.batch_id.toString() }
        val status = item.transfer_status?.uppercase(Locale.US).orEmpty()

        tvName.text = crop
        tvCode.text = code
        tvTime.text = timeAgo(item.transfer_created_at ?: item.batch_created_at)

        when (status) {
            "ASSIGNED" -> {
                pill.text = "Incoming"
                pill.setBackgroundResource(R.drawable.bg_pill_orange)
                pill.setTextColor(requireContext().getColor(R.color.orangeDark))
            }
            "IN_TRANSIT" -> {
                pill.text = "In Transit"
                pill.setBackgroundResource(R.drawable.bg_pill_orange)
                pill.setTextColor(requireContext().getColor(R.color.orangeDark))
            }
            "PICKED_UP" -> {
                pill.text = "Picked Up"
                pill.setBackgroundResource(R.drawable.bg_pill_green)
                pill.setTextColor(requireContext().getColor(R.color.greenDark))
            }
            else -> {
                pill.text = if (status.isNotBlank()) status.replace('_', ' ') else "Status"
                pill.setBackgroundResource(R.drawable.bg_pill_verified)
                pill.setTextColor(requireContext().getColor(R.color.textSecondary))
            }
        }

        root.setOnClickListener {
            val intent = Intent(requireContext(), DistributorBatchDetailsActivity::class.java).apply {
                putExtra("batch_id", item.batch_id.toInt())
                if (item.batch_code?.isNotBlank() == true) {
                    putExtra("batch_code", item.batch_code)
                }
            }
            startActivity(intent)
        }
    }

    private fun clearDynamicRecentCards() {
        val toRemove = ArrayList<View>()
        for (i in 0 until recentList.childCount) {
            val v = recentList.getChildAt(i)
            if (v !== item1 && v !== emptyRecent) {
                toRemove.add(v)
            }
        }
        for (v in toRemove) {
            recentList.removeView(v)
        }
    }

    private fun showEmptyRecent(show: Boolean) {
        emptyRecent.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun timeAgo(raw: String?): String {
        if (raw.isNullOrBlank()) return "--"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val dt: Date = parser.parse(raw) ?: return raw
            val diffMs = System.currentTimeMillis() - dt.time
            val diffMin = diffMs / 60000
            val diffHr = diffMin / 60
            val diffDay = diffHr / 24
            when {
                diffMin < 1 -> "Just now"
                diffMin < 60 -> if (diffMin == 1L) "1 min ago" else "$diffMin mins ago"
                diffHr < 24 -> if (diffHr == 1L) "1 hr ago" else "$diffHr hrs ago"
                diffDay < 7 -> if (diffDay == 1L) "1 day ago" else "$diffDay days ago"
                else -> SimpleDateFormat("MMM dd, yyyy", Locale.US).format(dt)
            }
        } catch (_: Exception) {
            raw
        }
    }
}
