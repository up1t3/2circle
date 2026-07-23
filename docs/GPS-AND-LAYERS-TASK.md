# Задание кодеру: GPS/ГЛОНАСС + наложения слоёв на карте

## Контекст

После исправления карты Урала нужно проверить и починить GPS-функциональность и визуальные наложения. Аудит выявил **3 блокирующих бага** (GPS не работает без permission flow) и **несколько косметических z-order проблем**. Этот документ — точное ТЗ для кодера, под контролем/ревью ведущего.

**Ветка:** создать `fix/gps-and-layers` от `main`.
**Сборка после фиксов:** пересобрать APK, bump version 0.8.2, задеплоить (ведущий ревьюит перед деплоем).

---

## ЧАСТЬ 1 — GPS/ГЛОНАСС (3 блокера, обязательно)

### Задача 1.1: Runtime-permission flow на локацию

**Проблема:** `LocationPermissionGate` написан, но нигде не вызывается. App никогда не спрашивает «Разрешить доступ к местоположению» → `getCurrentLocation()`/`requestLocationUpdates()` падают в `SecurityException` или возвращают null.

**Файлы:**
- `app/src/main/java/com/twocircle/bike/permissions/LocationPermissionGate.kt` — существующий composable (запрашивает только `ACCESS_FINE_LOCATION`)
- `app/src/main/java/com/twocircle/bike/MainActivity.kt` — точка интеграции
- `app/src/main/java/com/twocircle/bike/permissions/PermissionRequester.kt` — `locationPermissionState()`

**Что сделать:**
1. Обернуть главный Composable-контент в `LocationPermissionGate` так, чтобы при первом запуске (или когда permission не granted) показывался системный диалог `RequestMultiplePermissions` для `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`.
2. В `LocationPermissionGate.kt` добавить `ACCESS_COARSE_LOCATION` в `launcher.launch(...)` (сейчас запрашивается только FINE).
3. Для background-location (нужен трекинг-сервису в фоне) — отдельный запрос позже, на Android 11+ это делается **после** granting FINE (нельзя в одном диалоге). В этой задаче оставить TODO-комментарий; background попросим когда пользователь впервые стартует поездку (отдельная задача).
4. Если пользователь отказал — показать Snackbar/диалог с объяснением «Для навигации нужен доступ к местоположению» + кнопкой «Открыть настройки» (intent `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`).

**Критерий приёмки:**
- [ ] При первом запуске app показывает системный диалог «Разрешить доступ к местоположению»
- [ ] После granting — FAB «Моё местоположение» работает (камера перемещается к пользователю)
- [ ] После отказа — показывается объяснение + путь в настройки
- [ ] На Android 13+ запрашивается также `POST_NOTIFICATIONS` (для foreground service трекинга)

---

### Задача 1.2: FAB «Моё местоположение» — continuous updates

**Проблема:** FAB сейчас делает **one-shot** запрос `getCurrentLocation()` с `PRIORITY_BALANCED_POWER_ACCURACY`. «Following» в UI — камера следует за одним фиксом, а не за живой позицией. В движении позиция не обновляется.

**Файлы:**
- `feature/map/src/main/java/com/twocircle/bike/feature/map/location/MyLocationController.kt` — один метод `lastKnown()` (one-shot BALANCED)
- `feature/map/src/main/java/com/twocircle/bike/feature/map/model/MyLocationViewModel.kt` — `toggleFollowing()` → `resolve()` → one-shot

**Что сделать:**
1. Добавить в `MyLocationController` метод `startContinuousUpdates(onLocationChanged: (LatLon) -> Unit)` / `stopContinuousUpdates()` через `requestLocationUpdates(request, callback, mainLooper)`.
2. **СОХРАНИТЬ существующий `lastKnown()`** — не удалять! Его стоит вызывать в `toggleFollowing()` для мгновенного первого фикса (пока continuous не дал данных), чтобы камера прыгнула к пользователю сразу, а не ждала 1-2 секунды до первого continuous-сэмпла.
3. `LocationRequest`: `PRIORITY_HIGH_ACCURACY` (это автоматически включает GPS+ГЛОНАСС+Galileo через FusedLocationProvider), interval = 2000ms, minUpdateInterval = 1000ms.
4. **Leak-safe callback (обязательно):**
   - Хранить `private var callback: LocationCallback? = null` в controller между start/stop.
   - В `stopContinuousUpdates()`: `client.removeLocationUpdates(callback)` + `callback = null`.
   - Иначе — leak: LocationCallback держит ссылку на activity/context, сборщик не уберёт.
   - `@SuppressLint("MissingPermission")` на обоих методах (permission gate гарантирует granted).
5. `MyLocationViewModel.toggleFollowing()`:
   - При `following=true` → сначала `resolve()` (one-shot для мгновенного отклика), потом `startContinuousUpdates { loc -> _location.value = loc }`.
   - При `following=false` → `stopContinuousUpdates()`.
6. В `MapScreen.kt` существующий `LaunchedEffect(myLocation, following)` (строки ~356-367) уже двигает камеру при каждой эмиссии — это сработает автоматически, как только `myLocation` станет обновляться continuously.
7. Ручной пан карты уже сбрасывает following через `addOnCameraIdleListener` → `stopFollowing()` — оставить как есть. **Но добавить в `stopFollowing()` вызов `controller.stopContinuousUpdates()`** — иначе подписка течёт после ручного пана.

**Критерий приёмки:**
- [ ] Тап по FAB включает following → камера плавно едет к пользователю
- [ ] При движении (тест: пройти 20м пешком) — синяя точка/камера следует в реальном времени
- [ ] Повторный тап по FAB выключает following → позиция больше не обновляется (батарея)
- [ ] Ручной пан/зум отключает following (как сейчас)

**Замечание про ГЛОНАСС:** явных настроек GNSS в коде НЕ добавлять. `PRIORITY_HIGH_ACCURACY` достаточно — Android FusedLocationProvider сам комбинирует GPS+ГЛОНАСС+Galileo. Если кодер хочет — может добавить логирование `location.extras` (там есть `satellites` count), но это опционально.

---

### Задача 1.3: Синяя точка пользователя на карте (LocationComponent)

**Решение (подтверждено пользователем):** использовать **родной `map.locationComponent` MapLibre** (НЕ свой SymbolLayer).

**Проблема:** `LocationComponent` MapLibre не активирован. Позиция пользователя есть в StateFlow, но визуально её не видно на карте.

**Файлы:**
- `feature/map/src/main/java/com/twocircle/bike/feature/map/view/BikeMap.kt` — Compose-обёртка MapLibreMap
- `feature/map/src/main/java/com/twocircle/bike/feature/map/screen/MapScreen.kt` — onMapReady callback

**Что сделать:**
1. В `onMapReady` (после инициализации слоёв) активировать LocationComponent:
   ```kotlin
   val locationComponent = map.locationComponent
   locationComponent.activateLocationComponent(
       LocationComponentActivationOptions.builder(context, map.style!!)
           .useDefaultLocationEngine(false)  // мы управляем позицией сами через StateFlow
           .build()
   )
   locationComponent.isLocationComponentEnabled = true
   ```
2. Настроить стиль точки (опционально, дефолтная синяя точка тоже ок): можно задать `LocationComponentOptions` с точностью-кругом (`accuracyAlpha`, `accuracyColor`).
3. В существующем `LaunchedEffect(myLocation, following)` добавить `locationComponent.forceLocationUpdate(location)` — это обновит позицию синей точки при каждой эмиссии.
4. **Важно:** если родной LocationComponent глючит на MapLibre 11.11.0 (мерцает, не двигается, пропадает) — переключиться на fallback: SymbolLayer через GeoJSON source (по образцу `PoiMarkerLayer`, с иконкой-кругом `#1976D2`). Зафиксировать в комментариях какой вариант выбран и почему.

**Критерий приёмки:**
- [ ] На карте видна синяя точка в позиции пользователя
- [ ] Вокруг точки — полупрозрачный круг точности (accuracy)
- [ ] При движении точка перемещается плавно
- [ ] При отключении following — точка остаётся видимой (просто камера не следит)

**Альтернатива** (если LocationComponent глючит на версии MapLibre 11.11.0): рисовать позицию как отдельный SymbolLayer через GeoJSON source (по образцу `PoiMarkerLayer`, с иконкой-кругом). Менее красиво, но надёжно.

---

## ЧАСТЬ 2 — Наложение слоёв (косметика, проверить + починить если мешает)

### Задача 2.1: Зафиксировать z-order route vs track (track ПОВЕРХ route)

**Решённое требование (подтверждено пользователем):** иерархия снизу вверх:
```
road → route-overlay-layer (синий план) → track-overlay-layer (зелёная поездка) → poi-layer → waypoint-layer
```
**Track (зелёный, актуальная поездка) ПОВЕРХ route (синий, план)** — потому что «что я реально проехал» важнее «что планировал». Когда едешь по маршруту, видишь свой реальный прогресс поверх плана.

**Текущее состояние (баг):** `RouteOverlayLayer` (синий, 5px) сейчас рисуется **поверх** `TrackOverlayLayer` (зелёный, 4px) при пересечении — потому что порядок определяется очерёдностью `start()` в onMapReady, а не явным ref между ними.

**Файлы:**
- `feature/map/.../view/RouteOverlayLayer.kt:73-74` — `addLayerBelow("poi-layer")` с fallback
- `feature/map/.../view/TrackOverlayLayer.kt:56` — `addLayerAbove("road")`
- `feature/map/.../screen/MapScreen.kt` — порядок `.start()` вызовов

**Что сделать:**
1. В `RouteOverlayLayer.kt`: размещать слой **над road, под track-overlay-layer**. Использовать `addLayerBelow(layer, "track-overlay-layer")` (вместо текущего `addLayerBelow("poi-layer")`). Если track-overlay-layer ещё не создан — fail-loud (см. задачу 2.2).
2. В `TrackOverlayLayer.kt`: оставить `addLayerAbove("road")` — это корректно, track над road. Но убедиться что route стартует **раньше** track в onMapReady, чтобы track-overlay-layer существовал к моменту route-add (или наоборот — route добавлять через ref на track, а track через ref на road, порядок не важен если refs разрешаются).
3. **Порядок `.start()` в onMapReady зафиксировать:** POI → Route → Track → Waypoint. Документировать комментарий «order matters: track must exist before route references it».

**Проверить на устройстве:**
1. Построить маршрут (синяя полилиния).
2. Начать запись поездки (зелёная полилиния трека).
3. Проехать часть маршрута — там где track и route совпадают, **зелёный track должен быть поверх синего route**.

**Критерий приёмки:**
- [ ] При пересечении зелёный track виден поверх синего route (актуальное поверх плана)
- [ ] Оба слоя полупрозрачные, различимы по цвету даже когда не совпадают

---

### Задача 2.2: Зафиксировать хрупкость addLayer* с fallback

**Проблема:** `RouteOverlayLayer` и `WaypointMarkerLayer` используют `runCatching { addLayerBelow/Above(ref) }.onFailure { addLayer(layer) }`. Если порядок `start()` изменится или POI-слой не успеет создаться, слой улетит на верх стека и сломает z-order.

**Файлы:**
- `RouteOverlayLayer.kt:73-74`
- `WaypointMarkerLayer.kt:67-68`

**Что сделать:**
1. Гарантировать порядок `start()` в `MapScreen.onMapReady`: **POI → Route → Waypoint → Track** (или любой детерминированный порядок). Сейчас POI стартует раньше route/waypoint — это работает, но не задокументировано.
2. Добавить комментарий в каждый слой-класс: «depends on `poi-layer` being attached first; order matters in MapScreen.onMapReady».
3. Вместо `runCatching { ... }.onFailure { addLayer }` — явно проверить `style.getLayer("poi-layer") != null` и бросить осмысленную ошибку если его нет (fail-loud лучше silent fallback, который ломает z-order).

**Критерий приёмки:**
- [ ] Если `poi-layer` не создан к моменту route/waypoint start — app падает с понятной ошибкой, а не молча рисует слой поверх всего

---

### Задача 2.3: Проверить наложение POI/waypoint/labels на разных зумах

**Файлы:** `MapStyleProvider.kt` (place-label z0-14, road-name z8-14), `PoiMarkerLayer.kt` (z12-14 implicit), `WaypointMarkerLayer.kt`

**Что проверить на устройстве (методика):**
1. Открыть карту Урала, приблизиться к Екатеринбургу/Челябинску/Оренбургу.
2. На разных зумах (z10 обзор города, z12 районы, z14 улицы) проверить:
   - **z10-11**: видны ли place-labels (названия городов)? Не перекрываются ли POI-маркерами?
   - **z12-13**: появляются ли POI (магазины, гостиницы)? Какого они цвета? (должны быть разные по категории — медицинские красные, вода синие, еда оранжевые и т.д.)
   - **z14**: видны ли road-names (названия улиц на линиях)? Перекрываются ли POI?
   - **Waypoint markers** (при постановке точек маршрута): всегда ли поверх POI? Виден ли номер/роль (S/F/цифра)?
3. Поставить 2-3 waypoint-а через тап → проверить что они видны поверх POI и друг друга.
4. Проверить плотные кластеры POI (центр города) — не сливаются ли маркеры в кашу? `textAllowOverlap=true` сейчас рисует все, возможно стоит включить кластеризацию на низких зумах (опционально, обсудить с ведущим).

**Критерий приёмки:**
- [ ] На z10 видны названия городов, POI скрыты (z12+)
- [ ] На z12-14 POI разных цветов по категориям
- [ ] Waypoints всегда поверх POI, с читаемыми S/F/номер
- [ ] Названия улиц (road-name) читаемы на z13-14, не перекрыты POI полностью

---

## ЧАСТЬ 3 — Edge cases (проверить, не обязательно чинить)

Отчет кодера по каждому пункту (работает/не работает + скриншот):

1. **Поворот экрана** — карта и слои сохраняются? Маршрут/waypoints не теряются?
2. **Background → foreground** — при возврате из фона карта живая? GPS-подписка восстановилась?
3. **Потеря GPS-сигнала** (войти в здание) — синяя точка пропадает или «зависает»? Есть ли индикатор «нет сигнала»?
4. **Построить маршрут → начать поездку → отключить экран на 30с → включить** — запись продолжилась? NavigationOverlay активен?
5. **Два региона скачаны** (Andorra + Ural) — переключение работает? Слои не путаются?
6. **Тёмная/светлая тема** — POI/waypoints/track/route видны в обеих? Цвета контрастные?

---

## Что НЕ делать в этой задаче

- Не трогать `routing.rd5` / offline routing (отдельная задача, cloud fallback работает)
- Не добавлять новых слоёв (только починить z-order существующих)
- Не менять API `PlannedRouteHolder`/`NavigationSink`/`RouteDraftMutator` (они работают)
- Не bump version/deploy без ревью ведущего

---

## Финальная сборка после фиксов

После того как кодер завершил и ведущий одобрил:
1. `versionCode` 9 → 10, `versionName` 0.8.1 → 0.8.2
2. `./gradlew :app:assembleRelease`
3. Обновить `version.json` на VPS (versionCode=10, versionName=0.8.2, новые release notes)
4. Деплой APK в `/opt/2circle/backend/regions/apk/twocircle-0.8.2.apk`, обновить symlink `latest.apk`
5. Ведущий ревьюит → деплой

---

## Ссылки на код (для кодера)

| Компонент | Файл |
|---|---|
| Permission gate | `app/.../permissions/LocationPermissionGate.kt`, `PermissionRequester.kt` |
| MainActivity | `app/.../MainActivity.kt` |
| FAB + location VM | `feature/map/.../location/MyLocationController.kt`, `model/MyLocationViewModel.kt` |
| Map screen (FAB, layers, onMapReady) | `feature/map/.../screen/MapScreen.kt` |
| BikeMap (MapLibre wrapper) | `feature/map/.../view/BikeMap.kt` |
| Style layers | `feature/map/.../style/MapStyleProvider.kt` |
| POI layer | `feature/map/.../view/PoiMarkerLayer.kt` |
| Waypoint layer | `feature/map/.../view/WaypointMarkerLayer.kt` |
| Route layer | `feature/map/.../view/RouteOverlayLayer.kt` |
| Track layer | `feature/map/.../view/TrackOverlayLayer.kt` |
| Tracking service (HIGH_ACCURACY reference) | `feature/tracking/.../service/LocationForegroundService.kt:126` |
| Polling policy (adaptive intervals) | `feature/tracking/.../sampler/PollingPolicy.kt` |
| AndroidManifest (permissions) | `app/src/main/AndroidManifest.xml` |

## Контрольные вопросы кодеру (ответить в PR)

1. Какой priority использовал для FAB continuous updates? (ожидание: HIGH_ACCURACY)
2. Активировал LocationComponent или нарисовал точку вручную? Почему?
3. В каком z-order-конфликте route-vs-track обнаружил проблему? Как починил?
4. Работает ли GPS на эмуляторе? (mock location) И на реальном устройстве?
5. Скриншоты: z10 (города), z13 (POI + дорога), z14 (waypoint + POI + road-name), навигация активна.
