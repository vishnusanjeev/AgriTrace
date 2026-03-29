package com.simats.agritrace.Farmer

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.simats.agritrace.ApiClient
import com.simats.agritrace.DistributorDto
import com.simats.agritrace.R
import com.simats.agritrace.TransferToDistributorReq
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class SelectDistributorActivity : AppCompatActivity() {

    // ---- UI ----
    private lateinit var btnBack: ImageView
    private lateinit var distributorListContainer: LinearLayout
    private lateinit var etManualDistributor: EditText
    private lateinit var btnConfirmTransfer: com.google.android.material.button.MaterialButton
    private lateinit var btnCancel: com.google.android.material.button.MaterialButton
    private lateinit var tvTransferSubtitle: TextView
    private lateinit var tvTransferDesc: TextView
    private lateinit var tvTransferBadge: TextView

    // ---- State ----
    private var selectedDistributor: DistributorDto? = null
    private var selectedRow: MaterialCardView? = null

    // ---- Input (from previous screen) ----
    private var batchCode: String? = null
    private var batchId: Long = 0L
    private var productName: String? = null
    private var productType: String? = null
    private var cachedDistributors: List<DistributorDto> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_select_distributor)

        btnBack = findViewById(R.id.btnBack)
        distributorListContainer = findViewById(R.id.distributorListContainer)
        etManualDistributor = findViewById(R.id.etManualDistributor)
        btnConfirmTransfer = findViewById(R.id.btnConfirmTransfer)
        btnCancel = findViewById(R.id.btnCancel)
        tvTransferSubtitle = findViewById(R.id.tvTransferSubtitle)
        tvTransferDesc = findViewById(R.id.tvTransferDesc)
        tvTransferBadge = findViewById(R.id.tvTransferBadge)

        // optional extras (safe)
        batchCode = intent.getStringExtra("batch_code") ?: intent.getStringExtra("batchCode")
        batchId = intent.getLongExtra("batch_id", 0L).takeIf { it > 0L }
            ?: intent.getLongExtra("batchId", 0L)
        productName = intent.getStringExtra("product_name") ?: intent.getStringExtra("productName")
        productType = intent.getStringExtra("product_type") ?: intent.getStringExtra("productType")

        if (!batchCode.isNullOrBlank()) {
            tvTransferSubtitle.text = "Transfer $batchCode"
        } else if (batchId > 0L) {
            tvTransferSubtitle.text = "Transfer B-$batchId"
        }

        val badgeText = productType?.trim().orEmpty()
        if (badgeText.isNotBlank()) {
            tvTransferBadge.text = badgeText
            val product = productName?.trim().orEmpty()
            val descProduct = if (product.isNotBlank()) product else "this batch"
            tvTransferDesc.text =
                "Select a verified distributor to transfer $descProduct. Once transferred, batch data will be locked."
        }

        btnBack.setOnClickListener { finish() }
        btnCancel.setOnClickListener { finish() }

        // When user types manual ID, clear selection highlight (and vice versa)
        etManualDistributor.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val txt = s?.toString()?.trim().orEmpty()
                if (txt.isNotEmpty()) {
                    clearSelectedRow()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnConfirmTransfer.setOnClickListener {
            val manual = etManualDistributor.text?.toString()?.trim().orEmpty()

            val chosenId = when {
                manual.isNotBlank() -> parseDistributorId(manual)
                selectedDistributor != null -> selectedDistributor?.id
                else -> null
            }

            if (chosenId == null || chosenId <= 0L) {
                toast("Please select a distributor or enter a valid Distributor ID")
                return@setOnClickListener
            }

            if (batchId <= 0L) {
                toast("Batch ID missing")
                return@setOnClickListener
            }

            submitTransfer(chosenId)
        }

        loadDistributors()
    }

    private fun loadDistributors() {
        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { ApiClient.farmerApi.getDistributors() }
                if (!resp.ok) {
                    toast(resp.error ?: "Failed to load distributors")
                    renderDistributorList(emptyList())
                    showEmptyMessageIfNeeded(true)
                    return@launch
                }

                val items = resp.items ?: emptyList()
                cachedDistributors = items
                renderDistributorList(items)
                showEmptyMessageIfNeeded(items.isEmpty())
            } catch (_: Exception) {
                toast("Failed to load distributors")
                cachedDistributors = emptyList()
                renderDistributorList(emptyList())
                showEmptyMessageIfNeeded(true)
            }
        }
    }

    private fun renderDistributorList(items: List<DistributorDto>) {
        distributorListContainer.removeAllViews()

        if (items.isEmpty()) return

        val inflater = LayoutInflater.from(this)

        for (d in items) {
            val row = inflater.inflate(R.layout.item_distributor_select, distributorListContainer, false)
            val card = row.findViewById<MaterialCardView>(R.id.cardDistributor)
            val tvName = row.findViewById<TextView>(R.id.tvName)
            val tvId = row.findViewById<TextView>(R.id.tvId)
            val tvPhone = row.findViewById<TextView>(R.id.tvPhone)
            val tvLocation = row.findViewById<TextView>(R.id.tvLocation)
            val ivSelected = row.findViewById<ImageView>(R.id.ivSelected)

            tvName.text = d.full_name?.ifBlank { "Distributor" } ?: "Distributor"
            tvId.text = formatDistributorId(d.id)
            tvPhone.text = d.phone_e164?.ifBlank { d.email ?: "Contact not provided" } ?: "Contact not provided"
            val loc = d.location?.trim().orEmpty()
            tvLocation.text = if (loc.isNotBlank()) loc else "Location not provided"

            card.setOnClickListener {
                etManualDistributor.setText("")
                selectRow(card, ivSelected, d)
            }

            distributorListContainer.addView(row)
        }
    }

    private fun selectRow(card: MaterialCardView, ivSelected: ImageView, distributor: DistributorDto) {
        selectedRow?.isSelected = false
        selectedRow?.findViewById<ImageView>(R.id.ivSelected)?.visibility = View.GONE
        selectedRow?.strokeWidth = 1
        selectedRow?.setStrokeColor(android.content.res.ColorStateList.valueOf(0xFFE5E7EB.toInt()))

        selectedRow = card
        selectedDistributor = distributor
        card.isSelected = true
        card.strokeWidth = 2
        card.setStrokeColor(android.content.res.ColorStateList.valueOf(0xFF16A34A.toInt()))
        ivSelected.visibility = View.VISIBLE
    }

    private fun clearSelectedRow() {
        selectedDistributor = null
        selectedRow?.isSelected = false
        selectedRow?.findViewById<ImageView>(R.id.ivSelected)?.visibility = View.GONE
        selectedRow?.strokeWidth = 1
        selectedRow?.setStrokeColor(android.content.res.ColorStateList.valueOf(0xFFE5E7EB.toInt()))
        selectedRow = null
    }

    private fun showEmptyMessageIfNeeded(isEmpty: Boolean) {
        if (!isEmpty) return

        val msg = TextView(this).apply {
            text = "No verified distributors found.\nYou can search by ID below to continue."
            setTextColor(0xFF6B7280.toInt())
            textSize = 14f
            setLineSpacing(0f, 1.15f)
            setPadding(6, 8, 6, 8)
        }
        distributorListContainer.addView(msg)
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun submitTransfer(distributorId: Long) {
        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.farmerApi.transferToDistributor(
                        TransferToDistributorReq(batch_id = batchId, distributor_id = distributorId)
                    )
                }
                if (!resp.ok) {
                    toast(resp.error ?: "Transfer failed")
                    return@launch
                }

                val distributorName = selectedDistributor?.full_name
                    ?: cachedDistributors.firstOrNull { it.id == distributorId }?.full_name
                    ?: "Distributor"
                val i = Intent(this@SelectDistributorActivity, FarmerTransferSuccessActivity::class.java).apply {
                    if (!batchCode.isNullOrBlank()) putExtra("batch_code", batchCode)
                    putExtra("batch_id", batchId.toString())
                    putExtra("product_name", productName ?: "")
                    putExtra("distributor_id", distributorId.toString())
                    putExtra("distributor_name", distributorName)
                }
                startActivity(i)
                finish()
            } catch (_: Exception) {
                toast("Transfer failed")
            }
        }
    }

    private fun parseDistributorId(raw: String): Long? {
        val digits = raw.filter { it.isDigit() }
        if (digits.isBlank()) return null
        return digits.toLongOrNull()
    }

    private fun formatDistributorId(id: Long): String {
        val padded = id.toString().padStart(3, '0')
        return "D-$padded"
    }
}
