package com.simats.agritrace.dist

import android.os.Bundle
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
import com.simats.agritrace.R
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class DistributorBatchDetailsActivity : AppCompatActivity() {

    private var currentBatchId: Int = -1
    private var currentBatchCode: String = ""
    private var currentTransferStatus: String = ""
    private lateinit var btnConfirmPickup: MaterialButton
    private lateinit var btnUpdateLocation: MaterialButton
    private var pendingLocationToast: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_distributor_batch_details)
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
        val qrPayload = intent.getStringExtra("qr_payload")
            ?: intent.getStringExtra("qrPayload")
            ?: ""
        val batchId = intent.getIntExtra("batch_id", -1)
            .takeIf { it > 0 }
            ?: intent.getIntExtra("batchId", -1)
        val batchCode = intent.getStringExtra("batch_code")
            ?: intent.getStringExtra("batchCode")
            ?: ""
        findViewById<ImageView>(R.id.ivQr).setOnClickListener {
            if (currentBatchId <= 0 && currentBatchCode.isBlank()) {
                Toast.makeText(this, "Batch ID missing", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val i = android.content.Intent(this, com.simats.agritrace.qr_code::class.java).apply {
                if (currentBatchId > 0) putExtra("batch_id", currentBatchId)
                if (currentBatchCode.isNotBlank()) putExtra("batch_code", currentBatchCode)
            }
            startActivity(i)
        }

        val parsedBatchId = if (batchId > 0) batchId else parseBatchId(qrPayload)
        currentBatchId = parsedBatchId
        currentBatchCode = batchCode
        if (parsedBatchId <= 0 && batchCode.isBlank() && qrPayload.isBlank()) {
            Toast.makeText(this, "Invalid batch data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.ivHistory).setOnClickListener {
            if (currentBatchId <= 0 && currentBatchCode.isBlank()) {
                Toast.makeText(this, "Batch ID missing", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = android.content.Intent(this, DistributorHistoryActivity::class.java).apply {
                if (currentBatchId > 0) putExtra("batch_id", currentBatchId)
                if (currentBatchCode.isNotBlank()) putExtra("batch_code", currentBatchCode)
            }
            startActivity(intent)
        }

        btnUpdateLocation = findViewById(R.id.btnUpdateLocation)
        btnConfirmPickup = findViewById(R.id.btnConfirmPickup)

        btnUpdateLocation.setOnClickListener {
            if (!btnUpdateLocation.isEnabled) {
                Toast.makeText(this, "Assign a retailer to update transport", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = android.content.Intent(this, DistributorUpdateTransportActivity::class.java).apply {
                if (currentBatchId > 0) putExtra(DistributorUpdateTransportActivity.EXTRA_BATCH_ID, currentBatchId.toString())
            }
            pendingLocationToast = true
            startActivity(intent)
        }

        btnConfirmPickup.setOnClickListener {
            if (currentBatchId <= 0) {
                Toast.makeText(this, "Batch ID missing", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val status = currentTransferStatus.uppercase(Locale.US)
            if (status.isNotBlank() && status != "IN_TRANSIT") {
                Toast.makeText(this, "Transfer already processed", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            confirmPickup(currentBatchId)
        }

        loadBatchDetails(parsedBatchId, batchCode)
    }

    override fun onResume() {
        super.onResume()
        if (currentBatchId > 0 || currentBatchCode.isNotBlank()) {
            loadBatchDetails(currentBatchId, currentBatchCode)
        }
        if (pendingLocationToast) {
            pendingLocationToast = false
            Toast.makeText(this, "Updated just now", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadBatchDetails(batchId: Int, batchCode: String) {
        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.distributorApi.getBatchDetails(
                        if (batchId > 0) batchId else null,
                        batchCode.ifBlank { null }
                    )
                }
                if (!resp.ok || resp.batch == null) {
                    Toast.makeText(this@DistributorBatchDetailsActivity, resp.error ?: "Failed to load batch", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val b = resp.batch
                val farmer = resp.farmer

                currentBatchCode = b.batch_code ?: currentBatchCode
                findViewById<TextView>(R.id.tvBatchId).text = currentBatchCode.ifBlank { "--" }
                findViewById<TextView>(R.id.tvCropName).text = b.crop_name ?: "--"
                val qty = b.quantity_kg?.trim().orEmpty()
                findViewById<TextView>(R.id.tvQuantity).text = if (qty.isNotBlank()) "$qty kg" else "--"
                findViewById<TextView>(R.id.tvHarvestDate).text = b.harvest_date ?: "--"
                findViewById<TextView>(R.id.tvFarmer).text = farmer?.name ?: "--"
                findViewById<TextView>(R.id.tvLocation).text = farmer?.location ?: "--"

                val transferStatus = resp.transfer?.status?.uppercase(Locale.US).orEmpty()
                currentTransferStatus = transferStatus
                val transportUpdated = resp.transfer?.transport_updated == true
                val retailerAssigned = resp.transfer?.retailer_assigned == true
                val statusLabel = when (transferStatus) {
                    "ASSIGNED" -> "Assigned"
                    "PICKED_UP" -> "Picked up"
                    "IN_TRANSIT" -> if (transportUpdated) "In Transit" else "Accepted"
                    else -> (b.status ?: "").replace('_', ' ').trim().ifBlank { "Assigned" }
                }
                findViewById<TextView>(R.id.tvInTransit).text = statusLabel
                val canUpdateLocation = retailerAssigned
                btnUpdateLocation.isEnabled = canUpdateLocation
                btnUpdateLocation.alpha = if (canUpdateLocation) 1f else 0.5f
                btnUpdateLocation.visibility = if (canUpdateLocation) android.view.View.VISIBLE else android.view.View.GONE

                val canConfirmPickup = transferStatus == "IN_TRANSIT"
                btnConfirmPickup.isEnabled = canConfirmPickup
                btnConfirmPickup.alpha = if (canConfirmPickup) 1f else 0.5f
                btnConfirmPickup.visibility = if (canConfirmPickup) android.view.View.VISIBLE else android.view.View.GONE
                val loc = resp.location_update
                val temp = loc?.temperature_c
                val locText = loc?.location_text?.trim().orEmpty()
                val tempText = if (temp != null) String.format(Locale.US, "%.1f°C", temp) else ""
                val statusText = when {
                    tempText.isNotBlank() && locText.isNotBlank() -> "Temperature: $tempText"
                    tempText.isNotBlank() -> "Temperature: $tempText"
                    locText.isNotBlank() -> "Location: $locText"
                    else -> "Temperature: --"
                }
                findViewById<TextView>(R.id.tvTemp).text = statusText
            } catch (_: Exception) {
                Toast.makeText(this@DistributorBatchDetailsActivity, "Failed to load batch", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmPickup(batchId: Int) {
        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.distributorApi.confirmPickup(ConfirmPickupReq(batch_id = batchId))
                }
                if (resp.ok) {
                    Toast.makeText(this@DistributorBatchDetailsActivity, "Pickup confirmed", Toast.LENGTH_SHORT).show()
                    val intent = android.content.Intent(this@DistributorBatchDetailsActivity, com.simats.agritrace.SelectRetailerActivity::class.java).apply {
                        putExtra("batch_id", batchId)
                        if (currentBatchCode.isNotBlank()) {
                            putExtra("batch_code", currentBatchCode)
                        }
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this@DistributorBatchDetailsActivity, resp.error ?: "Confirm pickup failed", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(this@DistributorBatchDetailsActivity, "Confirm pickup failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun parseBatchId(payload: String): Int {
        if (payload.isBlank()) return -1
        val trimmed = payload.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val obj = org.json.JSONObject(trimmed)
                if (obj.has("batch_id")) return obj.get("batch_id").toString().toIntOrNull() ?: -1
            } catch (_: Exception) {
            }
        }

        val parts = payload.split("|")
        for (p in parts) {
            val idx = p.indexOf("=")
            if (idx <= 0) continue
            val k = p.substring(0, idx).trim()
            if (k != "batch_id") continue
            return p.substring(idx + 1).trim().toIntOrNull() ?: -1
        }
        return -1
    }
}
