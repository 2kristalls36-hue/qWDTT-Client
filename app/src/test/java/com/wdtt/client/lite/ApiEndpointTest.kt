package com.wdtt.client.lite

import com.wdtt.client.PeerAddress
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiEndpointTest {
    @Test fun apiUsesMainPortWhateverPortTheProfileHas() {
        for (peer in listOf("203.0.113.10:56002", "203.0.113.10:56003", "203.0.113.10", "203.0.113.10:56000")) {
            assertEquals("203.0.113.10:56000", PeerAddress.httpEndpoint(PeerAddress.host(peer), ProfileStatusClient.API_PORT))
        }
    }
}
