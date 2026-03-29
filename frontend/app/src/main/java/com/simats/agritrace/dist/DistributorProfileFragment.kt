package com.simats.agritrace.dist

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.simats.agritrace.AppPrefs
import com.simats.agritrace.LoginActivity
import com.simats.agritrace.R
import com.simats.agritrace.Session
import com.simats.agritrace.session.DistributorSessionManager

class DistributorProfileFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_dist__profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tvCompany = view.findViewById<TextView>(R.id.tvCompanyName)
        val tvSubtitle = view.findViewById<TextView>(R.id.tvSubtitle)

        tvCompany.text = Session.name(requireContext()).takeIf { !it.isNullOrBlank() } ?: "FastTrans Logistics"
        tvSubtitle.text = "Authorized Distributor"

        // Remove/hide Vehicle row from UI (ID still exists in XML)
        view.findViewById<View>(R.id.rowVehicle)?.visibility = View.GONE

        bindRow(
            view,
            rowId = R.id.rowCompany,
            icon = R.drawable.ic_user_outline,
            title = "Company Profile"
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

        view.findViewById<View>(R.id.btnSignOut).setOnClickListener {
            hardLogout()
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

    private fun hardLogout() {
        val ctx = requireContext()
        try {
            Session.clear(ctx)
            AppPrefs.clearPendingEmailVerification(ctx)
            try { DistributorSessionManager(ctx).clearSession() } catch (_: Throwable) {}
            try { com.simats.agritrace.session.SessionManager(ctx).logout(userInitiated = true) } catch (_: Throwable) {}

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
            } catch (_: Throwable) {}
        }
        Toast.makeText(ctx, "Coming soon", Toast.LENGTH_SHORT).show()
    }
}
