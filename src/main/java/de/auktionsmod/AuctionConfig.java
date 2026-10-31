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
 * Wird als config/auktionsmod.json gespeichert. Alle Felder koennen dort angepasst werden
 * (z.B. die Erkennungsmuster, falls dein Server andere Zahlungsnachrichten benutzt).
 */
public class AuctionConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static AuctionConfig instance;

	// ---- Zuletzt benutzte Auktionseinstellungen (werden im Fenster vorbelegt) ----
	public double startPrice = 100;
	public double minIncrement = 10;
	public double buyNowPrice = 0;
	public int durationSeconds = 300;
	public int antiSnipeSeconds = 15;
	public boolean announceInChat = true;
	public boolean verifyBalance = true;
	public boolean autoRefund = false;

	// ---- Server-spezifisch ----
	/** Befehl ohne "/", der deinen Kontostand anzeigt (money, bal, balance, geld ...). */
	public String balanceCommand = "money";
	/** Befehl ohne "/" fuer Rueckerstattungen. {player} und {amount} werden ersetzt. */
	public String payCommand = "pay {player} {amount}";
	/** Wird vor jede Chat-Ansage gesetzt. Beginnt es mit "/", wird ein Befehl gesendet (z.B. "/g "). */
	public String chatPrefix = "";
	public String currency = "$";
	/** Abstand zwischen zwei Chat-Ansagen in Ticks (20 = 1 Sekunde), schuetzt vor Spam-Kick. */
	public int announceDelayTicks = 40;
	public boolean hideBalanceReplies = true;
	public boolean showHud = true;

	/**
	 * Regulaere Ausdruecke fuer ECHTE Zahlungs-Systemnachrichten. Sie muessen die KOMPLETTE
	 * Nachricht treffen (Anfang bis Ende) und die Gruppen (?&lt;player&gt;...) und (?&lt;amount&gt;...) enthalten.
	 * Platzhalter: {PRE} = optionales Server-Prefix in [Klammern], {P} = Spielername, {AMT} = Betrag.
	 */
	public List<String> paymentPatterns = new ArrayList<>(List.of(
			"^{PRE}{P} hat dir {AMT} (?:gezahlt|gegeben|überwiesen|ueberwiesen|gesendet|geschickt)[.!]?$",
			"^{PRE}Du hast {AMT} von {P} (?:erhalten|bekommen|empfangen)[.!]?$",
			"^{PRE}{AMT} (?:has been|was) received from {P}[.!]?$",
			"^{PRE}You (?:have )?received {AMT} from {P}[.!]?$",
			"^{PRE}{P} (?:has )?(?:paid|sent) you {AMT}[.!]?$"
	));

	/** Muster fuer die Antwort auf den Kontostand-Befehl. Gruppe (?&lt;amount&gt;...) noetig. */
	public List<String> balancePatterns = new ArrayList<>(List.of(
			"^{PRE}(?:Dein |Your )?(?:Kontostand|Guthaben|Balance|Geld|Money|Konto)(?: beträgt| betraegt| is| von [A-Za-z0-9_]{2,16})?\\s?:?\\s?{AMT}[.!]?$",
			"^{PRE}Du hast {AMT}[.!]?$",
			"^{PRE}You have {AMT}[.!]?$"
	));

	/** Enthaelt eine Nachricht eines dieser Woerter, ist sie garantiert KEINE Zahlung (Private Nachrichten etc.). */
	public List<String> blockedKeywords = new ArrayList<>(List.of(
			"->", "→", "»", ":", "flüstert", "fluestert", "whispers", "[msg]", "[pn]", "[privat]", "[me", "-> me", "-> mir"
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
	}
}
