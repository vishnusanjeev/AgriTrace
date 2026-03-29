package com.simats.agritrace.model

data class BatchTransfer(
    val id: String,
    val batchId: String,
    val fromId: String,
    val toId: String,
    val status: String,
    val createdAt: String,
    val distributorName: String?, // optional
    val batch: Batch? // nested batch info
)
