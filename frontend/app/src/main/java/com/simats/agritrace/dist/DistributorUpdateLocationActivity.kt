package com.simats.agritrace.dist

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.simats.agritrace.ApiClient
import com.simats.agritrace.R
import com.simats.agritrace.UpdateLocationReq
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DistributorUpdateLocationActivity : AppCompatActivity() {

    private val requestLocationPerms =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            val ok = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (ok) {
                fetchLocation()
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_distributor_update_location)
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
        val batchId = intent.getIntExtra("batch_id", -1)
        val batchCode = intent.getStringExtra("batch_code").orEmpty()

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener { finish() }

        val tvBatchId = findViewById<android.widget.TextView>(R.id.tvBatchId)
        if (batchCode.isNotBlank()) {
            tvBatchId.text = batchCode
        }

        val etDateTime = findViewById<EditText>(R.id.etDateTime)
        if (etDateTime.text.isNullOrBlank()) {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
            etDateTime.setText(fmt.format(Date()))
        }

        findViewById<android.view.View>(R.id.rowAutoDetect).setOnClickListener {
            requestLocation()
        }

        findViewById<MaterialButton>(R.id.btnSubmit).setOnClickListener {
            val location = findViewById<EditText>(R.id.etLocation).text?.toString()?.trim().orEmpty()
            if (location.isBlank()) {
                Toast.makeText(this, "Enter location", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (batchId <= 0) {
                Toast.makeText(this, "Batch ID missing", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val remarks = findViewById<EditText>(R.id.etRemarks).text?.toString()?.trim().orEmpty()
            val req = UpdateLocationReq(
                batch_id = batchId,
                location_text = location,
                temperature_c = null,
                remarks = remarks.ifBlank { null }
            )

            lifecycleScope.launch {
                try {
                    val resp = withContext(Dispatchers.IO) { ApiClient.distributorApi.updateLocation(req) }
                    if (resp.ok) {
                        Toast.makeText(this@DistributorUpdateLocationActivity, "Location updated", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@DistributorUpdateLocationActivity, resp.error ?: "Update failed", Toast.LENGTH_SHORT).show()
                    }
                } catch (_: Exception) {
                    Toast.makeText(this@DistributorUpdateLocationActivity, "Update failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun requestLocation() {
        if (!isLocationEnabled()) {
            Toast.makeText(this, "Please turn on location services to auto-detect", Toast.LENGTH_SHORT).show()
            return
        }

        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

        if (fine || coarse) {
            fetchLocation()
        } else {
            requestLocationPerms.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun fetchLocation() {
        if (!isLocationEnabled()) {
            Toast.makeText(this, "Please turn on location services to auto-detect", Toast.LENGTH_SHORT).show()
            return
        }

        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val loc = getLastKnown(lm)
        if (loc == null) {
            Toast.makeText(this, "Location not available", Toast.LENGTH_SHORT).show()
            return
        }

        val label = reverseGeocode(loc)
        if (label.isBlank()) {
            Toast.makeText(this, "Location not available", Toast.LENGTH_SHORT).show()
            return
        }
        findViewById<EditText>(R.id.etLocation).setText(label)
    }

    private fun isLocationEnabled(): Boolean {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val gps = runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        val net = runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
        return gps || net
    }

    private fun getLastKnown(lm: LocationManager): Location? {
        val gps = if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } else null
        if (gps != null) return gps

        val net = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } else null
        return net
    }

    private fun reverseGeocode(loc: Location): String {
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val list = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
            val addr = list?.firstOrNull()
            val parts = listOfNotNull(addr?.locality, addr?.adminArea, addr?.countryName)
            if (parts.isNotEmpty()) parts.joinToString(", ")
            else String.format(Locale.US, "%.5f, %.5f", loc.latitude, loc.longitude)
        } catch (_: Exception) {
            String.format(Locale.US, "%.5f, %.5f", loc.latitude, loc.longitude)
        }
    }
}
