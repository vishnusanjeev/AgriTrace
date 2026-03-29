package com.simats.agritrace

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.simats.agritrace.Farmer.FarmerQrDetailsActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CertificateActivity : AppCompatActivity() {

    private var qrBitmap: Bitmap? = null
    private var qrPayload: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_certificate)

        // Apply window insets to root container, preserving original padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootCertificate)) { v, insets ->
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

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarCertificate)
        toolbar.setNavigationOnClickListener { finish() }

        val product = intent.getStringExtra("cert_product") ?: "Product"
        val batch = intent.getStringExtra("cert_batch") ?: "N/A"
        val farmer = intent.getStringExtra("cert_farmer")
            ?.takeIf { it.isNotBlank() }
            ?: Session.name(this)?.takeIf { it.isNotBlank() }
            ?: "N/A"
        val harvest = intent.getStringExtra("cert_harvest") ?: "--"
        val qty = intent.getStringExtra("cert_qty") ?: "--"
        val productType = intent.getStringExtra("cert_type") ?: "Organic"

        val txRaw = intent.getStringExtra("cert_tx") ?: ""
        val blockRaw = intent.getStringExtra("cert_block") ?: ""
        val blockNoRaw = intent.getStringExtra("cert_block_no") ?: ""
        val chain = intent.getStringExtra("cert_chain") ?: "Ethereum"
        val network = intent.getStringExtra("cert_network") ?: "Mainnet"
        val timeRaw = intent.getStringExtra("cert_time") ?: ""

        val certId = intent.getStringExtra("cert_id") ?: "CERT-$batch"
        val issued = intent.getStringExtra("cert_issued") ?: ""

        val batchId = intent.getIntExtra("batch_id", -1)
        val batchCode = intent.getStringExtra("batch_code")

        val txText = if (txRaw.isBlank()) "--" else txRaw
        val blockText = if (blockRaw.isBlank()) "--" else blockRaw
        val blockNoText = if (blockNoRaw.isBlank()) "--" else blockNoRaw
        val timeText = if (timeRaw.isBlank()) "--" else timeRaw

        findViewById<TextView>(R.id.tvProduct).text = product
        findViewById<TextView>(R.id.tvBatch).text = batch
        findViewById<TextView>(R.id.tvFarmer).text = farmer
        findViewById<TextView>(R.id.tvHarvest).text = harvest
        findViewById<TextView>(R.id.tvQty).text = qty
        findViewById<TextView>(R.id.tvType).text = productType

        findViewById<TextView>(R.id.tvTxValue).text = txText
        findViewById<TextView>(R.id.tvBlockValue).text = blockText

        findViewById<TextView>(R.id.tvBlockNoValue).text = blockNoText
        findViewById<TextView>(R.id.tvNetValue).text = chain
        findViewById<TextView>(R.id.tvNetValue2).text = network
        findViewById<TextView>(R.id.tvTimeValue).text = timeText

        findViewById<TextView>(R.id.tvCertId).text = "Certificate ID: $certId"
        if (issued.isNotBlank()) {
            findViewById<TextView>(R.id.tvIssued).text = issued
        }

        findViewById<androidx.appcompat.widget.AppCompatImageButton>(R.id.btnCopyTx)
            .setOnClickListener { copy(findViewById<TextView>(R.id.tvTxValue).text.toString()) }

        findViewById<androidx.appcompat.widget.AppCompatImageButton>(R.id.btnCopyBlock)
            .setOnClickListener { copy(findViewById<TextView>(R.id.tvBlockValue).text.toString()) }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnBackBottom)
            .setOnClickListener { finish() }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnShareCert)
            .setOnClickListener {
                val msg = buildString {
                    appendLine("AgriTrace Certificate")
                    appendLine("Product: $product")
                    appendLine("Batch: $batch")
                    appendLine("Farmer: $farmer")
                    appendLine("Harvest: $harvest")
                    appendLine("Quantity: $qty")
                    appendLine("Type: $productType")

                    if (txRaw.isNotBlank()) {
                        appendLine()
                        appendLine("Blockchain TX:")
                        appendLine(txRaw)
                    }

                    if (blockRaw.isNotBlank()) {
                        appendLine()
                        appendLine("Block Hash:")
                        appendLine(blockRaw)
                    }

                    if (blockNoRaw.isNotBlank()) appendLine("Block Number: $blockNoRaw")
                    if (timeRaw.isNotBlank()) appendLine("Timestamp: $timeRaw")
                    appendLine("Network: $chain $network")
                    appendLine("Certificate ID: $certId")
                }

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "AgriTrace Certificate - $batch")
                    putExtra(Intent.EXTRA_TEXT, msg)
                }

                startActivity(Intent.createChooser(shareIntent, "Share Certificate"))
            }

        // ✅ CHANGE ONLY THIS AREA (QR actions)
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveQr)
            .setOnClickListener { openQrDetails(batchId, batchCode) }   // was download/save

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnShareQr)
            .setOnClickListener { shareQr() } // keep Share exactly

        // QR tap should open details
        findViewById<ImageView>(R.id.ivQrPlaceholder).setOnClickListener {
            openQrDetails(batchId, batchCode)
        }
        findViewById<android.widget.FrameLayout>(R.id.qrWrap).setOnClickListener {
            openQrDetails(batchId, batchCode)
        }

        if (Session.isLoggedIn(this) && (batchId != -1 || !batchCode.isNullOrBlank())) {
            loadCertificateFromApi(batchId, batchCode)
            if (batchId != -1) {
                loadQrFromApi(batchId)
            }
        }
    }
    private fun shareQr() {
        val bitmap = qrBitmap
        if (bitmap == null) {
            Toast.makeText(this, "QR not available yet", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = saveBitmap(bitmap, "agritrace_qr_${System.currentTimeMillis()}.png") ?: run {
            Toast.makeText(this, "Unable to share QR", Toast.LENGTH_SHORT).show()
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Share QR Code"))
    }


    private fun openQrDetails(batchId: Int, batchCode: String?) {
        val i = Intent(this, FarmerQrDetailsActivity::class.java).apply {
            if (batchId != -1) putExtra("batch_id", batchId)
            if (!batchCode.isNullOrBlank()) putExtra("batch_code", batchCode)
            putExtra("product_name", findViewById<TextView>(R.id.tvProduct).text.toString())
        }
        startActivity(i)
    }

    private fun copy(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("value", text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun loadCertificateFromApi(batchId: Int, batchCode: String?) {
        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.farmerApi.getCertificate(
                        if (batchId != -1) batchId else null,
                        batchCode
                    )
                }
                if (!resp.ok) return@launch

                val batch = resp.batch ?: return@launch
                val farmer = resp.farmer
                val chain = resp.blockchain

                findViewById<TextView>(R.id.tvProduct).text = batch.crop_name ?: "Product"
                findViewById<TextView>(R.id.tvBatch).text = batch.batch_code ?: "N/A"
                val fallbackFarmer = Session.name(this@CertificateActivity)?.trim().orEmpty()
                findViewById<TextView>(R.id.tvFarmer).text =
                    farmer?.name?.ifBlank { fallbackFarmer }.takeIf { !it.isNullOrBlank() } ?: "N/A"
                findViewById<TextView>(R.id.tvHarvest).text = batch.harvest_date ?: "--"

                val qty = batch.quantity_kg?.trim().orEmpty()
                findViewById<TextView>(R.id.tvQty).text = if (qty.isNotBlank()) "$qty kg" else "--"
                findViewById<TextView>(R.id.tvType).text = batch.product_type ?: "Organic"

                val txText = chain?.tx_hash?.trim().orEmpty()
                val blockHashText = chain?.block_hash?.trim().orEmpty()
                val blockNoText = chain?.block_number?.toString()?.trim().orEmpty()
                val chainId = chain?.chain_id ?: "31337"
                val network = if (chainId == "31337") "Local" else chainId
                val timeText = chain?.confirmed_at?.trim().orEmpty()

                val certId = batch.batch_code?.takeIf { it.isNotBlank() }?.let { "CERT-$it" } ?: "CERT-N/A"
                val issuedText = timeText.ifBlank { batch.created_at?.trim().orEmpty() }

                findViewById<TextView>(R.id.tvTxValue).text = if (txText.isNotBlank()) txText else "--"
                findViewById<TextView>(R.id.tvBlockValue).text = if (blockHashText.isNotBlank()) blockHashText else "--"
                findViewById<TextView>(R.id.tvBlockNoValue).text = if (blockNoText.isNotBlank()) blockNoText else "--"

                findViewById<TextView>(R.id.tvNetValue).text = "Ethereum"
                findViewById<TextView>(R.id.tvNetValue2).text = network
                findViewById<TextView>(R.id.tvTimeValue).text = if (timeText.isNotBlank()) timeText else "--"

                findViewById<TextView>(R.id.tvCertId).text = "Certificate ID: $certId"
                if (issuedText.isNotBlank()) {
                    findViewById<TextView>(R.id.tvIssued).text = "Issued: $issuedText"
                }
            } catch (_: Exception) {
                // Keep existing intent values if network fails.
            }
        }
    }

    private fun loadQrFromApi(batchId: Int) {
        lifecycleScope.launch {
            try {
                val payload = withContext(Dispatchers.IO) {
                    val getResp = ApiClient.qrApi.getQr(batchId)
                    if (getResp.ok && getResp.qr?.qr_payload?.isNotBlank() == true) {
                        return@withContext getResp.qr!!.qr_payload!!.trim()
                    }

                    // If missing, generate once (same as AddCrop)
                    val genResp = ApiClient.qrApi.generateQr(QrGenerateReq(batch_id = batchId))
                    if (genResp.ok && genResp.qr?.qr_payload?.isNotBlank() == true) {
                        return@withContext genResp.qr!!.qr_payload!!.trim()
                    }

                    ""
                }

                if (payload.isBlank()) return@launch
                qrPayload = payload
                renderQr(payload)

            } catch (_: Exception) {
                // keep placeholder
            }
        }
    }


    private fun renderQr(payload: String) {
        val iv = findViewById<ImageView>(R.id.ivQrPlaceholder)
        val barcodeEncoder = BarcodeEncoder()
        val bitmap: Bitmap = barcodeEncoder.encodeBitmap(
            payload,
            BarcodeFormat.QR_CODE,
            520,
            520
        )
        qrBitmap = bitmap
        iv.setImageBitmap(bitmap)
        iv.setColorFilter(null)
    }

    // Kept for safety if referenced elsewhere, but no longer used from UI.
    private fun saveBitmap(bitmap: Bitmap, displayName: String): Uri? {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AgriTrace")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) return null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            uri
        } catch (_: Exception) {
            null
        }
    }
}
