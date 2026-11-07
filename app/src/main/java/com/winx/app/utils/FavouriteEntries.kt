package com.winx.app.utils

import android.content.Context
import com.winx.app.data.remote.FirebaseRequestSession
import com.winx.app.data.remote.RetrofitClient
import retrofit2.HttpException

/** Caches cloud favourites separately for each Firebase account. */
object FavouriteEntries {
    private const val ENTRY_IDS = "entry_ids"
    private const val IMPORTED = "cloud_imported"

    private fun preferences(context: Context, uid: String) =
        context.getSharedPreferences("winx_favourites_$uid", Context.MODE_PRIVATE)

    fun read(context: Context, uid: String): Set<Int> =
        preferences(context, uid).getStringSet(ENTRY_IDS, emptySet())
            .orEmpty().mapNotNull { it.toIntOrNull() }.filter { it > 0 }.toSet()

    suspend fun refresh(context: Context, uid: String): Set<Int> {
        val prefs = preferences(context, uid)
        val session = FirebaseRequestSession(uid)
        val api = RetrofitClient.api
        val cloudIds = session.request { api.getFavourites(it) }.toMutableSet()

        if (!prefs.getBoolean(IMPORTED, false)) {
            for (id in read(context, uid) - cloudIds) {
                val result = session.request { api.saveFavourite(it, id) }
                when {
                    result.isSuccessful -> cloudIds.add(id)
                    result.code() != 404 -> throw HttpException(result)
                }
            }
        }

        cache(context, uid, cloudIds)
        return cloudIds
    }

    suspend fun setFavourite(
        context: Context,
        uid: String,
        id: Int,
        saved: Boolean
    ): Set<Int> {
        val api = RetrofitClient.api
        val response = FirebaseRequestSession(uid).request {
            if (saved) api.saveFavourite(it, id) else api.removeFavourite(it, id)
        }
        if (!response.isSuccessful) throw HttpException(response)

        val cached = read(context, uid)
        val updated = if (saved) cached + id else cached - id
        cache(context, uid, updated)
        return updated
    }

    private fun cache(context: Context, uid: String, ids: Set<Int>) {
        preferences(context, uid).edit()
            .putStringSet(ENTRY_IDS, ids.map { it.toString() }.toSet())
            .putBoolean(IMPORTED, true)
            .apply()
    }
}
