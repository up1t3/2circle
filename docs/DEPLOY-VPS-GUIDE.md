# 2circle — Инструкция по деплою backend на VPS

> Для сотрудника (исполнителя). Выполнять по шагам. При проблемах — писать.

---

## Что разворачиваем

```
VPS (Ubuntu 22.04+)
├── Docker
│   ├── PostgreSQL 16 (порт 5432) — база данных
│   └── Redis 7 (порт 6379) — кеш/сессии
├── Ktor сервер (порт 8080) — API: auth + rides
├── Python serve.py (порт 8765) — region downloads
└── Nginx (опционально) — reverse proxy + HTTPS
```

---

## Шаг 0: Подготовка VPS

```bash
# Подключиться по SSH
ssh root@VPS_IP

# Обновить систему
apt update && apt upgrade -y

# Установить Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh

# Проверить
docker --version
docker-compose --version

# Установить Java 21 (для Ktor)
apt install -y openjdk-21-jdk
java -version

# Установить Python 3 (для serve.py)
apt install -y python3

# Открыть порты в фаерволе
ufw allow 8080/tcp   # API
ufw allow 8765/tcp   # region downloads
ufw allow 443/tcp    # HTTPS (если будет домен)
ufw allow 22/tcp     # SSH
ufw enable
```

**Проверка:** `java -version` должно показать 21.x

---

## Шаг 1: Перенести код на сервер

```bash
# На сервере
mkdir -p /opt/2circle
cd /opt/2circle

# Вариант A: клонировать с GitHub
apt install -y git
git clone https://github.com/up1t3/2circle.git .

# Вариант B: scp с локальной машины (если нет git доступа)
# На локальной машине:
# scp -r E:/2circle/backend-server root@VPS_IP:/opt/2circle/
# scp -r E:/2circle/backend root@VPS_IP:/opt/2circle/
```

**Проверка:** `ls /opt/2circle/backend-server/docker-compose.yml` — файл существует

---

## Шаг 2: Запустить PostgreSQL + Redis

```bash
cd /opt/2circle/backend-server

# Запустить БД и кеш
docker-compose up -d

# Проверить что контейнеры работают
docker-compose ps
# Должно быть: twocircle_db (healthy) + twocircle_redis (healthy)

# Проверить подключение к БД
docker exec -it twocircle_db psql -U twocircle -d twocircle -c "\dt"
# Должно показать таблицы: users, rides (если Ktor уже запускался)
# Если пусто — нормально, таблицы создаст Ktor при первом запуске
```

**Проверка:** `docker-compose ps` показывает оба контейнера healthy

---

## Шаг 3: Собрать и запустить Ktor сервер

```bash
cd /opt/2circle/backend-server

# Установить Gradle (если нет)
apt install -y gradle
# ИЛИ использовать wrapper:
# (скопировать gradlew из основного проекта)

# Собрать сервер
./gradlew installDist  # или: gradle installDist

# Бинарник будет здесь:
ls build/install/backend-server/bin/
# backend-server (исполняемый)

# Настроить переменные окружения
export DB_URL="jdbc:postgresql://localhost:5432/twocircle"
export DB_USER="twocircle"
export DB_PASSWORD="twocircle_dev"
export JWT_SECRET="CHANGE_THIS_TO_RANDOM_64_CHAR_STRING"

# Сгенерировать случайный секрет:
openssl rand -hex 32
# Вставить результат в JWT_SECRET

# Запустить сервер (в фоне через nohup или systemd)
nohup ./build/install/backend-server/bin/backend-server > /var/log/2circle.log 2>&1 &

# Проверить что запустился
curl http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"12345678","displayName":"Test"}'

# Должен вернуться JSON с токенами:
# {"accessToken":"...","refreshToken":"...","user":{...}}
```

**Проверка:** `curl` возвращает JSON с accessToken

---

## Шаг 3.5: Создать systemd сервис (чтобы сервер не падал)

```bash
cat > /etc/systemd/system/2circle-api.service << 'EOF'
[Unit]
Description=2circle API Server (Ktor)
After=network.target docker.service
Requires=docker.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/2circle/backend-server
Environment=DB_URL=jdbc:postgresql://localhost:5432/twocircle
Environment=DB_USER=twocircle
Environment=DB_PASSWORD=twocircle_dev
Environment=JWT_SECRET=ВСТАВИТЬ_СГЕНЕРИРОВАННЫЙ_СЕКРЕТ_СЮДА
ExecStart=/opt/2circle/backend-server/build/install/backend-server/bin/backend-server
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

# Активировать
systemctl daemon-reload
systemctl enable 2circle-api
systemctl start 2circle-api

# Проверить статус
systemctl status 2circle-api
# Должно быть: active (running)

# Логи
journalctl -u 2circle-api -f
```

---

## Шаг 4: Запустить region downloads сервер

```bash
cd /opt/2circle/backend/regions

# Убедиться что файлы регионов есть
ls
# Должно быть: manifest.json, andorra/, ural-russia/, ...

# Если регионов нет — скопировать с локальной машины:
# scp -r E:/2circle/backend/regions/* root@VPS_IP:/opt/2circle/backend/regions/

# Запустить serve.py в фоне
nohup python3 /opt/2circle/backend/bin/serve.py 8765 /opt/2circle/backend/regions > /var/log/2circle-regions.log 2>&1 &

# Или через systemd:
cat > /etc/systemd/system/2circle-regions.service << 'EOF'
[Unit]
Description=2circle Region Download Server
After=network.target

[Service]
Type=simple
User=root
ExecStart=/usr/bin/python3 /opt/2circle/backend/bin/serve.py 8765 /opt/2circle/backend/regions
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable 2circle-regions
systemctl start 2circle-regions

# Проверить
curl http://localhost:8765/manifest.json | head -5
# Должен вернуть JSON с регионами
```

**Проверка:** `curl` возвращает JSON каталога регионов

---

## Шаг 5: Обновить manifest.json с IP сервера

```bash
cd /opt/2circle/backend/regions

# Узнать публичный IP сервера
curl ifconfig.me

# Обновить download_url в manifest.json
# Заменить 192.168.1.42 на ВАШ ПУБЛИЧНЫЙ IP:
sed -i 's/192.168.1.42/VPS_PUBLIC_IP/g' manifest.json

# Проверить
cat manifest.json | grep download_url
```

---

## Шаг 6: Настроить Android клиент

На машине разработчика:

```bash
# В app/build.gradle.kts заменить:
# debug { MANIFEST_URL = "http://192.168.1.42:8765/manifest.json" }
# НА:
# debug { MANIFEST_URL = "http://VPS_PUBLIC_IP:8765/manifest.json" }

# В app/src/main/res/xml/network_security_config.xml добавить IP сервера:
# <domain includeSubdomains="false">VPS_PUBLIC_IP</domain>

# В feature/auth/.../AuthRepository.kt (или новом AuthApi.kt):
# baseUrl = "http://VPS_PUBLIC_IP:8080/"

# Собрать APK
./gradlew assembleDebug

# Установить на телефон
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Шаг 7: Полный тест

### 7.1 Тест API (с сервера)
```bash
# Регистрация
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"rider@test.com","password":"12345678","displayName":"Test Rider"}'

# Логин
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"rider@test.com","password":"12345678"}'

# Получить токен из ответа, затем:
TOKEN="полученный_access_token"

# Создать поездку
curl -X POST http://localhost:8080/api/v1/rides/sync \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"rides":[{"externalId":"test-1","name":"Test Ride","distanceMeters":15000,"durationSeconds":3600,"ascentMeters":100,"descentMeters":90,"avgSpeedMps":4.17,"startedAtMs":1700000000000,"endedAtMs":1700003600000}]}'

# Получить список поездок
curl http://localhost:8080/api/v1/rides \
  -H "Authorization: Bearer $TOKEN"
```

### 7.2 Тест на телефоне
1. Установить APK
2. Профиль → Войти → регистрация → должно работать через API
3. Регионы → Обновить → скачать Andorra
4. Записать трек → остановить → проверить что синхронизировался

---

## Чек-лист готовности

```
□ VPS куплен, SSH доступ работает
□ Docker установлен
□ PostgreSQL + Redis запущены (docker-compose ps → healthy)
□ Ktor сервер собран и запущен (systemctl status 2circle-api → running)
□ curl register возвращает JSON с токенами
□ serve.py запущен (systemctl status 2circle-regions → running)
□ curl manifest.json возвращает список регионов
□ manifest.json обновлён с публичным IP
□ Android APK собран с правильным IP
□ APK установлен на телефон
□ Регистрация с телефона работает
□ Скачивание региона с телефона работает
□ Запись трека + cloud sync работает
```

---

## Полезные команды

```bash
# Логи API
journalctl -u 2circle-api -f

# Логи регионов
journalctl -u 2circle-regions -f

# Перезапустить API
systemctl restart 2circle-api

# Перезапустить БД
cd /opt/2circle/backend-server && docker-compose restart

# Статус всех сервисов
systemctl status 2circle-api 2circle-regions
docker-compose -f /opt/2circle/backend-server/docker-compose.yml ps
```

---

## Проблемы и решения

| Проблема | Решение |
|---|---|
| `Connection refused :8080` | Ktor не запущен → `systemctl start 2circle-api` |
| `Connection refused :8765` | serve.py не запущен → `systemctl start 2circle-regions` |
| `psql: connection refused` | Docker не запущен → `docker-compose up -d` |
| Телефон не видит сервер | Проверить фаервол: `ufw status`, добавить порт |
| Android: CLEARTEXT not permitted | Добавить IP в network_security_config.xml |
| Ktor: OutOfMemory | Увеличить JVM: `JAVA_OPTS="-Xmx512m"` |
| Docker: no space left | `docker system prune -a` |

---

## Контакт

При проблемах — писать в чат с описанием:
1. На каком шаге остановились
2. Точный текст ошибки
3. Вывод `journalctl -u 2circle-api --no-pager -n 50`
