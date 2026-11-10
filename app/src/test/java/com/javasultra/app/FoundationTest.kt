package com.javasultra.app

import org.junit.Assert.assertEquals
import org.junit.Test

class FoundationTest {
    @Test
    fun applicationIdentityUsesJavasUltraNamespace() {
        assertEquals("com.javasultra.app", BuildIdentity.applicationId)
    }
}

internal object BuildIdentity {
    const val applicationId = "com.javasultra.app"
}