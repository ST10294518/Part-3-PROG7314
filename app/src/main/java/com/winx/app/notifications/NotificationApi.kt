package com.winx.app.notifications

import retrofit2.Response
import retrofit2.http.*

data class DeviceRegistration(val installationId: String, val secret: String, val token: String, val enabled: Boolean)
data class DeviceRevocation(val installationId: String, val secret: String)
data class NotificationIds(val ids: List<String>, val beforeReceivedAt: Long? = null)

interface NotificationApi {
    @PUT("api/Notifications/device")
    suspend fun register(@Header("Authorization") authorization: String, @Body body: DeviceRegistration): Response<Unit>
    @POST("api/Notifications/device/revoke")
    suspend fun revoke(@Body body: DeviceRevocation): Response<Unit>
    @GET("api/Notifications")
    suspend fun inbox(@Header("Authorization") authorization: String): List<NotificationRecord>
    @POST("api/Notifications/read")
    suspend fun read(@Header("Authorization") authorization: String, @Body body: NotificationIds): Response<Unit>
    @POST("api/Notifications/clear")
    suspend fun clear(@Header("Authorization") authorization: String, @Body body: NotificationIds): Response<Unit>
}
