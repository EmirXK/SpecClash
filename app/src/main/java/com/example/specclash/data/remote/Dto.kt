package com.example.specclash.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    val status: String,
    val count: Int = 0,
    val data: List<SearchDeviceItem> = emptyList(),
)

@Serializable
data class SearchDeviceItem(
    val name: String,
    val slug: String,
    val thumbnail: String,
    val description: String? = null,
)

@Serializable
data class PhoneDetailResponse(
    val status: String,
    val data: PhoneDetailData,
)

@Serializable
data class PhoneDetailData(
    val name: String,
    val image: String,
    val quickSpec: List<String> = emptyList(),
    val specs: Map<String, Map<String, String>> = emptyMap(),
)
