package com.simats.agritrace.Farmer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.BitmapFactory
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.simats.agritrace.AppPrefs
import com.simats.agritrace.ApiClient
import com.simats.agritrace.HelpAndSupportActivity
import com.simats.agritrace.LoginActivity
import com.simats.agritrace.PrivacyAndSecurityActivity
import com.simats.agritrace.R
import com.simats.agritrace.Session
import com.simats.agritrace.UserDisplay
import com.simats.agritrace.databinding.FragmentProfileBinding
import com.simats.agritrace.farmer_settings
import com.simats.agritrace.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class ProfileFragment : Fragment() {

    private val avatarPaddingDp = 14

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindUserHeader()

        bindRow(
            view,
            rowId = R.id.rowEditProfile,
            icon = R.drawable.ic_edit,
            title = "Edit Profile"
        ) {
            openFirstAvailable("com.simats.agritrace.edit_profile")
        }

        bindRow(
            view,
            rowId = R.id.rowNotifications,
            icon = R.drawable.ic_notifications,
            title = "Notifications"
        ) {
            openFirstAvailable(
                "com.simats.agritrace.NotificationsActivity",
                "com.simats.agritrace.ui.NotificationsActivity"
            )
        }

        bindRow(
            view,
            rowId = R.id.rowPrivacy,
            icon = R.drawable.ic_lock,
            title = "Privacy & Security"
        ) {
            startActivity(Intent(requireContext(), PrivacyAndSecurityActivity::class.java))
        }

        bindRow(
            view,
            rowId = R.id.rowHelp,
            icon = R.drawable.ic_help,
            title = "Help & Support"
        ) {
            startActivity(Intent(requireContext(), HelpAndSupportActivity::class.java))
        }

        bindRow(
            view,
            rowId = R.id.rowSettings,
            icon = R.drawable.ic_menu,
            title = "Settings"
        ) {
            startActivity(Intent(requireContext(), farmer_settings::class.java))
        }

        binding.btnLogout.setOnClickListener { hardLogoutToLogin() }
    }

    private fun bindRow(
        root: View,
        rowId: Int,
        icon: Int,
        title: String,
        onClick: () -> Unit
    ) {
        val row = root.findViewById<View>(rowId)
        val iv = row.findViewById<ImageView>(R.id.ivIcon)
        val tv = row.findViewById<android.widget.TextView>(R.id.tvTitle)
        iv.setImageResource(icon)
        tv.text = title
        row.setOnClickListener { onClick() }
    }

    private fun bindUserHeader() {
        val ctx = requireContext()

        // Name from session (via your helper)
        val name = UserDisplay.displayName(ctx)

        // Always fixed label
        binding.tvFarmName.text = name
        binding.tvFarmStatus.text = "Verified Producer"

        val avatarUrl = AppPrefs.profileImageUrl(ctx)
        if (avatarUrl.isNotBlank()) {
            loadAvatar(binding.ivAvatar, avatarUrl)
        } else {
            showAvatarPlaceholder(binding.ivAvatar)
        }
    }

    private fun hardLogoutToLogin() {
        val ctx = requireContext()

        try {
            Session.clear(ctx)
            AppPrefs.clearPendingEmailVerification(ctx)

            try {
                SessionManager(ctx).logout(userInitiated = true)
            } catch (_: Throwable) {
            }

            val i = Intent(ctx, LoginActivity::class.java)
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(i)
            requireActivity().finish()
        } catch (_: Throwable) {
            Toast.makeText(ctx, "Logout failed. Try again.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFirstAvailable(vararg classNames: String) {
        val ctx = requireContext()
        for (name in classNames) {
            try {
                val cls = Class.forName(name)
                startActivity(Intent(ctx, cls))
                return
            } catch (_: Throwable) {
            }
        }
        Toast.makeText(ctx, "Coming soon", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        bindUserHeader()
        refreshProfile()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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

                if (isAdded && _binding != null) {
                    bindUserHeader()
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
                if (bmp != null && isAdded && _binding != null) {
                    iv.setImageBitmap(bmp)
                    iv.setPadding(0, 0, 0, 0)
                    iv.scaleType = ImageView.ScaleType.CENTER_CROP
                }
            } catch (_: Exception) {
                if (isAdded && _binding != null) showAvatarPlaceholder(iv)
            }
        }
    }

    private fun showAvatarPlaceholder(iv: ImageView) {
        val pad = (avatarPaddingDp * resources.displayMetrics.density).toInt()
        iv.setImageResource(R.drawable.ic_user_outline)
        iv.setPadding(pad, pad, pad, pad)
        iv.scaleType = ImageView.ScaleType.CENTER_INSIDE
    }

    private fun resolveUrl(raw: String): String {
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        val base = com.simats.agritrace.ApiConfig.BASE_URL.trimEnd('/') + "/"
        return base + raw.trimStart('/')
    }
}
