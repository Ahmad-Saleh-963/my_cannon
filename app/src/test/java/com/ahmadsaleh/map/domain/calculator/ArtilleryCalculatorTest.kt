package com.ahmadsaleh.map.domain.calculator

import com.ahmadsaleh.map.data.model.Quadrant
import com.ahmadsaleh.map.data.model.UtmPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtilleryCalculatorTest {

    @Test
    fun testQuadrant1_NorthEast() {
        // Battery at (0,0), Target at (100, 100) -> X increases, Y increases -> Q1
        val battery = UtmPoint(0.0, 0.0)
        val target = UtmPoint(100.0, 100.0)
        val result = ArtilleryCalculator.calculateBetweenPoints(battery, target)
        
        assertEquals(Quadrant.FIRST, result.quadrant)
        assertEquals(45.0, result.azimuth, 0.01)
    }

    @Test
    fun testQuadrant2_SouthEast() {
        // Battery at (0,0), Target at (100, -100) -> X+, Y- -> Q2
        val battery = UtmPoint(0.0, 0.0)
        val target = UtmPoint(100.0, -100.0)
        val result = ArtilleryCalculator.calculateBetweenPoints(battery, target)
        
        assertEquals(Quadrant.SECOND, result.quadrant)
        // Q2 Formula: 180 - theta = 180 - 45 = 135
        assertEquals(135.0, result.azimuth, 0.01)
    }

    @Test
    fun testQuadrant3_SouthWest() {
        // Battery at (0,0), Target at (-100, -100) -> X-, Y- -> Q3
        val battery = UtmPoint(0.0, 0.0)
        val target = UtmPoint(-100.0, -100.0)
        val result = ArtilleryCalculator.calculateBetweenPoints(battery, target)
        
        assertEquals(Quadrant.THIRD, result.quadrant)
        // Q3 Formula: 180 + theta = 180 + 45 = 225
        assertEquals(225.0, result.azimuth, 0.01)
    }

    @Test
    fun testQuadrant4_NorthWest() {
        // Battery at (0,0), Target at (-100, 100) -> X-, Y+ -> Q4
        val battery = UtmPoint(0.0, 0.0)
        val target = UtmPoint(-100.0, 100.0)
        val result = ArtilleryCalculator.calculateBetweenPoints(battery, target)
        
        assertEquals(Quadrant.FOURTH, result.quadrant)
        // Q4 Formula: 360 - theta = 360 - 45 = 315
        assertEquals(315.0, result.azimuth, 0.01)
    }
}
