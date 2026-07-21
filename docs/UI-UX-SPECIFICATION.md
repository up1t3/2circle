# 2circle — UI/UX Design Specification v3

> **Документ для UI/UX дизайнера.** Это детальное ТЗ по каждому экрану, компоненту
> и взаимодействию. Содержит: текущее состояние, проблемы, требования к редизайну,
> референсы и конкретные измерения.

---

## 1. Бренд и позиционирование

**2circle** — оффлайн-навигатор для велопутешественников. Уникальное преимущество:
раскраска дорог по типу покрытия (асфальт — зелёный, гравий — оранжевый, песок —
красный). Целевая аудитория: велотуристы, бэккантри райдеры, bikepackers.

**Brand glyph:** два круга (зелёный + синий) — символизируют два колеса велосипеда.

**Brand colors (primary):**
- Asphalt Green: `#4CAF50` (основной)
- Water Blue: `#2C5F7E`
- Background Dark: `#2A343C` (тёмная тема, основная)
- Background Light: `#F5F7FA` (светлая тема, вторичная)

**Brand colors (surface types):**
- Asphalt: `#4CAF50` — зелёный
- Compacted (укатанный гравий): `#FF9800` — оранжевый
- Dirt (грунт): `#8D6E63` — коричневый
- Sand (песок): `#F44336` — красный
- Grass: `#7CB342` — светло-зелёный
- Rock: `#607D8B` — сине-серый

---

## 2. Цветовая система

### Dark theme (основная)

| Роль | HEX | Использование |
|---|---|---|
| `background` | `#1E252D` | Основной фон экранов |
| `surface` | `#262F38` | Карточки, sheets, FAB |
| `surfaceVariant` | `#2A343C` | Фон карты (минимальный) |
| `primary` | `#4CAF50` | Кнопки, активные элементы |
| `onPrimary` | `#FFFFFF` | Текст на primary |
| `secondary` | `#2C5F7E` | Вода, акценты |
| `onSurface` | `#E0E4E8` | Основной текст |
| `onSurfaceMuted` | `#8B95A0` | Вторичный текст (alpha 0.6) |
| `error` | `#EF4444` | Ошибки |
| `success` | `#22C55E` | Успех |
| `warning` | `#FFC107` | POI маркеры, предупреждения |

### Light theme

| Роль | HEX | Использование |
|---|---|---|
| `background` | `#F5F7FA` | Основной фон |
| `surface` | `#FFFFFF` | Карточки, sheets |
| `surfaceVariant` | `#E8ECF0` | Разделители, toggle |
| `primary` | `#388E3C` | Кнопки (тёмнее для контраста) |
| `onPrimary` | `#FFFFFF` | Текст на primary |
| `onSurface` | `#1E252D` | Основной текст |
| `onSurfaceMuted` | `#6B7280` | Вторичный текст |
| `error` | `#DC2626` | Ошибки |
| `water` | `#3B7CA8` | Водные тела |

---

## 3. Типографика

**Font family:** `Inter` (или `Roboto Flex`) — переменный шрифт, оптимизирован для экранов.

| Стиль | Размер | Вес | Line height | Использование |
|---|---|---|---|---|
| `displayLarge` | 32sp | 700 | 40sp | Заголовки onboarding |
| `headlineMedium` | 24sp | 600 | 32sp | Заголовки экранов |
| `titleLarge` | 20sp | 600 | 28sp | Заголовки секций |
| `titleMedium` | 16sp | 600 | 24sp | Имена в списках |
| `bodyLarge` | 16sp | 400 | 24sp | Основной текст |
| `bodyMedium` | 14sp | 400 | 20sp | Описания |
| `bodySmall` | 12sp | 400 | 16sp | Метаданные |
| `labelLarge` | 14sp | 600 | 20sp | Кнопки |
| `labelMedium` | 12sp | 600 | 16sp | Чипы, теги |
| `labelSmall` | 11sp | 600 | 16sp | Подписи к иконкам |

**Для карты (MapLibre):** `Open Sans Semibold` (PBF embedded), 10-12sp.

---

## 4. Spacing и Layout

**Базовая сетка:** 4dp (все отступы кратны 4)

| Токен | Значение | Использование |
|---|---|---|
| `xs` | 4dp | Минимальный отступ (иконка + текст) |
| `sm` | 8dp | Между связанными элементами |
| `md` | 12dp | Между строками в списке |
| `lg` | 16dp | Стандартный padding экрана |
| `xl` | 24dp | Между секциями |
| `xxl` | 32dp | Между блоками |

**Border radius:**
- Карточки: 16dp
- Кнопки: 12dp (full-rounded для FAB)
- Чипы: 20dp (pill)
- ModalBottomSheet: 28dp (Material 3 default)

**Elevation:**
- FAB: 6dp
- Bottom navigation: 8dp (в тёмной теме — border вместо тени)
- ModalBottomSheet: 0dp (overlay Dim)
- Карточки: 2dp

---

## 5. Экраны — Детальные требования

### 5.1 Splash Screen

**Текущее состояние:** Работает, но минималистичное.

**Требования:**
- Тёмный фон `#1E252D`
- Brand glyph (два круга) по центру, размер 120×120dp
- Анимация: круги появляются с bounce, затем масштабируются к финальному размеру
- Длительность анимации: 800ms
- Под логотипом: название "2circle" шрифтом `titleLarge`, `primary` цвет
- Под названием: слоган "Bike touring, offline" шрифтом `bodySmall`, `onSurfaceMuted`
- Длительность показа: 1.5s → переход к Onboarding или Map
- **На Android 12+:** системный splash (windowBackground) → плавный переход к нашему

**Референс:** Komoot splash (чистый, минималистичный, брендовый).

---

### 5.2 Onboarding (3 карточки)

**Текущее состояние:** Работает, но иллюстрации примитивные (vector без глубины).

**Требования:**

**Карточка 1: "Офлайн-карты для велопутешествий"**
- Иллюстрация: стилизованная карта с зелёными/оранжевыми дорогами, горы на горизонте, силуэт велосипеда
- Стиль: flat design с градиентами, в brand-цветах
- Размер иллюстрации: 240×160dp (соотношение 3:2)
- Заголовок: `headlineMedium`, `onSurface`
- Описание: `bodyMedium`, `onSurfaceMuted`, 2-3 строки
- Page indicator: 3 точки, активная — `primary`, неактивные — `onSurfaceMuted` (alpha 0.3)
- Кнопка "Next": `labelLarge`, `primary` background, full-width, height 48dp
- "Skip": `TextButton`, `onSurfaceMuted`,右上角

**Карточка 2: "Ищите места по названию"**
- Иллюстрация: лупа над картой с маркерами (аптека, кафе, кемпинг)
- Те же размеры и стили

**Карточка 3: "Запись и экспорт"**
- Иллюстрация: GPX-трек на карте + стрелка экспорта + иконка Strava
- Кнопка меняется на "Начать" (`primary` background)

**Анимации:**
- Переход между карточками: slide horizontal (300ms, easeInOut)
- Иллюстрация: scale + fade in при появлении карточки
- Кнопка: ripple effect на tap

**Референс:** Strava onboarding (чистые иллюстрации, плавные переходы).

---

### 5.3 Map Screen (главный экран)

**Текущее состояние:** Карта рендерится, FAB работают, но визуально перегружено.

**Проблемы для исправления:**

1. **FAB перегруженность** — 5 FAB (Search, Settings, POI, My-location, Start-ride) на одном экране. Нужно сгруппировать:
   - **Top bar** (вместо отдельных FAB): слева — burger menu (профиль), по центру — название региона, справа — search icon
   - **Bottom-right cluster:** одна главная FAB "+" (Start ride), при tap раскрывает: Start ride / Navigate / Add waypoint
   - **Bottom-left:** My-location FAB (всегда видимый)
   - **Settings:** перенести в профиль (через burger menu или отдельную вкладку)

2. **POI categories** — вместо FAB → чипы внизу карты (как Google Maps):
   - Горизонтальный LazyRow с чипами: Все | Аптека | Кафе | Магазин | Заправка | Вода | Гостиница
   - При выборе чипа — маркеры появляются на карте, чип подсвечивается
   - Не закрывает карту (в отличие от текущего ModalBottomSheet)

3. **Map overlay UI** — полупрозрачные панели поверх карты:
   - Top bar: `surface.copy(alpha = 0.9f)`, высота 56dp, без elevation
   - POI chips bar: `surface.copy(alpha = 0.9f)`, height 44dp
   - Bottom FAB cluster: отступ 16dp от bottom nav

4. **Long-press на карте** → анимированный пин-маркер (красный, с bounce), а не невидимый переход
   - Над пином: bubble с координатами + кнопки "В маршрут" / "Поиск рядом"
   - Bubble: `surface` background, `12dp` radius, pointer вниз

5. **Compass** — переместить в top-right (внутри top bar), а не плавающий
   - Размер: 40×40dp
   - Appearance: circle с N-стрелкой, поворачивается с картой
   - Tap → north-up (сброс вращения)

6. **Scale bar** — внизу-слева, рядом с my-location FAB
   - Metric: "1 km ──────" с масштабной полосой
   - Обновляется при zoom

**Map theme (dark):**
- Background: `#263038` (уже есть)
- Roads: current surface-coloring scheme (сохранить!)
- Water: `#2C5F7E`
- Landcover: `#2E3A2A` с opacity 0.4
- Place labels: white text, black halo 1.5px, size 12-14sp
- Road names: grey `#A0A8B0`, size 10sp, minzoom 12
- POI markers: circular dots с иконкой внутри (не текст), 24×24dp

**Референс:** Komoot map (clean overlay UI, bottom chips), OsmAnd (long-press menu).

---

### 5.4 Search Screen

**Текущее состояние:** Базовый TextField + список. Работает, но не хватает визуальной полировки.

**Требования:**

1. **Search bar:**
   - Расположение: top, sticky
   - Стиль: `surfaceVariant` background, `12dp` radius, height 48dp
   - Leading icon: `Search` (24dp, `onSurfaceMuted`)
   - Trailing: clear button (когда есть текст) — `Close` icon
   - Placeholder: "Поиск мест, источников, перевалов…" — `bodyMedium`, `onSurfaceMuted`
   - Focus: border `1dp` `primary` при фокусе

2. **Result row:**
   - Height: min 64dp (touch target)
   - Layout: icon слева (24dp, category-colored) + Column(имя + метаданные) + trailing "В маршрут" TextButton
   - Name: `titleMedium`, `onSurface`
   - Metadata row: kind label (`labelSmall`, `primary`) · distance (`labelSmall`, `onSurfaceMuted`) · population (`labelSmall`, `onSurfaceMuted`)
   - Divider: `1dp` `surfaceVariant`, полный width
   - Tap on row → map fly-to (с smooth animation)
   - Long-press on row → bottom sheet с деталями

3. **States:**
   - **Idle (empty query):** centred illustration + "Введите название места" — `bodyMedium`, `onSurfaceMuted`
   - **Searching:** `CircularProgressIndicator` (24dp, `primary`), центрированный
   - **Results:** LazyColumn с Divider между строками
   - **No results:** illustration + "Ничего не найдено" — `titleMedium`, `onSurfaceMuted`
   - **No region:** illustration + "Скачайте регион для поиска" + кнопка "К регионам"

4. **Category filter chips** (над результатами, опционально):
   - Горизонтальный LazyRow
   - Чипы: "Все" | "Города" | "Природа" | "POI"
   - Выбор чипа фильтрует результаты

**Референс:** Google Maps search, Komoot place search.

---

### 5.5 Route Builder Screen

**Текущее состояние:** Базовый список waypoints + profile chips + plan button.

**Требования:**

1. **Layout restructure:**
   ```
   ┌─────────────────────────────────┐
   │  Top bar: "Маршрут" | Saved ↗   │  ← 48dp, surface
   ├─────────────────────────────────┤
   │  Profile chips: Touring|Road|MTB│  ← 44dp, scrollable
   ├─────────────────────────────────┤
   │                                 │
   │  Waypoint list (drag-reorder):  │  ← weight 1
   │  ┌──────────────────────────┐   │
   │  │ ⠿ 📍 Челябинск           × │   │  ← Start, draggable
   │  │   Start · Search          │   │
   │  └──────────────────────────┘   │
   │  ┌──────────────────────────┐   │
   │  │ ⠿ 📍 Пласт              × │   │  ← End, draggable
   │  │   End · Search            │   │
   │  └──────────────────────────┘   │
   │                                 │
   │  [Planned route preview card]   │  ← when route is computed
   │  ┌──────────────────────────┐   │
   │  │ Dist: 245 km  Time: 12h  │   │
   │  │ ↑ 1,200 m    ↓ 950 m     │   │
   │  │ Surface: asphalt gravel  │   │
   │  └──────────────────────────┘   │
   │                                 │
   ├─────────────────────────────────┤
   │  [Save route]  [Построить]     │  ← bottom fixed
   └─────────────────────────────────┘
   ```

2. **Waypoint row redesign:**
   - Drag handle: `⠿` icon слева (16dp, `onSurfaceMuted`)
   - Pin icon: category-colored (24dp)
   - Name: `titleMedium`, `onSurface`
   - Role badge: chip `labelSmall` ("Старт" / "Транзит" / "Финиш"), colored:
     - Start: `primary` background, white text
     - Via: `surfaceVariant` background, `onSurfaceMuted` text
     - End: `secondary` background, white text
   - Source: маленькая иконка справа (search/manual/gps), 16dp, `onSurfaceMuted`
   - Delete: `×` icon (24dp), при tap → swipe-right confirmation

3. **Planned route preview:**
   - Card: `surface` background, `12dp` radius, padding 16dp
   - Stats: 2-колоночный grid
     - Distance: `headlineMedium`, `onSurface`
     - Time: `headlineMedium`, `onSurface`
     - Ascent: `titleMedium`, `primary` (with ↑ arrow)
     - Descent: `titleMedium`, `onSurfaceMuted` (with ↓ arrow)
   - Surface breakdown: горизонтальные цветные полоски (пропорционально покрытию)
     - Зелёный (asphalt) / Оранжевый (gravel) / Коричневый (dirt) / Красный (sand)
     - Высота полоски: 8dp, full-width, `4dp` radius
     - Под полосками: легенда "45% асфальт · 30% гравий · 25% грунт"
   - Elevation profile: SVG-подобная кривая (упрощённая, без библиотек)
     - Высота: 80dp, full-width
     - Цвет заливки: gradient от `primary` (низ) к `surfaceVariant` (верх)
     - Линия: `primary`, 2dp
     - Ось X: расстояние (0 → total km)
     - Ось Y: высота (min → max elevation)

4. **Plan button states:**
   - Disabled (0-1 waypoints): `surfaceVariant` background, `onSurfaceMuted` text, "Добавьте ≥2 точек"
   - Enabled (2+ waypoints): `primary` background, white text, "Построить маршрут"
   - Planning (in-flight): `primary` background, `CircularProgressIndicator` (20dp, white) + "Прокладка…"
   - Planned: `primary` background, "Перестроить" (profile change triggers re-plan)

5. **Empty state:**
   - Illustration (200×120dp) — пустая карта с пунктирной линией
   - "Добавляйте точки на карте (долгое нажатие) или через поиск"
   - Hint: "Нужно минимум 2 точки для маршрута"

6. **Drag-to-reorder:**
   - Long-press на drag handle → элемент поднимается (elevation 4dp)
   - Dragging → остальные элементы сдвигаются (анимация 200ms)
   - Release → элемент встаёт на новое место
   - Роли (Start/Via/End) автоматически пересчитываются

**Референс:** Komoot route planner (drag-reorder, surface breakdown bar), RideWithGPS (elevation profile).

---

### 5.6 Saved Routes Screen

**Текущее состояние:** Новый экран, базовый список.

**Требования:**

1. **List row:**
   - Pin icon (24dp, `primary`)
   - Route name: `titleMedium`, `onSurface`
   - Stats: "245 km · 12h · ↑1,200m" — `labelMedium`, `onSurfaceMuted`
   - Profile badge: chip (Touring/Road/MTB) — `labelSmall`
   - Date: "5 июля" — `labelSmall`, `onSurfaceMuted`
   - Swipe-left → delete (red background, trash icon)
   - Tap → load into Route Builder

2. **Empty state:**
   - Illustration
   - "Нет сохранённых маршрутов"
   - "Постройте маршрут и сохраните его"

3. **Sort options** (overflow menu):
   - По дате (новые сверху)
   - По дистанции
   - По названию

---

### 5.7 Tracks Screen (История поездок)

**Текущее состояние:** Базовый список + GPX импорт + экспорт.

**Требования:**

1. **List row (ride card):**
   ```
   ┌──────────────────────────────────────┐
   │  ╭───────────╮                       │
   │  │ Mini map  │  🚴 Поездка 5 июля    │
   │  │ (preview) │     45.2 km · 2h 15m  │
   │  ╰───────────╯  ↑320m ↓280m · 20km/h│
   │                                      │
   └──────────────────────────────────────┘
   ```
   - Mini map: 80×80dp Canvas preview трека (полилиния), `surfaceVariant` background
   - Name: `titleMedium`, `onSurface`
   - Stats row: distance · duration · ascent — `labelMedium`, `onSurfaceMuted`
   - Date: "5 июля 2026, 14:30" — `labelSmall`, `onSurfaceMuted`
   - Status badge: "Завершена" (зелёный), "Прервана" (оранжевый), "Импорт" (серый)
   - Tap → Track Detail

2. **Top bar:**
   - "Треки" title
   - "Импорт GPX" TextButton (right-aligned)
   - Sort overflow (by date / distance / duration)

3. **Empty state:**
   - Illustration: велосипедист на горизонте
   - "Поездок пока нет"
   - "Начните первую с экрана карты" — `bodyMedium`, `onSurfaceMuted`

---

### 5.8 Track Detail Screen

**Текущее состояние:** Telemetry stats + export/delete.

**Требования:**

1. **Layout:**
   ```
   ┌──────────────────────────────────────┐
   │  ← Back                              │
   ├──────────────────────────────────────┤
   │                                      │
   │       Map preview (full width)       │  ← 200dp height
   │       with track polyline            │
   │                                      │
   ├──────────────────────────────────────┤
   │  Ride name + date                    │
   ├──────────────────────────────────────┤
   │  ┌─────────┬─────────┬─────────┐    │
   │  │ 45.2 km │ 2h 15m  │ ↑320m   │    │  ← Stat grid (3 cols)
   │  │ DISTANCE│ DURATION│ ASCENT  │    │
   │  ├─────────┼─────────┼─────────┤    │
   │  │ 20 km/h │ ↓280m   │ 1,250pt │    │
   │  │ AVG SPD │ DESCENT │ POINTS  │    │
   │  └─────────┴─────────┴─────────┘    │
   ├──────────────────────────────────────┤
   │  [Elevation profile chart]           │  ← 120dp
   ├──────────────────────────────────────┤
   │  [Speed profile chart]               │  ← 80dp (опционально)
   ├──────────────────────────────────────┤
   │  [Экспорт GPX]  [Удалить]           │  ← bottom buttons
   └──────────────────────────────────────┘
   ```

2. **Stat card:**
   - `surface` background, `8dp` radius
   - Value: `headlineMedium`, `primary` (for positive stats) or `onSurface`
   - Label: `labelSmall`, `onSurfaceMuted`, uppercase
   - Centered

3. **Elevation profile:**
   - SVG path с gradient fill
   - Ось: расстояние (X) × высота (Y)
   - Сетка: 2 горизонтальные линии (grid)
   - Labels: "120m" (min) ... "440m" (max) — `labelSmall`, `onSurfaceMuted`
   - Точки Start/End с метками

4. **Export button:**
   - `OutlinedButton` (не filled — secondary action)
   - Icon: share/upload
   - Text: "Экспорт GPX"
   - Tap → system share sheet (ACTION_SEND)

5. **Delete:**
   - `TextButton`, `error` colour
   - Text: "Удалить"
   - Tap → confirmation dialog ("Удалить поездку? Это действие необратимо.")

---

### 5.9 Regions Screen

**Текущее состояние:** Базовый список с download/delete.

**Требования:**

1. **List row:**
   ```
   ┌──────────────────────────────────────┐
   │  🗺  Ural Russia                     │
   │      733 MB · v2                     │
   │      ┌──────────────────────┐        │
   │      │████████████░░░░░░░░░░│ 65%    │  ← progress bar during download
   │      └──────────────────────┘        │
   │      Скачивание… 65%                 │
   │                          [Скачать]   │  ← when not installed
   │   - или -                            │
   │                          [Удалить]   │  ← when installed
   │   - или -                            │
   │                      ✓ Активный      │  ← when active region
   └──────────────────────────────────────┘
   ```

2. **Active region indicator:**
   - Когда регион активен: зелёный checkmark + "Активный" chip
   - Tap на неактивный установленный регион → делает его активным (`setActiveRegion`)

3. **Download progress:**
   - Linear progress bar: `primary` fill, `surfaceVariant` track, height 4dp
   - Label: "Скачивание… 65%" или "Проверка…" или "Распаковка…"
   - Resume support: если загрузка прервана → "Продолжить" вместо "Скачать"

4. **Multi-region switching:**
   - Если несколько регионов установлено → radio button рядом с каждым
   - Активный регион отмечен точкой в circle (radio checked)
   - Tap на другой → делает активным, карта переключается

5. **Storage info:**
   - Внизу экрана: "Использовано: 2.1 GB из 8.0 GB" — `bodySmall`, `onSurfaceMuted`
   - Bar chart распределения (опционально)

---

### 5.10 Settings Screen

**Текущее состояние:** Language picker + About.

**Требуется расширить:**

1. **Sections:**
   ```
   ┌──────────────────────────────────────┐
   │  Настройки                           │
   ├──────────────────────────────────────┤
   │  👤 Аккаунт                          │
   │     Войти / email@example.com        │
   ├──────────────────────────────────────┤
   │  🌐 Язык                             │  ← existing
   │     Системный / English / Русский    │
   ├──────────────────────────────────────┤
   │  🎨 Тема                             │  ← NEW
   │     Системная / Тёмная / Светлая     │
   ├──────────────────────────────────────┤
   │  📏 Единицы                          │  ← NEW
   │     Метрические / Имперские          │
   ├──────────────────────────────────────┤
   │  🗺  Карты                           │  ← NEW section
   │     Активный регион: Ural Russia     │
   │     Качество тайлов: Стандартное     │
   │     Онлайн подложка: Выкл            │
   ├──────────────────────────────────────┤
   │  🔊 Навигация                        │  ← NEW (Phase 3)
   │     Голосовые подсказки: Вкл         │
   │     Язык голоса: Русский             │
   │     Громкость: ────●─────            │
   ├──────────────────────────────────────┤
   │  💎 Premium                          │  ← NEW (Phase 5)
   │     2circle Premium: Не активна      │
   │     [Управление подпиской]           │
   ├──────────────────────────────────────┤
   │  ℹ️  О приложении                    │  ← existing
   │     Версия 0.1.0                     │
   │     Карты: © OpenStreetMap           │
   │     Рельеф: Copernicus DEM           │
   └──────────────────────────────────────┘
   ```

2. **Row style:**
   - Height: min 56dp (touch target)
   - Leading icon: 24dp, `primary`
   - Title: `bodyLarge`, `onSurface`
   - Subtitle/value: `bodyMedium`, `onSurfaceMuted`
   - Trailing: chevron `>` или toggle switch
   - Divider: `1dp`, `surfaceVariant`, left indent 56dp

3. **Toggle switches:**
   - Material 3 `Switch`
   - Active: `primary` thumb, `primary` track
   - Inactive: `onSurfaceMuted` thumb, `surfaceVariant` track

---

### 5.11 Bottom Navigation

**Текущее состояние:** 4 вкладки (Map / Routes / Tracks / Regions).

**Требования:**

1. **Tabs (для v3 с социальной частью):**
   - 🗺 **Карта** (Map) — главная
   - 🧭 **Маршруты** (Routes) — планирование + сохранённые
   - 📊 **Треки** (Tracks) — история поездок
   - 👥 **Сообщество** (Community) — лента, друзья (NEW, Phase 2)
   - 👤 **Профиль** (Profile) — настройки, аккаунт (NEW, Phase 1)

2. **Стиль:**
   - Material 3 NavigationBar
   - Background: `surface` (dark theme) или `background` (light theme)
   - Active: `primary` icon + label, `primary` indicator pill
   - Inactive: `onSurfaceMuted` icon + label
   - Height: 80dp (включая safe-area inset)
   - Label: `labelSmall`, всегда видим (не скрывать при скролле)
   - Badge: circle `error` color, для notification count

---

### 5.12 Login / Register Screens (NEW — Phase 1)

**Требования:**

1. **Login Screen:**
   ```
   ┌──────────────────────────────────────┐
   │                                      │
   │         [2circle logo]               │
   │                                      │
   │  Email                               │
   │  ┌──────────────────────────────┐    │
   │  │ you@example.com              │    │  ← OutlinedTextField
   │  └──────────────────────────────┘    │
   │                                      │
   │  Пароль                              │
   │  ┌──────────────────────────────┐    │
   │  │ ••••••••                  👁 │    │  ← password visibility toggle
   │  └──────────────────────────────┘    │
   │                                      │
   │  [       Войти           ]           │  ← Button, primary, full width
   │                                      │
   │  ──────── или ────────               │
   │                                      │
   │  [   G  Google   ]                   │  ← Google Sign-In, outlined
   │                                      │
   │  Нет аккаунта? Зарегистрироваться    │  ← TextButton, primary
   │                                      │
   │  Забыли пароль?                      │  ← TextButton, onSurfaceMuted
   └──────────────────────────────────────┘
   ```

2. **Register Screen:**
   - Те же поля + "Имя" + "Подтвердите пароль"
   - Кнопка: "Создать аккаунт"
   - Чекбокс: "Я согласен с условиями использования"

3. **Validation:**
   - Email: regex валидация, error text "Некорректный email"
   - Password: min 8 символов, error "Минимум 8 символов"
   - Inline validation (onFocusChange → validate)

4. **Loading state:**
   - Button → `CircularProgressIndicator` (white, 20dp) + disabled
   - TextField → disabled

5. **Error state:**
   - Snackbar: "Неверный email или пароль" — `error` background
   - 3 попытки → "Слишком много попыток. Попробуйте через 5 минут"

---

### 5.13 Profile Screen (NEW — Phase 1-2)

**Требования:**

```
┌──────────────────────────────────────┐
│  ← back            ⚙ Настройки       │
├──────────────────────────────────────┤
│                                      │
│     ┌─────────┐                      │
│     │ Avatar  │  Иван Петров         │  ← 80dp circle avatar
│     │ (photo) │  @ivanpetrov         │
│     └─────────┘  Екатеринбург        │
│                                      │
│  ┌────────┬────────┬────────┐       │
│  │ 1,245  │   48   │ 12,400 │       │  ← Stat grid
│  │   km   │ rides  │   ↑m   │       │
│  └────────┴────────┴────────┘       │
│                                      │
│  Достижения (6)                     │
│  ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐    │  ← badge icons row (horizontal scroll)
│  │🏆│ │🎯│ │💯│ │⛰│ │🌙│ │📊│    │
│  └──┘ └──┘ └──┘ └──┘ └──┘ └──┘    │
│                                      │
│  Последние поездки                   │
│  ┌──────────────────────────────┐    │
│  │ 🚴 Big loop   45 km · 2h     │    │  ← ride card (compact)
│  └──────────────────────────────┘    │
│  ┌──────────────────────────────┐    │
│  │ 🚴 City ride  12 km · 35m    │    │
│  └──────────────────────────────┘    │
│                                      │
│  [Подписки]  [Подписчики]           │  ← tabs or TextButtons
└──────────────────────────────────────┘
```

---

### 5.14 Feed Screen (NEW — Phase 2)

**Требования:**

```
┌──────────────────────────────────────┐
│  Лента                          🔔   │  ← top bar
├──────────────────────────────────────┤
│  ┌──────────────────────────────┐    │
│  │ 👤 Ivan   🚴  2h ago         │    │  ← header
│  │                              │    │
│  │ ┌────────────────────────┐   │    │
│  │ │   Map preview          │   │    │  ← track polyline on mini map
│  │ │   (with surface colors)│   │    │
│  │ └────────────────────────┘   │    │
│  │                              │    │
│  │ Big mountain loop           │    │  ← title
│  │ 45.2 km · 2h 15m · ↑320m   │    │  ← stats
│  │                              │    │
│  │ ❤️ 12   💬 3                │    │  ← kudos + comments
│  └──────────────────────────────┘    │
│                                      │
│  ┌──────────────────────────────┐    │  ← next card
│  │ ...                          │    │
│  └──────────────────────────────┘    │
│                                      │
│  Pull-to-refresh ↓                  │
└──────────────────────────────────────┘
```

**Feed card details:**
- Avatar: 32dp circle
- Name: `titleSmall`, `onSurface`
- Timestamp: `labelSmall`, `onSurfaceMuted`
- Map preview: full-width, 160dp height, `12dp` radius
- Title: `titleMedium`, `onSurface`
- Stats: `bodyMedium`, `onSurfaceMuted`
- Kudos: ❤ icon + count, tap → animate heart
- Comments: 💬 icon + count, tap → comments screen
- Card background: `surface`, `12dp` radius, padding 12dp

---

## 6. Компоненты — Library

### 6.1 FAB Cluster (новый)

Вместо 5 отдельных FAB — один главный + раскрывающееся меню:

```
                    ┌───┐
                    │ ▶ │ ← Start ride (primary FAB, всегда видим)
                    └───┘
                   /  |  \
          ┌───┐  ┌───┐  ┌───┐
          │ + │  │ 🧭 │  │ 📍 │ ← раскрывающиеся (при tap на primary)
          └───┘  └───┘  └───┘
         waypoint  navigate  POI
```

**Анимация:** раскрывающиеся FAB появляются с задержкой 50ms между каждым, scale 0→1 + rotate.

### 6.2 Surface Breakdown Bar

Горизонтальная цветная полоска пропорционального покрытия:

```
┌─────────────────────────────────────────┐
│████████████████│████████│██████│██│     │
└─────────────────────────────────────────┘
 45% asphalt       30%     20%   5%
                   gravel  dirt  sand
```

- Height: 8dp (compact) или 12dp (detail)
- Width: full available
- Colors: brand surface colors
- Labels: `labelSmall`, под полоской, выровнены по центру сегмента
- Radius: 4dp на концах

### 6.3 Stat Grid

Сетка 2×3 или 2×2 для статистики:

```
┌─────────┬─────────┐
│ 45.2 km │ 2h 15m  │
│ DISTANCE│ DURATION│
├─────────┼─────────┤
│ ↑ 320 m │ 20 km/h │
│ ASCENT  │ AVG SPD │
└─────────┴─────────┘
```

- Cell: equal width, `surface` background, `8dp` radius
- Padding: 16dp
- Value: `headlineMedium` (или `titleLarge` для compact)
- Label: `labelSmall`, `onSurfaceMuted`, uppercase, letter-spacing 0.5dp

### 6.4 Elevation Profile Chart

SVG-подобный профиль высот:

- Параметры: `List<Pair<distance_m, elevation_m>>`
- Width: full available
- Height: 80-120dp (compact/detail)
- Цвет линии: `primary`, strokeWidth 2dp
- Цвет заливки: gradient (`primary` 30% opacity внизу → прозрачный вверху)
- Grid: 2 горизонтальные пунктирные линии (33% и 66% от диапазона)
- Labels: min/max elevation слева/справа — `labelSmall`, `onSurfaceMuted`
- Touch interaction: drag → показывает tooltip с точными значениями в этой точке
- **Без сторонних библиотек** — Canvas drawPath + drawLine

### 6.5 POI Marker (на карте)

Вместо текущего "● Name" текста:

```
   ┌───┐
   │ 💊 │ ← circular marker с иконкой категории
   └───┘
   ║   ║ ← pointer вниз
```

- Circle: 28×28dp, `surface` background, `primary` border 2dp
- Icon: category-specific (💊 pharmacy, ☕ cafe, ⛽ fuel, etc.), 16dp, centered
- Pointer: triangle вниз, 6dp
- Tap → scale 1.2× (bounce) → detail card
- Selected state: `primary` background вместо `surface`

### 6.6 Pin Marker (long-press на карте)

```
   ┌─────┐
   │ 📍  │ ← drop pin, `error` color
   └─────┘
   ║     ║
   ║     ║
```

- Drop animation: falls from top (200ms, bounce on landing)
- Color: `error` (#EF4444)
- Shadow: elevation 4dp
- При tap → убирается

### 6.7 Pull-to-refresh

- Material 3 `PullToRefreshBox`
- Indicator: `primary` → `onSurface` gradient во время pull
- Release threshold: 64dp
- On refresh: `CircularProgressIndicator` внутри indicator

---

## 7. Анимации и переходы

### 7.1 Screen transitions

| От | К | Тип | Длительность |
|---|---|---|---|
| Splash | Onboarding | Fade | 300ms |
| Onboarding | Map | Slide up | 400ms |
| Map | Search | Slide in from right | 250ms |
| Search | Map (back) | Slide out to right | 200ms |
| Any tab | Any tab | Fade | 150ms |
| Map | Route detail | Slide up + scale | 300ms |
| Feed card | Ride detail | Shared element (map preview) | 400ms |

### 7.2 Micro-interactions

- **Button tap:** Ripple effect (Material 3 default)
- **FAB tap:** Scale 0.95→1.0 (100ms, easeOut)
- **Chip select:** Background color change (150ms), scale 1.0→1.05→1.0
- **Waypoint drag:** Elevation 0→4dp (100ms), scale 1.05
- **Map zoom:** Smooth easeInOut (MapLibre built-in)
- **POI marker tap:** Scale 1.0→1.2→1.0 bounce (200ms)
- **Heart (kudos) tap:** Scale 0→1.3→1.0 + red color burst (300ms)
- **Download progress:** Smooth fill animation (linear, соответствует реальной скорости)

### 7.3 Loading states

- **Skeleton loading** для lists (Feed, Tracks, Saved Routes):
  - Grey shimmer placeholder в форме строк
  - Duration: 1500ms loop
- **Spinner** для one-shot actions (Plan route, Login):
  - CircularProgressIndicator, 24dp, `primary`
- **Inline** для button (Plan route during planning):
  - Button → disabled + spinner 20dp inside

---

## 8. Accessibility

### 8.1 Минимум

- **Touch targets:** min 48×48dp для всех interactive elements
- **Contrast ratio:** ≥ 4.5:1 для text, ≥ 3:1 для large text и graphics
- **Content descriptions:** для всех icons и images
- **Focus order:** логичный (top-to-bottom, left-to-right)
- **Dynamic Type:** уважать `fontScale` пользователя

### 8.2 Дополнительно

- **TalkBack:** семантические роли для всех элементов (`Role.Button`, `Tab`, etc.)
- **Keyboard navigation:** Tab/Shift+Tab для всех интерактивных элементов
- **Reduced motion:** `Settings.Global.ANIMATOR_DURATION_SCALE` → отключить анимации

---

## 9. Dark/Light Theme

**Стратегия:**
- System default: следовать системной теме
- Manual override: через Settings (Системная / Тёмная / Светлая)
- Map theme: связан с UI theme (тёмная карта для тёмной темы, светлая для светлой)
- При переключении: smooth cross-fade (200ms)

**Light theme map colors:**
- Background: `#E8ECF0`
- Roads: те же brand colors, но на 20% темнее (для контраста)
- Water: `#A8D0E8`
- Place labels: `#1E252D` text, white halo
- Road names: `#4B5563`, size 10sp

---

## 10. Иконки

**Library:** Material Symbols (Outlined variant)

**Custom icons needed:**
- Bicycle (для brand, tabs, markers) — outlined, 24dp
- Mountain pass — triangle с точкой
- Spring — капля воды
- Campsite — палатка
- Bicycle repair — гаечный ключ + колесо
- Drinking water — кран/капля
- Surface type indicator (для legend) — короткие полоски в цвете

**Icon mapping (POI categories):**

| Category | Icon | Color |
|---|---|---|
| Pharmacy | `local_pharmacy` | `#E53935` |
| Hospital | `local_hospital` | `#E53935` |
| Fuel | `local_gas_station` | `#F57C00` |
| Water | `water_drop` | `#1E88E5` |
| Cafe | `coffee` | `#795548` |
| Restaurant | `restaurant` | `#5D4037` |
| Shop | `store` | `#558B2F` |
| Hotel | `hotel` | `#6A1B9A` |
| ATM | `atm` | `#455A64` |
| Bicycle service | `pedal_bike` | `#4CAF50` |
| Bicycle rental | `directions_bike` | `#4CAF50` |
| Campsite | `holiday_village` | `#8D6E63` |
| Viewpoint | `photo_camera` | `#78909C` |

---

## 11. Файлы для изменения

### Существующие (править):

| Файл | Что править |
|---|---|
| `core/designsystem/.../theme/Color.kt` | Добавить light/dark palette |
| `core/designsystem/.../theme/Theme.kt` | Dynamic color + light theme support |
| `core/designsystem/.../theme/Type.kt` | Inter font family |
| `feature/map/.../screen/MapScreen.kt` | FAB cluster, top bar, POI chips |
| `feature/map/.../style/MapStyleProvider.kt` | Light theme style JSON |
| `feature/map/.../view/PoiMarkerLayer.kt` | Circular markers with icons |
| `feature/routing/.../screen/RouteBuilderScreen.kt` | Drag-reorder, elevation profile |
| `feature/tracks/.../screen/TracksScreen.kt` | Ride cards with mini map preview |
| `feature/tracks/.../screen/TrackDetailScreen.kt` | Stat grid, elevation profile |
| `feature/regions/.../screen/RegionsScreen.kt` | Active region indicator |
| `app/.../screens/SettingsScreen.kt` | Expand sections |
| `app/.../nav/TwoCircleNavHost.kt` | 5 tabs (add Community + Profile) |

### Новые (создать):

| Файл | Описание |
|---|---|
| `core/designsystem/.../components/FabCluster.kt` | Expandable FAB group |
| `core/designsystem/.../components/SurfaceBreakdownBar.kt` | Proportional surface bar |
| `core/designsystem/.../components/StatGrid.kt` | 2×2 or 2×3 stat card grid |
| `core/designsystem/.../components/ElevationProfile.kt` | Canvas-drawn elevation chart |
| `core/designsystem/.../components/PoiMarker.kt` | Circular marker with icon |
| `core/designsystem/.../components/DropPin.kt` | Animated long-press pin |
| `core/designsystem/.../components/SkeletonLoader.kt` | Shimmer placeholder |
| `feature/auth/.../LoginScreen.kt` | Login UI |
| `feature/auth/.../RegisterScreen.kt` | Register UI |
| `feature/profile/.../ProfileScreen.kt` | User profile |
| `feature/feed/.../FeedScreen.kt` | Activity feed |
| `feature/feed/.../FeedCard.kt` | Ride card in feed |

---

## 12. Приоритеты для UI-дизайнера

### Sprint 1 (неделя 1) — Critical UX:
1. Map screen: top bar + FAB cluster + POI chips bar
2. Route Builder: drag-reorder + surface breakdown bar + elevation profile
3. Track cards с mini-map preview

### Sprint 2 (неделя 2) — Polish:
4. Track Detail: stat grid + elevation profile
5. Settings: expand sections + theme switcher
6. Regions: active region indicator + multi-region switch
7. Light theme (полная)

### Sprint 3 (неделя 3) — New screens:
8. Login / Register screens
9. Profile screen
10. Feed screen + feed cards

### Sprint 4 (неделя 4) — Animations:
11. Screen transitions (shared elements)
12. Skeleton loaders
13. Micro-interactions (heart, FAB, chip)
14. Splash animation v2

---

## 13. Референсы (для вдохновения)

| App | Что взять |
|---|---|
| **Komoot** | Map overlay UI, surface-type coloring, route planning flow, elevation profile |
| **Strava** | Feed card design, stat grid, segment analysis, achievements/badges |
| **RideWithGPS** | Cue sheet style, detailed route stats, climb categorization |
| **OsmAnd** | Long-press menu, multi-layer map, POI categories depth |
| **Google Maps** | Bottom sheet patterns, search bar, POI chip bar |
| **Apple Fitness** | Activity ring animation, clean stat cards, workout summary |
| **Wahoo ELEMNT** | Live HUD layout during ride (speed, HR, power) |

---

*Документ создан для UI/UX специалиста 2circle. Вопросы и предложения приветствуются.*
