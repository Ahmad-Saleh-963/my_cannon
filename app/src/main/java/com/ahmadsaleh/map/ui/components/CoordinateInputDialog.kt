package com.ahmadsaleh.map.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ahmadsaleh.map.data.model.GeoPoint
import com.ahmadsaleh.map.data.model.UtmPoint
import java.util.Locale

@Composable
fun CoordinateInputDialog(
    title: String,
    initialGeo: GeoPoint,
    initialUtm: UtmPoint,
    onConfirm: (GeoPoint?, UtmPoint?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    // Lat/Long State
    var latText by remember { mutableStateOf(String.format(Locale.US, "%.6f", initialGeo.latitude)) }
    var lonText by remember { mutableStateOf(String.format(Locale.US, "%.6f", initialGeo.longitude)) }
    
    // UTM State
    var eastingText by remember { mutableStateOf(String.format(Locale.US, "%.1f", initialUtm.easting)) }
    var northingText by remember { mutableStateOf(String.format(Locale.US, "%.1f", initialUtm.northing)) }
    var zoneText by remember { mutableStateOf(initialUtm.zoneNumber.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "تعديل إحداثيات $title", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                        Text("جغرافي (Lat/Lng)", modifier = Modifier.padding(12.dp))
                    }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                        Text("متري (UTM)", modifier = Modifier.padding(12.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                if (selectedTab == 0) {
                    // Geographic Input
                    CoordinateTextField(label = "خط العرض (Latitude)", value = latText, onValueChange = { latText = it })
                    Spacer(modifier = Modifier.height(8.dp))
                    CoordinateTextField(label = "خط الطول (Longitude)", value = lonText, onValueChange = { lonText = it })
                } else {
                    // UTM Input
                    CoordinateTextField(label = "الشرق (Easting - X)", value = eastingText, onValueChange = { eastingText = it })
                    Spacer(modifier = Modifier.height(8.dp))
                    CoordinateTextField(label = "الشمال (Northing - Y)", value = northingText, onValueChange = { northingText = it })
                    Spacer(modifier = Modifier.height(8.dp))
                    CoordinateTextField(label = "نطاق UTM (Zone)", value = zoneText, onValueChange = { zoneText = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedTab == 0) {
                        val lat = latText.toDoubleOrNull()
                        val lon = lonText.toDoubleOrNull()
                        if (lat != null && lon != null) {
                            onConfirm(GeoPoint(lat, lon), null)
                        }
                    } else {
                        val easting = eastingText.toDoubleOrNull()
                        val northing = northingText.toDoubleOrNull()
                        val zone = zoneText.toIntOrNull() ?: 36
                        if (easting != null && northing != null) {
                            onConfirm(null, UtmPoint(easting, northing, zone))
                        }
                    }
                }
            ) {
                Text("حفظ التعديلات")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
fun CoordinateTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true
    )
}
