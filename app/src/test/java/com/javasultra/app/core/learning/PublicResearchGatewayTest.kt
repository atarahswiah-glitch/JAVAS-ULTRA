package com.javasultra.app.core.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URI

class PublicResearchGatewayTest {
    private fun request(uri: String = "https://www.nasa.gov/learning", method: ResearchHttpMethod = ResearchHttpMethod.GET) =
        PublicPageRequest(URI(uri), method, true, true)

    @Test fun allowsApprovedPublicHttpsRead() {
        assertNull(PublicResearchGateway.evaluate(request(), setOf("nasa.gov")))
    }
    @Test fun rejectsNonHttps() {
        assertEquals(PublicFetchDenial.HTTPS_REQUIRED, PublicResearchGateway.evaluate(request("http://nasa.gov"), setOf("nasa.gov")))
    }
    @Test fun rejectsLookalikeDomain() {
        assertEquals(PublicFetchDenial.DOMAIN_NOT_ALLOWED, PublicResearchGateway.evaluate(request("https://nasa.gov.attacker.example"), setOf("nasa.gov")))
    }
    @Test fun rejectsNonGetMethods() {
        assertEquals(PublicFetchDenial.GET_ONLY, PublicResearchGateway.evaluate(request(method = ResearchHttpMethod.POST), setOf("nasa.gov")))
    }
    @Test fun rejectsRobotsUncheckedPages() {
        assertEquals(PublicFetchDenial.ROBOTS_POLICY_NOT_CHECKED, PublicResearchGateway.evaluate(request().copy(robotsPolicyChecked = false), setOf("nasa.gov")))
    }
    @Test fun rejectsAuthenticationAndBypassAttempts() {
        assertEquals(PublicFetchDenial.AUTHENTICATION_NOT_ALLOWED, PublicResearchGateway.evaluate(request().copy(attemptsAuthentication = true), setOf("nasa.gov")))
        assertEquals(PublicFetchDenial.BYPASS_ATTEMPT_BLOCKED, PublicResearchGateway.evaluate(request().copy(attemptsPaywallOrCaptchaBypass = true), setOf("nasa.gov")))
    }
}
