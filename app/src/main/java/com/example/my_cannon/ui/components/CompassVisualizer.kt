package com.example.my_cannon.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my_cannon.data.model.CalculationResult
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CompassVisualizer(
    result: CalculationResult?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("التمثيل البياني للأرباع والسمت", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(250.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.width / 2

                // رسم المحاور
                drawLine(Color.Gray, Offset(0f, center.y), Offset(size.width, center.y), 2f)
                drawLine(Color.Gray, Offset(center.x, 0f), Offset(center.x, size.height), 2f)
                
                // رسم الدائرة الخارجية
                drawCircle(Color.Gray, radius, center, style = Stroke(width = 2f))

                // رسم اتجاه الهدف إذا وجد نتيجة
                result?.let {
                    // السمت العسكري يبدأ من الشمال (الأعلى) باتجاه عقارب الساعة
                    // في Canvas، الزاوية 0 هي اليمين، والزيادة مع عقارب الساعة.
                    // لتحويل السمت (من الشمال) إلى زاوية Canvas:
                    // CanvasAngle = Azimuth - 90
                    val angleRad = Math.toRadians(it.normalizedAzimuth - 90.0)
                    val targetX = center.x + radius * cos(angleRad).toFloat()
                    val targetY = center.y + radius * sin(angleRad).toFloat()

                    drawLine(
                        color = Color.Red,
                        start = center,
                        end = Offset(targetX, targetY),
                        strokeWidth = 8f
                    )
                    
                    drawCircle(Color.Red, 10f, Offset(targetX, targetY))
                }

                // كتابة أسماء الأرباع
                // ملاحظة: الرسم في Compose Canvas لا يدعم النص مباشرة بسهولة دون NativeCanvas
            }

            // تسميات الجهات
            Text("N", Modifier.align(Alignment.TopCenter), fontWeight = FontWeight.Bold)
            Text("S", Modifier.align(Alignment.BottomCenter), fontWeight = FontWeight.Bold)
            Text("E", Modifier.align(Alignment.CenterEnd), fontWeight = FontWeight.Bold)
            Text("W", Modifier.align(Alignment.CenterStart), fontWeight = FontWeight.Bold)
            
            // المربط في المركز
            Box(Modifier.size(12.dp).background(Color.Green, CircleShape))
        }
    }
}
