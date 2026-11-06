package com.winx.app.data.remote

import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.Part
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Streaming
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface MediaApiService {

    @GET("api/Media/entry/{travelEntryId}")
    suspend fun getMediaForEntry(
        @Header("Authorization") authorization: String,
        @Path("travelEntryId") travelEntryId: Int
    ): List<MediaItemDto>

    @GET("api/Media/{id}")
    suspend fun getMedia(
        @Header("Authorization") authorization: String,
        @Path("id") id: Int
    ): MediaItemDto

    @POST("api/Media")
    suspend fun createMedia(
        @Header("Authorization") authorization: String,
        @Body media: MediaItemDto
    ): Response<MediaItemDto>

    @Multipart
    @POST("api/Media/entry/{travelEntryId}/upload")
    suspend fun uploadMedia(
        @Header("Authorization") authorization: String,
        @Path("travelEntryId") travelEntryId: Int,
        @Header("Idempotency-Key") operationId: String,
        @Part file: MultipartBody.Part
    ): MediaItemDto

    @Streaming
    @GET("api/Media/{id}/content")
    suspend fun downloadMedia(
        @Header("Authorization") authorization: String,
        @Path("id") id: Int
    ): ResponseBody

    @DELETE("api/Media/{id}")
    suspend fun deleteMedia(
        @Header("Authorization") authorization: String,
        @Path("id") id: Int
    ): Response<Unit>
}
