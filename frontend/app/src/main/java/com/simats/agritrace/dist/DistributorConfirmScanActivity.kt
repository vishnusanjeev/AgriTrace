package com.simats.agritrace.dist

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.simats.agritrace.R
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.journeyapps.barcodescanner.camera.CameraSettings
import com.journeyapps.barcodescanner.camera.CenterCropStrategy

class DistributorConfirmScanActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_QR_PAYLOAD = "qr_payload"
        const val EXTRA_EXPECTED_BATCH_ID = "expected_batch_id"
        const val EXTRA_EXPECTED_BATCH_CODE = "expected_batch_code"
    }

    private lateinit var barcodeScanner: DecoratedBarcodeView
    private lateinit var btnBack: ImageView
    private lateinit var btnTapToScan: View

    private var isScanning = false
    private var lastText: String? = null
    private var lastAtMs: Long = 0L
    private val scanCooldownMs = 2000L

    private val cameraPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startScanning()
            else toast("Camera permission required to scan QR")
        }

    private val callback = object : BarcodeCallback {
        override fun barcodeResult(result: BarcodeResult?) {
            val text = result?.text?.trim().orEmpty()
            if (text.isBlank()) return

            val now = SystemClock.elapsedRealtime()
            if (text == lastText && (now - lastAtMs) < scanCooldownMs) return
            lastText = text
            lastAtMs = now

            stopScanning()

            val data = intent
            data.putExtra(EXTRA_QR_PAYLOAD, text)
            setResult(RESULT_OK, data)
            finish()
        }

        override fun possibleResultPoints(resultPoints: MutableList<com.google.zxing.ResultPoint>?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        // Reuse your existing scan layout
        setContentView(R.layout.fragment_dist_scan)

        barcodeScanner = findViewById(R.id.barcodeScanner)
        btnBack = findViewById(R.id.btnBack)
        btnTapToScan = findViewById(R.id.btnTapToScan)

        btnBack.setOnClickListener { finish() }

        btnTapToScan.setOnClickListener {
            ensureCameraThenScan()
        }

        // Setup scanner (QR only)
        barcodeScanner.setStatusText("")
        barcodeScanner.barcodeView.setPreviewScalingStrategy(CenterCropStrategy())
        barcodeScanner.barcodeView.decoderFactory = DefaultDecoderFactory(
            listOf(BarcodeFormat.QR_CODE)
        )
        barcodeScanner.decodeContinuous(callback)

        val settings = CameraSettings().apply {
            setAutoFocusEnabled(true)
            setMeteringEnabled(true)
            setExposureEnabled(true)
            setBarcodeSceneModeEnabled(true)
            setFocusMode(CameraSettings.FocusMode.CONTINUOUS)
            setContinuousFocusEnabled(true)
        }
        barcodeScanner.barcodeView.cameraSettings = settings

        // Important: start hidden (same UX as fragment)
        barcodeScanner.visibility = View.INVISIBLE
    }

    override fun onResume() {
        super.onResume()
        // keep: DO NOT auto start. user taps.
        // but if you want auto-start, call ensureCameraThenScan() here.
    }

    override fun onPause() {
        super.onPause()
        stopScanning()
    }

    private fun ensureCameraThenScan() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) startScanning()
        else cameraPermLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startScanning() {
        if (isScanning) return
        isScanning = true

        try { window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } catch (_: Throwable) {}

        barcodeScanner.visibility = View.VISIBLE

        barcodeScanner.post {
            runCatching { barcodeScanner.resume() }
                .onFailure {
                    isScanning = false
                    barcodeScanner.visibility = View.INVISIBLE
                    runCatching { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                    toast("Scanner failed to start")
                }
        }
    }

    private fun stopScanning() {
        if (!isScanning) return
        isScanning = false

        runCatching { barcodeScanner.pause() }
        barcodeScanner.visibility = View.INVISIBLE

        runCatching { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
