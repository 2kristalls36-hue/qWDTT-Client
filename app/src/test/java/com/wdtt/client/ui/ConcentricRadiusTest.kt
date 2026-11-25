package com.wdtt.client.ui

import androidx.compose.ui.unit.dp
import com.wdtt.client.ui.components.layout.concentricRadius
import org.junit.Assert.assertEquals
import org.junit.Test

class ConcentricRadiusTest {
    @Test fun followsTheScreenCornerMinusTheInset() {
        assertEquals(42.dp, concentricRadius(58.dp, 16.dp, 20.dp))
        assertEquals(26.dp, concentricRadius(58.dp, 32.dp, 20.dp))
    }

    @Test fun neverSharperThanTheUsualShape() {
        assertEquals(20.dp, concentricRadius(30.dp, 16.dp, 20.dp))
    }

    @Test fun unknownCornersKeepTheUsualShape() {
        assertEquals(20.dp, concentricRadius(0.dp, 16.dp, 20.dp))
    }
}
