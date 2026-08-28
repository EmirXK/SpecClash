package com.example.specclash.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface SpecClashApi {
    @GET("search")
    suspend fun searchPhones(@Query("q") query: String): SearchResponse

    @GET("phone")
    suspend fun getPhoneDetails(@Query("slug") slug: String): PhoneDetailResponse
}
