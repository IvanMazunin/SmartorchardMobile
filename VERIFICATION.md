# Проверка проекта

Дата: 29 октября 2026. Windows, JDK 17.0.17 (Eclipse Adoptium), установленный Android SDK, Gradle Wrapper 9.5.1.

- **APK:** `assembleDebug` — BUILD SUCCESSFUL. Файл `app/build/outputs/apk/debug/app-debug.apk`, около 12 МБ.
- **Android Lint:** `lintDebug` — BUILD SUCCESSFUL, ошибок нет. Остались предупреждения о доступных новых версиях зависимостей, targetSdk 36, настройках резервного копирования и рекомендации по оформлению компонентов/ресурсов. Они не блокируют сборку.
- **Подпись APK:** `apksigner verify --verbose` — Verifies, APK Signature Scheme v2, один подписант. Используется локальный debug-ключ.
- **Правила предметной области:** JUnit 4.13.2 — **OK (10 tests)**. Запущены скомпилированные классы `RulesTest` напрямую через JUnitCore. Для повторения есть `verify-rules.ps1`.

Обычный `testDebugUnitTest` в данной Windows-среде не запускает тестовый процесс из-за ошибки Java `Unable to establish loopback connection`. Тесты не пропускались: они выполнены отдельным запуском JUnitCore. Для сборки APK использован комплектный Wrapper без фонового процесса и с теми же параметрами JVM, что в `build-debug.bat`.

SHA-256 APK:

```text
FE955FDCC019A08DA6B2F24D955EB23F3BA2EABBAB6E741BE66747F36A1336F3
```

Проверки на эмуляторе или физическом устройстве не выполнялись: настроенного AVD/тестового устройства нет. Работа камеры, диалога печати и внешний вид на конкретном телефоне требуют запуска через Android Studio. Реальные ИИ, Bluetooth-принтер и оборудование полива в шаблоне не подключены.
