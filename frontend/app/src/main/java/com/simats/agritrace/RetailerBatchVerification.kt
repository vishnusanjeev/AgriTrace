package com.simats.agritrace

import android.content.Intent
import android.os.Bundle
import android.view.View
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class RetailerBatchVerification : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var tvBatchIdTop: TextView

    private lateinit var tvCropName: TextView
    private lateinit var tvQuantity: TextView
    private lateinit var tvHarvestDate: TextView
    private lateinit var tvFarmer: TextView
    private lateinit var tvDistributor: TextView

    private lateinit var btnConfirmReceipt: Button
    private lateinit var btnViewHistory: Button

    private var currentBatchId: Int = -1
    private var currentBatchCode: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_batch_verification)

        // Apply window insets to root container, preserving original padding
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

        // Apply window insets to bottom buttons container to prevent clipping
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bottomButtons)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val originalPaddingBottom = v.paddingBottom
            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                maxOf(originalPaddingBottom, systemBars.bottom)
            )
            insets
        }

        btnBack = findViewById(R.id.btnBack)
        tvBatchIdTop = findViewById(R.id.tvBatchIdTop)

        tvCropName = findViewById(R.id.tvCropName)
        tvQuantity = findViewById(R.id.tvQuantity)
        tvHarvestDate = findViewById(R.id.tvHarvestDate)
        tvFarmer = findViewById(R.id.tvFarmer)
        tvDistributor = findViewById(R.id.tvDistributor)

        btnConfirmReceipt = findViewById(R.id.btnConfirmReceipt)
        btnViewHistory = findViewById(R.id.btnViewHistory)

        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val batchId = intent.getIntExtra("batch_id", -1)
        val batchCode = intent.getStringExtra("batch_code").orEmpty().trim()
        currentBatchId = batchId
        currentBatchCode = batchCode

        tvBatchIdTop.text = when {
            batchCode.isNotBlank() -> batchCode
            batchId > 0 -> "Batch #$batchId"
            else -> "--"
        }

        if (batchId > 0 || batchCode.isNotBlank()) {
            loadBatchDetails(batchId, batchCode)
        } else {
            Toast.makeText(this, "Batch not found", Toast.LENGTH_SHORT).show()
        }

        btnConfirmReceipt.setOnClickListener {
            try {
                if (currentBatchId <= 0) {
                    Toast.makeText(this, "Batch ID missing", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val i = Intent(this, RetailerConfirmReceipt::class.java)
                i.putExtra("batch_id", currentBatchId)
                if (currentBatchCode.isNotBlank()) i.putExtra("batch_code", currentBatchCode)
                startActivity(i)
            } catch (_: Throwable) {
                Toast.makeText(this, "Unable to open receipt screen", Toast.LENGTH_SHORT).show()
            }
        }

        btnViewHistory.setOnClickListener {
            if (currentBatchId <= 0 && currentBatchCode.isBlank()) {
                Toast.makeText(this, "Batch ID missing", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val i = Intent(this, com.simats.agritrace.retailer.RetailerHistoryActivity::class.java).apply {
                if (currentBatchId > 0) putExtra("batch_id", currentBatchId)
                if (currentBatchCode.isNotBlank()) putExtra("batch_code", currentBatchCode)
            }
            startActivity(i)
        }
    }

    private fun loadBatchDetails(batchId: Int, batchCode: String) {
        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.retailerApi.getBatchDetails(
                        batchId.takeIf { it > 0 }?.toLong(),
                        batchCode.ifBlank { null }
                    )
                }

                if (!resp.ok || resp.batch == null) {
                    Toast.makeText(
                        this@RetailerBatchVerification,
                        resp.error ?: "Failed to load batch details",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val batch = resp.batch

                currentBatchId = batch?.id?.toInt() ?: currentBatchId
                if (currentBatchCode.isBlank()) {
                    currentBatchCode = batch?.batch_code?.trim().orEmpty()
                }

                tvBatchIdTop.text = when {
                    currentBatchCode.isNotBlank() -> currentBatchCode
                    currentBatchId > 0 -> "Batch #$currentBatchId"
                    else -> "--"
                }

                tvCropName.text = batch?.crop_name?.trim().orEmpty().ifBlank { "Batch" }

                val qty = batch?.quantity_kg?.trim().orEmpty()
                tvQuantity.text = if (qty.isNotBlank()) "$qty kg" else "--"

                tvHarvestDate.text = batch?.harvest_date?.trim().orEmpty().ifBlank { "--" }
                tvFarmer.text = resp.farmer?.name?.trim().orEmpty().ifBlank { "--" }
                tvDistributor.text = resp.distributor?.name?.trim().orEmpty().ifBlank { "--" }

                val batchStatus = batch?.status?.trim()?.uppercase(Locale.US).orEmpty()
                val transferStatus = resp.transfer?.status?.trim()?.uppercase(Locale.US).orEmpty()
                val receiptConfirmed = resp.transfer?.receipt_confirmed == true

                // Final batch states
                val isFinalBatch =
                    batchStatus == "SOLD" ||
                            batchStatus == "CONSUMED" ||
                            batchStatus == "CANCELLED"

                // ✅ Only treat receipt_confirmed as "received" if transfer status is actually received/completed
                val isReceivedStatus =
                    transferStatus == "RECEIVED_BY_RETAILER" ||
                            transferStatus == "RECEIVED" ||
                            transferStatus == "COMPLETED" ||
                            transferStatus == "CLOSED"

                val isAlreadyReceived = isReceivedStatus || (receiptConfirmed && isReceivedStatus)

                // ✅ For PICKED_UP / IN_TRANSIT / ASSIGNED etc, show Confirm Receipt
                val shouldShowConfirm = !isFinalBatch && !isAlreadyReceived

                btnConfirmReceipt.visibility = if (shouldShowConfirm) View.VISIBLE else View.GONE

            } catch (_: Exception) {
                Toast.makeText(
                    this@RetailerBatchVerification,
                    "Failed to load batch details",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

}
