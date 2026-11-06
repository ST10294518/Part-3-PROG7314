package com.winx.app.utils

import kotlin.random.Random

object RandomMemoryRules {
    /** Choose only available saved entries; avoid the previous pick when possible. */
    fun pickId(
        availableIds: List<Int>,
        previousId: Int? = null,
        random: Random = Random.Default
    ): Int? {
        val savedIds = availableIds.filter { it > 0 }.distinct()
        val candidates = if (savedIds.size > 1) {
            savedIds.filter { it != previousId }
        } else savedIds
        return if (candidates.isEmpty()) null else candidates[random.nextInt(candidates.size)]
    }
}
