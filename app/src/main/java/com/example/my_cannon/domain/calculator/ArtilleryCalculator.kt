package com.example.my_cannon.domain.calculator

import com.example.my_cannon.data.model.CalculationResult
import com.example.my_cannon.data.model.CannonPosition
import com.example.my_cannon.data.model.Quadrant
import com.example.my_cannon.data.model.TargetPosition
import com.example.my_cannon.data.model.UtmPoint
import kotlin.math.*

/**
 * المحرك الرياضي لحسابات المدفعية بناءً على القوانين المحددة من قبل المستخدم.
 */
object ArtilleryCalculator {

    fun calculate(cannon: CannonPosition, target: TargetPosition): CalculationResult {
        return calculateBetweenPoints(cannon.utmPoint, target.utmPoint)
    }

    fun calculateBetweenPoints(battery: UtmPoint, point: UtmPoint): CalculationResult {
        val x1 = battery.easting
        val y1 = battery.northing
        val x2 = point.easting
        val y2 = point.northing

        // 1. حساب فروق الإحداثيات (Delta X, Delta Y) بالقيمة المطلقة كما هو مطلوب
        val deltaX = abs(x2 - x1)
        val deltaY = abs(y2 - y1)

        // 2. حساب الزاوية تيتا (θ) = arctg (deltaX / deltaY)
        // ملاحظة: التحويل للدرجات لأن القوانين بالدرجات
        val thetaRad = atan(if (deltaY != 0.0) deltaX / deltaY else 1e10)
        val thetaDeg = Math.toDegrees(thetaRad)

        // 3. تحديد الربع بناءً على زيادة أو نقصان الإحداثيات
        val isXIncreasing = x2 >= x1
        val isYIncreasing = y2 >= y1

        /**
         * حسب طلب المستخدم الدقيق:
         * 1. الربع الأول رياضياً (X+, Y+) -> الربع الأول هنا.
         * 2. الربع الثاني رياضياً (X-, Y+) -> الربع الرابع هنا.
         * 3. الربع الثالث رياضياً (X-, Y-) -> الربع الثاني هنا.
         * 4. الربع الرابع رياضياً (X+, Y-) -> الربع الثالث هنا.
         */
        val quadrant = when {
            isXIncreasing && isYIncreasing -> Quadrant.FIRST     // Q1 Math -> Q1 Tactical
            !isXIncreasing && isYIncreasing -> Quadrant.FOURTH   // Q2 Math -> Q4 Tactical
            !isXIncreasing && !isYIncreasing -> Quadrant.SECOND  // Q3 Math -> Q2 Tactical
            else -> Quadrant.THIRD                               // Q4 Math -> Q3 Tactical
        }

        // 4. حساب السمت (Azimuth) بناءً على قوانين الربع المحددة من المستخدم
        val azimuth = when (quadrant) {
            Quadrant.FIRST -> thetaDeg
            Quadrant.SECOND -> thetaDeg - 180.0
            Quadrant.THIRD -> thetaDeg + 180.0
            Quadrant.FOURTH -> thetaDeg - 360.0
        }

        // 5. حساب المسافة الوترية المباشرة (Euclidean Distance) بدقة عالية جداً
        val distance = hypot(deltaX, deltaY)

        return CalculationResult(
            deltaX = deltaX,
            deltaY = deltaY,
            theta = thetaDeg,
            distance = distance,
            azimuth = azimuth,
            quadrant = quadrant,
            isXIncreasing = isXIncreasing,
            isYIncreasing = isYIncreasing,
            x1 = x1,
            y1 = y1,
            x2 = x2,
            y2 = y2
        )
    }
}
