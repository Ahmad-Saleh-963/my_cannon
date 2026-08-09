package com.example.my_cannon.domain.calculator

import com.example.my_cannon.data.model.Quadrant
import com.example.my_cannon.data.model.UtmPoint
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
    fun testQuadrant2_SouthWest() {
        // Battery at (0,0), Target at (-100, -100) -> X decreases, Y decreases -> User says Q2
        val battery = UtmPoint(0.0, 0.0)
        val target = UtmPoint(-100.0, -100.0)
        val result = ArtilleryCalculator.calculateBetweenPoints(battery, target)
        
        assertEquals(Quadrant.SECOND, result.quadrant)
        // User Q2 Formula: theta - 180. theta = 45. 45 - 180 = -135
        assertEquals(-135.0, result.azimuth, 0.01)
    }

    @Test
    fun testQuadrant3_SouthEast() {
        // Battery at (0,0), Target at (100, -100) -> X increases, Y decreases -> User says Q3
        val battery = UtmPoint(0.0, 0.0)
        val target = UtmPoint(100.0, -100.0)
        val result = ArtilleryCalculator.calculateBetweenPoints(battery, target)
        
        assertEquals(Quadrant.THIRD, result.quadrant)
        // User Q3 Formula: theta + 180. theta = 45. 45 + 180 = 225
        assertEquals(225.0, result.azimuth, 0.01)
    }

    @Test
    fun testQuadrant4_NorthWest() {
        // Battery at (0,0), Target at (-100, 100) -> X decreases, Y increases -> User says Q4
        val battery = UtmPoint(0.0, 0.0)
        val target = UtmPoint(-100.0, 100.0)
        val result = ArtilleryCalculator.calculateBetweenPoints(battery, target)
        
        assertEquals(Quadrant.FOURTH, result.quadrant)
        // User Q4 Formula: theta - 360. theta = 45. 45 - 360 = -315
        assertEquals(-315.0, result.azimuth, 0.01)
    }
}
