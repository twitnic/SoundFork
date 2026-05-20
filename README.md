# SoundFork

Read-only Android-App fuer Bose SoundTouch im lokalen Netzwerk.

## Technischer Rahmen

- Package: `ninja.richter.soundfork`
- App-Name: `SoundFork`
- UI: Kotlin + Jetpack Compose
- Compile SDK: 36
- Min SDK: 34 (Android 14)
- Target SDK: 36

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
