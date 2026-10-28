# SoundFork

Read-only Android-App fuer Bose SoundTouch im lokalen Netzwerk.

## Technischer Rahmen

- Package: `ninja.richter.soundfork`
- App-Name: `SoundFork`
- UI: Kotlin + Jetpack Compose
- Compile SDK: 36
- Min SDK: 34 (Android 14)
- Target SDK: 36

## Tablet- und Fensterlayouts

Die Oberfläche richtet sich nach der verfügbaren Fensterbreite, auch bei Rotation und im geteilten Bildschirm:

- Unter 600 dp: Menü und Mini-Player mit ausklappbaren Player- und Lautsprecheransichten.
- Ab 600 dp: dauerhaft sichtbare, schmale Navigation links.
- Ab 1000 dp: zusätzlicher Player rechts mit Wiedergabe, Lautstärke und Quellenwahl. Inhalt und Player scrollen unabhängig voneinander.
- Ab 1200 dp: breite Navigation mit Lautsprechername und beschrifteten Menüzeilen.

Die Verbindungssuche bleibt auf eine gut lesbare Breite begrenzt. Beim Wechsel zu einem breiten Fenster schließen offene Menü- und Playeransichten; die gewählte Seite und Lautstärke bleiben erhalten.

## Foldables

Jetpack WindowManager meldet Faltzustand und Scharnierposition an die Oberfläche. Bei einem trennenden Falz oder einem verdeckenden Scharnier bleibt dessen gesamte Fläche frei:

- Buchmodus (senkrechter Falz): Seiteninhalt links, Wiedergabe und Lautsprechersteuerung rechts. Das funktioniert auch unterhalb der üblichen 1000-dp-Grenze für den seitlichen Player.
- Tischmodus (waagerechter Falz): Seiteninhalt oben, scrollbare Wiedergabe- und Lautsprechersteuerung unten.
- Sind die Teilflächen für zwei Ansichten zu klein (weniger als 280 dp Breite oder 180 dp Höhe pro Teilfläche), nutzt die App die größere freie Fläche. Playeransichten bleiben innerhalb dieser Fläche.
- Die Verbindungssuche nutzt ebenfalls die größere freie Fläche. Preset-Bestätigungen erscheinen bei getrennter Anzeige direkt im Seiteninhalt.
- Auf einem flach geöffneten, durchgehenden Display und auf dem Außendisplay gelten die normalen Fensterlayouts.

Die Falzposition wird von Fensterkoordinaten in die tatsächliche Inhaltsfläche umgerechnet, einschließlich Systemleisten und Fensterposition. Seiteninhalt, Scrollzustand und offene Preset-Bestätigungen bleiben bei Layoutwechseln innerhalb derselben Activity erhalten; Geräte- und Wiedergabestatus liegen weiterhin im ViewModel.

`SoundTouchAdaptiveLayoutTest` prüft die Layouts und die Bedienung mit simulierten Lautsprecherdaten und Falzpositionen. Die Tests decken schmale Fenster, Tablets, Buch- und Tischmodus, große Schrift, dunkles Design, freie Scharnierbereiche sowie Übergänge mit offenen Bedienansichten ab. `FoldingFeatureMappingTest` prüft die Auswertung der WindowManager-Meldungen; `FoldableLayoutTest` prüft die Geometrie einschließlich Fenster-Versatz und Nullbreiten-Falz. Ein Test auf physischer Foldable-Hardware steht noch aus.

## Features

- Netzwerksuche per
  - mDNS (`_soundtouch._tcp.`)
  - SSDP (`urn:schemas-upnp-org:device:MediaRenderer:1`)
- Manuelle Verbindung ueber IP/Hostname
- Auslesen und Anzeigen der API-XMLs auf Port `8090`

## Ausgelesene Endpoints

- `/info`
- `/sources`
- `/capabilities`
- `/bassCapabilities` (optional)
- `/bass` (optional)
- `/getZone` (optional)
- `/now_playing` (mit Fallback-Aliases)
- `/trackInfo` (optional)
- `/volume`
- `/presets`
- `/recents` (optional)
- zusaetzlich capability-basierte URLs aus `/capabilities`

## Hinweise

- Die SoundTouch-API laeuft ueber unverschluesseltes HTTP im LAN; daher ist Cleartext explizit erlaubt.
- Optional nicht verfuegbare Endpoints werden als "nicht verfuegbar" statt als harter Fehler markiert.
- `http://<host>[:port]/` liefert oft 404 auf SoundTouch, die App liest standardmaessig `/info` und weitere API-Pfade aus.

### Debug-Logs (Logcat)

- `adb logcat SoundTouchDiscovery:V SoundTouchRepository:V MainViewModel:V SoundForkUI:V *:S`
- Wichtig: Suche nach `onServiceFound`, `mDNS scan finished`, `discover(): no hosts discovered`.
- Ein erfolgreicher mDNS-Treffer sollte als `mDNS candidate` und danach als `mDNS resolved host=...` erscheinen.
