package com.simats.agritrace

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class FarmerBatchDetailsActivity : AppCompatActivity() {

    private val TAG = "BATCH_DETAILS"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = android.graphics.Color.WHITE
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_batch_details)

        Log.d(TAG, "batch_details activity started")

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

        val tvProductName = findViewById<TextView>(R.id.tvProductName)
        val tvBatchId = findViewById<TextView>(R.id.tvBatchId)

        val tvQuantity = findViewById<TextView>(R.id.tvQuantity)
        val tvHarvestDate = findViewById<TextView>(R.id.tvHarvestDate)
        val tvFertilizer = findViewById<TextView>(R.id.tvFertilizer)
        val tvSeedType = findViewById<TextView>(R.id.tvSeedType)

        val tag3 = findViewById<TextView>(R.id.tag3)

        val cardTransferAccepted = findViewById<MaterialCardView>(R.id.cardTransferAccepted)
        val rowActions = findViewById<View>(R.id.rowActions)

        val tvAcceptedSub = findViewById<TextView>(R.id.tvAcceptedSub)
        val tvTxHash = findViewById<TextView>(R.id.tvTxHash)

        val tvStep1Time = findViewById<TextView>(R.id.tvStep1Time)
        val tvStep2Time = findViewById<TextView>(R.id.tvStep2Time)
        val tvStep3Time = findViewById<TextView>(R.id.tvStep3Time)
        val tvStep2Title = findViewById<TextView>(R.id.tvStep2Title)
        val tvStep2Sub = findViewById<TextView>(R.id.tvStep2Sub)
        val tvStep3Title = findViewById<TextView>(R.id.tvStep3Title)
        val tvStep3Sub = findViewById<TextView>(R.id.tvStep3Sub)
        val tvStep2Num = findViewById<TextView>(R.id.tvStep2Num)
        val tvStep3Num = findViewById<TextView>(R.id.tvStep3Num)
        val circle2 = findViewById<MaterialCardView>(R.id.circle2)
        val circle3 = findViewById<MaterialCardView>(R.id.circle3)
        val line1 = findViewById<View>(R.id.line1)
        val line2 = findViewById<View>(R.id.line2)

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val btnTransfer = findViewById<MaterialButton>(R.id.btnTransfer)
        val btnShare = findViewById<MaterialButton>(R.id.btnShare)
        val btnShareCertificateAccepted = findViewById<MaterialButton>(R.id.btnShareCertificateAccepted)

        val cropName = intent.getStringExtra("crop_name")
        val batchId = intent.getIntExtra("batch_id", -1)
        val batchCode = intent.getStringExtra("batch_code")
        val quantity = intent.getStringExtra("quantity")
        val harvestDate = intent.getStringExtra("harvest_date")

        val fertilizer = intent.getStringExtra("fertilizer") ?: "Organic Compost"
        val seedType = intent.getStringExtra("seed_type") ?: "Heirloom"

        val registeredAt = intent.getStringExtra("registered_at") ?: "Oct 12, 2023 08:30 AM"
        val transferredAt = intent.getStringExtra("transferred_at")
        val receivedAt = intent.getStringExtra("received_at")

        val transferStatus = (intent.getStringExtra("transfer_status") ?: "").trim().uppercase()
        val txHash = intent.getStringExtra("tx_hash")
        val blockHash = intent.getStringExtra("block_hash")
        val blockNumber = intent.getStringExtra("block_number")
        val chainName = intent.getStringExtra("chain_name") ?: "Ethereum"
        val chainNetwork = intent.getStringExtra("chain_network") ?: "Mainnet"
        val chainTimestamp = intent.getStringExtra("chain_timestamp") ?: "2023-10-12 08:30:15 UTC"
        val farmerName = intent.getStringExtra("farmer_name") ?: "Green Valley Farms"
        val productType = intent.getStringExtra("product_type") ?: "Organic"

        val acceptedMessage = intent.getStringExtra("transfer_accepted_message")
            ?: "Distributor confirmed receipt on Oct 14, 2023 02:30 PM"

        Log.d(TAG, "Intent data received ↓↓↓")
        Log.d(TAG, "crop_name        = $cropName")
        Log.d(TAG, "batch_id         = $batchId")
        Log.d(TAG, "batch_code       = $batchCode")
        Log.d(TAG, "quantity         = $quantity")
        Log.d(TAG, "harvest_date     = $harvestDate")
        Log.d(TAG, "fertilizer       = $fertilizer")
        Log.d(TAG, "seed_type        = $seedType")
        Log.d(TAG, "registered_at    = $registeredAt")
        Log.d(TAG, "transferred_at   = $transferredAt")
        Log.d(TAG, "received_at      = $receivedAt")
        Log.d(TAG, "transfer_status  = $transferStatus")
        Log.d(TAG, "tx_hash          = $txHash")
        Log.d(TAG, "block_hash       = $blockHash")

        tvProductName.text = cropName?.takeIf { it.isNotBlank() } ?: "Product"
        tvBatchId.text = when {
            !batchCode.isNullOrBlank() -> "Batch #$batchCode"
            batchId != -1 -> "Batch #B-$batchId"
            else -> "Batch #N/A"
        }

        tvQuantity.text = quantity?.takeIf { it.isNotBlank() } ?: "--"
        tvHarvestDate.text = harvestDate?.takeIf { it.isNotBlank() } ?: "--"
        tvFertilizer.text = fertilizer
        tvSeedType.text = seedType

        tvStep1Time.text = registeredAt
        tvStep2Time.text = transferredAt ?: "Pending"
        tvStep3Time.text = receivedAt ?: "Pending"

        val isTransferred =
            transferStatus == "TRANSFERRED" || transferStatus == "PENDING" || transferStatus == "ACCEPTED" || !transferredAt.isNullOrBlank()
        val isAccepted = transferStatus == "ACCEPTED"

        tag3.visibility = if (isTransferred) View.VISIBLE else View.GONE

        if (isAccepted) {
            cardTransferAccepted.visibility = View.VISIBLE
            rowActions.visibility = View.GONE
            tvAcceptedSub.text = acceptedMessage
            tvTxHash.text = txHash?.takeIf { it.isNotBlank() } ?: "0x..."
        } else {
            cardTransferAccepted.visibility = View.GONE
            rowActions.visibility = View.VISIBLE
        }

        fun openCertificate() {
            val effectiveBatchId = batchId // never reassign batchId

            if (effectiveBatchId == -1 && batchCode.isNullOrBlank()) {
                Toast.makeText(this, "Batch ID missing", Toast.LENGTH_SHORT).show()
                return
            }

            val batchDisplay = when {
                !batchCode.isNullOrBlank() -> batchCode
                effectiveBatchId != -1 -> "B-$effectiveBatchId"
                else -> "N/A"
            }

            val certId = "CERT-$batchDisplay-${System.currentTimeMillis()}"

            val intentToCert = Intent(this, CertificateActivity::class.java).apply {
                putExtra("batch_id", effectiveBatchId)
                putExtra("batch_code", batchCode ?: "")
                putExtra("cert_product", tvProductName.text.toString())
                putExtra("cert_batch", batchDisplay)
                putExtra("cert_farmer", farmerName)
                putExtra("cert_harvest", tvHarvestDate.text.toString())
                putExtra("cert_qty", tvQuantity.text.toString())
                putExtra("cert_type", productType)

                putExtra("cert_tx", txHash ?: "")
                putExtra("cert_block", blockHash ?: "")
                putExtra("cert_block_no", blockNumber ?: "")
                putExtra("cert_chain", chainName)
                putExtra("cert_network", chainNetwork)
                putExtra("cert_time", chainTimestamp)

                putExtra("cert_id", certId)
                putExtra("cert_issued", "Issued: ${tvHarvestDate.text}")
            }

            startActivity(intentToCert)
        }


        btnBack.setOnClickListener { finish() }

        btnTransfer.setOnClickListener {
            if (batchId == -1) {
                Log.e(TAG, "Batch ID missing, cannot transfer")
                Toast.makeText(this, "Batch ID missing", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val i = Intent(this, com.simats.agritrace.Farmer.SelectDistributorActivity::class.java).apply {
                putExtra("batch_id", batchId.toLong())
                putExtra("batch_code", batchCode ?: "")
                putExtra("product_name", tvProductName.text.toString())
                putExtra("product_type", productType)
                putExtra("quantity", quantity ?: "")
                putExtra("harvest_date", harvestDate ?: "")
            }
            startActivity(i)
        }

        // ✅ NOW SHARE CERTIFICATE OPENS CertificateActivity
        btnShare.setOnClickListener {
            if (batchId == -1 && batchCode.isNullOrBlank()) {
                Toast.makeText(this, "Batch ID missing", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val batchDisplay = when {
                !batchCode.isNullOrBlank() -> batchCode
                batchId != -1 -> "B-$batchId"
                else -> "N/A"
            }

            val i = Intent(this, CertificateActivity::class.java).apply {
                putExtra("batch_id", batchId)
                putExtra("batch_code", batchCode ?: "")
                putExtra("cert_product", tvProductName.text.toString())
                putExtra("cert_batch", batchDisplay)
                putExtra("cert_farmer", farmerName)
                putExtra("cert_harvest", tvHarvestDate.text.toString())
                putExtra("cert_qty", tvQuantity.text.toString())
                putExtra("cert_type", productType)

                putExtra("cert_tx", txHash ?: "")
                putExtra("cert_block", blockHash ?: "")
                putExtra("cert_block_no", blockNumber ?: "")
                putExtra("cert_chain", chainName)
                putExtra("cert_network", chainNetwork)
                putExtra("cert_time", chainTimestamp)
            }

            startActivity(i)
        }

        btnShareCertificateAccepted.setOnClickListener { openCertificate() }


        if (batchId != -1 && Session.isLoggedIn(this)) {
            loadBatchDetails(
                batchId,
                tvProductName,
                tvBatchId,
                tvQuantity,
                tvHarvestDate,
                tvFertilizer,
                tvSeedType,
                tvTxHash,
                tag3,
                btnTransfer,
                rowActions,
                cardTransferAccepted,
                tvAcceptedSub,
                tvStep2Time,
                tvStep3Time,
                tvStep1Time,
                tvStep2Title,
                tvStep2Sub,
                tvStep3Title,
                tvStep3Sub,
                tvStep2Num,
                tvStep3Num,
                circle2,
                circle3,
                line1,
                line2
            )
        }
    }

    private fun loadBatchDetails(
        batchId: Int,
        tvProductName: TextView,
        tvBatchId: TextView,
        tvQuantity: TextView,
        tvHarvestDate: TextView,
        tvFertilizer: TextView,
        tvSeedType: TextView,
        tvTxHash: TextView,
        tag3: TextView,
        btnTransfer: MaterialButton,
        rowActions: View,
        cardTransferAccepted: MaterialCardView,
        tvAcceptedSub: TextView,
        tvStep2Time: TextView,
        tvStep3Time: TextView,
        tvStep1Time: TextView,
        tvStep2Title: TextView,
        tvStep2Sub: TextView,
        tvStep3Title: TextView,
        tvStep3Sub: TextView,
        tvStep2Num: TextView,
        tvStep3Num: TextView,
        circle2: MaterialCardView,
        circle3: MaterialCardView,
        line1: View,
        line2: View
    ) {
        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.farmerApi.getBatchDetails(batchId)
                }
                if (!resp.ok) return@launch

                val batch = resp.batch ?: return@launch
                val code = batch.batch_code?.takeIf { it.isNotBlank() }
                if (!code.isNullOrBlank()) {
                    tvBatchId.text = "Batch #$code"
                }

                tvProductName.text = batch.crop_name?.takeIf { it.isNotBlank() } ?: tvProductName.text

                val qty = batch.quantity_kg?.trim().orEmpty()
                tvQuantity.text = if (qty.isNotBlank()) "$qty kg" else tvQuantity.text
                tvHarvestDate.text = batch.harvest_date?.takeIf { it.isNotBlank() } ?: tvHarvestDate.text
                tvFertilizer.text = batch.fertilizers_used?.takeIf { it.isNotBlank() } ?: "--"
                tvSeedType.text = batch.seed_variety?.takeIf { it.isNotBlank() } ?: "--"

                val tx = resp.blockchain?.tx_hash?.takeIf { it.isNotBlank() }
                if (!tx.isNullOrBlank()) tvTxHash.text = tx

                val createdAt = batch.created_at?.takeIf { it.isNotBlank() }
                if (!createdAt.isNullOrBlank()) tvStep1Time.text = createdAt

                val transfer = resp.transfer
                val locUpdate = resp.location_update

                if (transfer != null) {
                    val status = transfer.status?.trim()?.uppercase(Locale.US).orEmpty()
                    val isRejected = status == "REJECTED" || status == "CANCELLED"

                    // Always show badge once a transfer exists
                    tag3.visibility = View.VISIBLE
                    tag3.text = when (status) {
                        "ASSIGNED" -> "Transferred"
                        "IN_TRANSIT" -> "In Transit"
                        "PICKED_UP" -> "Received"
                        "REJECTED" -> "Rejected"
                        "CANCELLED" -> "Cancelled"
                        else -> "Transferred"
                    }

                    // Step2 time (transfer created)
                    tvStep2Time.text = transfer.created_at ?: "Transferred"

                    if (status == "PICKED_UP") {
                        tvStep3Time.text = transfer.updated_at ?: "Received"

                        // Show accepted card for received (as you already do)
                        cardTransferAccepted.visibility = View.VISIBLE
                        rowActions.visibility = View.GONE

                        val name = transfer.to_user_name?.ifBlank { "Distributor" } ?: "Distributor"
                        tvAcceptedSub.text = "Received by $name on ${transfer.updated_at ?: "recently"}"

                        // Hide transfer button once received
                        btnTransfer.visibility = View.GONE
                    } else {
                        // For all non-received statuses (ASSIGNED / IN_TRANSIT / REJECTED / etc.)
                        cardTransferAccepted.visibility = View.GONE
                        rowActions.visibility = View.VISIBLE

                        // If rejected/cancelled, farmer must be able to transfer again
                        btnTransfer.visibility = if (isRejected) View.VISIBLE else View.GONE

                        tvStep3Time.text = when (status) {
                            "IN_TRANSIT" -> (locUpdate?.recorded_at ?: transfer.updated_at ?: "In transit")
                            "REJECTED", "CANCELLED" -> (transfer.updated_at ?: "Updated")
                            else -> "Pending"
                        }
                    }

                    // Keep your derived-status behavior exactly, just add rejected/cancelled pass-through
                    val derivedStatus = if (status == "ASSIGNED" && locUpdate != null) "IN_TRANSIT" else status

                    updateJourneyTimeline(
                        derivedStatus,
                        transfer,
                        tvStep2Title,
                        tvStep2Sub,
                        tvStep2Time,
                        tvStep3Title,
                        tvStep3Sub,
                        tvStep3Time,
                        tvStep2Num,
                        tvStep3Num,
                        circle2,
                        circle3,
                        line1,
                        line2,
                        locUpdate
                    )
                } else {
                    tag3.visibility = View.GONE
                    btnTransfer.visibility = View.VISIBLE

                    updateJourneyTimeline(
                        "",
                        null,
                        tvStep2Title,
                        tvStep2Sub,
                        tvStep2Time,
                        tvStep3Title,
                        tvStep3Sub,
                        tvStep3Time,
                        tvStep2Num,
                        tvStep3Num,
                        circle2,
                        circle3,
                        line1,
                        line2,
                        null
                    )
                }
            } catch (_: Exception) {
                // Keep intent fallback values.
            }
        }
    }


    private fun updateJourneyTimeline(
        status: String,
        transfer: TransferInfoDto?,
        tvStep2Title: TextView,
        tvStep2Sub: TextView,
        tvStep2Time: TextView,
        tvStep3Title: TextView,
        tvStep3Sub: TextView,
        tvStep3Time: TextView,
        tvStep2Num: TextView,
        tvStep3Num: TextView,
        circle2: MaterialCardView,
        circle3: MaterialCardView,
        line1: View,
        line2: View,
        locationUpdate: LocationUpdateDto?
    ) {
        val completed = 0xFF0A8F5A.toInt() // green
        val current = 0xFF2563EB.toInt()   // blue
        val gray = 0xFFE5E7EB.toInt()
        val textPrimary = 0xFF0F172A.toInt()
        val textSecondary = 0xFF94A3B8.toInt()
        val textSubtle = 0xFF64748B.toInt()

        val danger = 0xFFEF4444.toInt() // red (for rejected/cancelled)

        line1.setBackgroundColor(completed)

        when (status) {
            "ASSIGNED" -> {
                circle2.setCardBackgroundColor(completed)
                tvStep2Num.text = "✓"
                tvStep2Num.setTextColor(0xFFFFFFFF.toInt())
                tvStep2Title.text = "Transferred"
                tvStep2Sub.text = "Assigned to distributor"
                tvStep2Title.setTextColor(textPrimary)
                tvStep2Sub.setTextColor(textSubtle)

                circle3.setCardBackgroundColor(gray)
                tvStep3Num.text = "3"
                tvStep3Num.setTextColor(textSecondary)
                tvStep3Title.text = "Awaiting pickup"
                tvStep3Sub.text = "Distributor has not confirmed pickup"
                tvStep3Title.setTextColor(textSecondary)
                tvStep3Sub.setTextColor(textSecondary)
                tvStep3Time.text = "Pending"
                line2.setBackgroundColor(gray)
            }

            "IN_TRANSIT" -> {
                circle2.setCardBackgroundColor(completed)
                tvStep2Num.text = "✓"
                tvStep2Num.setTextColor(0xFFFFFFFF.toInt())
                tvStep2Title.text = "Transferred"
                tvStep2Sub.text = "Pickup confirmed, on the way"
                tvStep2Title.setTextColor(textPrimary)
                tvStep2Sub.setTextColor(textSubtle)

                circle3.setCardBackgroundColor(current)
                tvStep3Num.text = "3"
                tvStep3Num.setTextColor(0xFFFFFFFF.toInt())
                tvStep3Title.text = "In Transit"
                val locText = locationUpdate?.location_text?.trim().orEmpty()
                tvStep3Sub.text = if (locText.isNotBlank()) "Last location: $locText" else "Shipment in progress"
                tvStep3Title.setTextColor(textPrimary)
                tvStep3Sub.setTextColor(textSubtle)
                tvStep3Time.text = locationUpdate?.recorded_at ?: transfer?.updated_at ?: "In transit"
                line2.setBackgroundColor(completed)
            }

            "PICKED_UP" -> {
                circle2.setCardBackgroundColor(completed)
                tvStep2Num.text = "✓"
                tvStep2Num.setTextColor(0xFFFFFFFF.toInt())
                tvStep2Title.text = "Transferred"
                tvStep2Sub.text = "Pickup confirmed"
                tvStep2Title.setTextColor(textPrimary)
                tvStep2Sub.setTextColor(textSubtle)

                circle3.setCardBackgroundColor(completed)
                tvStep3Num.text = "✓"
                tvStep3Num.setTextColor(0xFFFFFFFF.toInt())
                tvStep3Title.text = "Received"
                tvStep3Sub.text = "Distributor confirmed receipt"
                tvStep3Title.setTextColor(textPrimary)
                tvStep3Sub.setTextColor(textSubtle)
                tvStep3Time.text = transfer?.updated_at ?: "Received"
                line2.setBackgroundColor(completed)
            }

            "REJECTED", "CANCELLED" -> {
                circle2.setCardBackgroundColor(danger)
                tvStep2Num.text = "!"
                tvStep2Num.setTextColor(0xFFFFFFFF.toInt())
                tvStep2Title.text = if (status == "REJECTED") "Rejected" else "Cancelled"
                tvStep2Sub.text = if (status == "REJECTED") "Distributor rejected the transfer" else "Transfer was cancelled"
                tvStep2Title.setTextColor(textPrimary)
                tvStep2Sub.setTextColor(textSubtle)

                circle3.setCardBackgroundColor(gray)
                tvStep3Num.text = "3"
                tvStep3Num.setTextColor(textSecondary)
                tvStep3Title.text = "Received by Distributor"
                tvStep3Sub.text = "Not received"
                tvStep3Title.setTextColor(textSecondary)
                tvStep3Sub.setTextColor(textSecondary)
                tvStep3Time.text = "—"
                line2.setBackgroundColor(gray)
            }

            else -> {
                circle2.setCardBackgroundColor(gray)
                tvStep2Num.text = "2"
                tvStep2Num.setTextColor(textSecondary)
                tvStep2Title.text = "Ready to transfer"
                tvStep2Sub.text = "Not transferred yet"
                tvStep2Title.setTextColor(textSecondary)
                tvStep2Sub.setTextColor(textSecondary)
                tvStep2Time.text = "Pending"

                circle3.setCardBackgroundColor(gray)
                tvStep3Num.text = "3"
                tvStep3Num.setTextColor(textSecondary)
                tvStep3Title.text = "Awaiting pickup"
                tvStep3Sub.text = "Distributor has not confirmed pickup"
                tvStep3Title.setTextColor(textSecondary)
                tvStep3Sub.setTextColor(textSecondary)
                tvStep3Time.text = "Pending"
                line2.setBackgroundColor(gray)
            }
        }
    }

}

