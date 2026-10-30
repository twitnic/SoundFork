# SoundFork-Website

Eigenständige, deutschsprachige Website zur Android-App. Statisches HTML und CSS, ohne JavaScript, Framework, Build-Schritt oder externe Ressourcen. Die Android-App wird nicht verändert.

## Dateien

- `index.html`: Funktionsübersicht, echter Screenshot der aktuellen App, Einrichtung und häufige Fragen.
- `datenschutz.html`: Datenschutz für App und Website, einschließlich Radio Browser, Android-Backups und optionaler Diagnoseberichte.
- `impressum.html`: Anbieter, Kontakt und Hinweis zur Unabhängigkeit von Bose.
- `styles.css`: Gemeinsames responsives Design, Tastaturfokus, Druckdarstellung und reduzierte Bewegung.
- `assets/soundfork-logo.svg`: Kopie des vorhandenen Projektlogos.
- `assets/soundfork-app-radio.png`: Unveränderter Screenshot der aktuellen App (1280 × 2856 Pixel), aufgenommen im Pixel-9-Pro-Emulator im hellen Design. Sender, Verlauf und Lautsprecherstatus sind ausdrücklich Beispieldaten eines lokalen Test-Lautsprechers. Die Oberfläche stammt aus der unveränderten App, gebaut mit `:app:assembleDebug`.

## Lokal ansehen

`index.html` direkt im Browser öffnen. Alternativ aus dem Repository-Hauptordner:

```sh
python3 -m http.server 8080 --bind 127.0.0.1 --directory website
```

Dann `http://127.0.0.1:8080` aufrufen. Mit `Ctrl+C` beenden.

## Auf ALL-INKL veröffentlichen

1. In `datenschutz.html` und `impressum.html` die Anschrift-Platzhalter ersetzen.
2. Im KAS die tatsächlichen Einstellungen für Zugriffs-/Fehlerprotokolle, IP-Kürzung, Statistik und Aufbewahrung prüfen. Die konkrete Löschfrist im markierten Hosting-Absatz eintragen. Keine feste Frist allein aus den Datenschutzhinweisen für die eigene ALL-INKL-Website übernehmen.
3. Den Vertrag zur Auftragsverarbeitung in der ALL-INKL-MembersArea prüfen bzw. abschließen und die markierte Entwurfsangabe durch eine zutreffende Bestätigung ersetzen.
4. Die Rechtstexte mit der tatsächlichen Veröffentlichung und App-Version abgleichen; anschließend die beiden Entwurfshinweise entfernen. Die Texte sind ein am aktuellen Quellcode orientierter Entwurf und wurden nicht anwaltlich geprüft.
5. Die drei HTML-Dateien, `styles.css` und den Ordner `assets/` per FTP/FTPS in das Webverzeichnis der gewünschten Domain hochladen. Diese README muss nicht veröffentlicht werden. Bestehende Dateien im Ziel vorher sichern.
6. HTTPS in ALL-INKL aktivieren und auf die HTTPS-Adresse weiterleiten. Startseite, Links und beide Rechtstexte unter der echten Domain prüfen.

Die Seite funktioniert auch in einem Domain-Unterordner, da alle lokalen Verweise relativ sind. Es gibt noch keinen Store-/Download-Link, weil im Projekt keine bestätigte öffentliche Download-Adresse hinterlegt ist. Das Bild im Handy zeigt die echte App-Oberfläche mit Beispieldaten und lässt sich zum Vergrößern öffnen. Der Hinweis zu regelmäßigen Tests mit einem Pixel-Telefon an einem SoundTouch-Radio stammt vom Entwickler.

## Grundlage der Texte

Stand: 30.10.2026. Geprüft wurden README, Android-Manifest, Gradle-Abhängigkeiten, Einstellungen/Senderverwaltung, Radio-Browser-Client, Berechtigungen und Diagnosebericht im App-Quellcode.

Ergänzende Quellen:

- [ALL-INKL-Anbieterangaben](https://all-inkl.com/impressum)
- [ALL-INKL-Datenschutzinformationen](https://all-inkl.com/datenschutzinformationen)
- [Radio Browser API](https://docs.radio-browser.info/)
- [Android Auto Backup](https://developer.android.com/identity/data/autobackup)
- [Betroffenenrechte, Datenschutzaufsicht Rheinland-Pfalz](https://www.datenschutz.rlp.de/buergerinnen-/-buerger/ihre-rechte)

Die Website selbst setzt keine Cookies und enthält keine Analyse, externen Schriftarten oder eingebetteten Medien. Bei zusätzlicher Einbindung von Tracking, Downloads, Formularen oder externen Ressourcen müssen die Datenschutzhinweise angepasst werden.
