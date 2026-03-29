package com.simats.agritrace

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class qr_code : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        window.statusBarColor = android.graphics.Color.WHITE
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_qr_code)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val backRow = findViewById<LinearLayout>(R.id.backRow)
        backRow.setOnClickListener {
            // ✅ guaranteed to go back (at least one screen)
            finish()
        }

        val batchId = intent.getIntExtra("batch_id", -1)
        val batchCode = intent.getStringExtra("batch_code") ?: ""
        val payloadExtra = intent.getStringExtra("qr_payload")?.trim().orEmpty()

        // Optional UI line if you want: "Batch #..."
        runCatching {
            findViewById<TextView>(R.id.subtitleInside).text =
                if (batchCode.isNotBlank()) "Batch #$batchCode" else "Batch"
        }

        if (payloadExtra.isNotBlank()) {
            // ✅ use canonical payload from previous screen
            renderQr(payloadExtra)
        } else if (batchId != -1 && Session.isLoggedIn(this)) {
            // ✅ always fetch/generate canonical payload from server
            loadOrCreateQrFromServer(batchId)
        } else {
            Toast.makeText(this, "QR not available yet", Toast.LENGTH_SHORT).show()
        }

        val mainView = findViewById<android.view.View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
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
    }

    private fun loadOrCreateQrFromServer(batchId: Int) {
        lifecycleScope.launch {
            try {
                val payload = withContext(Dispatchers.IO) {
                    val getResp = ApiClient.qrApi.getQr(batchId)
                    if (getResp.ok && getResp.qr?.qr_payload?.isNotBlank() == true) {
                        return@withContext getResp.qr!!.qr_payload!!.trim()
                    }

                    val genResp = ApiClient.qrApi.generateQr(QrGenerateReq(batch_id = batchId))
                    if (genResp.ok && genResp.qr?.qr_payload?.isNotBlank() == true) {
                        return@withContext genResp.qr!!.qr_payload!!.trim()
                    }

                    ""
                }

                if (payload.isBlank()) {
                    Toast.makeText(this@qr_code, "QR not available yet", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                renderQr(payload)

            } catch (_: Exception) {
                Toast.makeText(this@qr_code, "Unable to load QR. Try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderQr(payload: String) {
        val qrImage = findViewById<ImageView>(R.id.imgQr)
        val barcodeEncoder = BarcodeEncoder()
        val bitmap: Bitmap = barcodeEncoder.encodeBitmap(
            payload,
            BarcodeFormat.QR_CODE,
            600,
            600
        )
        qrImage.setImageBitmap(bitmap)
    }
}
