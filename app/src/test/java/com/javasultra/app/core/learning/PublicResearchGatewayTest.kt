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
    @Test fun rejectsMalformedOrEmptyAllowlist() {
        assertEquals(PublicFetchDenial.INVALID_DOMAIN_POLICY, PublicResearchGateway.evaluate(request(), emptySet()))
        assertEquals(PublicFetchDenial.INVALID_DOMAIN_POLICY, PublicResearchGateway.evaluate(request(), setOf("https://nasa.gov/path")))
        assertEquals(PublicFetchDenial.INVALID_DOMAIN_POLICY, PublicResearchGateway.evaluate(request(), setOf("*.nasa.gov")))
    }
    @Test fun rejectsCredentialsAndNonStandardPorts() {
        assertEquals(PublicFetchDenial.INVALID_URI, PublicResearchGateway.evaluate(request("https://user@nasa.gov/"), setOf("nasa.gov")))
        assertEquals(PublicFetchDenial.INVALID_URI, PublicResearchGateway.evaluate(request("https://nasa.gov:8443/"), setOf("nasa.gov")))
    }
    @Test fun rejectsIpLiteralAndLocalHostTargets() {
        assertEquals(PublicFetchDenial.DOMAIN_NOT_ALLOWED, PublicResearchGateway.evaluate(request("https://127.0.0.1/"), setOf("nasa.gov")))
        assertEquals(PublicFetchDenial.DOMAIN_NOT_ALLOWED, PublicResearchGateway.evaluate(request("https://localhost/"), setOf("localhost")))
    }
    @Test fun rejectsPersonalDataAndNonPublicPages() {
        assertEquals(PublicFetchDenial.PERSONAL_DATA_BLOCKED, PublicResearchGateway.evaluate(request().copy(containsPersonalData = true), setOf("nasa.gov")))
        assertEquals(PublicFetchDenial.PUBLIC_ACCESS_ONLY, PublicResearchGateway.evaluate(request().copy(declaredPublic = false), setOf("nasa.gov")))
    }
}
