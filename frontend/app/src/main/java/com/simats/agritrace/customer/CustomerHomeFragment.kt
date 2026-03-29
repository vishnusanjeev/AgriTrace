package com.simats.agritrace.Farmer.customer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.simats.agritrace.ApiClient
import com.simats.agritrace.ConsumerScanItemDto
import com.simats.agritrace.CustomerBatchVerification
import com.simats.agritrace.R
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CustomerHomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_customerhome, container, false)

        val btnStart = view.findViewById<View>(R.id.btnStartScanning)
        val tvViewAll = view.findViewById<TextView>(R.id.tvViewAll)
        val tvEmpty = view.findViewById<TextView>(R.id.tvRecentEmpty)

        val card1 = view.findViewById<MaterialCardView>(R.id.recentItem1)
        val card2 = view.findViewById<MaterialCardView>(R.id.recentItem2)
        val card3 = view.findViewById<MaterialCardView>(R.id.recentItem3)

        btnStart.setOnClickListener {
            findNavController().navigate(R.id.customerScanFragment)
        }
        tvViewAll.setOnClickListener {
            findNavController().navigate(R.id.customerHistoryFragment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            loadRecent(view, listOf(card1, card2, card3), tvEmpty)
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        view?.let { root ->
            val tvEmpty = root.findViewById<TextView>(R.id.tvRecentEmpty)
            val card1 = root.findViewById<MaterialCardView>(R.id.recentItem1)
            val card2 = root.findViewById<MaterialCardView>(R.id.recentItem2)
            val card3 = root.findViewById<MaterialCardView>(R.id.recentItem3)
            viewLifecycleOwner.lifecycleScope.launch {
                loadRecent(root, listOf(card1, card2, card3), tvEmpty)
            }
        }
    }

    private suspend fun loadRecent(
        root: View,
        cards: List<MaterialCardView>,
        emptyView: TextView
    ) {
        try {
            val resp = withContext(Dispatchers.IO) { ApiClient.consumerApi.recentScans() }
            if (!resp.ok) {
                toast(resp.error ?: "Failed to load recent scans")
                emptyView.isVisible = true
                cards.forEach { it.isVisible = false }
                return
            }

            val items = resp.items ?: emptyList()
            emptyView.isVisible = items.isEmpty()
            bindCard(root, cards.getOrNull(0), items.getOrNull(0))
            bindCard(root, cards.getOrNull(1), items.getOrNull(1))
            bindCard(root, cards.getOrNull(2), items.getOrNull(2))
        } catch (_: Exception) {
            toast("Failed to load recent scans")
            emptyView.isVisible = true
            cards.forEach { it.isVisible = false }
        }
    }

    private fun bindCard(root: View, card: MaterialCardView?, item: ConsumerScanItemDto?) {
        if (card == null) return
        if (item == null) {
            card.isVisible = false
            return
        }

        card.isVisible = true
        val nameViewId = when (card.id) {
            R.id.recentItem1 -> R.id.tvRecentName1
            R.id.recentItem2 -> R.id.tvRecentName2
            else -> R.id.tvRecentName3
        }
        val timeViewId = when (card.id) {
            R.id.recentItem1 -> R.id.tvRecentTime1
            R.id.recentItem2 -> R.id.tvRecentTime2
            else -> R.id.tvRecentTime3
        }

        root.findViewById<TextView>(nameViewId).text = item.crop_name ?: "Unknown"
        root.findViewById<TextView>(timeViewId).text = item.scanned_at ?: ""

        card.setOnClickListener {
            val intent = Intent(requireContext(), CustomerBatchVerification::class.java).apply {
                putExtra("batch_id", item.batch_id)
                putExtra("batch_code", item.batch_code ?: "")
            }
            startActivity(intent)
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}
