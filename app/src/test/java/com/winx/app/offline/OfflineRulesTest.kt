package com.winx.app.offline

import org.junit.Assert.*
import org.junit.Test

class OfflineRulesTest {
    @Test fun accountAIsNeverUploadedAsB() {
        assertFalse(OfflineRules.maySync("a", "b"))
        assertTrue(OfflineRules.maySync("a", "a"))
    }
    @Test fun signedOutAccountCannotSync() {
        assertFalse(OfflineRules.maySync("a", null))
        assertFalse(OfflineRules.maySync("", ""))
    }
    @Test fun serverAndRateLimitFailuresRetry() {
        listOf(408, 429, 500, 502, 503).forEach {
            assertEquals(OfflineRules.Failure.RETRY, OfflineRules.failureFor(it))
        }
    }
    @Test fun authenticationWaitsForCredentials() {
        listOf(401, 403).forEach {
            assertEquals(OfflineRules.Failure.AUTH, OfflineRules.failureFor(it))
        }
    }
    @Test fun invalidOrUndeployedRequestsAreRetainedAsFailed() {
        listOf(400, 404, 409, 410, 422).forEach {
            assertEquals(OfflineRules.Failure.PERMANENT, OfflineRules.failureFor(it))
        }
    }
}
