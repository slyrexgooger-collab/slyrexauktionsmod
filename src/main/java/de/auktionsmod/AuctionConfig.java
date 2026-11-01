package de.auktionsmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Wird als config/auktionsmod.json gespeichert. Design-Einstellungen gehen bequem im Spiel
 * (Design-Knopf), alles andere (z.B. Chat-Texte) kann direkt in der Datei geaendert werden.
 */
public class AuctionConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static AuctionConfig instance;

	// ================================================================ Auktion (zuletzt benutzte Werte)
	public double startPrice = 100;
	public double minIncrement = 10;
	public double buyNowPrice = 0;
	public int durationSeconds = 300;
	public int antiSnipeSeconds = 15;
	public boolean announceInChat = true;

	// ================================================================ Design (im Spiel einstellbar)
	public String theme = "Gold";
	/** Farben als #RRGGBB */
	public String accentColor = "#FFAA00";
	public String backgroundColor = "#101018";
	public String textColor = "#FFFFFF";
	public String mutedColor = "#9A9AA8";
	public String goodColor = "#55FF55";
	public String badColor = "#FF5555";
	/** Deckkraft des Hintergrunds in Prozent (0-100) */
	public int backgroundOpacity = 85;

	public boolean showHud = true;
	/** OBEN_LINKS, OBEN_MITTE, OBEN_RECHTS, UNTEN_LINKS, UNTEN_RECHTS */
	public String hudPosition = "OBEN_LINKS";
	public int hudOffsetX = 6;
	public int hudOffsetY = 6;
	/** HUD-Groesse in Prozent */
	public int hudScale = 100;
	/** Wie viele der letzten Gebote im HUD stehen */
	public int hudLines = 4;
	public boolean hudShowSeller = true;
	/** Animation + Sound, wenn jemand die Auktion gewinnt */
	public boolean winAnimation = true;
	public boolean winSound = true;
	/** Nach der Gewinner-Animation automatisch den Bildschirm "Auktion beendet" oeffnen */
	public boolean openEndScreen = true;

	/** Steht vor jeder Chat-Ansage und jeder Mod-Nachricht */
	public String chatTag = "[Auktion]";
	public String currency = "$";

	// ================================================================ Chat-Texte (nur in der Datei)
	/*
	 * Platzhalter: {tag} {seller} {item} {count} {start} {inc} {buynow} {duration} {player} {amount}
	 *              {paid} {need} {next} {time} {seconds} {leader}
	 */
	public String msgStart = "{tag} {seller} versteigert {count}x {item} | Start: {start} | +min {inc}{buynow} | Dauer: {duration} | Bieten: /pay {seller} <Betrag>";
	public String msgBuyNowPart = " | Sofortkauf: {amount}";
	public String msgInfo = "{tag} Zahlungen werden addiert. Ungültige Gebote & Überbotene bekommen ihr Geld zurück.";
	public String msgNewBid = "{tag} Neues Höchstgebot: {player} mit {amount} | Mindestgebot jetzt {next} | noch {time}";
	public String msgRaise = "{tag} {player} erhöht auf {amount}! | Mindestgebot jetzt {next}";
	public String msgInvalid = "{tag} {player}: {paid} reicht nicht – zahle mindestens {need} auf einmal. Du bekommst die {paid} zurück.";
	public String msgCountdown = "{tag} Noch {time}! {leader} | /pay {seller} <Betrag>";
	public String msgExtended = "{tag} Verlängert auf {seconds} Sekunden!";
	public String msgBuyNow = "{tag} SOFORTKAUF durch {player} für {amount}!";
	public String msgEnd = "{tag} BEENDET! {player} gewinnt {count}x {item} für {amount}. Überbotene bekommen ihr Geld zurück.";
	public String msgEndNoBid = "{tag} BEENDET – es gab kein gültiges Gebot.";
	public String msgCancel = "{tag} Die Auktion wurde abgebrochen. Alle Gebote werden zurückerstattet.";

	// ================================================================ Server
	/** Wird vor jede Chat-Ansage gesetzt. Beginnt es mit "/", wird ein Befehl gesendet (z.B. "/g "). */
	public String chatPrefix = "";
	/** Abstand zwischen zwei Chat-Ansagen in Ticks (20 = 1 Sekunde), schuetzt vor Spam-Kick. */
	public int announceDelayTicks = 40;

	/**
	 * Formate ECHTER Zahlungs-Systemnachrichten. Sie muessen die KOMPLETTE Nachricht treffen.
	 * Platzhalter: {PRE} = optionales Server-Prefix in [Klammern], {P} = Spielername, {AMT} = Betrag.
	 */
	public List<String> paymentPatterns = new ArrayList<>(List.of(
			"^{PRE}{P} hat dir {AMT} (?:gezahlt|gegeben|überwiesen|ueberwiesen|gesendet|geschickt)[.!]?$",
			"^{PRE}Du hast {AMT} von {P} (?:erhalten|bekommen|empfangen)[.!]?$",
			"^{PRE}{AMT} (?:has been|was) received from {P}[.!]?$",
			"^{PRE}You (?:have )?received {AMT} from {P}[.!]?$",
			"^{PRE}{P} (?:has )?(?:paid|sent) you {AMT}[.!]?$"
	));

	/** Enthaelt eine Nachricht eines dieser Woerter, ist sie garantiert KEINE Zahlung (Privatnachrichten etc.). */
	public List<String> blockedKeywords = new ArrayList<>(List.of(
			"flüster", "fluester", "whisper", "->", "→", "»", "<-", ":", "[msg]", "msg", "[pn]", "privat", "private",
			"[me", "-> me", "-> mir", "tell", "nachricht", "message", "an dich", "to you", "sagt", "says"
	));

	public static AuctionConfig get() {
		if (instance == null) load();
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("auktionsmod.json");
	}

	public static void load() {
		Path p = path();
		if (Files.exists(p)) {
			try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
				instance = GSON.fromJson(r, AuctionConfig.class);
			} catch (Exception e) {
				AuktionsMod.LOGGER.error("Konnte {} nicht lesen, benutze Standardwerte", p, e);
			}
		}
		if (instance == null) instance = new AuctionConfig();
		instance.save();
	}

	public void save() {
		try {
			Files.createDirectories(path().getParent());
			try (Writer w = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
				GSON.toJson(this, w);
			}
		} catch (Exception e) {
			AuktionsMod.LOGGER.error("Konnte Konfiguration nicht speichern", e);
		}
		PaymentDetector.reload(this);
		Theme.reload(this);
	}
}
