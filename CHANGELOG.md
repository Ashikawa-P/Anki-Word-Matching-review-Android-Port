# Änderungen

## 1.2.0 – 2026-08-26

- Provider-Rückgabewerte gelten nicht mehr allein als Bestätigung einer Scheduler-Bewertung.
- Jede Bewertung wird vor und nach dem Schreiben über AnkiDroids persistenten `reps`-Zähler verifiziert; nur nachweislich gespeicherte Antworten fließen in Statistik und Folgerunde ein.
- Queue-Prüfung und Scheduler-Update werden als bedingter Zwei-Schritt-Batch in einem Provider-Aufruf ausgeführt.
- Ändert sich die Queue unmittelbar vor dem Commit zu einer anderen Karte derselben Runde, plant die App anhand der neuen Spitze neu; fremde Spitzen beenden nur den blockierten Rest der Runde.
- Ein positiver, aber nicht persistierter Provider-Write stoppt sicher ohne automatisches Doppelsenden.
- Der Normalmodus prüft vor Beginn der Runde, ob AnkiDroid die für die Commit-Verifikation benötigte Karteninformation bereitstellt. Dafür ist AnkiDroid 2.24 oder neuer erforderlich; Speedrun bleibt davon unabhängig.
- Fünf API-/Queue-Integrationstests für Erfolg, falsche Erfolgsmeldung, Queue-Konflikt, Teilübertragung und Transportfehler nach Commit ergänzt.

## 1.1.2 – 2026-08-26

- Statistik- und Anleitungsbox wird während des Matchings im Querformat vollständig ausgeblendet.
- Das Matching-Raster nutzt den gesamten verbleibenden Bildschirm und besitzt keinen äußeren Scrollbereich mehr.
- Kartenhöhen werden je Spalte gleichmäßig auf den verfügbaren Platz verteilt, sodass jede Karte der laufenden Runde gleichzeitig sichtbar bleibt.
- Überschriften, Abstände, Innenränder und Schriftgrößen werden im Querformat kompakter dargestellt.
- Langer Karteninhalt kann innerhalb der einzelnen Karte scrollen, ohne das gesamte Spielfeld zu verschieben.

## 1.1.1 – 2026-08-26

- Ursprüngliche README von Word Matching Review V8.2.1 unverändert übernommen und ausschließlich um den Abschnitt `Android specials` ergänzt.
- Einmaligen Medienumzug mit klar bezeichneten Schritten für AnkiDroid, Android-Einstellungen, Google Files und Anki Match dokumentiert.
- Dokumentiert, dass eine Pfadänderung in AnkiDroid keine Dateien verschiebt und die Quelle erst nach Synchronisation, Sicherung und Prüfung entfernt werden darf.
- Startreihenfolge AnkiDroid → abgeschlossene Synchronisation → Anki Match dokumentiert.
- Audiohinweis in der App auf den gemeinsam zugänglichen Medienordner präzisiert.

## 1.1.0 – 2026-08-26

- Audio-Konfiguration des Desktop-Add-ons ergänzt: beliebiges Audiofeld sowie Spalte 1, 2 oder 3 als Auslöser.
- Audio im normalen Matching und im Speedrun ergänzt; `collection.media` wird einmalig über Androids Ordnerfreigabe gelesen.
- Normalmodus aktiviert den gewählten Stapel vor Scheduler-Schreibzugriffen über AnkiDroids öffentlichen `selected_deck`-Endpunkt.
- Bewertungen werden aus dem jeweils aktuellen Queue-Kopf übertragen, solange dieser noch zu den gepufferten Ergebnissen gehört.
- Bei einer Queue-Änderung bleiben bestätigte Bewertungen gespeichert; nur der noch blockierte Rest wird verworfen und bleibt fällig. Anschließend startet automatisch die neue oberste Gruppe.
- Signatur für privat verteilte Entwicklungs-APKs ab dieser Version stabilisiert.
- Tests für Audioverweise und partielle Scheduler-Ergebnisse ergänzt.

## 1.0.1

- Android-kompatible Verarbeitung von Cloze- und Sound-Markierungen beim Start korrigiert.
