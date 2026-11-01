# Auktions-Mod (Fabric, Minecraft 1.21.11, nur Client)

Versteigere das Item in deiner Hand. Spieler bieten mit **`/pay <dein Name> <Betrag>`**.
Die Mod erkennt die Zahlungen, filtert gefälschte Nachrichten (/msg, Flüstern, Chat) heraus und zeigt alles an.

## Bedienung

1. Item in die Hand nehmen → **Taste `O`** drücken (änderbar unter Optionen → Steuerung → „Auktions-Mod“).
2. Im Fenster „Neue Auktion“ einstellen – oben rechts steht dein Name als **Auktionator**:
   | Einstellung | Bedeutung |
   |---|---|
   | Startpreis | Mindestgebot für das erste Gebot |
   | Mindesterhöhung | Um so viel muss das Höchstgebot überboten werden |
   | Sofortkauf | Wer insgesamt so viel bietet, gewinnt sofort (0 = aus) |
   | Dauer | 30 Sek. bis 1 Std. |
   | Anti-Snipe | Neues Höchstgebot kurz vor Schluss → Restzeit wird verlängert |
   | Chat-Ansagen | Start, neue Gebote, Countdown und Gewinner automatisch in den Chat |
3. **„Auktion starten“** klicken.
4. Während der Auktion: Info-Box (HUD) mit Restzeit, Auktionator, Höchstgebot und den letzten Geboten.
   Mit `O` öffnest du die **Übersicht** mit drei Tabs:
   - **Gebote** – alle gültigen Gebote (Zeit, Spieler, Zahlung, Gesamtgebot)
   - **Rangliste** – Platzierung aller Bieter, Status (Führt / Überboten / Gewinner)
   - **Rückzahlungen** – wem du noch Geld schuldest, mit „Erstatten“-Knopf
5. Am Ende erscheint eine **Gewinner-Animation** (Konfetti + Banner mit Kopf, Name und Betrag).
   Dann Item an den Gewinner übergeben, unter „Rückzahlungen“ die Überbotenen per Knopf erstatten, „Neue Auktion“.

### Regeln
- Gültige Zahlungen eines Spielers werden **addiert** (= sein Gebot).
- Eine Zahlung ist nur gültig, wenn das Gebot danach das **Mindestgebot** erreicht
  (Startpreis bzw. Höchstgebot + Mindesterhöhung; der Führende muss mindestens die Mindesterhöhung nachlegen).
- **Ungültige Zahlungen** (zu niedrig oder nach dem Ende) werden **nicht angezeigt und nicht gezählt** –
  sie stehen nur unter „Rückzahlungen“, damit du das Geld zurückgeben kannst.
- Abbrechen = niemand gewinnt, alle Zahlungen stehen unter „Rückzahlungen“.
- Rückzahlungen gehen **nur per Knopf** („Erstatten“ bzw. „Alle erstatten“) – nie automatisch.

## Design anpassen

Knopf **„Design“** im Auktionsfenster oder in der Übersicht – mit Live-Vorschau:
- **Farbthema**: Gold, Ozean, Smaragd, Rubin, Lavendel, Pink, Eis, Minimal
- **Eigene Farben** als Hex-Code (Akzent + Hintergrund) und **Deckkraft**
- **HUD**: Position (5 Ecken/Mitte), Größe (50–200 %), Anzahl Gebote, an/aus, Auktionator an/aus
- **Gewinn-Effekt**: Animation und Sound an/aus, Knopf „Animation testen“
- **Chat-Tag** (z.B. `[Auktion]`, `[SlyrexAH]`) und **Währung** (`$`, `€`, ` Coins` …)

In `config/auktionsmod.json` kannst du zusätzlich **alle Chat-Texte** ändern (`msgStart`, `msgNewBid`, `msgEnd` …)
sowie Text-/Muted-/Grün-/Rot-Farben und den HUD-Abstand (`hudOffsetX`, `hudOffsetY`).
Platzhalter: `{tag} {seller} {item} {count} {start} {inc} {buynow} {duration} {player} {amount} {paid} {need} {next} {time} {seconds} {leader}`.

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
