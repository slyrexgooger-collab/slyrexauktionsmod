# Auktions-Mod (Fabric, Minecraft 1.21.11, nur Client)

Versteigere das Item in deiner Hand. Spieler bieten mit **`/pay <dein Name> <Betrag>`**.
Die Mod erkennt die Zahlungen, filtert gefälschte Nachrichten (/msg, Flüstern, Chat) heraus und zeigt alles an.

## Bedienung

1. Item in die Hand nehmen → **Taste `O`** drücken (änderbar unter Optionen → Steuerung → „Auktions-Mod“).
2. Im Fenster einstellen:
   | Einstellung | Bedeutung |
   |---|---|
   | Startpreis | Mindestgebot, damit jemand führen kann |
   | Mindesterhöhung | Um so viel muss das Höchstgebot überboten werden |
   | Sofortkauf-Preis | Wer insgesamt so viel zahlt, gewinnt sofort (0 = aus) |
   | Dauer | 30 Sek. bis 1 Std. |
   | Anti-Snipe | Neues Höchstgebot kurz vor Schluss → Restzeit wird verlängert |
   | Chat-Ansagen | Start, neue Gebote, Countdown, Gewinner automatisch in den Chat |
   | Auto-Erstattung | Überbotene bekommen ihr Geld am Ende automatisch per `/pay` zurück |
3. **„Auktion starten“** klicken.
4. Während der Auktion: Info-Box oben links. Mit `O` öffnest du die **Übersicht**:
   alle Zahlungen (Zeit, Spieler, Betrag), alle Bieter, Knöpfe zum Beenden/Abbrechen/Erstatten.
5. Am Ende: Gewinner wird angesagt → **Item an den Gewinner übergeben**. Überbotene per Knopf erstatten
   (oder automatisch). „Neue Auktion“ schließt die Auktion ab.

### Regeln
- Mehrere Zahlungen eines Spielers werden **addiert** (= sein Gebot).
- Zahlungen nach dem Ende zählen nicht und werden zur Erstattung vorgemerkt.
- Abbrechen = niemand gewinnt, alle bekommen ihr Geld zurück.

## Wie werden Fake-Zahlungen herausgefiltert?

| Schutz | |
|---|---|
| Spieler-Chat | Normale Chatnachrichten zählen **nie** – nur Systemnachrichten vom Server |
| Ganze Nachricht | Die Nachricht muss **komplett** dem Zahlungsformat entsprechen – steht etwas davor (`Steve flüstert: …`, `[Steve -> mir] …`, `<Steve> …`), wird sie verworfen |
| Sperrwörter | Nachrichten mit `flüstert`, `whisper`, `->`, `»`, `:`, `[MSG]`, `privat`, `tell` … werden nie gezählt |
| Name doppelt | Steht der Spielername zweimal drin (`[Steve] Steve hat dir …`), ist es keine echte Zahlung |

Verworfene Nachrichten, die wie eine Zahlung aussehen, werden dir rot im Chat angezeigt („✘ Ignoriert“).
Alle Zahlungen werden in `.minecraft/auktionsmod/zahlungen.log` protokolliert.

## Anpassen an deinen Server

`.minecraft/config/auktionsmod.json` (wird beim ersten Start erstellt):

- `paymentPatterns` – Formate der Zahlungsnachricht. Standardmäßig erkannt werden z.B.
  `Steve hat dir 1.500$ gezahlt`, `Du hast 100$ von Steve erhalten`, `$100 has been received from Steve.`,
  `You received $100 from Steve`, `Steve has paid you 100` (jeweils auch mit `[Prefix]` davor).
  Platzhalter: `{PRE}` = optionales `[Prefix]`, `{P}` = Spielername, `{AMT}` = Betrag.
- `payCommand` – Befehl zum Erstatten (Standard `pay {player} {amount}`).
- `chatPrefix` – z.B. `"/g "` wenn Ansagen in einen bestimmten Chat sollen.
- `announceDelayTicks` – Abstand zwischen Chat-Ansagen (Spam-Schutz, 20 = 1 Sek.).
- `blockedKeywords` – Wörter, bei denen eine Nachricht nie als Zahlung zählt.
- `currency`, `showHud`.

Wird eine echte Zahlung nicht erkannt, schick mir den genauen Text der Zahlungsnachricht deines Servers.

## Bauen

Voraussetzung: Java 21 (JDK).

```bash
./gradlew build          # Linux/macOS
gradlew.bat build        # Windows
```

Die fertige Mod liegt in `build/libs/auktionsmod-1.0.0.jar` → in `.minecraft/mods/` kopieren.
Zusätzlich nötig: **Fabric Loader** und **Fabric API** für 1.21.11.

Alternativ: Projekt auf GitHub hochladen – der enthaltene Workflow (`.github/workflows/build.yml`) baut die
Jar automatisch (Reiter „Actions“ → Artefakt „auktionsmod“).

Falls Gradle eine Version nicht findet: aktuelle Werte für 1.21.11 auf https://fabricmc.net/develop
nachsehen und in `gradle.properties` eintragen.

> Hinweis: Nutze die Mod nur auf Servern, deren Regeln automatische Chat-Nachrichten/Auktionen erlauben.
