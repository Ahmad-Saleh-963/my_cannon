@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.example.my_cannon.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.my_cannon.ui.viewmodel.CannonViewModel
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.MapViewportState
import com.mapbox.maps.extension.compose.annotation.IconImage
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.layers.generated.SymbolLayer
import com.mapbox.maps.extension.style.layers.getLayerAs
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location

@OptIn(MapboxExperimental::class)
@Composable
fun MapViewContainer(
    viewModel: CannonViewModel,
    mapViewportState: MapViewportState,
    locationPermissionGranted: Boolean,
    modifier: Modifier = Modifier
) {
    // الأيقونات التكتيكية الاحترافية
    val cannonBitmap = rememberIconBitmap(Icons.Default.GpsFixed, Color.Green)
    val targetBitmap = rememberIconBitmap(Icons.Default.TrackChanges, Color.Red)
    val refBitmap = rememberIconBitmap(Icons.Default.Flag, Color.Blue)

    // التركيز الفوري على الموقع عند أول ظهور للنقطة الزرقاء
    LaunchedEffect(locationPermissionGranted) {
        if (locationPermissionGranted) {
            mapViewportState.transitionToFollowPuckState()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
            style = {
                MapStyle(style = "mapbox://styles/ahmadsaleh963964/clydiahhj00o001nwggjtai7v")
            },
            compass = {
                Compass(
                    alignment = Alignment.TopStart,
                    contentPadding = PaddingValues(top = 48.dp, start = 16.dp),
                    fadeWhenFacingNorth = false
                )
            },
            onMapClickListener = { point ->
                viewModel.updatePointFromMap(point.latitude(), point.longitude())
                true
            }
        ) {
            MapEffect(locationPermissionGranted) { mapView ->
                mapView.mapboxMap.subscribeStyleLoaded {
                    mapView.mapboxMap.getStyle { style ->
                        try {
                            style.styleLayers.forEach { layer ->
                                style.getLayerAs<SymbolLayer>(layer.id)?.textField(
                                    com.mapbox.maps.extension.style.expressions.generated.Expression.coalesce(
                                        com.mapbox.maps.extension.style.expressions.generated.Expression.get("name_ar"),
                                        com.mapbox.maps.extension.style.expressions.generated.Expression.get("name")
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                mapView.location.updateSettings {
                    enabled = locationPermissionGranted
                    puckBearingEnabled = true
                    puckBearing = PuckBearing.HEADING
                    locationPuck = createDefault2DPuck(withBearing = true)
                    showAccuracyRing = true
                }
                
                // حفظ الموقع الأخير وحل مشكلة الانتقال الفوري
                var firstFix = true
                mapView.location.addOnIndicatorPositionChangedListener { point ->
                    if (firstFix && locationPermissionGranted) {
                        // إذا كان الفتح لأول مرة، نضبط الكاميرا فوراً بدون أنيميشن
                        if (viewModel.getLastLocation() == null) {
                            mapViewportState.setCameraOptions {
                                center(point)
                                zoom(14.0)
                            }
                        }
                        firstFix = false
                    }
                    viewModel.saveLastLocation(point.latitude(), point.longitude())
                }
            }
            // رسم المربط
            viewModel.cannonPos?.let { cannon ->
                PointAnnotation(
                    point = Point.fromLngLat(cannon.geoPoint.longitude, cannon.geoPoint.latitude)
                ) {
                    interactionsState.onLongClicked {
                        viewModel.openEditDialog(cannon)
                        true
                    }
                    iconImage = IconImage(cannonBitmap)
                    iconAnchor = IconAnchor.CENTER
                    iconSize = 1.2
                }
            }

            // رسم الأهداف والخطوط الواصلة
            viewModel.targets.forEach { target ->
                PointAnnotation(
                    point = Point.fromLngLat(target.geoPoint.longitude, target.geoPoint.latitude)
                ) {
                    interactionsState.onLongClicked {
                        viewModel.openEditDialog(target)
                        true
                    }
                    iconImage = IconImage(targetBitmap)
                    iconAnchor = IconAnchor.CENTER
                    iconSize = 1.4
                }

                viewModel.cannonPos?.let { cannon ->
                    PolylineAnnotation(
                        points = listOf(
                            Point.fromLngLat(cannon.geoPoint.longitude, cannon.geoPoint.latitude),
                            Point.fromLngLat(target.geoPoint.longitude, target.geoPoint.latitude)
                        )
                    ) {
                        lineColor = Color.Red
                        lineWidth = 3.0
                    }
                }
            }

            // رسم نقاط العلام
            viewModel.referencePoints.forEach { ref ->
                PointAnnotation(
                    point = Point.fromLngLat(ref.geoPoint.longitude, ref.geoPoint.latitude)
                ) {
                    interactionsState.onLongClicked {
                        viewModel.openEditDialog(ref)
                        true
                    }
                    iconImage = IconImage(refBitmap)
                    iconAnchor = IconAnchor.BOTTOM
                    iconSize = 1.0
                }
            }
        }
        CardinalDirectionsOverlay()
    }
}

@Composable
fun CardinalDirectionsOverlay() {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("N", Modifier.align(Alignment.TopCenter).padding(top = 40.dp), color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
        Text("S", Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp), color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
        Text("E", Modifier.align(Alignment.CenterEnd), color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
        Text("W", Modifier.align(Alignment.CenterStart), color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
    }
}

/**
 * وظيفة لتحويل أيقونات الـ Vector إلى Bitmap ليتمكن Mapbox من عرضها.
 */
@Composable
fun rememberIconBitmap(imageVector: ImageVector, color: Color): Bitmap {
    val density = LocalDensity.current
    val painter = rememberVectorPainter(imageVector)
    return remember(imageVector, color) {
        val size = 48.dp // مقاس الأيقونة
        val px = with(density) { size.toPx() }.toInt()
        val imageBitmap = ImageBitmap(px, px)
        val canvas = Canvas(imageBitmap)
        val drawScope = CanvasDrawScope()
        drawScope.draw(
            density = density,
            layoutDirection = LayoutDirection.Ltr,
            canvas = canvas,
            size = Size(px.toFloat(), px.toFloat())
        ) {
            with(painter) {
                draw(
                    size = Size(px.toFloat(), px.toFloat()),
                    colorFilter = ColorFilter.tint(color)
                )
            }
        }
        imageBitmap.asAndroidBitmap()
    }
}
