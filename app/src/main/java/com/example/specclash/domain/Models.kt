package com.example.specclash.domain

/**
 * Lightweight search-result row used by the device picker UI.
 */
data class SearchDevice(
    val name: String,
    val slug: String,
    val thumbnail: String,
    val description: String? = null,
)

/**
 * Full spec sheet for a single device.
 */
data class PhoneSpec(
    val slug: String,
    val name: String,
    val image: String,
    val specs: Map<String, Map<String, String>>,
)
