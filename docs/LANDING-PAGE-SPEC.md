# 2circle.ru — Лендинг: Детальное ТЗ

> Для frontend-разработчика. Современный одностраничный сайт с загрузкой APK.
> Домен: **2circle.ru**

---

## 1. Концепция

**Тёмная тема**, премиальный велосюрприз-вайб. Карта с раскрашенными дорогами как главный визуальный герой. Минимализм + typography-driven дизайн.

**Референсы:**
- [Komoot.com](https://www.komoot.com) — тёмные секции, крупная типографика
- [Strava.com](https://www.strava.com) — динамичные карточки, статистика
- [Linear.app](https://linear.app) — градиенты, стекло (glassmorphism), микроанимации

---

## 2. Цветовая система

```css
:root {
  /* Фон */
  --bg-primary: #0D1117;      /* основной тёмный */
  --bg-secondary: #161B22;    /* карточки */
  --bg-tertiary: #21262D;     /* hover, borders */

  /* Бренд */
  --green: #4CAF50;           /* asphalt — primary */
  --green-light: #66BB6A;
  --blue: #2C5F7E;            /* water */
  --orange: #FF9800;          /* gravel */
  --red: #F44336;             /* sand */
  --brown: #8D6E63;           /* dirt */

  /* Текст */
  --text-primary: #E6EDF3;
  --text-secondary: #8B949E;
  --text-muted: #484F58;

  /* Акценты */
  --gradient-hero: linear-gradient(135deg, #0D1117 0%, #1A2E1A 50%, #0D1117 100%);
  --gradient-cta: linear-gradient(135deg, #4CAF50 0%, #2C5F7E 100%);
  --gradient-card: linear-gradient(180deg, rgba(38,47,56,0.6) 0%, rgba(13,17,23,0.9) 100%);
}
```

---

## 3. Типографика

**Шрифт:** [Inter](https://fonts.google.com/specimen/Inter) (Google Fonts, variable)

| Класс | Размер | Вес | Использование |
|---|---|---|---|
| `.hero-title` | 72px / clamp(40px, 8vw, 72px) | 800 | Заголовок на главной |
| `.hero-subtitle` | 24px / clamp(18px, 3vw, 24px) | 400 | Подзаголовок |
| `.section-title` | 48px / clamp(28px, 5vw, 48px) | 700 | Заголовки секций |
| `.section-subtitle` | 18px | 400 | Описание секций |
| `.feature-title` | 24px | 600 | Заголовки фич |
| `.feature-text` | 16px | 400 | Описания фич |
| `.stat-number` | 56px | 800 | Большие цифры |
| `.stat-label` | 14px | 500 | Подписи к цифрам |
| `.cta-text` | 20px | 600 | Текст на кнопках |

---

## 4. Структура страницы (секции)

### 4.1 Навигация (sticky header)

```
┌──────────────────────────────────────────────────────┐
│  ⚫⚫ 2circle                    Возможности  Скачать  │
└──────────────────────────────────────────────────────┘
```

- Высота: 64px
- Background: `rgba(13,17,23,0.85)` + `backdrop-filter: blur(12px)`
- Логотип: два круга (SVG, 28×28px) + текст "2circle"
- Справа: якорные ссылки "Возможности", "Скриншоты", "Скачать"
- При скролле — sticky, появляется тонкая нижняя граница `1px solid var(--bg-tertiary)`
- **Mobile:** гамбургер → выпадающее меню

### 4.2 Hero (главный экран)

```
┌──────────────────────────────────────────────────────┐
│                                                       │
│                                                       │
│   Велопутешествия                                    │
│   без границ                                          │
│                                                       │
│   Офлайн-карты с раскраской дорог по покрытию.       │
│   Голосовая навигация. Запись треков.               │
│   Сообщество райдеров.                               │
│                                                       │
│   [  ⬇ Скачать APK ]    [ Смотреть возможности → ]  │
│                                                       │
│   ● Android 8.0+  ● Бесплатно  ● Без рекламы         │
│                                                       │
│                          [ Мокап телефона с картой ] │
│                          [ зелёные/оранжевые дороги ] │
│                          [ круги = бренд ]            │
│                                                       │
└──────────────────────────────────────────────────────┘
```

- Background: `var(--gradient-hero)`
- Слева: текст + кнопки. Справа: **мокап телефона** (CSS/SVG) с превью карты
- **CTA кнопка "Скачать APK":** `var(--gradient-cta)`, 56px высота, `border-radius: 16px`, тень `0 8px 32px rgba(76,175,80,0.3)`
- Вторичная кнопка: `transparent` + `1px solid var(--text-muted)`, hover → `var(--bg-tertiary)`
- Бейджи под кнопками: кружок + текст, `var(--text-secondary)`, 14px
- **Анимация при загрузке:** текст fade-in + slide-up (300ms), мокап fade-in + scale (500ms)
- **Параллакс:** мокап слегка двигается при скролле (`transform: translateY(calc(var(--scroll) * 0.3))`)

**Мокап телефона:**
- SVG: рамка телефона (dark, rounded 32px), экран с картой
- На карте: зелёные линии (асфальт), оранжевые (гравий), синие пятна (вода)
- Белые надписи (имитация place labels)
- POI маркеры (жёлтые точки)
- Размер: `380×560px` (desktop), `full-width` (mobile)

### 4.3 Полоса преимуществ (stats bar)

```
┌──────────────────────────────────────────────────────┐
│                                                       │
│    20+         9            100%          3           │
│   ТИПОВ       КАТЕГОРИЙ    ОФФЛАЙН      ЯЗЫКА        │
│   ПОКРЫТИЯ    POI          БЕЗ СЕТИ     РУ/EN/ES     │
│                                                       │
└──────────────────────────────────────────────────────┘
```

- Background: `var(--bg-secondary)`
- 4 колонки (grid), centered
- Цифры: `var(--green)`, 56px, 800 weight
- Подписи: `var(--text-secondary)`, 14px, uppercase, letter-spacing 1px
- **Анимация:** счётчик (count-up) при появлении в viewport (IntersectionObserver)

### 4.4 Секция "Возможности" (feature grid)

```
┌──────────────────────────────────────────────────────┐
│                                                       │
│              Почему 2circle?                          │
│              Всё, что нужно райдеру в дороге         │
│                                                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐           │
│  │ 🗺        │  │ 🔊        │  │ 📍        │           │
│  │ КАРТЫ    │  │ НАВИГАЦИЯ │  │ POI       │           │
│  │ Офлайн   │  │ Голосовые │  │ Аптеки    │           │
│  │ векторные│  │ подсказки │  │ Кафе      │           │
│  │ тайлы с  │  │ поворотов │  │ Заправки  │           │
│  │ раскрас- │  │ и проклад-│  │ и 9 кат.  │           │
│  │ кой дорог│  │ кой марш. │  │ — всё     │           │
│  │          │  │           │  │ оффлайн   │           │
│  └──────────┘  └──────────┘  └──────────┘           │
│                                                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐           │
│  │ 🚴        │  │ 📊        │  │ 👥        │           │
│  │ ТРЕКИ    │  │ ДАННЫЕ   │  │ СООБЩЕСТВО│           │
│  │ Запись   │  │ Графики  │  │ Лента     │           │
│  │ GPX      │  │ высот    │  │ Kudos     │           │
│  │ Импорт/  │  │ Скорость │  │ Достижения│           │
│  │ Экспорт  │  │ Покрытие │  │ Профиль   │           │
│  └──────────┘  └──────────┘  └──────────┘           │
│                                                       │
└──────────────────────────────────────────────────────┘
```

- Background: `var(--bg-primary)`
- Grid: 3 колонки (desktop), 2 (tablet), 1 (mobile)
- Карточка: `var(--bg-secondary)`, `border-radius: 20px`, padding 32px, `border: 1px solid var(--bg-tertiary)`
- Hover: lift effect (`translateY(-4px)`), border → `var(--green)` (200ms ease)
- Иконка: 48px, эмодзи или SVG (в брендовых цветах)
- Заголовок: `var(--text-primary)`, 24px, 600
- Текст: `var(--text-secondary)`, 16px
- **Анимация:** карточки fade-in + slide-up при появлении, stagger 100ms

### 4.5 Секция "Скриншоты" (галерея)

```
┌──────────────────────────────────────────────────────┐
│                                                       │
│              Как это выглядит                         │
│                                                       │
│   ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐   │
│   │ Скрин  │  │ Скрин  │  │ Скрин  │  │ Скрин  │   │
│   │ карты  │  │ поиска │  │ маршру │  │ ленты  │   │
│   │        │  │        │  │ та     │  │        │   │
│   └────────┘  └────────┘  └────────┘  └────────┘   │
│                                                       │
│   ← прокрутка →                                      │
│                                                       │
└──────────────────────────────────────────────────────┘
```

- Background: `var(--bg-secondary)`
- Горизонтальный скролл (scroll-snap) или grid 4 колонки
- Картинка: `border-radius: 16px`, `overflow: hidden`, aspect ratio 9:16 (phone)
- Рамка: `2px solid var(--bg-tertiary)`, shadow `0 16px 48px rgba(0,0,0,0.4)`
- Hover: scale(1.03) + border → `var(--green)`
- Tap/click → lightbox (полноэкранный просмотр)
- **Скриншоты:** сделать 4-6 скриншотов из эмулятора (карта, поиск, маршрут, трек, лента, профиль)

### 4.6 Секция "Road surface" (уникальная фича)

```
┌──────────────────────────────────────────────────────┐
│                                                       │
│   Уникальная фича:                                    │
│   Раскраска дорог по покрытию                         │
│                                                       │
│   ┌──────────────────────────────────────────────┐   │
│   │                                                │   │
│   │   [карта-превью с разноцветными дорогами]      │   │
│   │                                                │   │
│   │   ━━━━━ Асфальт (зелёный)                     │   │
│   │   ━━━━━ Гравий (оранжевый)                    │   │
│   │   ━━━━━ Грунт (коричневый)                    │   │
│   │   ━━━━━ Песок (красный)                       │   │
│   │                                                │   │
│   └──────────────────────────────────────────────┘   │
│                                                       │
│   Знайте заранее, что вас ждёт на маршруте.          │
│   Никаких сюрпризов с покрытием.                     │
│                                                       │
└──────────────────────────────────────────────────────┘
```

- Background: `var(--gradient-hero)` (другой угол)
- Слева: текст + легенда цветов. Справа: карта-превью (большое)
- Легенда: цветные полоски (8px высота, 40px ширина) + текст
- **Анимация:** линии дорог "рисуются" при появлении (SVG stroke-dashoffset animation)

### 4.7 Секция "Скачать" (CTA)

```
┌──────────────────────────────────────────────────────┐
│                                                       │
│                                                       │
│              Начните своё путешествие                │
│              прямо сейчас                             │
│                                                       │
│                                                       │
│        [  ⬇ Скачать APK (63 МБ)  ]                   │
│                                                       │
│        Android 8.0+ · v2.0 · Бесплатно               │
│                                                       │
│        или отсканируйте QR-код →                      │
│                                                       │
│                                          ┌─────────┐ │
│                                          │ QR CODE │ │
│                                          │         │ │
│                                          └─────────┘ │
│                                                       │
└──────────────────────────────────────────────────────┘
```

- Background: `var(--gradient-cta)` (зелёно-синий градиент)
- Огромная кнопка: white background, dark text, 64px height, `border-radius: 20px`
- Ссылка кнопки: прямая загрузка APK с сервера
- QR-код: генерировать через API (`https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=https://2circle.ru/app-debug.apk`)
- Размер APK и версия — актуальные

### 4.8 Footer

```
┌──────────────────────────────────────────────────────┐
│  ⚫⚫ 2circle                                          │
│  Офлайн-навигатор для велопутешествий                │
│                                                       │
│  © OpenStreetMap contributors    © Copernicus DEM    │
│  GitHub: github.com/up1t3/2circle                     │
│  Контакт: hello@2circle.ru                            │
│                                                       │
│  © 2026 2circle. All rights reserved.                │
└──────────────────────────────────────────────────────┘
```

- Background: `var(--bg-primary)`
- Верхняя граница: `1px solid var(--bg-tertiary)`
- OSM attribution — обязательно (лицензия ODbL)

---

## 5. Технические требования

### 5.1 Стек

| Компонент | Технология |
|---|---|
| HTML | Семантический HTML5 |
| CSS | Tailwind CSS (рекомендую) или vanilla CSS |
| JS | Vanilla JS (не нужен React для лендинга) |
| Шрифты | Google Fonts: Inter (variable) |
| Иконки | SVG inline или [Heroicons](https://heroicons.com) |
| Анимации | CSS `@keyframes` + IntersectionObserver для scroll-trigger |

### 5.2 Производительность

- LCP (Largest Contentful Paint): < 2.5s
- FID (First Input Delay): < 100ms
- CLS (Cumulative Layout Shift): < 0.1
- Изображения: WebP, lazy loading
- CSS: минифицированный, inline critical CSS
- JS: минифицированный, `defer`
- Шрифт: `font-display: swap`

### 5.3 SEO

```html
<title>2circle — Офлайн-навигатор для велопутешествий</title>
<meta name="description" content="Офлайн-карты с раскраской дорог по покрытию, голосовая навигация, POI, запись треков и сообщество райдеров. Android, бесплатно.">
<meta name="keywords" content="велонавигатор, офлайн карты, велосипед, GPX, маршруты, brouter">
<meta property="og:title" content="2circle — Велопутешествия без границ">
<meta property="og:description" content="Офлайн-карты с раскраской дорог. Голосовая навигация. Запись треков.">
<meta property="og:image" content="https://2circle.ru/og-image.png">
<meta property="og:type" content="website">
<meta property="og:locale" content="ru_RU">
```

### 5.4 Favicon

- SVG favicon: два круга (зелёный + синий) на прозрачном фоне
- `<link rel="icon" href="/favicon.svg" type="image/svg+xml">`
- Также PNG fallback 32×32 и Apple touch icon 180×180

---

## 6. Размещение APK для скачивания

### 6.1 На VPS (72.56.238.106)

```bash
# Создать директорию для сайта
mkdir -p /var/www/2circle.ru

# Скопировать APK на сервер
scp app-debug.apk root@72.56.238.106:/var/www/2circle.ru/

# Структура:
/var/www/2circle.ru/
├── index.html          # лендинг
├── style.css           # стили
├── app-debug.apk       # APK для скачивания
├── favicon.svg
├── og-image.png        # 1200×630 для соцсетей
└── screenshots/        # скриншоты для галереи
    ├── map.png
    ├── search.png
    ├── route.png
    ├── tracks.png
    ├── feed.png
    └── profile.png
```

### 6.2 Nginx (обратный прокси + статика)

```nginx
# /etc/nginx/sites-available/2circle.ru
server {
    listen 80;
    server_name 2circle.ru www.2circle.ru;

    root /var/www/2circle.ru;
    index index.html;

    # Лендинг
    location / {
        try_files $uri $uri/ =404;
    }

    # APK загрузка
    location /app-debug.apk {
        add_header Content-Disposition "attachment";
        add_header Content-Type "application/vnd.android.package-archive";
    }

    # API проксирование (опционально — для HTTPS)
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

```bash
ln -s /etc/nginx/sites-available/2circle.ru /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx
```

---

## 7. Настройка DNS (домен 2circle.ru)

### Если домен на Reg.ru:

1. Войти в личный кабинет Reg.ru
2. Домены → 2circle.ru → DNS-серверы / Управление зоной

### Записи для добавления:

| Тип | Хост | Значение | TTL |
|---|---|---|---|
| **A** | `@` | `72.56.238.106` | 3600 |
| **A** | `www` | `72.56.238.106` | 3600 |

### Если нужна почта (hello@2circle.ru):
| Тип | Хост | Значение | Приоритет |
|---|---|---|---|
| **MX** | `@` | `mx.yandex.ru` | 10 |
| **TXT** | `@` | `v=spf1 redirect=_spf.yandex.ru` | — |

(почта через Яндекс 360 для бизнеса — бесплатно)

### Проверка DNS:

```bash
# После настройки (5-30 минут):
dig 2circle.ru A
# Должно вернуть: 72.56.238.106

curl http://2circle.ru
# Должна открыться страница лендинга
```

---

## 8. HTTPS (бесплатно через Let's Encrypt)

**После настройки DNS:**

```bash
apt install -y certbot python3-certbot-nginx
certbot --nginx -d 2circle.ru -d www.2circle.ru
# Выбрать: автоматический редирект HTTP → HTTPS
```

После этого:
- `https://2circle.ru` — работает с HTTPS
- Сертификат автоматически обновляется (cron)
- Android API может ходить на `https://2circle.ru/api/...` (без cleartext)

---

## 9. QR-код для скачивания

Сгенерировать QR-код pointing на:
```
https://2circle.ru/app-debug.apk
```

Использовать:
```bash
# На сервере:
curl -o /var/www/2circle.ru/qr-code.png \
  "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=https://2circle.ru/app-debug.apk&bgcolor=13-17-23&color=230-237-243"
```

Или встроить в HTML:
```html
<img src="https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=https://2circle.ru/app-debug.apk" alt="Скачать APK">
```

---

## 10. Итоговый чек-лист

```
□ Домен 2circle.ru куплен
□ DNS: A-запись @ → 72.56.238.106
□ DNS: A-запись www → 72.56.238.106
□ VPS: Nginx установлен и настроен
□ /var/www/2circle.ru/index.html — лендинг загружен
□ /var/www/2circle.ru/app-debug.apk — APK доступен
□ /var/www/2circle.ru/screenshots/ — скриншоты загружены
□ HTTPS через certbot настроен
□ http://2circle.ru открывается
□ https://2circle.ru открывается (с замочком)
□ Скачивание APK по ссылке работает
□ QR-код генерируется и ведёт на APK
□ Open Graph tags настроены (превью в Telegram/WhatsApp)
□ Favicon отображается во вкладке браузера
```

---

## 11. Результат

Пользователь заходит на **2circle.ru**:
1. Видит красивый тёмный лендинг с превью карты
2. Читает про фичи (раскраска дорог, TTS, POI)
3. Нажимает "Скачать APK" или сканирует QR
4. Устанавливает на Android
5. Регистрируется (через API на том же сервере)
6. Скачивает регион (с того же сервера)
7. Едет 🚴
