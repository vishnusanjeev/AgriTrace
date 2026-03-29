package com.simats.agritrace.dist

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.simats.agritrace.ApiClient
import com.simats.agritrace.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DistributorBatchesFragment : Fragment() {

    private enum class StatusFilter { ALL, VERIFIED, PENDING }

    private data class BatchItem(
        val batchId: Int,
        val batchCode: String,
        val batchIdDisplay: String,
        val cropName: String,
        val quantityText: String,
        val createdText: String,
        val status: StatusFilter
    )

    private lateinit var rv: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptySub: TextView
    private lateinit var etSearch: EditText
    private lateinit var filterBtn: MaterialCardView

    private val allBatches = mutableListOf<BatchItem>()
    private var statusFilter: StatusFilter = StatusFilter.ALL
    private var queryText: String = ""
    private var userTouchedFilters: Boolean = false

    private lateinit var adapter: BatchesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_dist_product, container, false)

        rv = view.findViewById(R.id.rvBatches)
        emptyState = view.findViewById(R.id.emptyState)
        tvEmptyTitle = view.findViewById(R.id.tvEmptyTitle)
        tvEmptySub = view.findViewById(R.id.tvEmptySub)
        etSearch = view.findViewById(R.id.etSearch)
        filterBtn = view.findViewById(R.id.btnFilter)

        adapter = BatchesAdapter { item ->
            startActivity(Intent(requireContext(), DistributorBatchDetailsActivity::class.java).apply {
                if (item.batchId > 0) {
                    putExtra("batch_id", item.batchId)
                }
                if (item.batchCode.isNotBlank()) {
                    putExtra("batch_code", item.batchCode)
                }
            })
        }

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        filterBtn.setOnClickListener { showFilterMenu() }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                queryText = s?.toString()?.trim().orEmpty()
                userTouchedFilters = true
                applyFilters(showHint = true)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadBatches()
        return view
    }

    override fun onResume() {
        super.onResume()
        loadBatches()
    }

    private fun showFilterMenu() {
        val popup = PopupMenu(requireContext(), filterBtn)
        popup.menu.add(0, 1, 1, "All")
        popup.menu.add(0, 2, 2, "Verified")
        popup.menu.add(0, 3, 3, "Pending")
        popup.setOnMenuItemClickListener { m ->
            statusFilter = when (m.itemId) {
                2 -> StatusFilter.VERIFIED
                3 -> StatusFilter.PENDING
                else -> StatusFilter.ALL
            }
            userTouchedFilters = true
            applyFilters(showHint = true)
            true
        }
        popup.show()
    }

    private fun loadBatches() {
        allBatches.clear()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.distributorApi.getAssignedBatches()
                }
                if (!resp.ok) {
                    if (isAdded) {
                        Snackbar.make(requireView(), resp.error ?: "Failed to load batches", Snackbar.LENGTH_SHORT).show()
                    }
                    applyFilters(showHint = false)
                    return@launch
                }

                val items = resp.items ?: emptyList()
                for (it in items) {
                    val crop = it.crop_name?.trim().orEmpty().ifBlank { "Batch" }
                    val code = it.batch_code?.trim().orEmpty()
                    val qtyRaw = it.quantity_kg?.trim().orEmpty()
                    val qtyText = when {
                        qtyRaw.isBlank() -> "--"
                        qtyRaw.lowercase(Locale.US).contains("kg") -> qtyRaw
                        else -> "$qtyRaw kg"
                    }
                    val createdRaw = it.transfer_created_at ?: it.batch_created_at
                    val createdText = formatDate(createdRaw)
                    val status = when (it.transfer_status?.uppercase(Locale.US)) {
                        "ASSIGNED" -> StatusFilter.PENDING
                        else -> StatusFilter.VERIFIED
                    }
                    val batchIdText = if (code.isNotBlank()) code else it.batch_id.toString()
                    allBatches.add(BatchItem(it.batch_id.toInt(), code, batchIdText, crop, qtyText, createdText, status))
                }
                applyFilters(showHint = false)
            } catch (_: Exception) {
                if (isAdded) {
                    Snackbar.make(requireView(), "Failed to load batches", Snackbar.LENGTH_SHORT).show()
                }
                applyFilters(showHint = false)
            }
        }
    }

    private fun formatDate(raw: String?): String {
        if (raw.isNullOrBlank()) return "--"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val dt: Date = parser.parse(raw) ?: return raw
            val out = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            out.format(dt)
        } catch (_: Exception) {
            raw
        }
    }

    private fun applyFilters(showHint: Boolean) {
        val q = queryText.lowercase(Locale.US)

        val filtered = allBatches.filter { item ->
            val matchesText = q.isEmpty() ||
                    item.batchIdDisplay.lowercase(Locale.US).contains(q) ||
                    item.cropName.lowercase(Locale.US).contains(q)

            val matchesStatus = when (statusFilter) {
                StatusFilter.ALL -> true
                StatusFilter.VERIFIED -> item.status == StatusFilter.VERIFIED
                StatusFilter.PENDING -> item.status == StatusFilter.PENDING
            }

            matchesText && matchesStatus
        }

        adapter.submit(filtered)

        val showEmpty = filtered.isEmpty()
        emptyState.visibility = if (showEmpty) View.VISIBLE else View.GONE
        rv.visibility = if (showEmpty) View.GONE else View.VISIBLE

        if (showEmpty && isAdded) {
            val hasQuery = queryText.isNotBlank()
            val hasFilter = statusFilter != StatusFilter.ALL
            val filtered = userTouchedFilters && (hasQuery || hasFilter)
            if (filtered) {
                tvEmptyTitle.text = "No matching batches"
                tvEmptySub.text = "Try clearing filters or searching with a different keyword."
            } else {
                tvEmptyTitle.text = "No assigned batches yet"
                tvEmptySub.text = "Batches assigned by farmers will appear here."
            }
            if (showHint && filtered) {
                Snackbar.make(requireView(), "No matching batches. Try removing filters.", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private inner class BatchesAdapter(
        private val onClick: (BatchItem) -> Unit
    ) : RecyclerView.Adapter<BatchesAdapter.VH>() {

        private val items = ArrayList<BatchItem>()

        fun submit(newItems: List<BatchItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_batch_card, parent, false)
            return VH(v, onClick)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class VH(itemView: View, private val onClick: (BatchItem) -> Unit) :
            RecyclerView.ViewHolder(itemView) {

            private val card: MaterialCardView = itemView.findViewById(R.id.cardBatch)
            private val tvName: TextView = itemView.findViewById(R.id.tvName)
            private val tvBatchId: TextView = itemView.findViewById(R.id.tvBatchId)
            private val tvQtyValue: TextView = itemView.findViewById(R.id.tvQtyValue)
            private val tvCreatedValue: TextView = itemView.findViewById(R.id.tvCreatedValue)
            private val dot: View = itemView.findViewById(R.id.statusDot)

            fun bind(item: BatchItem) {
                tvName.text = item.cropName
                tvBatchId.text = item.batchIdDisplay
                tvQtyValue.text = item.quantityText
                tvCreatedValue.text = item.createdText

                val color = when (item.status) {
                    StatusFilter.VERIFIED -> 0xFF12B981.toInt()
                    StatusFilter.PENDING -> 0xFFF59E0B.toInt()
                    StatusFilter.ALL -> 0xFF12B981.toInt()
                }
                ViewCompat.setBackgroundTintList(dot, ColorStateList.valueOf(color))

                card.setOnClickListener { onClick(item) }
            }
        }
    }
}
