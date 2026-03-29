package com.simats.agritrace.Farmer.customer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.simats.agritrace.ApiClient
import com.google.gson.Gson
import com.simats.agritrace.CustomerBatchVerification
import com.simats.agritrace.R
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

class CustomerScanFragment : Fragment() {

    private lateinit var barcodeScanner: DecoratedBarcodeView
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

            stopScanning()
            handleScannedPayload(text)
        }

        override fun possibleResultPoints(resultPoints: MutableList<com.google.zxing.ResultPoint>?) {}
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_scanfragment, container, false)

        val scannerFrame = view.findViewById<FrameLayout>(R.id.scannerFrame)
        barcodeScanner = DecoratedBarcodeView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setStatusText("")
            barcodeView.setPreviewScalingStrategy(CenterCropStrategy())
            barcodeView.decoderFactory = DefaultDecoderFactory(
                listOf(BarcodeFormat.QR_CODE)
            )
            decodeContinuous(callback)
            visibility = View.INVISIBLE
        }
        scannerFrame.addView(barcodeScanner, 0)

        val btnScan = view.findViewById<Button>(R.id.btnSca)
        btnScan.setOnClickListener { ensureCameraThenScan() }

        view.findViewById<ImageView>(R.id.btnBack)?.visibility = View.GONE

        return view
    }

    override fun onResume() {
        super.onResume()
        isHandling = false
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
        isScanning = true

        try {
            requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (_: Throwable) {}

        barcodeScanner.visibility = View.VISIBLE

        val settings = CameraSettings().apply {
            setAutoFocusEnabled(true)
            setMeteringEnabled(true)
            setExposureEnabled(true)
            setBarcodeSceneModeEnabled(true)
            setFocusMode(CameraSettings.FocusMode.CONTINUOUS)
            setContinuousFocusEnabled(true)
        }

        try {
            barcodeScanner.barcodeView.cameraSettings = settings
            barcodeScanner.post {
                try {
                    barcodeScanner.resume()
                } catch (_: Throwable) {
                    isScanning = false
                    toast("Scanner failed to start")
                }
            }
        } catch (_: Throwable) {
            isScanning = false
            toast("Scanner failed to start")
        }
    }

    private fun stopScanning() {
        if (!isScanning) return
        isScanning = false

        try {
            barcodeScanner.pause()
        } catch (_: Throwable) {}

        barcodeScanner.visibility = View.INVISIBLE

        try {
            requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (_: Throwable) {}
    }

    private fun handleScannedPayload(payload: String) {
        val parsed = parseQrPayload(payload)
        val batchId = parsed["batch_id"]?.toIntOrNull() ?: 0
        val batchCode = parsed["batch_code"].orEmpty()

        if (batchId <= 0 && batchCode.isBlank()) {
            toast("Invalid QR code")
            isHandling = false
            startScanning()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { ApiClient.consumerApi.verifyQr(payload) }
                val data = resp.data
                if (!resp.ok || data == null) {
                    toast(resp.error ?: "Verification failed")
                    isHandling = false
                    startScanning()
                    return@launch
                }

                if (!data.verified) {
                    toast(data.verification_reason ?: "Verification mismatch")
                }

                val intent = Intent(requireContext(), CustomerBatchVerification::class.java).apply {
                    if (data.batch_id > 0) putExtra("batch_id", data.batch_id)
                    if (!data.batch_code.isNullOrBlank()) putExtra("batch_code", data.batch_code)
                    putExtra("verify_json", Gson().toJson(data))
                    putExtra("qr_payload", payload)
                }
                startActivity(intent)
                isHandling = false
            } catch (_: Exception) {
                toast("Verification failed")
                isHandling = false
                startScanning()
            }
        }
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
