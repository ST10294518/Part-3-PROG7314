package com.winx.app.data.remote

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import android.util.Log
import kotlinx.coroutines.CancellationException

class MediaRepository {

    private val api = RetrofitClient.mediaApi
    private val session = FirebaseRequestSession()

    suspend fun getMediaForEntry(travelEntryId: Int): Result<List<MediaItemDto>> {
        return try {
            val media = session.request { authorization -> api.getMediaForEntry(authorization, travelEntryId) }

            Log.d(
                "MediaRepository",
                "Loaded ${media.size} media item(s) for entry $travelEntryId"
            )

            Result.success(media)
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception

            Log.e(
                "MediaRepository",
                "Failed to load media for entry $travelEntryId",
                exception
            )

            Result.failure(exception)
        }
    }

    suspend fun createMedia(media: MediaItemDto): Result<MediaItemDto> {
        return try {
            val response = session.request { authorization -> api.createMedia(authorization, media) }

            if (response.isSuccessful && response.body() != null) {

                Log.d(
                    "MediaRepository",
                    "Media created successfully: ${response.body()?.id}"
                )

                Result.success(response.body()!!)
            } else {

                Log.e(
                    "MediaRepository",
                    "Failed to create media. HTTP ${response.code()}"
                )

                Result.failure(
                    Exception("Failed to create media. HTTP ${response.code()}")
                )
            }
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception

            Log.e(
                "MediaRepository",
                "Error creating media",
                exception
            )

            Result.failure(exception)
        }
    }

    suspend fun uploadMedia(context: Context, travelEntryId: Int, uri: Uri): Result<MediaItemDto> {
        return try {
            val uploaded = withContext(Dispatchers.IO) {
                val resolver = context.contentResolver
                val displayName = resolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }

                val extension = when (resolver.getType(uri)?.lowercase()) {
                    "image/jpeg" -> "jpg"
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    "video/mp4" -> "mp4"
                    else -> when (displayName?.substringAfterLast('.', "")?.lowercase()) {
                        "jpg", "jpeg" -> "jpg"
                        "png" -> "png"
                        "webp" -> "webp"
                        "mp4" -> "mp4"
                        else -> throw IOException("Choose a JPEG, PNG, WebP, or MP4 file.")
                    }
                }

                val baseName = displayName.orEmpty()
                    .substringAfterLast('/')
                    .substringAfterLast('\\')
                    .substringBeforeLast('.')
                    .filterNot { it.isISOControl() }
                    .trim()
                    .take(200)
                    .ifBlank { "attachment" }

                val temporaryFile = File.createTempFile("winx-upload-", ".tmp", context.cacheDir)
                try {
                    val input = resolver.openInputStream(uri)
                        ?: throw IOException("Could not open the selected attachment.")

                    input.use { source ->
                        temporaryFile.outputStream().use { destination ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var totalBytes = 0

                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = source.read(buffer)
                                if (read == -1) break

                                totalBytes += read
                                if (totalBytes > 25 * 1024 * 1024) {
                                    throw IOException("Choose an attachment no larger than 25 MiB.")
                                }
                                destination.write(buffer, 0, read)
                            }

                            if (totalBytes == 0) throw IOException("The selected attachment is empty.")
                        }
                    }

                    val part = MultipartBody.Part.createFormData(
                        "file", "$baseName.$extension", temporaryFile.asRequestBody()
                    )
                    session.request { authorization ->
                        api.uploadMedia(authorization, travelEntryId, part)
                    }
                } finally {
                    temporaryFile.delete()
                }
            }
            Result.success(uploaded)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    suspend fun downloadMedia(id: Int, destination: File): Result<Unit> {
        return try {
            session.request { authorization ->
                api.downloadMedia(authorization, id).use { body ->
                    withContext(Dispatchers.IO) {
                        destination.outputStream().use { output ->
                            val input = body.byteStream()
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                }
            }
            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    suspend fun deleteMedia(id: Int): Result<Unit> {
        return try {
            val response = session.request { authorization -> api.deleteMedia(authorization, id) }

            if (response.isSuccessful) {

                Log.d(
                    "MediaRepository",
                    "Media $id deleted successfully"
                )

                Result.success(Unit)
            } else {

                Log.e(
                    "MediaRepository",
                    "Failed to delete media $id. HTTP ${response.code()}"
                )

                Result.failure(
                    Exception("Failed to delete media. HTTP ${response.code()}")
                )
            }
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception

            Log.e(
                "MediaRepository",
                "Error deleting media $id",
                exception
            )

            Result.failure(exception)
        }
    }
    /**
     * Loads media belonging to all supplied travel-entry IDs.
     * This keeps LibraryScreen free from hard-coded photo/video data.
     */
    suspend fun getMediaForEntries(travelEntryIds: List<Int>): Result<List<MediaItemDto>> {
        return try {
            val validIds = travelEntryIds.filter { it > 0 }.distinct()
            val allMedia = mutableListOf<MediaItemDto>()

            for (entryId in validIds) {
                allMedia += session.request { authorization -> api.getMediaForEntry(authorization, entryId) }
            }

            Log.d(
                "MediaRepository",
                "Loaded ${allMedia.size} media item(s) for ${validIds.size} entries"
            )

            Result.success(allMedia)
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            Log.e(
                "MediaRepository",
                "Failed to load Library media",
                exception
            )
            Result.failure(exception)
        }
    }

}