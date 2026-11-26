package com.wdtt.client.ui.festive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class FestiveCalendarTest {
    @Test fun onlyFromTheLastDayOfTheYearToTheSecondOfJanuary() {
        assertFalse(FestiveCalendar.isActive(LocalDate.of(2026, 12, 30)))
        assertTrue(FestiveCalendar.isActive(LocalDate.of(2026, 12, 31)))
        assertTrue(FestiveCalendar.isActive(LocalDate.of(2027, 1, 1)))
        assertTrue(FestiveCalendar.isActive(LocalDate.of(2027, 1, 2)))
        assertFalse(FestiveCalendar.isActive(LocalDate.of(2027, 1, 3)))
        assertFalse(FestiveCalendar.isActive(LocalDate.of(2026, 11, 25)))
    }

    @Test fun theYearBeingMet() {
        assertEquals("the coming one on New Year's Eve", 2027, FestiveCalendar.year(LocalDate.of(2026, 12, 31)))
        assertEquals("the one just come", 2027, FestiveCalendar.year(LocalDate.of(2027, 1, 1)))
        assertEquals(2027, FestiveCalendar.year(LocalDate.of(2027, 1, 2)))
        // A preview build on any other day: the coming one.
        assertEquals(2027, FestiveCalendar.year(LocalDate.of(2026, 11, 26)))
        assertEquals(2028, FestiveCalendar.year(LocalDate.of(2027, 1, 3)))
    }

    @Test fun nextMidnightWhenTheDecorationStartsOrEnds() {
        fun next(y: Int, m: Int, d: Int) = FestiveCalendar.nextChange(LocalDateTime.of(y, m, d, 15, 0))
        assertEquals(LocalDateTime.of(2026, 12, 31, 0, 0), next(2026, 11, 25))
        assertEquals(LocalDateTime.of(2027, 1, 3, 0, 0), next(2026, 12, 31))
        assertEquals(LocalDateTime.of(2027, 1, 3, 0, 0), next(2027, 1, 2))
        assertEquals(LocalDateTime.of(2027, 12, 31, 0, 0), next(2027, 1, 3))
    }
}
