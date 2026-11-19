# SmartOrchard Mobile — Android (Kotlin / Jetpack Compose)

Мобильное приложение для цифрового учёта фруктовых и ореховых садов: интерактивная доска рядов и мест, паспорта деревьев, фотоосмотры с ИИ-диагностикой, автополив по произвольным зонам, учёт прямых затрат и расчёт окупаемости, сканирование и печать QR-этикеток.

## Технологический стек

- **Язык и сборка:** Kotlin 2.4, JDK 21, Android Gradle Plugin 9.3, Gradle Version Catalog (`gradle/libs.versions.toml`).
- **UI:** Jetpack Compose + Material 3, однонаправленный поток данных (UDF), `@Immutable` модели, виртуализация списков (`LazyColumn`), `@Preview`.
- **Навигация и состояние:** Type-Safe Navigation Compose (`@Serializable NavRoute`), фичевые ViewModel с сохранением черновиков в `SavedStateHandle` и доставкой одноразовых событий через `Channel`.
- **DI:** Dagger Hilt (`@HiltAndroidApp`, `@HiltViewModel`, `@Binds`, квалификаторы скоупов и HTTP-клиентов).
- **Локальное хранилище и оффлайн-режим:** Room v3 (реляционная схема, `ForeignKey`, индексы, `@Transaction`, явные SQL-миграции `MIGRATION_1_2` и `MIGRATION_2_3`).
- **Фоновая синхронизация:** паттерн Transactional Outbox (`sync_outbox`) + `WorkManager` (`SyncWorker` с констрейнтом `NetworkType.CONNECTED`) + `SyncEngine`.
- **Сетевой слой и безопасность:** Retrofit 2 + OkHttp 4 + `kotlinx.serialization`, `AuthInterceptor`, потокобезопасный `TokenAuthenticator` (обновление по refresh-токену при `401`), хранение токенов с аппаратным шифрованием AES-256-GCM через Android KeyStore (`EncryptedTokenStorage`), реактивный `ConnectivityNetworkMonitor`.

## Структура пакетов

| Пакет | Назначение |
|---|---|
| `ui/` | Общие Compose-компоненты (`Components.kt`, `GardenGrid.kt`, `PhotoPreview.kt`), тема и `ui/screens/*` (stateless-экраны) |
| `state/` | Фичевые ViewModel, `SavedStateHandle`-черновики, `SnackbarManager`, навигационные события |
| `domain/` | Бизнес-правила (`Rules.kt`), типизированные ошибки (`AppError.kt`) и Use Cases (`domain/usecase/*`) |
| `data/` | Доменные модели, `OfflineFirstRepository`, Room (`data/local/*`) и Outbox-синхронизация (`data/sync/*`) |
| `network/` | `OrchardApi`, `AuthApi`, DTO-модели, интерцепторы авторизации, `TokenManager`, `NetworkMonitor` |
| `di/` | Hilt-модули (`AppModule`, `DatabaseModule`, `NetworkModule`) |
| `services/` | Сервис ИИ-диагностики, локальное хранилище фотографий (`PhotoStore`), генерация и PDF-печать QR (`QrLabel`) |

## Сборка и тесты

Требуется **JDK 21** и **Android SDK 37** (`minSdk = 26`).

```bash
# Сборка debug APK
./gradlew assembleDebug

# Запуск всех JVM unit-тестов
./gradlew testDebugUnitTest
```

Для Windows-окружения также доступен скрипт `build-debug.bat`.

## Документация бэкенда

Полная спецификация REST API (авторизация, сады, культуры, деревья, осмотры, ИИ-диагностика, зоны и расписания полива, затраты, заметки и пользовательские настройки) описана в файле [`API.md`](API.md).
