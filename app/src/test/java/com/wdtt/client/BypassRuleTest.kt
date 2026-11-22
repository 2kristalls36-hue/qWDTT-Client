package com.wdtt.client

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BypassRuleTest {
    @Test fun accepted() {
        listOf("example.com", "*.example.org", "https://example.com/page", "203.0.113.7", "203.0.113.0/24", "203.0.*.*")
            .forEach { assertTrue(it, BypassRoutes.isValidRule(it)) }
    }

    @Test fun subnetKeepsItsPrefix() {
        org.junit.Assert.assertEquals(listOf("203.0.113.0/24", "example.com"), BypassRoutes.parseRules("203.0.113.0/24\nhttps://example.com/x"))
    }

    @Test fun rejected() {
        listOf("", "example", "300.1.1.1", "203.0.113.0/40", "пример", "a b.com")
            .forEach { assertFalse(it, BypassRoutes.isValidRule(it)) }
    }
}
