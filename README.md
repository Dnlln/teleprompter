# Телесуфлер для Android с записью видео и поддержкой DOCX

[![Release](https://img.shields.io/github/v/release/Dnlln/teleprompter)](https://github.com/Dnlln/teleprompter/releases/tag/v1.0.0)
[![Platform](https://img.shields.io/badge/platform-Android%20%7C%20Web-blue)](https://github.com/Dnlln/teleprompter)

Проект разработан в двух версиях:
1. **`teleprompter-android/`**: Полноценный нативный проект для **Android Studio (Kotlin / CameraX / Jetpack)** с записью видео с фронтальной камеры, сохранением в галерею смартфона (`MediaStore`), встроенным парсером `.docx` и оверлеем бегущей строки под глазком камеры (`DisplayCutout`).
2. **`teleprompter-web/`**: Мобильное веб-приложение (**PWA**), оптимизированное под экраны смартфонов Android. Позволяет мгновенно протестировать суфлер, фронтальную камеру, запись видео и загрузку файлов `.docx` прямо в браузере мобильного телефона (Chrome / Edge / Samsung Internet).

---

## 🚀 Быстрый запуск Web / PWA версии

Локальный сервер уже запущен на порту `8088`:
- **Адрес для проверки в браузере**: `http://localhost:8088/`
- **Проверка на мобильном телефоне в одной Wi-Fi сети**: `http://<IP_ВАШЕГО_КОМПЬЮТЕРА>:8088/`

### Возможности веб-версии:
- Расположение текста в самом верху экрана прямо под камерой.
- Плавный автоскролл с регулировкой темпа чтения (от 1x до 10x).
- Загрузка и разбор файлов `.docx` (в комплекте идет готовый образец `sample_script.docx`).
- Запись видео с фронтальной камеры со звуком (`MediaRecorder`) и моментальное скачивание файла на устройство.
- Поддержка установки на главный экран Android как отдельного приложения (PWA).

---

## 📱 Сборка и запуск нативного Android-приложения (`teleprompter-android/`)

### Стек технологий:
- **Язык**: Kotlin
- **Камера и видео**: AndroidX CameraX (`camera-camera2`, `camera-video`, `camera-view`)
- **Архитектура UI**: Modern HUD Glassmorphism, Material Components, ViewBinding
- **Парсер DOCX**: Встроенный потоковый `DocxReader` (`ZipInputStream` + `XmlPullParser`) — не утяжеляет приложение и работает молниеносно.
- **Движок прокрутки**: `Choreographer` для идеальной синхронизации со 120Hz/60Hz дисплеями смартфонов.
- **Поддержка выреза камеры**: `WindowInsetsCompat` и `DisplayCutoutCompat` для точного позиционирования под объективом.

### Запуск в Android Studio:
1. Откройте Android Studio.
2. Нажмите **Open** и выберите директорию `c:\Projects\teleprompter-android`.
3. Подключите смартфон или выберите эмулятор.
4. Нажмите **Run** (`Shift + F10`).
