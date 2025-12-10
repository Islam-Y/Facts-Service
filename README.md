# Facts Service

Доменный сервис генерации «интересных фактов» о треках. Работает асинхронно через Kafka, не хранит собственных данных и берёт метаданные трека из Music Service по внутреннему HTTP API.

## Поток данных
- Слушает Kafka-топик входящих команд: `music.facts.events` (настраивается через `app.kafka.topics.facts-events`).
- Для событий `created`, `updated`, `refresh` ставит задачу в очередь с приоритетом (по `priority`, затем по `timestamp`).
- Забирает метаданные трека из Music Service: `GET {MUSIC_SERVICE_BASE_URL}/internal/tracks/{trackId}`.
- Генерирует факты (пока заглушка без LLM) и публикует результат в топик `music.track.facts.generated` (`app.kafka.topics.generated-facts`) с ключом `trackId`.

## Конфигурация (application.yaml)
- `app.music-service.base-url` — базовый URL Music Service для внутреннего API (по умолчанию `http://localhost:8080`).
- `app.kafka.topics.facts-events` — входной топик команд (по умолчанию `music.facts.events`).
- `app.kafka.topics.generated-facts` — выходной топик с фактами (по умолчанию `music.track.facts.generated`).
- `spring.kafka.bootstrap-servers` — адреса Kafka брокеров (по умолчанию `localhost:9092`).

Можно переопределять через переменные окружения: `MUSIC_SERVICE_BASE_URL`, `FACTS_EVENTS_TOPIC`, `GENERATED_FACTS_TOPIC`, `KAFKA_BOOTSTRAP_SERVERS`.

## Формат сообщений
- Вход (FactsEventPayload) из `music.facts.events`:
  ```json
  {
    "version": "1",
    "eventType": "created | updated | refresh | deleted",
    "trackId": "string",
    "priority": 0,
    "timestamp": "2025-12-10T09:00:00Z"
  }
  ```
- Выход (GeneratedFactsPayload) в `music.track.facts.generated`:
  ```json
  {
    "trackId": "string",
    "factsJson": "{\"formatVersion\":1,\"lang\":\"ru\",\"short\":\"...\",\"full\":\"...\"}"
  }
  ```

## Запуск локально
1) Поднять Kafka (например, через docker-compose) или указать существующий брокер в `KAFKA_BOOTSTRAP_SERVERS`.
2) Запустить Music Service, доступный по `MUSIC_SERVICE_BASE_URL`.
3) Стартовать Facts Service:
   ```bash
   ./gradlew bootRun
   ```

## Ограничения и TODO
- Генерация фактов сейчас заглушечная (без вызова LLM); заменить на реальную интеграцию, когда будет доступен провайдер.
- Нет автотестов на сквозной поток Kafka → HTTP → генерация → Kafka. Добавить при внедрении боевой логики.
