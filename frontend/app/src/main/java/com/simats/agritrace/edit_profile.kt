package com.simats.agritrace

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.net.URL
import java.util.Locale

class edit_profile : AppCompatActivity() {

    private var selectedAvatarUri: Uri? = null
    private var locationPlaceholder = false

    private lateinit var ivAvatarPhoto: ImageView
    private lateinit var ivAvatarPlaceholder: ImageView

    private lateinit var ivCamera: ImageView
    private lateinit var btnSetLocation: MaterialButton
    private lateinit var btnSave: AppCompatButton

    private lateinit var roleTheme: RoleTheme

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedAvatarUri = uri
            showPhoto(uri = uri)
        }
    }

    private val requestLocationPerms =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            val ok = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (ok) {
                fetchAndSaveLocation()
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        window.statusBarColor = android.graphics.Color.WHITE
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_edit_profile)

        roleTheme = RoleTheme.fromRole(Session.role(this))

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

        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }

        ivAvatarPhoto = findViewById(R.id.ivAvatar)
        ivAvatarPlaceholder = findViewById(R.id.ivAvatarPlaceholder)

        ivCamera = findViewById(R.id.ivCamera)
        val etName = findViewById<AppCompatEditText>(R.id.etFullName)
        val etEmail = findViewById<AppCompatEditText>(R.id.etEmail)
        val etPhone = findViewById<AppCompatEditText>(R.id.etPhone)
        val etLocation = findViewById<AppCompatEditText>(R.id.etLocation)
        btnSetLocation = findViewById(R.id.btnSetLocation)
        btnSave = findViewById(R.id.btnSave)

        applyRoleColors()

        val name = AppPrefs.profileName(this)
        val email = AppPrefs.profileEmail(this)
        val phone = AppPrefs.profilePhone(this)
        val location = AppPrefs.profileLocation(this)
        val avatarUrl = AppPrefs.profileImageUrl(this)

        if (name.isNotBlank()) etName.setText(name)
        if (email.isNotBlank()) etEmail.setText(email)
        if (phone.isNotBlank()) etPhone.setText(phone)
        applyLocation(etLocation, location)

        if (avatarUrl.isNotBlank()) {
            loadAvatarInto(avatarUrl)
        } else {
            showPlaceholder()
        }

        ivCamera.setOnClickListener { pickImage.launch("image/*") }
        btnSetLocation.setOnClickListener { requestLocation() }

        etLocation.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && locationPlaceholder) {
                etLocation.setText("")
                locationPlaceholder = false
            }
        }

        if (Session.isLoggedIn(this)) {
            lifecycleScope.launch {
                try {
                    val resp = withContext(Dispatchers.IO) { ApiClient.userApi.me() }
                    if (resp.ok && resp.user != null) {
                        val u = resp.user
                        etName.setText(u.full_name ?: "")
                        etEmail.setText(u.email ?: "")
                        etPhone.setText(u.phone_e164 ?: "")
                        applyLocation(etLocation, u.location ?: "")
                        val url = u.profile_image_url ?: ""
                        if (url.isNotBlank()) loadAvatarInto(url) else showPlaceholder()
                    }
                } catch (_: Exception) { }
            }
        }

        btnSave.setOnClickListener {
            val fullName = etName.text?.toString().orEmpty().trim()
            val emailText = etEmail.text?.toString().orEmpty().trim()
            val phoneText = etPhone.text?.toString().orEmpty().trim()
            val rawLocation = etLocation.text?.toString().orEmpty().trim()
            val locationText = if (locationPlaceholder || rawLocation.equals("Not set", true)) "" else rawLocation

            if (fullName.isBlank()) {
                etName.error = "Required"
                return@setOnClickListener
            }

            if (!Session.isLoggedIn(this)) {
                Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val resp = withContext(Dispatchers.IO) {
                        ApiClient.userApi.updateProfile(
                            ProfileUpdateReq(
                                full_name = fullName,
                                email = emailText.ifBlank { null },
                                phone_e164 = phoneText.ifBlank { null },
                                location = locationText.ifBlank { null }
                            )
                        )
                    }

                    if (resp.ok && resp.user != null) {
                        val u = resp.user
                        AppPrefs.setProfileName(this@edit_profile, u.full_name ?: "")
                        AppPrefs.setProfileEmail(this@edit_profile, u.email ?: "")
                        AppPrefs.setProfilePhone(this@edit_profile, u.phone_e164 ?: "")
                        AppPrefs.setProfileLocation(this@edit_profile, u.location ?: "")
                        AppPrefs.setProfileImageUrl(this@edit_profile, u.profile_image_url ?: "")
                        Session.updateProfile(this@edit_profile, u.full_name, u.email, u.profile_image_url)

                        val picked = selectedAvatarUri
                        if (picked != null) {
                            uploadAvatar(picked)
                        } else {
                            Toast.makeText(this@edit_profile, "Profile updated", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    } else {
                        Toast.makeText(this@edit_profile, resp.error ?: "Update failed", Toast.LENGTH_SHORT).show()
                    }
                } catch (_: Exception) {
                    Toast.makeText(this@edit_profile, "Update failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun applyRoleColors() {
        val accent = roleTheme.accent
        val accentSoft = roleTheme.accentSoft

        ivCamera.background?.mutate()?.let { bg ->
            val wrapped = DrawableCompat.wrap(bg)
            DrawableCompat.setTint(wrapped, accent)
            ivCamera.background = wrapped
        }

        btnSave.background?.mutate()?.let { bg ->
            val wrapped = DrawableCompat.wrap(bg)
            DrawableCompat.setTint(wrapped, accent)
            btnSave.background = wrapped
        }

        btnSetLocation.setTextColor(accent)
        btnSetLocation.backgroundTintList = ColorStateList.valueOf(accentSoft)
        btnSetLocation.strokeColor = ColorStateList.valueOf(accent)
    }

    private fun showPlaceholder() {
        ivAvatarPhoto.visibility = View.GONE
        ivAvatarPlaceholder.visibility = View.VISIBLE
        ivAvatarPhoto.setImageDrawable(null)
    }

    private fun showPhoto(uri: Uri? = null, bitmap: Bitmap? = null) {
        ivAvatarPlaceholder.visibility = View.GONE
        ivAvatarPhoto.visibility = View.VISIBLE
        ivAvatarPhoto.setPadding(0, 0, 0, 0)
        ivAvatarPhoto.scaleType = ImageView.ScaleType.CENTER_CROP

        when {
            uri != null -> ivAvatarPhoto.setImageURI(uri)
            bitmap != null -> ivAvatarPhoto.setImageBitmap(bitmap)
        }
    }

    private fun uploadAvatar(uri: Uri) {
        lifecycleScope.launch {
            try {
                val part = withContext(Dispatchers.IO) { buildAvatarPart(uri) }
                if (part == null) {
                    Toast.makeText(this@edit_profile, "Avatar upload failed", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val resp = withContext(Dispatchers.IO) { ApiClient.userApi.uploadAvatar(part) }
                if (resp.ok && resp.user != null) {
                    val u = resp.user
                    AppPrefs.setProfileImageUrl(this@edit_profile, u.profile_image_url ?: "")
                    Session.updateProfile(this@edit_profile, u.full_name, u.email, u.profile_image_url)
                    Toast.makeText(this@edit_profile, "Profile updated", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@edit_profile, resp.error ?: "Avatar upload failed", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(this@edit_profile, "Avatar upload failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun buildAvatarPart(uri: Uri): MultipartBody.Part? {
        return try {
            val input = contentResolver.openInputStream(uri) ?: return null
            val file = File(cacheDir, "avatar_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { out -> input.copyTo(out) }
            val reqBody = file.asRequestBody("image/*".toMediaTypeOrNull())
            MultipartBody.Part.createFormData("avatar", file.name, reqBody)
        } catch (_: Exception) {
            null
        }
    }

    private fun loadAvatarInto(url: String) {
        val resolved = resolveUrl(url)
        lifecycleScope.launch {
            try {
                val bmp = withContext(Dispatchers.IO) {
                    URL(resolved).openStream().use { BitmapFactory.decodeStream(it) }
                }
                if (bmp != null) showPhoto(bitmap = bmp) else showPlaceholder()
            } catch (_: Exception) {
                showPlaceholder()
            }
        }
    }

    private fun resolveUrl(raw: String): String {
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        val base = ApiConfig.BASE_URL.trimEnd('/') + "/"
        return base + raw.trimStart('/')
    }

    private fun applyLocation(etLocation: AppCompatEditText, value: String) {
        if (value.isBlank()) {
            etLocation.setText("Not set")
            locationPlaceholder = true
        } else {
            etLocation.setText(value)
            locationPlaceholder = false
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
            fetchAndSaveLocation()
        } else {
            requestLocationPerms.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun fetchAndSaveLocation() {
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

        lifecycleScope.launch {
            val label = withContext(Dispatchers.IO) { reverseGeocode(loc) }
            if (label.isBlank()) {
                Toast.makeText(this@edit_profile, "Location not available", Toast.LENGTH_SHORT).show()
                return@launch
            }
            findViewById<AppCompatEditText>(R.id.etLocation).setText(label)
            locationPlaceholder = false
            saveLocation(label)
        }
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

    private fun saveLocation(location: String) {
        val fullName = findViewById<AppCompatEditText>(R.id.etFullName).text?.toString().orEmpty().trim()
        val emailText = findViewById<AppCompatEditText>(R.id.etEmail).text?.toString().orEmpty().trim()
        val phoneText = findViewById<AppCompatEditText>(R.id.etPhone).text?.toString().orEmpty().trim()

        if (fullName.isBlank()) {
            Toast.makeText(this, "Please enter name first", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.userApi.updateProfile(
                        ProfileUpdateReq(
                            full_name = fullName,
                            email = emailText.ifBlank { null },
                            phone_e164 = phoneText.ifBlank { null },
                            location = location
                        )
                    )
                }

                if (resp.ok && resp.user != null) {
                    val u = resp.user
                    AppPrefs.setProfileLocation(this@edit_profile, u.location ?: "")
                    Toast.makeText(this@edit_profile, "Location updated", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@edit_profile, resp.error ?: "Location update failed", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(this@edit_profile, "Location update failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private data class RoleTheme(val accent: Int, val accentSoft: Int) {
        companion object {
            fun fromRole(roleRaw: String?): RoleTheme {
                val role = roleRaw?.trim()?.lowercase().orEmpty()
                return when {
                    role.contains("farmer") -> RoleTheme(
                        accent = 0xFF16A34A.toInt(),
                        accentSoft = 0xFFECFDF3.toInt()
                    )
                    role.contains("distributor") -> RoleTheme(
                        accent = 0xFF2563EB.toInt(),
                        accentSoft = 0xFFEFF6FF.toInt()
                    )
                    role.contains("retailer") -> RoleTheme(
                        accent = 0xFFF97316.toInt(),
                        accentSoft = 0xFFFFF7ED.toInt()
                    )
                    role.contains("consumer") || role.contains("customer") -> RoleTheme(
                        accent = 0xFF7C3AED.toInt(),
                        accentSoft = 0xFFF5F3FF.toInt()
                    )
                    else -> RoleTheme(
                        accent = 0xFF16A34A.toInt(),
                        accentSoft = 0xFFECFDF3.toInt()
                    )
                }
            }
        }
    }
}
