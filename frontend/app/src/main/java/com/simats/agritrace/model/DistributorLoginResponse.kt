package com.simats.agritrace.model

data class DistributorLoginResponse(
    val status: Boolean,
    val message: String,
    val data: DistributorData?
)
