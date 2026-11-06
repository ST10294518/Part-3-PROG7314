package com.winx.app.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface WinxApiService {

    // GET all restaurant/travel entries
    @GET("api/Entries")
    suspend fun getEntries(@Header("Authorization") authorization: String): List<TravelEntryDto>

    // GET one entry by ID
    @GET("api/Entries/{id}")
    suspend fun getEntry(
        @Header("Authorization") authorization: String,
        @Path("id") id: Int
    ): TravelEntryDto

    // CREATE a new entry
    @POST("api/Entries")
    suspend fun createEntry(
        @Header("Authorization") authorization: String,
        @Body entry: TravelEntryDto
    ): Response<TravelEntryDto>

    // UPDATE an existing entry
    @PUT("api/Entries/{id}")
    suspend fun updateEntry(
        @Header("Authorization") authorization: String,
        @Path("id") id: Int,
        @Body entry: TravelEntryDto
    ): Response<Unit>

    // DELETE an entry
    @DELETE("api/Entries/{id}")
    suspend fun deleteEntry(
        @Header("Authorization") authorization: String,
        @Path("id") id: Int
    ): Response<Unit>
}
