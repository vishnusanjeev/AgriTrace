package com.simats.agritrace.dist

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.simats.agritrace.ApiClient
import com.simats.agritrace.ScanVerifyReq
import com.simats.agritrace.R
import com.simats.agritrace.Session
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.journeyapps.barcodescanner.camera.CameraSettings
import com.journeyapps.barcodescanner.camera.CenterCropStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class DistributorScanFragment : Fragment() {

    private lateinit var barcodeScanner: DecoratedBarcodeView
    private lateinit var btnBack: ImageView
    private lateinit var btnTapToScan: View

    private val TAG = "DistScan"

    private var isScanning = false
    private var isHandling = false
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

            if (isHandling) return
            isHandling = true

            Log.d(TAG, "Scanned: $text")
            stopScanning()
            handleScannedBatch(text)
        }

        override fun possibleResultPoints(resultPoints: MutableList<com.google.zxing.ResultPoint>?) {}
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_dist_scan, container, false)

        barcodeScanner = v.findViewById(R.id.barcodeScanner)
        btnBack = v.findViewById(R.id.btnBack)
        btnTapToScan = v.findViewById(R.id.btnTapToScan)

        btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        btnTapToScan.setOnClickListener {
            ensureCameraThenScan()
        }

        // Big-app tuning: scan QR only + faster decode + consistent scaling
        barcodeScanner.setStatusText("")
        barcodeScanner.barcodeView.setPreviewScalingStrategy(CenterCropStrategy())
        barcodeScanner.barcodeView.decoderFactory = DefaultDecoderFactory(
            listOf(BarcodeFormat.QR_CODE)
        )
        barcodeScanner.decodeContinuous(callback)

        // Keep same UX: hidden until user taps
        barcodeScanner.visibility = View.INVISIBLE

        return v
    }

    override fun onResume() {
        super.onResume()
        isHandling = false
        // keep: don't auto start, user taps
    }

    override fun onPause() {
        super.onPause()
        stopScanning()
    }

    private fun ensureCameraThenScan() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) startScanning()
        else cameraPermLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startScanning() {
        if (isScanning) return
        if (Session.userId(requireContext()) <= 0L || Session.token(requireContext()).isNullOrBlank()) {
            toast("Please login again")
            return
        }

        isScanning = true

        // Keep screen on while scanning (helps stability + feels like real apps)
        try {
            requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (_: Throwable) {}

        barcodeScanner.visibility = View.VISIBLE

        val settings = CameraSettings().apply {
            setAutoFocusEnabled(true)
            setMeteringEnabled(true)
            setExposureEnabled(true)
            setBarcodeSceneModeEnabled(true)

            // Main blur fix: continuous focus
            setFocusMode(CameraSettings.FocusMode.CONTINUOUS)
            setContinuousFocusEnabled(true)
        }

        try {
            barcodeScanner.barcodeView.cameraSettings = settings
            barcodeScanner.post {
                try {
                    barcodeScanner.resume()
                } catch (t: Throwable) {
                    Log.e(TAG, "resume failed", t)
                    toast("Scanner failed to start")
                    isScanning = false
                    try { requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } catch (_: Throwable) {}
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "start failed", t)
            toast("Scanner failed to start")
            isScanning = false
            try { requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } catch (_: Throwable) {}
        }
    }

    private fun stopScanning() {
        if (!isScanning) return
        isScanning = false
        try {
            barcodeScanner.pause()
        } catch (_: Throwable) {}

        // Keep same UI behavior as your version
        barcodeScanner.visibility = View.INVISIBLE

        try {
            requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (_: Throwable) {}
    }

    private fun handleScannedBatch(batchId: String) {
        val payload = batchId.trim()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.distributorApi.scanVerify(ScanVerifyReq(qr_payload = payload))
                }
                if (!resp.ok) {
                    toast(resp.error ?: "Verification failed")
                    isHandling = false
                    startScanning()
                    return@launch
                }

                val status = resp.verified?.status?.uppercase(Locale.US).orEmpty()
                if (status != "BLOCKCHAIN_VERIFIED") {
                    val msg = when (status) {
                        "DATA_TAMPERED" -> "QR data tampered"
                        "CHAIN_NOT_FOUND" -> "Batch not found on blockchain"
                        "CHAIN_UNAVAILABLE" -> "Blockchain unavailable"
                        else -> "Verification failed"
                    }
                    toast(msg)
                    isHandling = false
                    startScanning()
                    return@launch
                }

                val b = resp.batch
                val batchId = (b?.id ?: 0L).toInt()
                val batchCode = b?.batch_code.orEmpty()
                openDetails(batchId, batchCode, payload)
            } catch (_: Exception) {
                toast("Verification failed")
                isHandling = false
                startScanning()
            }
        }
    }

    private fun openDetails(batchId: Int, batchCode: String, qrPayload: String) {
        val ctx = requireContext()
        val candidates = arrayOf(
            "com.simats.agritrace.dist.DistributorBatchDetailsActivity",
            "com.simats.agritrace.DistributorBatchDetailsActivity",
            "com.simats.agritrace.dist.BatchDetailsActivity",
            "com.simats.agritrace.BatchDetailsActivity"
        )

        for (name in candidates) {
            try {
                val cls = Class.forName(name)
                val i = Intent(ctx, cls).apply {
                    if (batchId > 0) {
                        putExtra("batch_id", batchId)
                        putExtra("batchId", batchId)
                    }
                    if (batchCode.isNotBlank()) {
                        putExtra("batch_code", batchCode)
                        putExtra("batchCode", batchCode)
                    }
                    putExtra("qr_payload", qrPayload)
                }
                startActivity(i)
                isHandling = false
                return
            } catch (_: Throwable) {}
        }

        toast("Batch verified")
        isHandling = false
        startScanning()
    }

    private fun parseQrPayload(payload: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        val trimmed = payload.trim()

        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val obj = org.json.JSONObject(trimmed)
                if (obj.has("batch_id")) out["batch_id"] = obj.get("batch_id").toString()
                if (obj.has("batch_code")) out["batch_code"] = obj.get("batch_code").toString()
                return out
            } catch (_: Exception) {}
        }

        val parts = payload.split("|")
        for (p in parts) {
            val idx = p.indexOf("=")
            if (idx <= 0) continue
            val k = p.substring(0, idx).trim()
            val v = p.substring(idx + 1).trim()
            if (k.isNotEmpty() && v.isNotEmpty()) out[k] = v
        }
        return out
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}
