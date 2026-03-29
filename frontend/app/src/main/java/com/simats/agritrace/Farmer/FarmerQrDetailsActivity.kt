package com.simats.agritrace.Farmer

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.simats.agritrace.ApiClient
import com.simats.agritrace.R
import com.google.android.material.button.MaterialButton
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

class FarmerQrDetailsActivity : AppCompatActivity() {

    private lateinit var ivQr: ImageView
    private lateinit var tvProduct: TextView
    private lateinit var tvBatch: TextView

    private var qrBitmap: Bitmap? = null
    private var lastSavedUri: Uri? = null
    private var resolvedBatchId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_farmer_qr_details)

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val btnBackBottom = findViewById<MaterialButton>(R.id.btnBackBottom)
        val btnSave = findViewById<AppCompatButton>(R.id.btnSave)
        val btnShare = findViewById<MaterialButton>(R.id.btnShare)

        ivQr = findViewById(R.id.ivQr)
        tvProduct = findViewById(R.id.tvProduct)
        tvBatch = findViewById(R.id.tvBatch)

        btnBack.setOnClickListener { finish() }
        btnBackBottom.setOnClickListener { finish() }

        val batchId = intent.getIntExtra("batch_id", -1)
        val batchCode = intent.getStringExtra("batch_code")
            ?: intent.getStringExtra("batchCode")
            ?: intent.getStringExtra("batch_id_str")
            ?: ""

        resolvedBatchId = batchId
        tvProduct.text = "--"
        tvBatch.text = if (batchCode.isNotBlank()) batchCode else "N/A"

        loadProductAndQr(batchId, batchCode)

        btnSave.setOnClickListener {
            val bmp = qrBitmap ?: return@setOnClickListener toast("QR not ready")
            val uri = saveToGallery(bmp, "agritrace_${safeName(batchCode)}.png")
            if (uri != null) {
                lastSavedUri = uri
                toast("Saved")
            } else {
                toast("Save failed")
            }
        }

        btnShare.setOnClickListener {
            val bmp = qrBitmap ?: return@setOnClickListener toast("QR not ready")

            val uri = lastSavedUri ?: saveToGallery(bmp, "agritrace_${safeName(batchCode)}.png")
            if (uri == null) return@setOnClickListener toast("Share failed")

            val share = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, "Share QR"))
        }
    }

    private fun safeName(s: String): String =
        s.trim().replace(" ", "_").replace("/", "_")

    private fun generateQr(text: String, size: Int): Bitmap {
        val writer = QRCodeWriter()
        val matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val fg = 0xFFFFFFFF.toInt()
        val bg = 0xFF0B1220.toInt()
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix[x, y]) fg else bg)
            }
        }
        return bmp
    }

    private fun saveToGallery(bitmap: Bitmap, fileName: String): Uri? {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AgriTrace")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null

            var os: OutputStream? = null
            try {
                os = resolver.openOutputStream(uri)
                if (os == null) return null
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)) return null
                os.flush()
            } finally {
                try { os?.close() } catch (_: Throwable) {}
            }

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (_: Throwable) {
            null
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun loadProductAndQr(batchId: Int, batchCode: String) {
        lifecycleScope.launch {
            try {
                val cert = withContext(Dispatchers.IO) {
                    ApiClient.farmerApi.getCertificate(
                        if (batchId != -1) batchId else null,
                        if (batchCode.isNotBlank()) batchCode else null
                    )
                }

                if (cert.ok && cert.batch != null) {
                    val b = cert.batch
                    val name = b.crop_name?.trim().orEmpty()
                    val code = b.batch_code?.trim().orEmpty()
                    tvProduct.text = if (name.isNotBlank()) name else tvProduct.text
                    tvBatch.text = if (code.isNotBlank()) code else tvBatch.text
                    resolvedBatchId = b.id.toInt()
                }

                val effectiveId = if (resolvedBatchId > 0) resolvedBatchId else batchId
                if (effectiveId <= 0) {
                    toast("QR not available")
                    return@launch
                }

                val qr = withContext(Dispatchers.IO) {
                    ApiClient.qrApi.getQr(effectiveId)
                }

                val payload = qr.qr?.qr_payload?.trim().orEmpty()
                if (payload.isBlank()) {
                    toast("QR not available")
                    return@launch
                }

                qrBitmap = runCatching { generateQr(payload, 720) }.getOrNull()
                qrBitmap?.let { ivQr.setImageBitmap(it) }
            } catch (_: Exception) {
                toast("Failed to load QR details")
            }
        }
    }
}
