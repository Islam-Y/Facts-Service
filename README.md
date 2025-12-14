# Facts Service

Доменный сервис генерации «интересных фактов» о треках. Работает асинхронно через Kafka, не хранит собственных данных и берёт метаданные трека из Music Service по внутреннему HTTP API.

## Поток данных
- Слушает Kafka-топик входящих команд: `music.facts.events` (настраивается через `app.kafka.topics.facts-events`).
- Для событий `created`, `updated`, `refresh` синхронно выполняет генерацию (метаданные из Music Service → LLM).
- Если генерация или загрузка метаданных падает, бросает исключение — Kafka ретраит сообщение (1s → 2s) и после исчерпания попыток кладёт его в `<facts-events>.dlt`. Битые JSON (`JacksonException`) и 404 по треку (`TrackNotFoundException`) уходят сразу в DLT без повторов.
- Публикует результат в топик `music.track.facts.generated` (`app.kafka.topics.generated-facts`) с ключом `trackId`.

## Конфигурация (application.yaml)
- Kafka: `app.kafka.topics.facts-events` / `app.kafka.topics.generated-facts`, брокер `spring.kafka.bootstrap-servers`.
- Music API: `app.music-service.base-url` — внутренний API Music Service (по умолчанию `http://localhost:8080`).
- LLM (ProxyAPI): `app.llm.proxyapi.*`
  - `base-url` — OpenAI-совместимый эндпоинт ProxyAPI (по умолчанию `https://openai.api.proxyapi.ru/v1`);
  - `api-key` — ключ ProxyAPI;
  - `model` — `openai/gpt-4o-mini`;
  - `timeout-ms`, `temperature`, `max-tokens`;
  - `retry.max-attempts`, `retry.backoff-ms` — транспортные ретраи (429/5xx/timeout);
  - `format-retry.max-attempts` — повторы, если модель вернула невалидный JSON.
- Prompt defaults: `app.llm.prompt.format-version`, `app.llm.prompt.lang`, `app.llm.prompt.max-sources`.

Переменные окружения для быстрого старта:  
`PROXYAPI_API_KEY`, `PROXYAPI_MODEL`, `PROXYAPI_BASE_URL`, `MUSIC_SERVICE_BASE_URL`, `FACTS_EVENTS_TOPIC`, `GENERATED_FACTS_TOPIC`, `KAFKA_BOOTSTRAP_SERVERS`.

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
    "factsJson": "{\"formatVersion\":1,\"lang\":\"ru\",\"short\":\"...\",\"full\":\"...\",\"sources\":[{\"title\":\"...\",\"url\":\"...\"}]}"
  }
  ```

## Запуск локально
1) Поднять Kafka (например, через docker-compose) или указать существующий брокер в `KAFKA_BOOTSTRAP_SERVERS`.
2) Запустить Music Service, доступный по `MUSIC_SERVICE_BASE_URL`.
3) Прописать `PROXYAPI_API_KEY` и при необходимости `PROXYAPI_MODEL`, `PROXYAPI_BASE_URL`.
4) Стартовать Facts Service:
   ```bash
   ./gradlew bootRun
   ```

## Ограничения и TODO
- Нет автотестов на сквозной поток Kafka → HTTP → генерация → Kafka. Добавить позже.
