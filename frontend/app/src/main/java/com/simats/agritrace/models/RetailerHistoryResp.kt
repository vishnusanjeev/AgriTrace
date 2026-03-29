package com.simats.agritrace.models

import com.google.gson.annotations.SerializedName

data class RetailerHistoryResp(
    @SerializedName("ok") val ok: Boolean? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("items") val items: List<HistoryItem>? = null
) {
    data class HistoryItem(
        @SerializedName("source") val source: String? = null,

        @SerializedName("time") val time: String? = null,

        @SerializedName("event_type") val eventType: String? = null,

        @SerializedName("result") val result: String? = null,

        @SerializedName("actor_role") val actorRole: String? = null,

        @SerializedName("meta") val meta: Meta? = null,

        // chain-only fields (present for BatchCreated entry)
        @SerializedName("status") val chainStatus: String? = null,
        @SerializedName("chain_id") val chainId: String? = null,
        @SerializedName("tx_hash") val txHash: String? = null,
        @SerializedName("block_number") val blockNumber: Long? = null,
        @SerializedName("confirmed_at") val confirmedAt: String? = null
    )

    data class Meta(
        @SerializedName("location_text") val locationText: String? = null,
        @SerializedName("temperature_c") val temperatureC: Double? = null,
        @SerializedName("remarks") val remarks: String? = null
    )
}
