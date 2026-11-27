package com.wdtt.client.ui.festive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnowfallTest {
    @Test fun aboutHalfTheFlakesAreSeenAtATime() {
        var t = 0f
        var least = 40f
        var most = 0f
        var sum = 0f
        var n = 0
        while (t < 120f) {
            val seen = (0 until 40).sumOf { snowflakeShown(it, t).toDouble() }.toFloat()
            least = minOf(least, seen)
            most = maxOf(most, seen)
            sum += seen
            n++
            t += 0.25f
        }
        println("snowflakes seen: $least to $most of 40, on average ${sum / n}")
        assertTrue("never too many: $most", most <= 30f)
        // The snow never stops: some flakes are always there.
        assertTrue("never none: $least", least >= 5f)
        assertEquals("about half on average", 20f, sum / n, 6f)
    }

    @Test fun eachFlakeFadesAwayAndComesBackSmoothly() {
        for (i in 0 until 40) {
            var t = 0f
            var gone = false
            var back = false
            var prev = snowflakeShown(i, 0f)
            while (t < 30f) {
                t += 1f / 60f
                val now = snowflakeShown(i, t)
                assertTrue("flake $i jumps at $t: $prev to $now", kotlin.math.abs(now - prev) < 0.05f)
                if (now < 0.01f) gone = true
                if (gone && now > 0.99f) back = true
                prev = now
            }
            assertTrue("flake $i fades away and comes back", gone && back)
        }
    }
}
