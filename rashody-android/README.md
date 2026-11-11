# Мои деньги — APK для Android

Скачать: https://raw.githubusercontent.com/EvgenyTarget/evgenytarget.github.io/main/rashody-android/downloads/moi-dengi.apk

Версия: 1.0.0 · Пакет: `kg.evgeny.moidengi` · Android 8+.

## Как работает
- Нативная иконка и стартовый экран, затем открывается Google Apps Script через Chrome Custom Tabs.
- Для работы необходим Chrome или другой браузер, интернет и вход в **тот же Google-аккаунт**, который развёртывал Apps Script с доступом «Только я».
- Не используется Android WebView: он не подходит для входа в Google. Chrome Custom Tabs использует авторизацию и cookies браузера.
- Приложение не запрашивает разрешений к банковским данным или доступ к содержимому браузера.
- В интерфейсе можно вести расходы, список покупок, отчёты и конвертер; данные в закрытой Google Таблице.
- Изменения HTML на GitHub загружаются в веб-приложение через Apps Script и не требуют переустановки APK.

## Сборка и проверка
- GitHub Actions: `.github/workflows/build-moi-dengi-apk.yml`.
- GitHub runner компилирует и подписывает APK, проверяет `apksigner` и `aapt`, публикует файл в `rashody-android/downloads/moi-dengi.apk`.
- **Важно:** текущая первая версия подписана временным debug-ключом CI. Если заново собирать Android-оболочку, подпись может отличаться и Android потребует переустановить APK; веб-интерфейс и данные в Google Таблице от этого не удаляются.
- Для стабильных обновлений самой Android-оболочки понадобится постоянный закрытый release-keystore, который следует хранить только в GitHub Actions Secrets или у владельца, но не в публичном репозитории.
- Фактическая установка и работа на конкретном Android-телефоне проверяются после установки пользователем.
