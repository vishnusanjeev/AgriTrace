package com.simats.agritrace.Farmer

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.simats.agritrace.ApiClient
import com.simats.agritrace.ApiConfig
import com.simats.agritrace.R
import com.simats.agritrace.Session
import com.simats.agritrace.FarmerBatchDetailsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

class ProductsFragment : Fragment() {

    private lateinit var rvProducts: RecyclerView
    private lateinit var etSearch: AppCompatEditText
    private lateinit var btnFilter: View

    private lateinit var emptyState: View
    private lateinit var tvEmpty: TextView
    private lateinit var tvEmptyTitle: TextView
    private lateinit var btnEmptyAdd: View

    private val client get() = ApiClient.httpClient

    private var allItems: List<ProductUi> = emptyList()
    private var searchJob: Job? = null
    private var loadJob: Job? = null
    private var statusFilter: String? = null
    private var lastLoadError: String? = null

    private val PRODUCT_DIFF = object : DiffUtil.ItemCallback<ProductUi>() {
        override fun areItemsTheSame(oldItem: ProductUi, newItem: ProductUi): Boolean {
            return oldItem.batchId == newItem.batchId
        }

        override fun areContentsTheSame(oldItem: ProductUi, newItem: ProductUi): Boolean {
            return oldItem == newItem
        }
    }

    private val adapter by lazy {
        ProductsAdapter(PRODUCT_DIFF) { p ->
            val intent = Intent(requireContext(), FarmerBatchDetailsActivity::class.java).apply {
                putExtra("crop_name", p.cropName)
                putExtra("batch_id", p.batchId)
                putExtra("quantity", p.quantity)
                putExtra("harvest_date", p.harvestDateRaw)
            }
            startActivity(intent)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_products, container, false)

        rvProducts = view.findViewById(R.id.rvProducts)
        etSearch = view.findViewById(R.id.etSearch)
        btnFilter = view.findViewById(R.id.btnFilter)

        emptyState = view.findViewById(R.id.emptyState)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        tvEmptyTitle = view.findViewById(R.id.tvEmptyTitle)
        btnEmptyAdd = view.findViewById(R.id.btnEmptyAdd)

        rvProducts.adapter = adapter
        rvProducts.setHasFixedSize(true)

        btnEmptyAdd.setOnClickListener {
            val bottom = activity?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.farmerBottomNav)
            bottom?.selectedItemId = R.id.navigation_add
        }

        btnFilter.setOnClickListener { showFilterSheet() }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilterDebounced(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadProducts()
        return view
    }

    override fun onDestroyView() {
        searchJob?.cancel()
        loadJob?.cancel()
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        loadProducts()
    }

    private fun loadProducts() {
        loadJob?.cancel()

        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val baseUrl = ApiConfig.BASE_URL.trimEnd('/') + "/batches/my_products.php"
            val httpUrl = baseUrl.toHttpUrlOrNull()?.newBuilder()
            val status = statusFilter
            if (!status.isNullOrBlank()) httpUrl?.addQueryParameter("status", status)
            httpUrl?.addQueryParameter("limit", "200")
            val url = httpUrl?.build()?.toString() ?: baseUrl

            val result: Pair<List<ProductUi>, String?> = withContext(Dispatchers.IO) {
                runCatching {
                    val req = Request.Builder()
                        .url(url)
                        .get()
                        .build()

                    client.newCall(req).execute().use { resp ->
                        val raw = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) {
                            val msg = when (resp.code) {
                                401 -> "Session expired. Please login again."
                                403 -> "Access denied for this account."
                                else -> parseErrorMessage(raw).ifBlank { "Failed to load products." }
                            }
                            return@use Pair(emptyList<ProductUi>(), msg)
                        }
                        Pair(parseProducts(raw), null)
                    }
                }.getOrElse {
                    Pair(emptyList(), "Network error. Please try again.")
                }
            }

            if (!isAdded || !isActive) return@launch

            allItems = result.first
            lastLoadError = result.second
            applyFilterDebounced(etSearch.text?.toString().orEmpty(), immediate = true)
        }
    }

    private fun applyFilterDebounced(q: String, immediate: Boolean = false) {
        searchJob?.cancel()
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            if (!immediate) delay(160)

            val query = q.trim().lowercase(Locale.US)

            val filtered = withContext(Dispatchers.Default) {
                if (query.isEmpty()) allItems
                else allItems.filter {
                    it.cropName.lowercase(Locale.US).contains(query) ||
                            it.batchCode.lowercase(Locale.US).contains(query) ||
                            it.batchId.toString().contains(query)
                }
            }

            if (!isAdded || !isActive) return@launch

            adapter.submitList(filtered)
            renderEmptyState(filtered, query)
        }
    }

    private fun renderEmptyState(filtered: List<ProductUi>, query: String) {
        val hasSearch = query.isNotBlank()
        val hasFilter = !statusFilter.isNullOrBlank()
        val isEmpty = filtered.isEmpty()

        if (isEmpty) {
            rvProducts.visibility = View.GONE
            emptyState.visibility = View.VISIBLE

            val err = lastLoadError
            if (!err.isNullOrBlank()) {
                tvEmptyTitle.text = "Unable to load products"
                tvEmpty.text = err
                btnEmptyAdd.visibility = View.GONE
            } else if (hasSearch) {
                tvEmptyTitle.text = "No results"
                tvEmpty.text = "Try a different crop name or batch ID."
                btnEmptyAdd.visibility = View.GONE
            } else if (hasFilter) {
                tvEmptyTitle.text = "No products for this filter"
                tvEmpty.text = "Try another status or clear the filter."
                btnEmptyAdd.visibility = View.GONE
            } else {
                tvEmptyTitle.text = "No products yet"
                tvEmpty.text = "Create your first crop and batch to see it here."
                btnEmptyAdd.visibility = View.VISIBLE
            }
        } else {
            emptyState.visibility = View.GONE
            rvProducts.visibility = View.VISIBLE
        }
    }

    private fun parseProducts(raw: String): List<ProductUi> {
        val clean = sanitizeJson(raw)
        if (clean.isBlank()) return emptyList()
        val root = JSONObject(clean)
        val success = root.optBoolean("ok", false)
        if (!success) return emptyList()

        val arr: JSONArray = root.optJSONArray("items") ?: JSONArray()
        val list = ArrayList<ProductUi>(arr.length())

        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue

            val cropName = o.optString("crop_name", "").ifBlank { "Product" }
            val batchCode = o.optString("batch_code", "").ifBlank { "B-0000-000" }
            val batchId = o.optInt("id", -1)
            val status = o.optString("status", "ACTIVE").ifBlank { "ACTIVE" }

            val qtyText = when {
                o.has("quantity_kg") -> o.optString("quantity_kg")
                else -> "0"
            }.let { q2 ->
                val v = q2.trim()
                if (v.isEmpty()) "0 kg" else if (v.lowercase(Locale.US).contains("kg")) v else "$v kg"
            }

            val harvestRaw = o.optString(
                "harvest_date",
                o.optString("created_at", "")
            )

            list.add(ProductUi(batchId, batchCode, cropName, status, qtyText, harvestRaw))
        }

        return list
    }

    private fun parseErrorMessage(raw: String): String {
        val clean = sanitizeJson(raw)
        if (clean.isBlank()) return ""
        return runCatching {
            val obj = JSONObject(clean)
            obj.optString("error").ifBlank { obj.optString("message") }
        }.getOrDefault("")
    }

    private fun sanitizeJson(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val start = trimmed.indexOf('{')
        if (start == -1) return ""
        return trimmed.substring(start)
    }

    private fun prettyDate(raw: String): String {
        val v = raw.trim()
        if (v.length >= 10 && v[4] == '-' && v[7] == '-') {
            val yyyy = v.substring(0, 4)
            val mm = v.substring(5, 7)
            val dd = v.substring(8, 10)
            val mon = when (mm) {
                "01" -> "Jan"
                "02" -> "Feb"
                "03" -> "Mar"
                "04" -> "Apr"
                "05" -> "May"
                "06" -> "Jun"
                "07" -> "Jul"
                "08" -> "Aug"
                "09" -> "Sep"
                "10" -> "Oct"
                "11" -> "Nov"
                "12" -> "Dec"
                else -> ""
            }
            if (mon.isNotBlank()) return "$mon $dd, $yyyy"
        }
        return v
    }

    private data class ProductUi(
        val batchId: Int,
        val batchCode: String,
        val cropName: String,
        val status: String,
        val quantity: String,
        val harvestDateRaw: String
    )

    private fun showFilterSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val v = layoutInflater.inflate(R.layout.bottom_sheet_products_filter, null)

        val btnAll = v.findViewById<MaterialButton>(R.id.btnFilterAll)
        val btnActive = v.findViewById<MaterialButton>(R.id.btnFilterActive)
        val btnPending = v.findViewById<MaterialButton>(R.id.btnFilterPending)
        val btnSold = v.findViewById<MaterialButton>(R.id.btnFilterSold)
        val btnApply = v.findViewById<MaterialButton>(R.id.btnFilterApply)
        val btnReset = v.findViewById<MaterialButton>(R.id.btnFilterReset)

        var selected = statusFilter

        fun setSelected(btn: MaterialButton, isSelected: Boolean) {
            if (isSelected) {
                btn.setBackgroundColor(0xFF0B8B5B.toInt())
                btn.setTextColor(0xFFFFFFFF.toInt())
            } else {
                btn.setBackgroundColor(0xFFF3F4F6.toInt())
                btn.setTextColor(0xFF111827.toInt())
            }
        }

        fun refresh() {
            setSelected(btnAll, selected == null)
            setSelected(btnActive, selected == "ACTIVE")
            setSelected(btnPending, selected == "PENDING")
            setSelected(btnSold, selected == "SOLD")
        }

        btnAll.setOnClickListener { selected = null; refresh() }
        btnActive.setOnClickListener { selected = "ACTIVE"; refresh() }
        btnPending.setOnClickListener { selected = "PENDING"; refresh() }
        btnSold.setOnClickListener { selected = "SOLD"; refresh() }

        btnApply.setOnClickListener {
            statusFilter = selected
            dialog.dismiss()
            loadProducts()
        }

        btnReset.setOnClickListener {
            selected = null
            statusFilter = null
            refresh()
            dialog.dismiss()
            loadProducts()
        }

        refresh()
        dialog.setContentView(v)
        dialog.show()
    }

    private inner class ProductsAdapter(
        diff: DiffUtil.ItemCallback<ProductUi>,
        private val onClick: (ProductUi) -> Unit
    ) : ListAdapter<ProductUi, ProductsAdapter.VH>(diff) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_my_product, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
        }

        private inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvProductName: TextView = itemView.findViewById(R.id.tvProductName)
            private val tvBatchId: TextView = itemView.findViewById(R.id.tvBatchId)
            private val tvQuantity: TextView = itemView.findViewById(R.id.tvQuantity)
            private val tvHarvestDate: TextView = itemView.findViewById(R.id.tvHarvestDate)
            private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
            private val vStatusDot: View = itemView.findViewById(R.id.vStatusDot)
            private val cardProduct: com.google.android.material.card.MaterialCardView =
                itemView.findViewById(R.id.cardProduct)

            fun bind(p: ProductUi) {
                tvProductName.text = p.cropName
                tvBatchId.text = p.batchCode
                tvQuantity.text = p.quantity
                tvHarvestDate.text = prettyDate(p.harvestDateRaw)
                tvStatus.text = p.status.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }

                val s = p.status.trim().lowercase(Locale.US)
                val dotRes = when {
                    s == "pending" -> R.drawable.dot_status_pending
                    s == "sold" -> R.drawable.dot_status_sold
                    else -> R.drawable.dot_status_active
                }
                vStatusDot.setBackgroundResource(dotRes)

                cardProduct.setOnClickListener { onClick(p) }
            }
        }
    }
}
