package com.simats.agritrace.Farmer.customer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.simats.agritrace.AppPrefs
import com.simats.agritrace.LoginActivity
import com.simats.agritrace.R
import com.simats.agritrace.Session
import com.simats.agritrace.databinding.FragmentCustomerProfileBinding

class CustomerProfileFragment : Fragment() {

    private var _binding: FragmentCustomerProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCustomerProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val name = Session.name(requireContext())?.trim().orEmpty()
        binding.tvCustomerName.text = if (name.isNotBlank()) name else "Consumer"
        binding.root.findViewById<TextView>(R.id.tvSubtitle)?.text = "Conscious Consumer"

        bindRow(
            view,
            rowId = R.id.rowProfile,
            icon = R.drawable.ic_user_outline,
            title = "My Profile"
        ) { openFirstAvailable("com.simats.agritrace.edit_profile") }

        bindRow(
            view,
            rowId = R.id.rowNotifications,
            icon = R.drawable.ic_bell_outline,
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
            icon = R.drawable.ic_shield_outline,
            title = "Privacy & Security"
        ) { openFirstAvailable("com.simats.agritrace.PrivacyAndSecurityActivity") }

        bindRow(
            view,
            rowId = R.id.rowHelp,
            icon = R.drawable.ic_help_outline,
            title = "Help & Support"
        ) { openFirstAvailable("com.simats.agritrace.HelpAndSupportActivity") }

        bindRow(
            view,
            rowId = R.id.rowSettings,
            icon = R.drawable.ic_settings_outline,
            title = "Settings"
        ) { openFirstAvailable("com.simats.agritrace.dist.DistributorSettingsActivity", "com.simats.agritrace.farmer_settings") }

        binding.btnSignOut.setOnClickListener { hardLogout() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun hardLogout() {
        val ctx = requireContext()
        try {
            Session.clear(ctx)
            AppPrefs.clearPendingEmailVerification(ctx)
            try { com.simats.agritrace.session.SessionManager(ctx).logout(userInitiated = true) } catch (_: Throwable) {}

            val i = Intent(ctx, LoginActivity::class.java)
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(i)
            requireActivity().finish()
        } catch (_: Throwable) {
            android.widget.Toast.makeText(ctx, "Logout failed. Try again.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindRow(
        root: View,
        rowId: Int,
        icon: Int,
        title: String,
        onClick: () -> Unit
    ) {
        val row = root.findViewById<View>(rowId)
        val iv = row.findViewById<android.widget.ImageView>(R.id.ivIcon)
        val tv = row.findViewById<TextView>(R.id.tvTitle)
        iv.setImageResource(icon)
        tv.text = title
        row.setOnClickListener { onClick() }
    }

    private fun openFirstAvailable(vararg classNames: String) {
        val ctx = requireContext()
        for (name in classNames) {
            try {
                val cls = Class.forName(name)
                startActivity(Intent(ctx, cls))
                return
            } catch (_: Throwable) {}
        }
        android.widget.Toast.makeText(ctx, "Coming soon", android.widget.Toast.LENGTH_SHORT).show()
    }
}
