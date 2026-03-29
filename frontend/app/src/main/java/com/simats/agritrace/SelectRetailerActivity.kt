package com.simats.agritrace

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.simats.agritrace.dist.DistributorReceivedProductsActivity
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SelectRetailerActivity : AppCompatActivity() {

    private var batchId: Long = -1L
    private var batchCode: String = ""
    private var selectedRetailerId: Long? = null
    private lateinit var listContainer: LinearLayout
    private lateinit var btnConfirm: Button
    private lateinit var etManual: EditText
    private val retailerRows = ArrayList<RetailerRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_select_retailer)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
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

        batchId = intent.getLongExtra("batch_id", -1L)
        if (batchId <= 0) {
            batchId = intent.getIntExtra("batch_id", -1).toLong()
        }
        batchCode = intent.getStringExtra("batch_code")
            ?: intent.getStringExtra("batchCode")
            ?: ""

        listContainer = findViewById(R.id.retailerListContainer)
        btnConfirm = findViewById(R.id.btnConfirmTransferRetailer)
        etManual = findViewById(R.id.etManualRetailer)

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        val btnCancel = findViewById<Button>(R.id.btnCancel)
        btnCancel.setOnClickListener { finish() }

        btnConfirm.setOnClickListener { submitTransfer() }

        loadRetailers()
    }

    private fun loadRetailers() {
        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.distributorApi.getRetailersList()
                }
                if (!resp.ok) {
                    Toast.makeText(this@SelectRetailerActivity, resp.error ?: "Failed to load retailers", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                renderRetailers(resp.items.orEmpty())
            } catch (_: Exception) {
                Toast.makeText(this@SelectRetailerActivity, "Failed to load retailers", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderRetailers(items: List<RetailerDto>) {
        listContainer.removeAllViews()
        retailerRows.clear()

        if (items.isEmpty()) {
            Toast.makeText(this, "No retailers available", Toast.LENGTH_SHORT).show()
            return
        }

        val inflater = LayoutInflater.from(this)
        for (item in items) {
            val view = inflater.inflate(R.layout.item_retailer_select, listContainer, false)
            val card = view.findViewById<MaterialCardView>(R.id.cardRetailer)
            val tvName = view.findViewById<android.widget.TextView>(R.id.tvRetailerName)
            val tvMeta = view.findViewById<android.widget.TextView>(R.id.tvRetailerMeta)
            val ivSelected = view.findViewById<ImageView>(R.id.ivSelected)

            tvName.text = item.full_name?.ifBlank { "Retailer" } ?: "Retailer"
            val phone = item.phone_e164?.trim().orEmpty()
            val location = item.location?.trim().orEmpty()
            val meta = listOf(phone, location).filter { it.isNotBlank() }.joinToString(" • ")
            tvMeta.text = if (meta.isNotBlank()) meta else "--"

            val row = RetailerRow(item.id, card, ivSelected)
            retailerRows.add(row)

            card.setOnClickListener {
                selectedRetailerId = item.id
                updateSelection()
            }

            listContainer.addView(view)
        }
    }

    private fun updateSelection() {
        for (row in retailerRows) {
            val selected = row.id == selectedRetailerId
            row.check.visibility = if (selected) android.view.View.VISIBLE else android.view.View.GONE
            row.card.strokeWidth = if (selected) 2 else 1
            row.card.setStrokeColor(if (selected) android.graphics.Color.parseColor("#2D6AE8") else android.graphics.Color.parseColor("#E6E8EC"))
        }
    }

    private fun submitTransfer() {
        if (batchId <= 0) {
            Toast.makeText(this, "Batch ID missing", Toast.LENGTH_SHORT).show()
            return
        }

        var retailerId = selectedRetailerId
        if (retailerId == null) {
            val manual = etManual.text?.toString()?.trim().orEmpty()
            if (manual.isNotBlank()) {
                retailerId = manual.toLongOrNull()
                if (retailerId == null) {
                    Toast.makeText(this, "Enter a valid retailer ID", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }

        if (retailerId == null) {
            Toast.makeText(this, "Select a retailer first", Toast.LENGTH_SHORT).show()
            return
        }

        btnConfirm.isEnabled = false
        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.distributorApi.transferToRetailer(
                        TransferToRetailerReq(batch_id = batchId, retailer_id = retailerId!!)
                    )
                }
                if (resp.ok) {
                    Toast.makeText(this@SelectRetailerActivity, "Retailer assigned", Toast.LENGTH_SHORT).show()
                    val intent = android.content.Intent(this@SelectRetailerActivity, com.simats.agritrace.dist.DistributorUpdateTransportActivity::class.java).apply {
                        putExtra(com.simats.agritrace.dist.DistributorUpdateTransportActivity.EXTRA_BATCH_ID, batchId.toString())
                    }
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@SelectRetailerActivity, resp.error ?: "Transfer failed", Toast.LENGTH_SHORT).show()
                    btnConfirm.isEnabled = true
                }
            } catch (_: Exception) {
                Toast.makeText(this@SelectRetailerActivity, "Transfer failed", Toast.LENGTH_SHORT).show()
                btnConfirm.isEnabled = true
            }
        }
    }

    private data class RetailerRow(
        val id: Long,
        val card: MaterialCardView,
        val check: ImageView
    )
}
