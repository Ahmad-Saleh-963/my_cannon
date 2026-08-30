package com.ahmadsaleh.map.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ahmadsaleh.map.data.model.CalculationResult
import com.ahmadsaleh.map.data.model.Quadrant
import com.ahmadsaleh.map.data.model.ReadingResult
import java.util.Locale

@Composable
fun ResultsDashboard(
    result: CalculationResult?,
    pointName: String = "الهدف",
    showTitle: Boolean = true,
    readings: List<ReadingResult> = emptyList(),
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    if (result == null) {
        if (showTitle) {
            Box(modifier = modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "الرجاء تحديد المربط و $pointName لبدء الحسابات",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

        // 1. النتائج الأساسية (السمت والمسافة) - استجابة للشاشات الصغيرة
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isSmallScreen = maxWidth < 360.dp
            if (isSmallScreen) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResultCard(
                        title = "السمت (Azimuth)",
                        value = String.format(Locale.US, "%.2f°", result.normalizedAzimuth),
                        subValue = String.format(
                            Locale.US,
                            "%.0f مليم (6000) | %.0f مليم (6400)",
                            result.azimuthMils6000,
                            result.azimuthMils6400
                        ),
                        icon = Icons.Default.CompassCalibration,
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                    ResultCard(
                        title = "المسافة (Range)",
                        value = String.format(Locale.US, "%.2f م", result.distance),
                        subValue = String.format(Locale.US, "%.3f كم", result.distance / 1000.0),
                        icon = Icons.Default.Calculate,
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ResultCard(
                        title = "السمت (Azimuth)",
                        value = String.format(Locale.US, "%.2f°", result.normalizedAzimuth),
                        subValue = String.format(
                            Locale.US,
                            "%.0f مليم (6000)",
                            result.azimuthMils6000
                        ),
                        icon = Icons.Default.CompassCalibration,
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                    ResultCard(
                        title = "المسافة (Range)",
                        value = String.format(Locale.US, "%.2f م", result.distance),
                        subValue = String.format(Locale.US, "%.3f كم", result.distance / 1000.0),
                        icon = Icons.Default.Calculate,
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                }
            }
        }

        // 2. تفاصيل الربع العسكري
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = result.quadrant.nameAr,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = result.quadrant.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Text(
                    text = "القانون: ${result.quadrant.formulaAr}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // 3. الخطوات الرياضية التفصيلية المنسقة دقيقاً
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Icon(
                Icons.Default.Functions,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "الخطوات الرياضية بالتفصيل",
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // خطوة 1: فروق الإحداثيات
        MathStepCard(
            stepNumber = "1",
            title = "حساب فروق الإحداثيات (ΔX, ΔY)",
            description = "طرح إحداثيات المربط من إحداثيات الهدف بالقيمة المطلقة",
            equations = listOf(
                String.format(Locale.US, "ΔX = | X₂ - X₁ | = | %.2f - %.2f | = %.2f m", result.x2, result.x1, result.deltaX),
                String.format(Locale.US, "ΔY = | Y₂ - Y₁ | = | %.2f - %.2f | = %.2f m", result.y2, result.y1, result.deltaY)
            )
        )

        // خطوة 2: الزاوية المختصرة θ
        MathStepCard(
            stepNumber = "2",
            title = "حساب الزاوية المختصرة (θ)",
            description = "تطبيق قانون الظل: tan(θ) = ΔX / ΔY",
            equations = listOf(
                String.format(
                    Locale.US,
                    "θ = tan⁻¹(ΔX / ΔY) = tan⁻¹(%.2f / %.2f) = %.4f°",
                    result.deltaX,
                    result.deltaY,
                    result.theta
                )
            )
        )

        // خطوة 3: حساب السمت بناءً على الربع
        val (azimuthFormulaText, azimuthSubstitutionText) = when (result.quadrant) {
            Quadrant.FIRST -> Pair(
                "Azimuth = θ",
                String.format(Locale.US, "Azimuth = %.4f°", result.theta)
            )
            Quadrant.SECOND -> Pair(
                "Azimuth = 180° - θ",
                String.format(Locale.US, "Azimuth = 180° - %.4f° = %.4f°", result.theta, result.normalizedAzimuth)
            )
            Quadrant.THIRD -> Pair(
                "Azimuth = 180° + θ",
                String.format(Locale.US, "Azimuth = 180° + %.4f° = %.4f°", result.theta, result.normalizedAzimuth)
            )
            Quadrant.FOURTH -> Pair(
                "Azimuth = 360° - θ",
                String.format(Locale.US, "Azimuth = 360° - %.4f° = %.4f°", result.theta, result.normalizedAzimuth)
            )
        }

        MathStepCard(
            stepNumber = "3",
            title = "حساب السمت النهائي (${result.quadrant.nameAr})",
            description = "تطبيق قانون الربع المحدد لحساب السمت الجغرافي النهائي",
            equations = listOf(
                azimuthFormulaText,
                azimuthSubstitutionText
            )
        )

        // خطوة 4: تحويل السمت إلى مليم عسكري
        MathStepCard(
            stepNumber = "4",
            title = "تحويل السمت إلى المليم العسكري",
            description = "التحويل من درجات إلى مليم (6000 مليم عسكري / 6400 مليم ناتو)",
            equations = listOf(
                String.format(
                    Locale.US,
                    "Mil (6000) = (%.2f° × 6000) / 360° = %.0f ₥",
                    result.normalizedAzimuth,
                    result.azimuthMils6000
                ),
                String.format(
                    Locale.US,
                    "Mil (6400) = (%.2f° × 6400) / 360° = %.0f ₥",
                    result.normalizedAzimuth,
                    result.azimuthMils6400
                )
            )
        )

        // خطوة 5: حساب المسافة الوترية المباشرة
        MathStepCard(
            stepNumber = "5",
            title = "حساب المسافة الوترية المباشرة (D)",
            description = "تطبيق فيثاغورس: D = √(ΔX² + ΔY²)",
            equations = listOf(
                String.format(
                    Locale.US,
                    "D = √((%.2f)² + (%.2f)²) = %.2f m",
                    result.deltaX,
                    result.deltaY,
                    result.distance
                )
            )
        )

        // 4. قراءات التوجيه (في حال وجود نقاط علام)
        if (readings.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "قراءات التوجيه (قانون القراءة)",
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            readings.forEach { reading ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "نقطة العلام: ${reading.refName}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = String.format(Locale.US, "Formula: (AzT %s 30-00) - AzR", reading.operation),
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = String.format(
                                            Locale.US,
                                            "Calc: (%.0f %s 3000) - %.0f = %.0f ₥",
                                            reading.targetAzMil,
                                            reading.operation,
                                            reading.refAzMil,
                                            reading.result
                                        ),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "النتيجة النهائية للقراءة:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.0f مليم", reading.finalReading),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subValue,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MathStepCard(
    stepNumber: String,
    title: String,
    description: String? = null,
    equations: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stepNumber,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (description != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    equations.forEach { eq ->
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = eq,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
