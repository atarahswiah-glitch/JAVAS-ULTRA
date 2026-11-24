package com.javasultra.app.core.learning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class SafePublicResearchPolicyTest {

    @Test fun rejectsPrivateAndReservedIpv4Addresses() {
        val blocked = listOf(
            "0.0.0.1", "10.0.0.1", "100.64.0.1", "127.0.0.1",
            "169.254.1.1", "172.16.0.1", "172.31.255.254",
            "192.0.0.8", "192.0.2.10", "192.88.99.1", "192.168.1.1",
            "198.18.0.1", "198.51.100.4", "203.0.113.7",
            "224.0.0.1", "240.0.0.1"
        )
        blocked.forEach { value ->
            assertFalse("$value must not be considered public", PublicAddressPolicy.isGlobalUnicast(InetAddress.getByName(value)))
        }
    }

    @Test fun acceptsKnownGlobalIpv4Addresses() {
        listOf("1.1.1.1", "8.8.8.8", "9.9.9.9").forEach { value ->
            assertTrue("$value should be considered public", PublicAddressPolicy.isGlobalUnicast(InetAddress.getByName(value)))
        }
    }

    @Test fun rejectsNonGlobalIpv6Addresses() {
        val blocked = listOf(
            "::", "::1", "fc00::1", "fe80::1", "ff02::1",
            "2001:db8::1", "2001:0::1", "2002::1", "64:ff9b::7f00:1"
        )
        blocked.forEach { value ->
            assertFalse("$value must not be considered public", PublicAddressPolicy.isGlobalUnicast(InetAddress.getByName(value)))
        }
    }

    @Test fun acceptsGlobalUnicastIpv6Address() {
        assertTrue(
            PublicAddressPolicy.isGlobalUnicast(InetAddress.getByName("2606:4700:4700::1111"))
        )
    }

    @Test fun robotsPolicyBlocksWildcardAndPrefixDisallowRules() {
        val robots = """
            User-agent: *
            Disallow: /private
            Disallow: /account/*
        """.trimIndent()

        assertFalse(RobotsTxtPolicy.isAllowed(robots, "/private/profile"))
        assertFalse(RobotsTxtPolicy.isAllowed(robots, "/account/settings"))
        assertTrue(RobotsTxtPolicy.isAllowed(robots, "/public/article"))
    }

    @Test fun robotsPolicyAllowsWhenNoMatchingDisallowExists() {
        assertTrue(
            RobotsTxtPolicy.isAllowed(
                "User-agent: *\nDisallow: /admin",
                "/research/overview"
            )
        )
    }

    @Test fun robotsPolicyAppliesOnlyMatchingAgentGroups() {
        val robots = """
            User-agent: OtherBot
            Disallow: /

            User-agent: JAVAS-ULTRA
            Disallow: /private
        """.trimIndent()

        assertTrue(RobotsTxtPolicy.isAllowed(robots, "/public"))
        assertFalse(RobotsTxtPolicy.isAllowed(robots, "/private/notes"))
    }
}
