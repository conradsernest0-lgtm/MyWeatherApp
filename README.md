# MyWeather

Android-Wetter-App mit Compose.

## APK über GitHub bauen

Im Ordner `GITHUB_ANLEITUNG.md` befindet sich eine Schritt-für-Schritt-Anleitung für Anfänger.

Der Workflow unter `.github/workflows/build-apk.yml` baut automatisch eine Debug-APK mit GitHub Actions und stellt sie als Artifact `MyWeather-APK` bereit.

Der Workflow verwendet Java 17, Android SDK und Gradle 8.9. Ein Gradle Wrapper ist in diesem Projekt nicht erforderlich, weil GitHub Gradle direkt einrichtet.
