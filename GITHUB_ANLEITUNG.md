# MyWeather als APK über GitHub bauen – ganz einfache Anleitung

Diese Anleitung ist für jemanden gedacht, der noch nie mit GitHub gearbeitet hat.

## Was passiert?

Du lädst dieses Projekt zu GitHub hoch. GitHub startet danach automatisch einen Computer in der Cloud, baut daraus die Android-App und legt am Ende eine Datei namens `app-debug.apk` als Download ab.

Du musst dafür **Android Studio nicht installieren**.

## 1. GitHub-Konto öffnen

Gehe auf https://github.com/ und melde dich an.

Falls du noch kein Konto hast: kostenlos registrieren.

## 2. Neues Projekt (Repository) erstellen

1. Oben rechts auf das **+** klicken.
2. **New repository** auswählen.
3. Bei **Repository name** eingeben:
   `MyWeather`
4. **Public** auswählen.
5. NICHT „Add a README file“ anklicken.
6. Auf **Create repository** klicken.

## 3. ZIP entpacken

Auf deinem Mac:

1. Die ZIP-Datei entpacken.
2. Öffne den entstandenen Ordner.
3. Wichtig: Du musst die Dateien **aus dem Projektordner** hochladen, nicht die ZIP-Datei selbst.

Im Ordner müssen unter anderem diese Dateien/Ordner liegen:

- `app`
- `build.gradle.kts`
- `settings.gradle.kts`
- `gradle.properties`
- `.github`

## 4. Dateien zu GitHub hochladen

Auf deiner neuen GitHub-Seite:

1. Klicke auf **uploading an existing file**.
2. Öffne auf deinem Mac den entpackten MyWeather-Ordner.
3. Markiere ALLE Dateien und Ordner darin.
4. Ziehe sie in das große Upload-Feld im Browser.
5. Warte, bis alles hochgeladen ist.
6. Ganz unten auf **Commit changes** klicken.

### Wichtig

Der Ordner `.github` beginnt mit einem Punkt. Er darf nicht fehlen!
Darin liegt der automatische Bauplan für die APK:

`.github/workflows/build-apk.yml`

## 5. APK bauen lassen

Nach dem Hochladen:

1. Oben im GitHub-Projekt auf **Actions** klicken.
2. Links sollte **MyWeather APK bauen** stehen.
3. Klicke darauf.
4. Klicke auf den neuesten Lauf.
5. Warte, bis ein grüner Haken erscheint.

Das kann einige Minuten dauern.

## 6. APK herunterladen

Wenn ein grüner Haken da ist:

1. Öffne den Lauf.
2. Scrolle nach unten zum Bereich **Artifacts**.
3. Klicke auf **MyWeather-APK**.
4. GitHub lädt eine ZIP-Datei herunter.
5. Entpacke diese ZIP-Datei.
6. Darin findest du:
   `app-debug.apk`

Das ist deine Android-App.

## 7. APK auf das Android-Handy bekommen

Am einfachsten:

1. Schicke die APK an dein Handy, z. B. per USB, Google Drive oder E-Mail.
2. Öffne die `app-debug.apk` auf dem Android-Handy.
3. Android fragt eventuell, ob die Installation aus dieser Quelle erlaubt werden soll.
4. Erlaube es für die verwendete Datei-/Browser-App.
5. Danach **Installieren** drücken.

## 8. Wenn GitHub rot wird

Kein Problem.

1. Klicke auf **Actions**.
2. Klicke auf den roten Lauf.
3. Klicke auf den roten Schritt.
4. Kopiere die Fehlermeldung hier in den Chat oder schicke einen Screenshot.

Dann sage ich dir genau, wo du klicken musst.

## Wichtig zur ersten Version

Diese GitHub-Konfiguration baut zunächst eine **Debug-APK**. Sie ist für die Installation und zum Testen gedacht.

Eine später veröffentlichungsfertige signierte Play-Store-Version ist ein separater Schritt.
