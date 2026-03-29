package com.simats.agritrace.retailer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.cardview.widget.CardView
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.simats.agritrace.R
import com.simats.agritrace.RetailerBatchVerification
import com.simats.agritrace.ApiClient
import com.simats.agritrace.retailer.RetailerReceivedInventoryActivity
import com.simats.agritrace.RetailerRecentBatchDto
import com.simats.agritrace.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class RetailerDashboardFragment : Fragment() {

    private data class RecentCardRefs(
        val card: CardView,
        val titleId: Int,
        val subId: Int,
        val statusId: Int,
        val timeId: Int
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_retailer_home, container, false)

        val btnScan: Button = view.findViewById(R.id.btnScan)
        btnScan.setOnClickListener {
            // go to Scan screen using NavController (not BottomNav hacks)
            findNavController().navigate(R.id.retailerScanFragment)
        }

        view.findViewById<TextView>(R.id.tvViewAll).setOnClickListener {
            // go to History tab properly
            val bottom = requireActivity().findViewById<BottomNavigationView>(R.id.retailerBottomNav)
            bottom.selectedItemId = R.id.retailerHistoryFragment
        }

        view.findViewById<CardView>(R.id.cardIncoming).setOnClickListener {
            startActivity(Intent(requireContext(), RetailerIncomingStockActivity::class.java))
        }

        view.findViewById<CardView>(R.id.cardInventory).setOnClickListener {
            startActivity(Intent(requireContext(), RetailerReceivedInventoryActivity::class.java))
        }

        view.findViewById<CardView>(R.id.cardSold).setOnClickListener {
            val bottom = requireActivity().findViewById<BottomNavigationView>(R.id.retailerBottomNav)
            bottom.selectedItemId = R.id.retailerHistoryFragment
        }

        bindHeader(view)
        loadHome(view)
        return view
    }


    private fun bindHeader(view: View) {
        val name = Session.name(requireContext())?.trim().orEmpty()
        val email = Session.email(requireContext())?.trim().orEmpty()
        val display = when {
            name.isNotBlank() -> name
            email.isNotBlank() -> email.substringBefore("@")
            else -> "Retailer"
        }
        view.findViewById<TextView>(R.id.tvCompany).text = display
    }

    private fun loadHome(view: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { ApiClient.retailerApi.getHome() }
                if (!resp.ok) return@launch

                val stats = resp.stats
                view.findViewById<TextView>(R.id.tvRecValue).text = (stats?.received ?: 0).toString()
                view.findViewById<TextView>(R.id.tvAvailValue).text = (stats?.available ?: 0).toString()
                view.findViewById<TextView>(R.id.tvSoldValue).text = (stats?.sold ?: 0).toString()

                val incomingBadge = view.findViewById<TextView>(R.id.tvIncomingBadge)
                val incoming = stats?.incoming ?: 0
                if (incoming > 0) {
                    incomingBadge.text = incoming.toString()
                    incomingBadge.visibility = View.VISIBLE
                } else {
                    incomingBadge.visibility = View.GONE
                }

                bindRecent(view, resp.recent ?: emptyList())
            } catch (_: Exception) {
            }
        }
    }

    override fun onResume() {
        super.onResume()
        view?.let { loadHome(it) }
    }

    private fun bindRecent(view: View, items: List<RetailerRecentBatchDto>) {
        val empty = view.findViewById<TextView>(R.id.tvRecentEmpty)
        val cards: List<RecentCardRefs> = listOf(
            RecentCardRefs(view.findViewById(R.id.item1), R.id.t1, R.id.t1sub, R.id.tvStatus1, R.id.tvTime1),
            RecentCardRefs(view.findViewById(R.id.item2), R.id.t2, R.id.t2sub, R.id.tvStatus2, R.id.tvTime2),
            RecentCardRefs(view.findViewById(R.id.item3), R.id.t3, R.id.t3sub, R.id.tvStatus3, R.id.tvTime3)
        )

        if (items.isEmpty()) {
            empty.visibility = View.VISIBLE
        } else {
            empty.visibility = View.GONE
        }

        for (i in cards.indices) {
            val card = cards[i].card
            if (i >= items.size) {
                card.visibility = View.GONE
                continue
            }
            card.visibility = View.VISIBLE
            val item = items[i]
            val title = card.findViewById<TextView>(cards[i].titleId)
            val sub = card.findViewById<TextView>(cards[i].subId)
            val status = card.findViewById<TextView>(cards[i].statusId)
            val time = card.findViewById<TextView>(cards[i].timeId)

            title.text = item.crop_name ?: "Batch"
            sub.text = item.batch_code ?: ""
            val rawStatus = (item.status ?: "ACTIVE").uppercase(Locale.US)
            val isSold = rawStatus == "SOLD"
            status.text = when (rawStatus) {
                "SOLD" -> "Sold"
                "PENDING" -> "Pending"
                else -> "Available"
            }
            status.setBackgroundResource(
                if (isSold) R.drawable.bg_status_pill_green else R.drawable.bg_status_pill_blue
            )
            val color = if (isSold) "#059669" else "#2563EB"
            status.setTextColor(android.graphics.Color.parseColor(color))
            time.text = timeAgo(item.created_at)

            card.setOnClickListener {
                val intent = Intent(requireContext(), RetailerBatchVerification::class.java)
                intent.putExtra("batch_id", item.batch_id.toInt())
                intent.putExtra("batch_code", item.batch_code ?: "")
                startActivity(intent)
            }
        }
    }

    private fun timeAgo(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val dt = parser.parse(raw) ?: return ""
            val diffMs = System.currentTimeMillis() - dt.time
            val mins = diffMs / 60000
            when {
                mins < 60 -> "$mins mins ago"
                mins < 1440 -> "${mins / 60} hours ago"
                else -> "${mins / 1440} days ago"
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun openBatchVerification(batchId: String) {
        val intent = Intent(requireContext(), RetailerBatchVerification::class.java)
        intent.putExtra("BATCH_ID", batchId)
        startActivity(intent)
    }
}
