package com.montecarlo.ledger.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardScreenTest {

    @Test
    fun normalizedSparklineX_usesTheCurrentSeriesLength() {
        assertEquals(100f, normalizedSparklineX(index = 2, pointCount = 3, width = 100f), 0.001f)
    }

    @Test
    fun normalizedSparklineX_handlesSinglePointSeries() {
        assertEquals(0f, normalizedSparklineX(index = 0, pointCount = 1, width = 100f), 0.001f)
    }
}
