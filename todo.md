# SoundFork: Code-Review und nächste Schritte

Stand: 28.10.2026. Die Liste wurde mit dem aktuellen Code abgeglichen.

## Erledigt: Zuverlässigkeit

- [x] Coroutine-Abbrüche separat behandeln. `runSuspendCatching` reicht Abbrüche weiter und prüft vor der Ergebnisübernahme, ob der Auftrag noch aktiv ist.
- [x] Geräteaktionen an eine gemeinsame Sitzung binden. Beim Gerätewechsel oder Verlassen des Geräts werden die bisherigen Aufträge beendet.
- [x] Alte Gabbo-Rückmeldungen über die Sitzungsidentität verwerfen. Das funktioniert auch beim Wechsel A -> B -> A.
- [x] Veraltete Now-Playing-Antworten verwerfen und gleichzeitige Abfragen aus Polling und Gabbo bündeln.
- [x] Gerätebezogene Daten, Fehler und Beschäftigt-Zustände beim Wechsel zurücksetzen. Senderliste, Favoriten und Hörverlauf bleiben erhalten.
- [x] Eine gehaltene Preset-Taste auch bei einem Abbruch am bisherigen Lautsprecher loslassen.

## Erledigt: Zuständigkeiten und Datenhaltung

- [x] `DeviceSessionController` verwaltet die Lebensdauer gerätebezogener Aufträge.
- [x] `DeviceConnectionController` übernimmt den Verbindungsablauf; Zustandsübergänge liegen in `DeviceConnectionState.kt`.
- [x] `PlaybackRefreshController` übernimmt Polling und die Koordination von Status-Abfragen.
- [x] `RemotePlayback.kt` vereinheitlicht die Auswertung von Wiedergabe, Pause, Stop und Standby für UI und Medienbenachrichtigung.
- [x] Sender anhand der Stream-URL wiedererkennen, wenn Bose nur generische UPNP-Metadaten liefert.
- [x] Direkte SharedPreferences-Zugriffe aus dem MainViewModel in ein `SettingsRepository` verschieben. Bestehende Schlüssel und der Hörverlauf bleiben kompatibel.
- [x] Netzwerkfehler zentral in deutsche und englische Meldungen übersetzen.

## Erledigt: Netzwerk und Ressourcen

- [x] HTTP-Aufrufe beim Abbruch tatsächlich über `Call.cancel()` beenden.
- [x] Höchstens vier HTTP-Anfragen pro Repository gleichzeitig ausführen; vollständiges Lesen und Schließen der Antwort gehören zur Begrenzung.
- [x] Eine maximale HTTP-Aufrufdauer festlegen und optionale Snapshot-Abfragen zeitlich begrenzen.
- [x] Nichtoptionale Snapshot-Endpunkte vor optionalen Abfragen einreihen.
- [x] Eine erfolgreiche und auswertbare `/info`-Antwort für den Verbindungsaufbau verlangen. Fehler oder fremde Antworten dürfen nicht als erfolgreiche Verbindung gespeichert werden.
- [x] Timeout, Nichterreichbarkeit, HTTP-Ablehnung, ungültige Antwort und sonstige Netzwerkfehler unterscheiden.
- [x] SSDP-Sockets per `use` schließen und Multicast-Locks im `finally` freigeben. Diese Absicherung war bereits vorhanden.
- [x] SSDP-Schleifen regelmäßig auf Abbruch prüfen und mDNS-Suchen auch bei einem Abbruch stoppen.

## Erledigt: UI-Grundstruktur

- [x] Scaffold, Drawer, Player und gemeinsame Bedienelemente in eigene Komponenten auslagern.
- [x] Bedienicons durch konsistente Vektorsymbole mit zugänglichen Beschriftungen ersetzen.
- [x] Reguläre UI-Texte über deutsche und englische String-Ressourcen bereitstellen.

## Offen: Weitere Architekturarbeit

- [ ] MainViewModel weiter verkleinern: Gerätebefehle, Presets und Zonen können noch stärker eigene Abläufe erhalten. Verbindungs- und Wiedergabestatus müssen dabei gemeinsam konsistent bleiben.
- [ ] Android-Abhängigkeiten im MainViewModel reduzieren: Ressourcenreferenzen statt fertig aufgelöster Statusmeldungen und ein gekapselter Zugang zur Medienbenachrichtigung.
- [ ] Zustände bei Bedarf in thematische Datenklassen gliedern. Ein gemeinsamer StateFlow ist mit unidirektionalem Datenfluss vereinbar; unabhängige StateFlows sind kein Selbstzweck.

## Offen: Gemessene UI-Optimierung

- [ ] Recomposition und Frame-Zeiten bei Sendersuche, langen Listen und Lautstärkeänderungen messen.
- [ ] Anschließend gezielt Zustandsbeobachtung verschieben oder große Listen weiter in einzelne Lazy-Elemente zerlegen.
- [ ] Gleichzeitige Slider-Bedienung und externe Lautstärkeänderungen durch Gabbo untersuchen. Lokaler Bearbeitungszustand sollte nur mit klarer Synchronisation eingeführt werden.

Eine Recomposition ist nicht automatisch ein vollständiges Neuzeichnen des Bildschirms. Deshalb keine pauschale Verdopplung sämtlicher Textfeld- und Slider-Zustände.

## Prüfung

- Nach jedem Umsetzungsschritt Kotlin-Kompilierung und passende Regressionstests.
- Unit-Tests für Gerätewechsel, Abbruch, veraltete Antworten, Playback-Zustände, HTTP-Parallelität und Preset-Tastenfreigabe.
- Emulator-Tests für bestehende Einstellungen und Hörverlauf, ungültige `/info`-Antworten, optionale Endpunkte und Netzwerk-Timeouts.
- Bestehende UI-Tests für Navigation, Player, Lautstärke, Presets sowie Deutsch/Englisch weiterverwenden.
- Abschließend APK-Build und Android-Lint prüfen.
- Letzter Abschlusslauf: 29 Unit-Tests und sieben Emulator-Tests erfolgreich; APK-Build erfolgreich; Lint ohne Fehler, vorhandene Warnungen bleiben separat zu bearbeiten.
- [ ] Verhalten mit echten Lautsprechern bei WLAN-Abbruch, schnellem Gerätewechsel und mehreren Geräten überprüfen. Emulator-Tests ersetzen diesen Hardwaretest nicht.
