package com.winx.app.data.remote

import android.util.Log
import kotlinx.coroutines.CancellationException
import com.winx.app.screens.TravelEntry

class TravelEntryRepository {

    private val api = RetrofitClient.api
    private val session = FirebaseRequestSession()

    companion object {
        private const val TAG = "TravelEntryRepository"
    }

    /**
     * Retrieves all entries from the Winx REST API.
     */
    suspend fun getEntries(): Result<List<TravelEntry>> {

        return try {

            Log.d(TAG, "GET /api/Entries")

            val response = session.request { authorization -> api.getEntries(authorization) }

            Log.d(
                TAG,
                "GET /api/Entries successful. Entries received: ${response.size}"
            )

            Result.success(
                response.map { it.toTravelEntry() }
            )

        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception

            Log.e(
                TAG,
                "Failed to retrieve entries",
                exception
            )

            Result.failure(exception)
        }
    }

    /**
     * Creates a new entry through the Winx REST API.
     */
    suspend fun createEntry(entry: TravelEntry): Result<TravelEntry> {

        return try {

            Log.d(
                TAG,
                "POST /api/Entries - title=${entry.title}"
            )

            val dto = entry.toDto()

            val response = session.request { authorization -> api.createEntry(authorization, dto) }

            if (response.isSuccessful) {

                val createdEntry = response.body()

                if (createdEntry != null) {

                    Log.d(
                        TAG,
                        "POST /api/Entries successful. ID=${createdEntry.id}"
                    )

                    Result.success(
                        createdEntry.toTravelEntry()
                    )

                } else {

                    Log.e(
                        TAG,
                        "POST succeeded but API returned an empty response body"
                    )

                    Result.failure(
                        Exception("The API created the entry but returned no entry data.")
                    )
                }

            } else {

                Log.e(
                    TAG,
                    "POST /api/Entries failed. HTTP ${response.code()}"
                )

                Result.failure(
                    Exception(
                        "Unable to save entry. HTTP ${response.code()}"
                    )
                )
            }

        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception

            Log.e(
                TAG,
                "Failed to create entry",
                exception
            )

            Result.failure(exception)
        }
    }

    /**
     * Retrieves one entry by ID.
     */
    suspend fun getEntry(id: Int): Result<TravelEntry> {

        return try {

            Log.d(
                TAG,
                "GET /api/Entries/$id"
            )

            val response = session.request { authorization -> api.getEntry(authorization, id) }

            Log.d(
                TAG,
                "GET /api/Entries/$id successful"
            )

            Result.success(
                response.toTravelEntry()
            )

        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception

            Log.e(
                TAG,
                "Failed to retrieve entry ID=$id",
                exception
            )

            Result.failure(exception)
        }
    }

    /**
     * Updates an existing entry.
     */
    suspend fun updateEntry(entry: TravelEntry): Result<Unit> {

        return try {

            Log.d(
                TAG,
                "PUT /api/Entries/${entry.id}"
            )

            val response = session.request { authorization ->
                api.updateEntry(authorization, entry.id, entry.toDto())
            }

            if (response.isSuccessful) {

                Log.d(
                    TAG,
                    "PUT /api/Entries/${entry.id} successful"
                )

                Result.success(Unit)

            } else {

                Log.e(
                    TAG,
                    "PUT failed. HTTP ${response.code()}"
                )

                Result.failure(
                    Exception(
                        "Unable to update entry. HTTP ${response.code()}"
                    )
                )
            }

        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception

            Log.e(
                TAG,
                "Failed to update entry ID=${entry.id}",
                exception
            )

            Result.failure(exception)
        }
    }

    /**
     * Deletes an existing entry.
     */
    suspend fun deleteEntry(id: Int): Result<Unit> {

        return try {

            Log.d(
                TAG,
                "DELETE /api/Entries/$id"
            )

            val response = session.request { authorization -> api.deleteEntry(authorization, id) }

            if (response.isSuccessful) {

                Log.d(
                    TAG,
                    "DELETE /api/Entries/$id successful"
                )

                Result.success(Unit)

            } else {

                Log.e(
                    TAG,
                    "DELETE failed. HTTP ${response.code()}"
                )

                Result.failure(
                    Exception(
                        "Unable to delete entry. HTTP ${response.code()}"
                    )
                )
            }

        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception

            Log.e(
                TAG,
                "Failed to delete entry ID=$id",
                exception
            )

            Result.failure(exception)
        }
    }

    suspend fun getDeletedEntries(): Result<List<TravelEntry>> = try {
        val response = session.request { auth -> api.getDeletedEntries(auth) }
        Result.success(response.map { it.toTravelEntry() })
    } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        Result.failure(exception)
    }

    suspend fun restoreEntry(id: Int): Result<Unit> = deletedAction {
        session.request { auth -> api.restoreEntry(auth, id) }
    }

    suspend fun permanentlyDeleteEntry(id: Int): Result<Unit> = deletedAction {
        session.request { auth -> api.permanentlyDeleteEntry(auth, id) }
    }

    suspend fun emptyDeletedEntries(): Result<Unit> = deletedAction {
        session.request { auth -> api.emptyDeletedEntries(auth) }
    }

    private suspend fun deletedAction(
        request: suspend () -> retrofit2.Response<Unit>
    ): Result<Unit> = try {
        val response = request()
        if (response.isSuccessful) Result.success(Unit)
        else Result.failure(Exception("Unable to update Recently Deleted. HTTP ${response.code()}"))
    } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        Result.failure(exception)
    }

    private fun TravelEntryDto.toTravelEntry(): TravelEntry {

        return TravelEntry(
            id = id,
            title = title,
            location = location,
            country = country,
            date = date,
            rating = rating,
            notes = notes
        )
    }

    private fun TravelEntry.toDto(): TravelEntryDto {

        return TravelEntryDto(
            id = id,
            title = title,
            location = location,
            country = country,
            date = date,
            rating = rating,
            notes = notes
        )
    }
}