package com.simats.agritrace

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.simats.agritrace.ApiClient
import com.simats.agritrace.ConfirmPickupReq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RetailerProductStatusActivity : AppCompatActivity() {

    private var currentBatchId: Int = -1
    private var currentBatchCode: String = ""

    private lateinit var tvBatchIdTop: TextView
    private lateinit var tvCropName: TextView
    private lateinit var tvQuantity: TextView
    private lateinit var tvHarvestDate: TextView
    private lateinit var tvFarmer: TextView
    private lateinit var tvDistributor: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_batch_verification)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootBatchVerify)) { v, insets ->
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
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        tvBatchIdTop = findViewById(R.id.tvBatchIdTop)

        tvCropName = findViewById(R.id.tvCropName)
        tvQuantity = findViewById(R.id.tvQuantity)
        tvHarvestDate = findViewById(R.id.tvHarvestDate)
        tvFarmer = findViewById(R.id.tvFarmer)
        tvDistributor = findViewById(R.id.tvDistributor)

        val btnConfirmReceipt = findViewById<Button>(R.id.btnConfirmReceipt)
        val btnViewHistory = findViewById<Button>(R.id.btnViewHistory)

        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        tvTitle.text = "Product Status"
        btnConfirmReceipt.text = "Available for Sale"
        btnViewHistory.text = "Mark as Sold"

        currentBatchId = intent.getIntExtra("batch_id", -1)
        currentBatchCode = intent.getStringExtra("batch_code").orEmpty().trim()

        bindHeader(
            cropName = intent.getStringExtra("crop_name"),
            quantity = intent.getStringExtra("quantity_kg"),
            harvestDate = intent.getStringExtra("harvest_date"),
            farmer = intent.getStringExtra("farmer_name"),
            distributor = intent.getStringExtra("distributor_name")
        )

        if (currentBatchId > 0 || currentBatchCode.isNotBlank()) {
            loadBatchDetailsIfMissing()
        } else {
            Toast.makeText(this, "Batch ID missing", Toast.LENGTH_SHORT).show()
        }

        btnConfirmReceipt.setOnClickListener {
            if (currentBatchId <= 0) {
                Toast.makeText(this, "Batch ID missing", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                try {
                    val resp = withContext(Dispatchers.IO) {
                        ApiClient.retailerApi.markAvailable(ConfirmPickupReq(batch_id = currentBatchId))
                    }
                    if (!resp.ok) {
                        Toast.makeText(this@RetailerProductStatusActivity, resp.error ?: "Update failed", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    Toast.makeText(this@RetailerProductStatusActivity, "Marked available", Toast.LENGTH_SHORT).show()
                    finish()
                } catch (_: Exception) {
                    Toast.makeText(this@RetailerProductStatusActivity, "Update failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnViewHistory.setOnClickListener {
            if (currentBatchId <= 0) {
                Toast.makeText(this, "Batch ID missing", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                try {
                    val resp = withContext(Dispatchers.IO) {
                        ApiClient.retailerApi.markSold(ConfirmPickupReq(batch_id = currentBatchId))
                    }
                    if (!resp.ok) {
                        Toast.makeText(this@RetailerProductStatusActivity, resp.error ?: "Update failed", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    Toast.makeText(this@RetailerProductStatusActivity, "Marked sold", Toast.LENGTH_SHORT).show()
                    finish()
                } catch (_: Exception) {
                    Toast.makeText(this@RetailerProductStatusActivity, "Update failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun bindHeader(
        cropName: String?,
        quantity: String?,
        harvestDate: String?,
        farmer: String?,
        distributor: String?
    ) {
        tvBatchIdTop.text = when {
            currentBatchCode.isNotBlank() -> currentBatchCode
            currentBatchId > 0 -> "Batch #$currentBatchId"
            else -> "--"
        }
        tvCropName.text = cropName?.trim().orEmpty().ifBlank { "Batch" }
        val qty = quantity?.trim().orEmpty()
        tvQuantity.text = if (qty.isNotBlank()) qty else "--"
        tvHarvestDate.text = harvestDate?.trim().orEmpty().ifBlank { "--" }
        tvFarmer.text = farmer?.trim().orEmpty().ifBlank { "--" }
        tvDistributor.text = distributor?.trim().orEmpty().ifBlank { "--" }
    }

    private fun loadBatchDetailsIfMissing() {
        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.retailerApi.getBatchDetails(
                        batchId = currentBatchId.takeIf { it > 0 }?.toLong(),
                        batchCode = currentBatchCode.ifBlank { null }
                    )
                }
                val batch = resp.batch
                if (!resp.ok || batch == null) {
                    return@launch
                }

                val resolvedId = batch.id.toInt()
                if (resolvedId > 0 && currentBatchId <= 0) {
                    currentBatchId = resolvedId
                }
                val code = batch.batch_code?.trim().orEmpty()
                if (code.isNotBlank()) {
                    currentBatchCode = code
                }

                bindHeader(
                    cropName = batch.crop_name,
                    quantity = batch.quantity_kg,
                    harvestDate = batch.harvest_date,
                    farmer = resp.farmer?.name,
                    distributor = resp.distributor?.name
                )
            } catch (_: Exception) {
            }
        }
    }
}
