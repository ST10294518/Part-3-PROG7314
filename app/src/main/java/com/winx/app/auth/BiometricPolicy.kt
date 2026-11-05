package com.winx.app.auth

/** Local app locking supplements, rather than replaces, Firebase authentication. */
object BiometricPolicy {
    fun requiresUnlock(
        signedIn: Boolean,
        enabled: Boolean,
        sessionUnlocked: Boolean
    ): Boolean = signedIn && enabled && !sessionUnlocked
}