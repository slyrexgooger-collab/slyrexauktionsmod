# Auktions-Mod (Fabric, Minecraft 1.21.11, nur Client)

Versteigere das Item in deiner Hand. Spieler bieten mit **`/pay <dein Name> <Betrag>`**.
Die Mod erkennt die Zahlungen, prüft, ob das Geld **wirklich** angekommen ist, und zeigt alles an.

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
   | Kontostand-Befehl | z.B. `money`, `bal`, `geld` – für die Echtheitsprüfung |
   | Chat-Ansagen | Start, neue Gebote, Countdown, Gewinner automatisch in den Chat |
   | Geld-Prüfung | **Empfohlen** – gleicht jede Zahlung mit deinem Kontostand ab |
   | Auto-Erstatt. | Überbotene bekommen ihr Geld am Ende automatisch per `/pay` zurück |
3. **„Auktion starten“** klicken.
4. Während der Auktion: Info-Box oben links. Mit `O` öffnest du die **Übersicht**:
   alle Zahlungen (Zeit, Spieler, Betrag, Status), alle Bieter, Knöpfe zum Beenden/Abbrechen/Erstatten.
5. Am Ende: Gewinner wird angesagt → **Item an den Gewinner übergeben**. Überbotene per Knopf erstatten
   (oder automatisch). „Neue Auktion“ schließt die Auktion ab.

### Regeln
- Mehrere Zahlungen eines Spielers werden **addiert** (= sein Gebot).
- Zahlungen nach dem Ende zählen nicht und werden zur Erstattung vorgemerkt.
- Abbrechen = niemand gewinnt, alle bekommen ihr Geld zurück.

## Wie werden echte Zahlungen von Fake-Nachrichten unterschieden?

| Stufe | Schutz |
|---|---|
| 1 | Normale Spieler-Chatnachrichten zählen **nie** – nur Systemnachrichten vom Server |
| 2 | Die Nachricht muss **komplett** dem Zahlungsformat entsprechen. `/msg` hat immer etwas davor (`[Steve -> mir] …`, `Steve flüstert …`) → abgelehnt |
| 3 | Nachrichten mit `->`, `»`, `:`, `[MSG]`, `flüstert` … werden verworfen |
| 4 | **Kontostand-Abgleich:** Nach jeder Zahlung fragt die Mod `/money` ab. Nur wenn dein Kontostand um genau diesen Betrag gestiegen ist → **✔ Echt**. Sonst **✘ Fake** (zählt nicht als Gebot) |

Status in der Liste: `✔ Echt` · `✘ Fake` · `… Prüfe` · `? Unklar` (kannst du selbst bestätigen/ablehnen) · `~ Ungeprüft` (Geld-Prüfung aus).

Tipp: Gib während einer Auktion selbst kein Geld aus, sonst passt der Kontostand-Abgleich nicht
(die Zahlungen werden dann als „Unklar“ markiert, nicht falsch bestätigt).

Alle Zahlungen werden in `.minecraft/auktionsmod/zahlungen.log` protokolliert.

## Anpassen an deinen Server

`.minecraft/config/auktionsmod.json` (wird beim ersten Start erstellt):

- `paymentPatterns` – Formate der Zahlungsnachricht. Standardmäßig erkannt werden z.B.
  `Steve hat dir 1.500$ gezahlt`, `Du hast 100$ von Steve erhalten`, `$100 has been received from Steve.`,
  `You received $100 from Steve`, `Steve has paid you 100` (jeweils auch mit `[Prefix]` davor).
  Platzhalter: `{PRE}` = optionales `[Prefix]`, `{P}` = Spielername, `{AMT}` = Betrag.
- `balancePatterns` – Format der Kontostand-Antwort (`Kontostand: 1.234$`, `Balance: $1,234` …).
- `payCommand` – Befehl zum Erstatten (Standard `pay {player} {amount}`).
- `chatPrefix` – z.B. `"/g "` wenn Ansagen in einen bestimmten Chat sollen.
- `announceDelayTicks` – Abstand zwischen Chat-Ansagen (Spam-Schutz, 20 = 1 Sek.).
- `currency`, `hideBalanceReplies`, `showHud`.

Wenn der Start mit „Konnte den Kontostand nicht lesen“ abbricht, passt `balanceCommand` oder
`balancePatterns` nicht zu deinem Server – schick mir die Antwort von `/money`, dann passe ich das Muster an.

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
