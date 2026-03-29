package com.simats.agritrace.model

data class DistributorData(
    val distributor_id: Int,
    val full_name: String,
    val company_name: String?,
    val email: String?,
    val phone: String?,
    val city: String?,
    val state: String?,
    val country: String?,
    val distributor_type: String,
    val created_at: String
)
