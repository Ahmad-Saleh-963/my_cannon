package com.ahmadsaleh.map.data.model

enum class Quadrant(
    val nameAr: String,
    val description: String,
    val formulaAr: String
) {
    FIRST(
        nameAr = "الربع الأول",
        description = "تزيد X وتزيد Y (أعلى اليمين)",
        formulaAr = "السمت = θ"
    ),
    SECOND(
        nameAr = "الربع الثاني",
        description = "تزيد X وتنقص Y (أسفل اليمين)",
        formulaAr = "السمت = 180° - θ"
    ),
    THIRD(
        nameAr = "الربع الثالث",
        description = "تنقص X وتنقص Y (أسفل اليسار)",
        formulaAr = "السمت = 180° + θ"
    ),
    FOURTH(
        nameAr = "الربع الرابع",
        description = "تنقص X وتزيد Y (أعلى اليسار)",
        formulaAr = "السمت = 360° - θ"
    )
}
