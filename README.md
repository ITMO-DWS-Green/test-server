# test-server

Тестовый сервер для [log-collector](https://github.com/ITMO-DWS-Green/log-collector). Он изображает цепочку сервисов, в которой можно по кнопке включать сбои: ответы 5xx и 4xx, задержки, зависания, недоступность, падение процесса, нагрузку на процессор, утечку памяти, поток ошибок в логах. log-collector подключается к стенду и должен эти сбои обнаружить.

## Быстрый старт

```bash
docker compose up --build
```

Откройте **http://localhost:8090** — это панель управления. На ней:
- все сервисы стенда, их текущий сценарий и живая статистика (запросы в секунду, доля 5xx и 4xx, задержка);
- переключение сценария на любом сервисе;
- запуск готовых плейбуков — последовательностей сбоев по времени.

Поднимаются четыре экземпляра, которые вызывают друг друга:

```
gateway → orders → payments → database
                 ↘ database
```

| Сервис | Порт | Вызывает |
|---|---|---|
| gateway | 8090 | orders. Генерирует трафик сам — 10 запросов в секунду |
| orders | 8091 | payments, database |
| payments | 8092 | database |
| database | 8093 | — |

Каждый запрос к gateway проходит по цепочке. Если сломать database, у вызывающих сервисов появятся ошибки 502 и 504 — так выглядит каскадный сбой, и log-collector должен указать на настоящий источник.

## Подключение к log-collector

1. Запустите log-collector так, чтобы он слушал порт 8080 (`docker compose up` в его репозитории или `./gradlew :app:bootRun`).
2. Запустите стенд с адресом log-collector:
   ```bash
   COLLECTOR_URL=http://host.docker.internal:8080 docker compose up --build
   ```

Раз в секунду каждый сервис отправляет в log-collector (`POST /api/v1/telemetry`):
- **записи лога**: журнал запросов, ошибки, предупреждения, со сквозным `traceId` через всю цепочку;
- **метрики**: `rps`, `latency_ms`, `error_rate` (доля 5xx), `client_error_rate` (доля 4xx), `cpu_usage`, `memory_mb`.

Когда сбой заканчивается, сервис отправляет его **разметку** (`POST /api/v1/labels`): сценарий, тип, сервис-источник, начало и конец. По ней log-collector считает precision, recall и задержку обнаружения.

`client_error_rate` пока не входит в список метрик log-collector (`core/.../model/Metrics.java`) — его нужно туда добавить.

## Сценарии

| Сценарий | Что происходит | Параметры | Тип сбоя в разметке |
|---|---|---|---|
| `NORMAL` | Работает нормально | — | — |
| `ERRORS_5XX` | 500 на долю запросов, в логе исключения | `rate` (0–1) | ERROR_BURST |
| `ERRORS_4XX` | 400/404/409/429 на долю запросов | `rate` (0–1) | ERROR_BURST |
| `LATENCY` | Каждый ответ с задержкой | `delayMs` (2000) | LATENCY_SPIKE |
| `HANG` | Запросы висят — у вызывающих таймауты, потоки сервера кончаются | `delayMs` (60000) | LATENCY_SPIKE |
| `UNAVAILABLE` | 503 на всё, `/actuator/health` — DOWN | — | SERVICE_DOWN |
| `CRASH` | Процесс завершается с кодом 1; Docker поднимает его заново | — | SERVICE_DOWN |
| `CPU_LOAD` | Потоки нагружают процессор | `intensity` — потоков (2) | RESOURCE_LEAK |
| `MEMORY_LEAK` | Память растёт до 90% кучи | `intensity` — МБ в секунду (10) | RESOURCE_LEAK |
| `LOG_FLOOD` | Поток ERROR-записей в логе | `intensity` — строк в секунду (50) | ERROR_BURST |

У любого сценария есть `durationSeconds`: через столько секунд сервис сам вернётся в `NORMAL`. Без этого параметра сценарий действует, пока его не выключат.

Для `CRASH` процесс не узнает, когда его перезапустили, поэтому разметку он отправляет сразу. Длительность сбоя в ней — `testserver.collector.crash-label-seconds` (по умолчанию 30 секунд).

## Управление через API

Всё, что делает панель, доступно через HTTP. Параметр `target` — имя сервиса. Без него команда относится к тому экземпляру, которому вы её отправили.

```bash
# database отвечает с задержкой 3 с в течение 2 минут
curl -X POST 'localhost:8090/chaos/scenario?target=database' \
  -H 'Content-Type: application/json' \
  -d '{"type": "LATENCY", "delayMs": 3000, "durationSeconds": 120}'

# payments: 30% ответов — 500
curl -X POST 'localhost:8090/chaos/scenario?target=payments' \
  -H 'Content-Type: application/json' -d '{"type": "ERRORS_5XX", "rate": 0.3}'

# вернуть норму
curl -X DELETE 'localhost:8090/chaos/scenario?target=payments'

# изменить встроенный трафик
curl -X PUT localhost:8090/chaos/traffic -H 'Content-Type: application/json' -d '{"rps": 50}'
```

| Метод | Путь | Что делает |
|---|---|---|
| GET | `/chaos/status` | Статус этого экземпляра |
| GET | `/chaos/status/all` | Статусы всех сервисов стенда |
| GET | `/chaos/scenarios` | Список сценариев с параметрами |
| POST | `/chaos/scenario?target=` | Включить сценарий |
| DELETE | `/chaos/scenario?target=` | Вернуть `NORMAL` |
| PUT | `/chaos/traffic?target=` | Встроенный трафик, `{"rps": N}` |
| GET | `/chaos/playbooks` | Список плейбуков |
| POST | `/chaos/playbooks/{name}/run` | Запустить плейбук |
| POST | `/chaos/playbooks/stop` | Остановить плейбук и вернуть норму |
| GET | `/chaos/playbooks/current` | Ход текущего плейбука |
| GET | `/api/orders/{id}`, POST `/api/orders` | «Полезная» нагрузка сервиса |
| GET | `/actuator/health`, `/actuator/prometheus` | Здоровье и метрики в формате Prometheus |

## Плейбуки

Плейбук — это JSON-файл в папке `playbooks/`: последовательность сбоев по времени на разных сервисах. Все сбои одного запуска получают общий `scenarioId` вида `cascade-database-20261023-104500`, так что прогон легко найти в log-collector и сравнить подходы именно на нём.

```json
{
  "name": "cascade-database",
  "description": "База тормозит — задержки и таймауты каскадом поднимаются до gateway",
  "steps": [
    {"atSeconds": 60, "target": "database",
     "scenario": {"type": "LATENCY", "delayMs": 4000, "durationSeconds": 120}}
  ]
}
```

Готовые плейбуки:

| Плейбук | Что проверяет |
|---|---|
| `cascade-database` | Каскад: виноват database, а ошибки видны у всех выше по цепочке |
| `payments-errors-then-crash` | Ошибки 5xx, потом падение и перезапуск |
| `all-fault-types` | Все типы сбоев по очереди на orders, ~14 минут |
| `two-incidents` | Два независимых сбоя внахлёст — должно получиться два инцидента |

Папка `playbooks/` подключена в контейнеры, и при каждом запросе она перечитывается заново. Чтобы добавить новый плейбук, достаточно положить файл и обновить панель — перезапуск не нужен.

## Без Docker

```bash
./gradlew bootRun
```

Так запускается один экземпляр на порту 8080 с именем `test-service`. Настройки задаются через переменные окружения:

| Переменная | Что задаёт |
|---|---|
| `TESTSERVER_SERVICENAME` | Имя сервиса |
| `TESTSERVER_DOWNSTREAM` | Кого вызывать, через запятую |
| `TESTSERVER_TRAFFIC_RPS` | Встроенный трафик |
| `TESTSERVER_COLLECTOR_URL` | Адрес log-collector |
| `SERVER_PORT` | Порт |

Адреса других экземпляров задаются в `testserver.peers` в `application.yml`.

## Разработка

```bash
./gradlew build    # сборка и тесты
```

Стек: Java 21, Spring Boot 3.5. Код разложен по пакетам:

| Пакет | Что внутри |
|---|---|
| `chaos` | Сценарии, их переключение, фильтр сбоев, эффекты на ресурсы |
| `shop` | «Полезная» работа и вызовы зависимых сервисов |
| `stats` | traceId, журнал запросов, секундная статистика |
| `telemetry` | Отправка логов, метрик и разметки в log-collector |
| `traffic` | Встроенный генератор запросов |
| `peers` | Управление другими экземплярами |
| `playbook` | Плейбуки |
