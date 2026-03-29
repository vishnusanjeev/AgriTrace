package com.simats.agritrace.Farmer

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.simats.agritrace.ApiClient
import com.simats.agritrace.AppPrefs
import com.simats.agritrace.HomeResp
import com.simats.agritrace.LoginActivity
import com.simats.agritrace.R
import com.simats.agritrace.RecentBatchDto
import com.simats.agritrace.Session
import com.simats.agritrace.FarmerBatchDetailsActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.net.URL
import java.util.Locale

class HomeFragment : Fragment() {

    private val avatarPaddingDp = 15

    private lateinit var recentBatchesContainer: LinearLayout
    private lateinit var tvFarmNameHeader: TextView
    private lateinit var tvActiveValue: TextView
    private lateinit var tvQrValue: TextView
    private lateinit var tvActiveDelta: TextView
    private lateinit var tvQrDelta: TextView
    private lateinit var btnViewAll: TextView
    private lateinit var pillAddCrop: View
    private lateinit var pillProducts: View
    private lateinit var ivHeaderIcon: ImageView

    private lateinit var emptyRecentState: View

    private var farmerUserId: Long = 0L
    private var loading: Boolean = false
    private var loadedOnce: Boolean = false
    private var lastLoadedAt: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        farmerUserId = Session.userId(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        tvFarmNameHeader = view.findViewById(R.id.tvFarmNameHeader)
        recentBatchesContainer = view.findViewById(R.id.recentBatchesContainer)
        tvActiveValue = view.findViewById(R.id.tvActiveValue)
        tvQrValue = view.findViewById(R.id.tvQrValue)
        tvActiveDelta = view.findViewById(R.id.tvActiveDelta)
        tvQrDelta = view.findViewById(R.id.tvQrDelta)
        btnViewAll = view.findViewById(R.id.btnViewAll)
        pillAddCrop = view.findViewById(R.id.pillAddCrop)
        pillProducts = view.findViewById(R.id.pillProducts)
        ivHeaderIcon = view.findViewById(R.id.ivHeaderIcon)

        emptyRecentState = view.findViewById(R.id.emptyRecentState)

        bindClicks()
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!Session.isLoggedIn(requireContext()) || farmerUserId <= 0L) {
            routeToLogin("User not logged in")
            return
        }

        bindHeaderName()
        bindAvatar()
        loadHome(force = true)
        refreshProfile()
    }

    override fun onResume() {
        super.onResume()
        if (Session.isLoggedIn(requireContext())) {
            bindHeaderName()
            bindAvatar()
            val now = System.currentTimeMillis()
            if (loadedOnce && now - lastLoadedAt > 5000) {
                loadHome(force = false)
            }
            refreshProfile()
        }
    }

    private fun bindClicks() {
        btnViewAll.setOnClickListener { openProducts() }
        pillProducts.setOnClickListener { openProducts() }
        pillAddCrop.setOnClickListener {
            val bottom = activity?.findViewById<BottomNavigationView>(R.id.farmerBottomNav)
            if (bottom != null) {
                bottom.selectedItemId = R.id.navigation_add
            }
        }
    }

    private fun openProducts() {
        val bottom = activity?.findViewById<BottomNavigationView>(R.id.farmerBottomNav)
        if (bottom != null) {
            bottom.selectedItemId = R.id.navigation_products
        }
    }

    private fun bindHeaderName() {
        val ctx = requireContext()
        val name = Session.name(ctx)?.trim().orEmpty()
        val email = Session.email(ctx)?.trim().orEmpty()

        val display = when {
            name.isNotBlank() -> name
            email.isNotBlank() -> email.substringBefore("@")
            else -> "Welcome"
        }

        tvFarmNameHeader.text = display
    }

    private fun bindAvatar() {
        val ctx = requireContext()
        val avatarUrl = AppPrefs.profileImageUrl(ctx)
        if (avatarUrl.isNotBlank()) {
            loadAvatar(ivHeaderIcon, avatarUrl)
        } else {
            showAvatarPlaceholder(ivHeaderIcon)
        }
    }

    private fun loadHome(force: Boolean) {
        if (farmerUserId <= 0L) {
            routeToLogin("User not logged in")
            return
        }
        if (loading && !force) return
        loading = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resp: HomeResp = withContext(Dispatchers.IO) {
                    ApiClient.farmerApi.getHome()
                }

                if (!isAdded) return@launch

                if (resp.ok) {
                    val stats = resp.stats
                    val active = stats?.active_batches ?: 0
                    val scans = stats?.qr_scans ?: 0

                    tvActiveValue.text = active.toString()
                    tvQrValue.text = formatQrCount(scans)
                    tvActiveDelta.text = if (active > 0) "+$active" else "0"
                    tvQrDelta.text = if (scans > 0) "+${formatQrDelta(scans)}" else "0"

                    renderRecent(resp.recent ?: emptyList())
                    loadedOnce = true
                    lastLoadedAt = System.currentTimeMillis()
                    bindAvatar()
                } else {
                    Toast.makeText(requireContext(), resp.error ?: "Failed to load dashboard", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (!isAdded) return@launch
                Toast.makeText(requireContext(), "Failed to load dashboard", Toast.LENGTH_SHORT).show()
            } finally {
                loading = false
            }
        }
    }

    private fun renderRecent(items: List<RecentBatchDto>) {
        recentBatchesContainer.removeAllViews()

        if (items.isEmpty()) {
            emptyRecentState.visibility = View.VISIBLE
            recentBatchesContainer.visibility = View.GONE
            return
        }

        emptyRecentState.visibility = View.GONE
        recentBatchesContainer.visibility = View.VISIBLE

        val inflater = layoutInflater
        for (item in items) {
            val row = inflater.inflate(R.layout.item_recent_batch_center, recentBatchesContainer, false)
            val tvTitle = row.findViewById<TextView>(R.id.tvTitle)
            val tvSub = row.findViewById<TextView>(R.id.tvSub)
            val tvMeta = row.findViewById<TextView>(R.id.tvMeta)
            val chipStatus = row.findViewById<com.google.android.material.card.MaterialCardView>(R.id.chipStatus)
            val tvStatus = row.findViewById<TextView>(R.id.tvStatus)

            tvTitle.text = item.crop_name ?: "Batch"
            val code = item.batch_code ?: ""
            val qtyRaw = item.quantity_kg ?: ""
            val qtyText = if (qtyRaw.isNotBlank() && !qtyRaw.lowercase(Locale.US).contains("kg")) "$qtyRaw kg" else qtyRaw
            val whenText = timeAgo(item.created_at)
            val subText = listOf(code, whenText).filter { it.isNotBlank() }.joinToString(" | ")
            if (subText.isNotBlank()) {
                tvSub.text = subText
                tvSub.visibility = View.VISIBLE
            } else {
                tvSub.visibility = View.GONE
            }

            val statusInfo = statusStyleFor(item.status)
            tvStatus.text = statusInfo.label
            tvStatus.setTextColor(statusInfo.textColor)
            chipStatus.setCardBackgroundColor(statusInfo.bgColor)
            chipStatus.strokeColor = statusInfo.strokeColor

            val metaParts = mutableListOf<String>()
            if (qtyText.isNotBlank()) metaParts.add("Qty $qtyText")
            if (!item.harvest_date.isNullOrBlank()) metaParts.add("Harvest ${item.harvest_date}")
            val metaText = metaParts.joinToString(" | ")
            if (metaText.isNotBlank()) {
                tvMeta.text = metaText
                tvMeta.visibility = View.VISIBLE
            } else {
                tvMeta.visibility = View.GONE
            }

            row.setOnClickListener {
                val intent = Intent(requireContext(), FarmerBatchDetailsActivity::class.java).apply {
                    putExtra("crop_name", item.crop_name ?: "")
                    putExtra("batch_id", item.id.toInt())
                    putExtra("quantity", qtyText)
                    putExtra("harvest_date", item.harvest_date ?: "")
                }
                startActivity(intent)
            }

            recentBatchesContainer.addView(row)
        }
    }

    private fun formatQrCount(v: Int): String {
        return when {
            v >= 1000 -> String.format(Locale.US, "%.1fk", v / 1000.0)
            else -> v.toString()
        }
    }

    private fun formatQrDelta(v: Int): String {
        return when {
            v >= 1000 -> String.format(Locale.US, "%.1fk", v / 1000.0)
            else -> v.toString()
        }
    }

    private fun timeAgo(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val dt = parser.parse(raw) ?: return ""
            val diffMs = System.currentTimeMillis() - dt.time
            val mins = diffMs / 60000
            when {
                mins < 60 -> "$mins mins ago"
                mins < 1440 -> "${mins / 60} hours ago"
                else -> "${mins / 1440} days ago"
            }
        } catch (_: Exception) {
            ""
        }
    }

    private data class StatusStyle(
        val label: String,
        val textColor: Int,
        val bgColor: Int,
        val strokeColor: Int
    )

    private fun statusStyleFor(status: String?): StatusStyle {
        val normalized = status?.trim()?.uppercase(Locale.US) ?: ""
        return when (normalized) {
            "PENDING" -> StatusStyle(
                label = "Pending",
                textColor = Color.parseColor("#B45309"),
                bgColor = Color.parseColor("#FEF3C7"),
                strokeColor = Color.parseColor("#F59E0B")
            )
            "SOLD" -> StatusStyle(
                label = "Sold",
                textColor = Color.parseColor("#475569"),
                bgColor = Color.parseColor("#E2E8F0"),
                strokeColor = Color.parseColor("#CBD5E1")
            )
            "IN_TRANSIT" -> StatusStyle(
                label = "In Transit",
                textColor = Color.parseColor("#1D4ED8"),
                bgColor = Color.parseColor("#DBEAFE"),
                strokeColor = Color.parseColor("#93C5FD")
            )
            "PICKED_UP" -> StatusStyle(
                label = "Picked Up",
                textColor = Color.parseColor("#0F172A"),
                bgColor = Color.parseColor("#E2E8F0"),
                strokeColor = Color.parseColor("#94A3B8")
            )
            "ASSIGNED" -> StatusStyle(
                label = "Assigned",
                textColor = Color.parseColor("#7C3AED"),
                bgColor = Color.parseColor("#F3E8FF"),
                strokeColor = Color.parseColor("#C4B5FD")
            )
            else -> {
                val label = if (normalized.isBlank()) "Active" else formatStatusLabel(normalized)
                StatusStyle(
                    label = label,
                    textColor = Color.parseColor("#0B8F63"),
                    bgColor = Color.parseColor("#E7F6EF"),
                    strokeColor = Color.parseColor("#86E0B8")
                )
            }
        }
    }

    private fun formatStatusLabel(status: String): String {
        return status.lowercase(Locale.US)
            .replace('_', ' ')
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { ch -> ch.uppercase(Locale.US) } }
    }

    private fun routeToLogin(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        Session.clear(requireContext())

        val i = Intent(requireContext(), LoginActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(i)
        requireActivity().finish()
    }

    private fun refreshProfile() {
        val ctx = requireContext()
        if (!Session.isLoggedIn(ctx)) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { ApiClient.userApi.me() }
                if (!resp.ok || resp.user == null) return@launch
                val u = resp.user
                AppPrefs.setProfileName(ctx, u.full_name ?: "")
                AppPrefs.setProfileEmail(ctx, u.email ?: "")
                AppPrefs.setProfilePhone(ctx, u.phone_e164 ?: "")
                AppPrefs.setProfileLocation(ctx, u.location ?: "")
                AppPrefs.setProfileImageUrl(ctx, u.profile_image_url ?: "")
                Session.updateProfile(ctx, u.full_name, u.email, u.profile_image_url)
                if (isAdded) {
                    bindHeaderName()
                    bindAvatar()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun loadAvatar(iv: ImageView, url: String) {
        val resolved = resolveUrl(url)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val bmp = withContext(Dispatchers.IO) {
                    URL(resolved).openStream().use { BitmapFactory.decodeStream(it) }
                }
                if (bmp != null && isAdded) {
                    iv.setImageBitmap(bmp)
                    iv.setPadding(0, 0, 0, 0)
                    iv.scaleType = ImageView.ScaleType.CENTER_CROP
                }
            } catch (_: Exception) {
                if (isAdded) showAvatarPlaceholder(iv)
            }
        }
    }

    private fun showAvatarPlaceholder(iv: ImageView) {
        val pad = (avatarPaddingDp * resources.displayMetrics.density).toInt()
        iv.setImageResource(R.drawable.ic_user)
        iv.setPadding(pad, pad, pad, pad)
        iv.scaleType = ImageView.ScaleType.CENTER_INSIDE
    }

    private fun resolveUrl(raw: String): String {
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        val base = com.simats.agritrace.ApiConfig.BASE_URL.trimEnd('/') + "/"
        return base + raw.trimStart('/')
    }
}
