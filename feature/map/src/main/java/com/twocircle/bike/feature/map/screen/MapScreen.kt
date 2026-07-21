package com.twocircle.bike.feature.map.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.LocationSearching
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twocircle.bike.designsystem.components.FabCluster
import com.twocircle.bike.feature.poi.model.PoiCategory

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twocircle.bike.designsystem.R
import com.twocircle.bike.designsystem.l10n.messageRes
import com.twocircle.bike.feature.map.focus.MapFocusController
import com.twocircle.bike.feature.map.model.MapUiState
import com.twocircle.bike.feature.map.model.MapViewModel
import com.twocircle.bike.feature.map.model.MyLocationViewModel
import com.twocircle.bike.feature.map.view.BikeMap
import com.twocircle.bike.feature.poi.model.Poi
import com.twocircle.bike.feature.poi.screen.PoiBottomSheet
import com.twocircle.bike.feature.poi.screen.PoiDetailSheet
import com.twocircle.bike.feature.poi.screen.PoiViewModel
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap

/**
 * Map screen — the app's home tab.
 *
 * Branches on [MapUiState]:
 * - NoRegion → prompt to download a region (the regions screen is reached via bottom nav).
 * - Loading → spinner.
 * - Ready → render [BikeMap] with the generated style JSON + POI/search/start-ride FABs.
 * - Error → show the typed failure with a retry button.
 *
 * POI flow: a third FAB opens a modal bottom sheet with category chips; the same
 * [PoiViewModel] drives the markers, which are rendered via [PoiMarkerLayer] attached
 * inside [BikeMap]'s onMapReady hook.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onOpenSearch: () -> Unit = {},
    onOpenRide: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onLongPressAt: (lat: Double, lon: Double) -> Unit = { _, _ -> },
    onAddPoiToRoute: (Poi) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = hiltViewModel(),
    poiViewModel: PoiViewModel = hiltViewModel(),
    myLocationViewModel: MyLocationViewModel = hiltViewModel(),
    focusController: MapFocusController = hiltViewModel<FocusViewModel>().focusController,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val poiState by poiViewModel.state.collectAsStateWithLifecycle()
    val myLocation by myLocationViewModel.location.collectAsStateWithLifecycle()
    val following by myLocationViewModel.following.collectAsStateWithLifecycle()
    val poiSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val context = LocalContext.current
    // Реактивное отслеживание темы через ThemeState — обновляется мгновенно
    // при смене в Settings (через StateFlow, без recreate Activity).
    val isDark by com.twocircle.bike.designsystem.theme.ThemeState.isDark.collectAsStateWithLifecycle()

    LaunchedEffect(isDark) {
        viewModel.rebuildStyle(isDark)
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val s = state) {
            is MapUiState.NoRegion -> NoRegionPrompt(modifier = Modifier.align(Alignment.Center))
            MapUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            is MapUiState.Ready -> {
                // Состояние зума и направления карты для кастомных компаса и масштабной шкалы
                var mapZoom by remember { mutableStateOf(s.initialCamera.zoom) }
                var mapBearing by remember { mutableStateOf(s.initialCamera.bearing) }

                // Ссылка на слои наложения и карту
                val overlayRef = remember { object {
                    var trackLayer: com.twocircle.bike.feature.map.view.TrackOverlayLayer? = null
                    var poiLayer: com.twocircle.bike.feature.map.view.PoiMarkerLayer? = null
                    var map: MapLibreMap? = null
                } }
                androidx.compose.runtime.DisposableEffect(s.styleJson) {
                    onDispose {
                        overlayRef.trackLayer?.stop()
                        overlayRef.poiLayer?.stop()
                        overlayRef.map = null
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    BikeMap(
                        styleJson = s.styleJson,
                        initialCamera = s.initialCamera,
                        modifier = Modifier.fillMaxSize(),
                        onMapReady = { map ->
                            overlayRef.trackLayer?.stop()
                            overlayRef.poiLayer?.stop()
                            overlayRef.map = map

                            // Отключаем встроенный компас и активируем вращение жестами
                            val ui = map.uiSettings
                            ui.isCompassEnabled = false
                            ui.isRotateGesturesEnabled = true

                            // Отслеживаем изменения положения камеры для компаса и масштаба
                            map.addOnCameraMoveListener {
                                mapZoom = map.cameraPosition.zoom
                                mapBearing = map.cameraPosition.bearing
                            }

                            // Инициализация слоя трека
                            val trackLayer = com.twocircle.bike.feature.map.view.TrackOverlayLayer(
                                map = map,
                                overlay = viewModel.trackOverlay,
                            )
                            trackLayer.start()
                            overlayRef.trackLayer = trackLayer

                            // Инициализация слоя POI
                            val poiLayer = com.twocircle.bike.feature.map.view.PoiMarkerLayer(map)
                            poiLayer.start()
                            overlayRef.poiLayer = poiLayer

                            // Долгое нажатие -> добавление путевой точки
                            map.addOnMapLongClickListener { point ->
                                onLongPressAt(point.latitude, point.longitude)
                                false
                            }

                            // Нажатие на маркер POI
                            map.addOnMapClickListener { point ->
                                val screenPoint = map.projection.toScreenLocation(point)
                                val tolerance = 10f
                                val hitBox = android.graphics.RectF(
                                    screenPoint.x - tolerance,
                                    screenPoint.y - tolerance,
                                    screenPoint.x + tolerance,
                                    screenPoint.y + tolerance,
                                )
                                val hits = map.queryRenderedFeatures(hitBox, "poi-layer")
                                if (hits.isNotEmpty()) {
                                    val id = hits[0].properties()?.get("id")?.asLong
                                    if (id != null) {
                                        val poi = poiState.pois.firstOrNull { it.id == id }
                                        if (poi != null) {
                                            poiViewModel.selectPoi(poi)
                                            return@addOnMapClickListener true
                                        }
                                    }
                                }
                                false
                            }

                            pushBounds(map, poiViewModel)
                            map.addOnCameraIdleListener {
                                pushBounds(map, poiViewModel)
                                if (myLocationViewModel.following.value) {
                                    myLocationViewModel.stopFollowing()
                                }
                            }
                        },
                    )

                    // Реактивно передаем список POI на карту
                    androidx.compose.runtime.LaunchedEffect(poiState.pois) {
                        overlayRef.poiLayer?.setPois(poiState.pois)
                    }

                    // Перемещение камеры при поиске
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        focusController.focusTarget.collect { target ->
                            var map: MapLibreMap? = overlayRef.map
                            var attempts = 0
                            while (map == null && attempts < 10) {
                                kotlinx.coroutines.delay(100)
                                map = overlayRef.map
                                attempts++
                            }
                            if (map == null) return@collect
                            map.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    org.maplibre.android.geometry.LatLng(target.lat, target.lon),
                                    12.0,
                                ),
                                800,
                            )
                        }
                    }

                    // Следование за геопозицией пользователя
                    androidx.compose.runtime.LaunchedEffect(myLocation, following) {
                        val loc = myLocation ?: return@LaunchedEffect
                        val map = overlayRef.map ?: return@LaunchedEffect
                        if (following) {
                            map.easeCamera(
                                CameraUpdateFactory.newLatLng(
                                    org.maplibre.android.geometry.LatLng(loc.lat, loc.lon),
                                ),
                                600,
                            )
                        }
                    }

                    // 1. MapTopBar (Верхняя панель оверлея с поиском, меню и компасом)
                    MapTopBar(
                        regionName = s.regionName,
                        bearing = mapBearing,
                        onMenuClick = onOpenSettings,
                        onSearchClick = onOpenSearch,
                        onResetBearing = {
                            overlayRef.map?.let { map ->
                                val targetPos = org.maplibre.android.camera.CameraPosition.Builder(map.cameraPosition)
                                    .bearing(0.0)
                                    .build()
                                map.animateCamera(CameraUpdateFactory.newCameraPosition(targetPos), 400)
                            }
                        },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )

                    // 2. PoiChipsBar (Горизонтальные чипы категорий POI)
                    PoiChipsBar(
                        selectedCategories = poiState.selectedCategories,
                        onToggleCategory = { poiViewModel.toggleCategory(it) },
                        onClearAll = {
                            poiState.selectedCategories.forEach { poiViewModel.toggleCategory(it) }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 96.dp)
                    )

                    // 3. ScaleBar (Масштабная линейка в левом нижнем углу)
                    ScaleBar(
                        zoom = mapZoom,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 80.dp)
                    )

                    // 4. Кнопка центрирования геопозиции (MyLocation FAB)
                    FloatingActionButton(
                        onClick = { myLocationViewModel.toggleFollowing() },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 116.dp)
                            .size(48.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = if (following) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurface
                    ) {
                        Icon(
                            imageVector = if (following) Icons.Outlined.MyLocation
                                           else Icons.Outlined.LocationSearching,
                            contentDescription = stringResource(R.string.map_cd_my_location)
                        )
                    }

                    // 5. Кластер действий FAB (Start ride / Navigate / Add waypoint)
                    FabCluster(
                        onStartRide = onOpenRide,
                        onNavigate = {
                            // Клик по навигации направляет на вкладку планировщика маршрутов
                        },
                        onAddWaypoint = {
                            overlayRef.map?.cameraPosition?.target?.let { target ->
                                onLongPressAt(target.latitude, target.longitude)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 80.dp)
                    )
                }
            }
            is MapUiState.Error -> ErrorState(
                failure = s.failure,
                onRetry = { },
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }

    // Детальная карточка POI, отображаемая при клике на маркер
    poiState.selectedPoi?.let { poi ->
        ModalBottomSheet(
            onDismissRequest = { poiViewModel.clearSelection() },
        ) {
            PoiDetailSheet(
                poi = poi,
                onAddToRoute = { p ->
                    poiViewModel.clearSelection()
                    onAddPoiToRoute(p)
                },
                onClose = { poiViewModel.clearSelection() },
            )
        }
    }
}

/**
 * Read the map's projected visible region and feed it to [PoiViewModel] so the next
 * POI query is constrained to what's on screen.
 */
private fun pushBounds(map: MapLibreMap, poiViewModel: PoiViewModel) {
    val bounds = map.projection.visibleRegion.latLngBounds
    poiViewModel.setBounds(
        minLat = bounds.latitudeSouth,
        minLon = bounds.longitudeWest,
        maxLat = bounds.latitudeNorth,
        maxLon = bounds.longitudeEast,
    )
}

@Composable
private fun NoRegionPrompt(modifier: Modifier = Modifier) {
    com.twocircle.bike.designsystem.components.EmptyState(
        illustration = com.twocircle.bike.designsystem.R.drawable.empty_no_region,
        title = stringResource(R.string.map_no_region_title),
        body = stringResource(R.string.map_no_region_body),
        modifier = modifier,
    )
}

@Composable
private fun ErrorState(
    failure: com.twocircle.bike.common.outcome.Failure,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.map_error_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(failure.messageRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) {
            Text(stringResource(R.string.action_retry))
        }
    }
}

@Composable
private fun MapTopBar(
    regionName: String,
    bearing: Double,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onResetBearing: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Outlined.Menu,
                    contentDescription = stringResource(R.string.map_cd_profile)
                )
            }
            Text(
                text = regionName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.map_cd_search)
                )
            }
            IconButton(
                onClick = onResetBearing,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Explore,
                    contentDescription = stringResource(R.string.map_cd_reset_bearing),
                    modifier = Modifier.rotate(-bearing.toFloat()),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PoiChipsBar(
    selectedCategories: Set<PoiCategory>,
    onToggleCategory: (PoiCategory) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        item {
            FilterChip(
                selected = selectedCategories.isEmpty(),
                onClick = onClearAll,
                label = { Text(stringResource(R.string.map_poi_chip_all)) }
            )
        }
        items(PoiCategory.entries) { cat ->
            val selected = cat in selectedCategories
            FilterChip(
                selected = selected,
                onClick = { onToggleCategory(cat) },
                label = { Text(stringResource(cat.displayNameRes)) }
            )
        }
    }
}

@Composable
private fun ScaleBar(
    zoom: Double,
    modifier: Modifier = Modifier
) {
    val meterUnit = stringResource(com.twocircle.bike.designsystem.R.string.format_unit_meter)
    val kmUnit = stringResource(com.twocircle.bike.designsystem.R.string.format_unit_kilometer)

    val (rawValue, widthDp) = remember(zoom) {
        when {
            zoom >= 16 -> 50 to 60.dp
            zoom >= 15 -> 100 to 60.dp
            zoom >= 14 -> 200 to 60.dp
            zoom >= 13 -> 500 to 75.dp
            zoom >= 12 -> 1000 to 60.dp
            zoom >= 11 -> 2000 to 60.dp
            zoom >= 10 -> 5000 to 75.dp
            zoom >= 9 -> 10000 to 60.dp
            zoom >= 8 -> 20000 to 60.dp
            zoom >= 7 -> 50000 to 75.dp
            zoom >= 6 -> 100000 to 60.dp
            zoom >= 5 -> 200000 to 60.dp
            else -> 500000 to 75.dp
        }
    }
    val text = if (rawValue >= 1000) "${rawValue / 1000} $kmUnit" else "$rawValue $meterUnit"

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
        )
        Canvas(
            modifier = Modifier
                .width(widthDp)
                .height(6.dp)
        ) {
            val strokeWidthPx = 2.dp.toPx()
            val heightPx = size.height
            val widthPx = size.width
            val color = Color(0xFFE0E4E8) // светлый цвет для видимости на тёмной карте

            drawLine(
                color = color,
                start = Offset(0f, heightPx / 2),
                end = Offset(widthPx, heightPx / 2),
                strokeWidth = strokeWidthPx
            )
            drawLine(
                color = color,
                start = Offset(0f, 0f),
                end = Offset(0f, heightPx),
                strokeWidth = strokeWidthPx
            )
            drawLine(
                color = color,
                start = Offset(widthPx, 0f),
                end = Offset(widthPx, heightPx),
                strokeWidth = strokeWidthPx
            )
        }
    }
}
