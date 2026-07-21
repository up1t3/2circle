# Sprint 4 — Анимации и полировка

> ТЗ для UI/UX дизайнера. Все строки — на русском в комментариях, stringResource в коде.

---

## 1. SkeletonLoader (shimmer плейсхолдер)

**Файл:** `core/designsystem/.../components/SkeletonLoader.kt` (НОВЫЙ)

Компонент для loading-состояний списков (Feed, Tracks, Saved Routes):

```kotlin
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -300f,
        targetValue = 300f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerX",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    start = Offset(x, 0f),
                    end = Offset(x + 300f, 0f),
                ),
            ),
    )
}
```

**Использование:**

```kotlin
// Skeleton строка для списка поездок:
@Composable
fun RideSkeletonRow() {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = spacedBy(12.dp)) {
        ShimmerBox(Modifier.size(80.dp), cornerRadius = 12.dp)  // mini map
        Column(Modifier.weight(1f), verticalArrangement = spacedBy(8.dp)) {
            ShimmerBox(Modifier.fillMaxWidth(0.6f).height(16.dp))  // title
            ShimmerBox(Modifier.fillMaxWidth(0.4f).height(12.dp))  // stats
        }
    }
}

// Skeleton карточка для Feed:
@Composable
fun FeedCardSkeleton() {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = spacedBy(12.dp)) {
        Row(horizontalArrangement = spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ShimmerBox(Modifier.size(32.dp), cornerRadius = 16.dp)  // avatar
            ShimmerBox(Modifier.fillMaxWidth(0.4f).height(14.dp))  // name
        }
        ShimmerBox(Modifier.fillMaxWidth().height(160.dp), cornerRadius = 12.dp)  // map preview
        ShimmerBox(Modifier.fillMaxWidth(0.7f).height(14.dp))  // title
        ShimmerBox(Modifier.fillMaxWidth(0.5f).height(12.dp))  // stats
    }
}
```

**Где применять:**
- `FeedScreen` — когда `feedState` == Loading → показать 3× `FeedCardSkeleton()`
- `TracksScreen` — когда `listState` == Loading → показать 4× `RideSkeletonRow()`
- `SavedRoutesScreen` — при загрузке → 3× skeleton rows

---

## 2. Splash v2 (анимация brand glyph)

**Файл:** `app/src/main/java/.../SplashAnimated.kt` (НОВЫЙ) или интегрировать в существующий splash

Анимация появления brand glyph (два круга):

```kotlin
@Composable
fun AnimatedSplash(onAnimationComplete: () -> Unit) {
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Фаза 1: circles scale from 0 to 1 with bounce (500ms)
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        }
        // Фаза 2: fade in (300ms, параллельно)
        launch {
            alpha.animateTo(1f, tween(300))
        }
        // Ждать завершения + пауза 500ms
        delay(800)
        onAnimationComplete()
    }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Brand glyph — два круга с масштабированием
            Box(
                Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        this.alpha = alpha.value
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(120.dp)) {
                    // Левый круг (зелёный)
                    drawCircle(
                        color = Color(0xFF4CAF50),
                        radius = 28.dp.toPx(),
                        center = Offset(size.width * 0.35f, size.height * 0.5f),
                        style = Stroke(width = 4.dp.toPx()),
                    )
                    // Правый круг (синий)
                    drawCircle(
                        color = Color(0xFF2C5F7E),
                        radius = 28.dp.toPx(),
                        center = Offset(size.width * 0.65f, size.height * 0.5f),
                        style = Stroke(width = 4.dp.toPx()),
                    )
                    // Соединительная линия
                    drawLine(
                        color = Color(0xFFE0E4E8),
                        start = Offset(size.width * 0.38f, size.height * 0.5f),
                        end = Offset(size.width * 0.62f, size.height * 0.5f),
                        strokeWidth = 3.dp.toPx(),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            // Название — fade in после кругов
            Text(
                text = "2circle",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer { this.alpha = alpha.value },
            )
            Text(
                text = stringResource(R.string.splash_tagline), // "Bike touring, offline"
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.graphicsLayer { this.alpha = alpha.value },
            )
        }
    }
}
```

**Строка `splash_tagline`:** добавить в strings.xml (en/ru/es):
- en: "Bike touring, offline"
- ru: "Велопутешествия, офлайн"
- es: "Ciclismo, sin conexión"

**Интеграция:** в `MainActivity` — показывать AnimatedSplash первые ~1.3 секунды, затем переход к Onboarding/Map.

---

## 3. Screen Transitions

**Файл:** `app/src/main/java/.../nav/TwoCircleNavHost.kt` (MODIFY)

Добавить кастомные transitionSpec для навигации:

```kotlin
NavHost(
    navController = nav,
    startDestination = TopLevel.MAP.route,
    modifier = Modifier.padding(padding),
    // Глобальный transition: slide для forward/back
    enterTransition = { slideInHorizontally(
        initialOffsetX = { it / 3 },
        animationSpec = tween(250, easing = FastOutSlowInEasing),
    ) + fadeIn(tween(250)) },
    exitTransition = { slideOutHorizontally(
        targetOffsetX = { -it / 3 },
        animationSpec = tween(200, easing = FastOutSlowInEasing),
    ) + fadeOut(tween(200)) },
    popEnterTransition = { slideInHorizontally(
        initialOffsetX = { -it / 3 },
        animationSpec = tween(250, easing = FastOutSlowInEasing),
    ) + fadeIn(tween(250)) },
    popExitTransition = { slideOutHorizontally(
        targetOffsetX = { it / 3 },
        animationSpec = tween(200, easing = FastOutSlowInEasing),
    ) + fadeOut(tween(200)) },
) {
    // Для bottom-nav вкладок — без slide (просто fade):
    composable(TopLevel.MAP.route,
        enterTransition = { fadeIn(tween(150)) },
        exitTransition = { fadeOut(tween(150)) },
    ) { ... }
}
```

**Правило:** 
- Bottom-nav переходы (Map→Routes→Tracks→Feed→Profile): `fadeIn(150ms)` / `fadeOut(150ms)` — без slide
- Detail переходы (Map→Search, Tracks→TrackDetail, Feed→Profile): `slideHorizontally` — чувство глубины
- Modal (BottomSheet): системная анимация (не настраивать)

---

## 4. Heart Burst (kudos анимация)

**Файл:** `feature/social/.../FeedCard.kt` (MODIFY)

Улучшить существующую kudos-кнопку:

```kotlin
@Composable
fun KudosButton(
    count: Int,
    isGiven: Boolean,
    onToggle: () -> Unit,
) {
    // Burst particles при первом тапе
    val burstScale = remember { Animatable(0f) }
    val burstAlpha = remember { Animatable(0f) }

    // Trigger burst animation when kudos goes from false→true
    LaunchedEffect(isGiven) {
        if (isGiven) {
            launch {
                burstScale.snapTo(0f)
                burstAlpha.snapTo(1f)
                launch { burstScale.animateTo(2.5f, tween(400, easing = FastOutSlowInEasing)) }
                launch { burstAlpha.animateTo(0f, tween(400)) }
            }
        }
    }

    Box(contentAlignment = Alignment.Center) {
        // Burst ring (за иконкой)
        if (burstScale.value > 0.01f) {
            Canvas(
                Modifier
                    .size(40.dp)
                    .graphicsLayer {
                        scaleX = burstScale.value
                        scaleY = burstScale.value
                        alpha = burstAlpha.value
                    },
            ) {
                drawCircle(
                    color = Color(0xFFE91E63).copy(alpha = 0.3f),
                    radius = 16.dp.toPx(),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        // Иконка + счётчик
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (isGiven) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isGiven) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.scale(
                        // Bounce при тапе: 1.0 → 1.3 → 1.0
                        remember(isGiven) {
                            if (isGiven) 1.15f else 1.0f
                        }
                    ),
                )
            }
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (isGiven) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}
```

---

## 5. FAB Stagger (FabCluster)

**Файл:** `core/designsystem/.../components/FabCluster.kt` (MODIFY)

Добавить staggered startDelay между sub-FABs (50ms между каждым):

```kotlin
// Текущая реализация анимирует все 3 sub-FAB одновременно.
// Добавить задержку:

val startRideOffset by transition.animateFloat(
    targetValueByState = { expanded -> if (expanded) -80f else 0f },
    animationSpec = tween(durationMillis = 250, delayMillis = 0, easing = FastOutSlowInEasing),
    label = "СтартСдвиг",
)
val navigateOffsetY by transition.animateFloat(
    targetValueByState = { expanded -> if (expanded) -60f else 0f },
    animationSpec = tween(durationMillis = 220, delayMillis = 50, easing = FastOutSlowInEasing), // +50ms
    label = "НавигацияСдвигY",
)
val waypointOffsetX by transition.animateFloat(
    targetValueByState = { expanded -> if (expanded) -60f else 0f },
    animationSpec = tween(durationMillis = 240, delayMillis = 100, easing = FastOutSlowInEasing), // +100ms
    label = "ТочкаСдвигX",
)
```

---

## 6. Pull-to-refresh

**Файл:** `feature/social/.../FeedScreen.kt` (MODIFY) + `feature/tracks/.../TracksScreen.kt` (MODIFY)

Добавить Material 3 PullToRefresh:

```kotlin
val refreshScope = rememberCoroutineScope()
var refreshing by remember { mutableStateOf(false) }

fun refresh() {
    refreshing = true
    refreshScope.launch {
        // Эмуляция загрузки
        delay(1500)
        refreshing = false
    }
}

PullToRefreshBox(
    isRefreshing = refreshing,
    onRefresh = ::refresh,
    modifier = modifier,
) {
    LazyColumn { ... }
}
```

**Зависимость:** Material 3 PullToRefresh уже включён в compose-material3 (BOM 2024.12.01).

---

## 7. Дополнительная полировка

### 7.1 Empty state иллюстрации

Заменить текстовые empty states на иллюстрации + текст:

| Экран | Иллюстрация | Текст |
|---|---|---|
| Tracks (пусто) | Велосипедист на горизонте (vector) | "Поездок пока нет" |
| Routes (пусто) | Пустая карта с пунктиром (vector) | "Добавьте точки для маршрута" |
| Feed (пусто) | Лента с плюсиком (vector) | "Подпишитесь на райдеров" |
| Saved Routes (пусто) | Маршрут с закладкой (vector) | "Нет сохранённых маршрутов" |

Иллюстрации: vector drawables (XML), не растровые. Размер 200×120dp.

### 7.2 Ripple effects

Все кнопки уже используют Material 3 ripple (стандартный). Проверить что:
- FAB cluster: ripple на каждом sub-FAB
- FeedCard kudos: ripple на IconButton
- Settings rows: ripple на Row

### 7.3 Haptic feedback

Добавить тактильную отдачу на ключевых действиях:

```kotlin
val haptics = LocalHapticFeedback.current

// FAB expand:
haptics.performHapticFeedback(HapticFeedbackType.LongPress)

// Kudos tap:
haptics.performHapticFeedback(HapticFeedbackType.LongPress)

// Waypoint drag start:
haptics.performHapticFeedback(HapticFeedbackType.LongPress)

// POI marker tap:
haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
```

---

## Верификация

1. `./gradlew assembleDebug` — компиляция
2. Проверить визуально: splash анимация → shimmer → heart burst → transitions
3. Проверить dark/light theme для всех новых компонентов
4. Проверить что shimmer обновляется при смене темы

## Порядок выполнения (рекомендация)

1. **SkeletonLoader** — самый простой, высокая ценность
2. **Splash v2** — визуальный wow-эффект
3. **Screen transitions** — улучшение навигации
4. **Heart burst + haptics** — полировка ленты
5. **FAB stagger** — мелочь
6. **Pull-to-refresh** — UX Feed/Tracks
