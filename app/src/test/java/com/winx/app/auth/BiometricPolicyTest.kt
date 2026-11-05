package com.winx.app.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricPolicyTest {
    @Test fun returningProtectedAccountIsLocked() {
        assertTrue(BiometricPolicy.requiresUnlock(true, true, false))
    }
    @Test fun successfulVerificationAllowsAccess() {
        assertFalse(BiometricPolicy.requiresUnlock(true, true, true))
    }
    @Test fun signedOutUserCanReachAccountLogin() {
        assertFalse(BiometricPolicy.requiresUnlock(false, true, false))
    }
    @Test fun accountWithoutOptInCanUseApp() {
        assertFalse(BiometricPolicy.requiresUnlock(true, false, false))
    }
    @Test fun returningFromBackgroundRequiresVerificationAgain() {
        assertFalse(BiometricPolicy.requiresUnlock(true, true, true))
        assertTrue(BiometricPolicy.requiresUnlock(true, true, false))
    }
}
