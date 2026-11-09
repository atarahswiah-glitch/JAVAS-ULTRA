package com.javasultra.app.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ModelRoutePolicyTest {
    private val now = Instant.parse("2026-11-09T12:00:00Z")
    private fun provider(
        id: String,
        kind: ModelProviderKind,
        accepted: Set<DataClassification> = DataClassification.entries.toSet()
    ) = ModelProviderDescriptor(
        id, kind, setOf(RuntimeModality.TEXT, RuntimeModality.IMAGE), true,
        100_000, 8_000, accepted, true
    )
    private fun request(classification: DataClassification = DataClassification.PUBLIC) = ModelRequest(
        "task-1", "Explain planetary motion", setOf(RuntimeModality.TEXT),
        classification, false, 1000, now.plusSeconds(60)
    )

    @Test fun selectsOnDeviceProviderForCreatorPrivateData() {
        val local = provider("local", ModelProviderKind.ON_DEVICE)
        val cloud = provider("cloud", ModelProviderKind.APPROVED_CLOUD)
        assertEquals("local", ModelRoutePolicy.select(request(DataClassification.CREATOR_PRIVATE), listOf(cloud, local), false, now)?.id)
    }

    @Test fun offlineModeNeverSelectsCloudProvider() {
        assertNull(ModelRoutePolicy.select(request(), listOf(provider("cloud", ModelProviderKind.APPROVED_CLOUD)), true, now))
    }

    @Test fun requiresProviderToAcceptDataClassification() {
        val publicOnly = provider("local", ModelProviderKind.ON_DEVICE, setOf(DataClassification.PUBLIC))
        assertNull(ModelRoutePolicy.select(request(DataClassification.HIGHLY_SENSITIVE), listOf(publicOnly), false, now))
    }

    @Test fun rejectsProviderWithoutRequestedToolSupport() {
        val noTools = provider("local", ModelProviderKind.ON_DEVICE).copy(supportedTools = false)
        val r = request().copy(requestedToolSupport = true)
        assertNull(ModelRoutePolicy.select(r, listOf(noTools), false, now))
    }

    @Test fun rejectsExpiredOrInvalidRequests() {
        assertNull(ModelRoutePolicy.select(request().copy(deadline = now), listOf(provider("local", ModelProviderKind.ON_DEVICE)), false, now))
        assertNull(ModelRoutePolicy.select(request().copy(prompt = " "), listOf(provider("local", ModelProviderKind.ON_DEVICE)), false, now))
    }
}
