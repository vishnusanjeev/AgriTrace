package com.simats.agritrace

import android.content.Context

object UserDisplay {

    fun displayName(ctx: Context): String {
        val name = Session.name(ctx)?.trim().orEmpty()
        if (name.isNotBlank()) return name

        val email = Session.email(ctx)?.trim().orEmpty()
        if (email.isNotBlank()) return email.substringBefore("@")

        return "User"
    }

    fun displayRoleLabel(ctx: Context): String {
        return when ((Session.role(ctx) ?: "").trim().uppercase()) {
            "FARMER" -> "Farmer"
            "DISTRIBUTOR" -> "Distributor"
            "RETAILER" -> "Retailer"
            "CONSUMER", "CUSTOMER" -> "Consumer"
            else -> "User"
        }
    }

    fun displayStatusLine(ctx: Context): String {
        return "✓ ${displayRoleLabel(ctx)}"
    }
}
