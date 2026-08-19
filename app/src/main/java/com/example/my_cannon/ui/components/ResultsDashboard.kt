package com.example.my_cannon.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my_cannon.data.model.CalculationResult
import java.util.Locale

@Composable
fun ResultsDashboard(
    result: CalculationResult?,
    pointName: String = "الهدف",
    showTitle: Boolean = true,
    readings: List<com.example.my_cannon.data.model.ReadingResult> = emptyList(),
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    if (result == null) {
        if (showTitle) {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("الرجاء تحديد المربط و $pointName لبدء الحسابات", style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (showTitle) {
            Text(
                text = "تقرير حسابات $pointName",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // 1. النتائج الأساسية (السمت والمسافة)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResultCard(
                title = "السمت (Azimuth)",
                value = String.format(Locale.US, "%.2f°", result.normalizedAzimuth),
                subValue = String.format(Locale.US, "%.0f mil", result.azimuthMils6000),
                icon = Icons.Default.CompassCalibration,
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
            ResultCard(
                title = "المسافة (m)",
                value = String.format(Locale.US, "%.2f م", result.distance),
                subValue = String.format(Locale.US, "%.1f كم", result.distance / 1000),
                icon = Icons.Default.Calculate,
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        }

        // 2. تفاصيل الأرباع
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(result.quadrant.nameAr, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Text(result.quadrant.description, style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    thickness = DividerDefaults.Thickness,
                    color = DividerDefaults.color
                )
                Text("المعادلة المستخدمة: ${result.quadrant.formulaAr}", fontWeight = FontWeight.SemiBold)
            }
        }

        // 3. الخطوات الرياضية التفصيلية
        Text("الخطوات الرياضية بالتفصيل", fontWeight = FontWeight.Bold)
        
        MathStep(
            label = "فرق الإحداثيات الأفقي ΔX",
            value = String.format(Locale.US, "|X₂ - X₁| = |%.1f - %.1f| = %.2f م", result.x2, result.x1, result.deltaX)
        )
        MathStep(
            label = "فرق الإحداثيات الشاقولي ΔY",
            value = String.format(Locale.US, "|Y₂ - Y₁| = |%.1f - %.1f| = %.2f م", result.y2, result.y1, result.deltaY)
        )
        MathStep(
            label = "الزاوية المختصرة θ",
            value = String.format(Locale.US, "arctg(ΔX / ΔY) = arctg(%.2f / %.2f) = %.4f°", result.deltaX, result.deltaY, result.theta)
        )
        MathStep(
            label = "حساب المسافة m",
            value = String.format(Locale.US, "ΔX / sin(θ) = %.2f / sin(%.2f°) = %.2f م", result.deltaX, result.theta, result.distance)
        )
        
        // إضافة توضيح الربع والسمت
        val azimuthCalc = when (result.quadrant) {
            com.example.my_cannon.data.model.Quadrant.FIRST -> "θ = %.2f°"
            com.example.my_cannon.data.model.Quadrant.SECOND -> "θ - 180° = %.2f° - 180° = %.2f°"
            com.example.my_cannon.data.model.Quadrant.THIRD -> "θ + 180° = %.2f° + 180° = %.2f°"
            com.example.my_cannon.data.model.Quadrant.FOURTH -> "θ - 360° = %.2f° - 360° = %.2f°"
        }
        
        MathStep(
            label = "حساب السمت النهائي",
            value = if (result.quadrant == com.example.my_cannon.data.model.Quadrant.FIRST) 
                String.format(Locale.US, azimuthCalc, result.theta)
            else
                String.format(Locale.US, azimuthCalc, result.theta, result.azimuth)
        )

        if (readings.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("قراءات التوجيه (قانون القراءة)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            readings.forEach { reading ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("نقطة العلام: ${reading.refName}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = String.format(Locale.US, "القانون: (AzT %s 30-00) - AzR", reading.operation),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.DarkGray
                        )
                        Text(
                            text = String.format(Locale.US, "التعويض: (%.0f %s 3000) - %.0f", reading.targetAzMil, reading.operation, reading.refAzMil),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("النتيجة:", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = String.format(Locale.US, "%.0f mil", reading.finalReading),
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResultCard(
    title: String,
    value: String,
    subValue: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(subValue, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun MathStep(label: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}
