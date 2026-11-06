package com.winx.app.offline

/** Pure rules shared by the worker and tests. Failed items keep their original operation ID. */
object OfflineRules {
    enum class Failure { RETRY, AUTH, PERMANENT }
    fun maySync(ownerUid: String, currentUid: String?): Boolean =
        ownerUid.isNotBlank() && ownerUid == currentUid

    fun failureFor(code: Int): Failure = when {
        code == 401 || code == 403 -> Failure.AUTH
        code == 408 || code == 429 || code in 500..599 -> Failure.RETRY
        else -> Failure.PERMANENT
    }
}
