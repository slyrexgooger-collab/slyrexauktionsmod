package de.auktionsmod;

import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Erkennt Zahlungen in SYSTEM-Nachrichten des Servers.
 *
 * Schutz gegen gefaelschte Zahlungen ("/msg dich Steve hat dir 1000$ gezahlt"):
 *  1. Normale Spieler-Chatnachrichten (signierter Chat) werden nie als Zahlung gewertet (siehe AuktionsMod).
 *  2. Das Muster muss die GESAMTE Nachricht treffen. Bei /msg, Chat usw. steht immer etwas davor
 *     ("[Steve -> mir]", "Steve: ", "Steve flüstert") – dann passt das Muster nicht.
 *  3. Nachrichten mit typischen Privatnachrichten-Zeichen (->, », :, flüstert ...) werden sofort verworfen.
 *  4. Der Spielername darf nur aus gueltigen Minecraft-Zeichen bestehen.
 */
public final class PaymentDetector {
	private static final String PRE = "(?:\\[[A-Za-z0-9 _&+-]{1,24}\\]\\s?)?";
	private static final String PLAYER = "(?<player>[A-Za-z0-9_]{2,16})";
	private static final String AMT = "\\$?\\s?(?<amount>[0-9][0-9.,]*\\s?(?:[kKmM]|Mio)?)\\s?(?:\\$|€|Coins?|Taler|Dollar|Euro)?";
	public static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{2,16}");
	private static final Pattern LOOKS_LIKE_PAYMENT = Pattern.compile(
			"(?i).*\\d.*(gezahlt|gegeben|überwiesen|ueberwiesen|erhalten|received|paid you|sent you|hat dir).*");

	private static List<Pattern> payment = new ArrayList<>();
	private static List<String> blocked = new ArrayList<>();

	public record Detected(String player, double amount) {}

	private PaymentDetector() {}

	public static void reload(AuctionConfig cfg) {
		payment = compile(cfg.paymentPatterns);
		List<String> b = new ArrayList<>();
		for (String s : cfg.blockedKeywords) if (s != null && !s.isEmpty()) b.add(s.toLowerCase(Locale.ROOT));
		blocked = b;
	}

	private static List<Pattern> compile(List<String> raw) {
		List<Pattern> out = new ArrayList<>();
		for (String r : raw) {
			if (r == null || r.isBlank()) continue;
			String expanded = r.replace("{PRE}", PRE).replace("{P}", PLAYER).replace("{AMT}", AMT);
			try {
				out.add(Pattern.compile(expanded, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
			} catch (PatternSyntaxException e) {
				AuktionsMod.LOGGER.error("Ungueltiges Muster in auktionsmod.json: {}", r, e);
			}
		}
		return out;
	}

	/** Entfernt Farbcodes und doppelte Leerzeichen. */
	public static String normalize(String s) {
		String stripped = ChatFormatting.stripFormatting(s);
		if (stripped == null) stripped = s;
		return stripped.replace(' ', ' ').replaceAll("\\s+", " ").trim();
	}

	/** @return erkannte Zahlung oder null */
	public static Detected matchPayment(String plain) {
		String lower = plain.toLowerCase(Locale.ROOT);
		for (Pattern p : payment) {
			Matcher m = p.matcher(plain);
			if (!m.matches()) continue;
			String player = group(m, "player");
			Double amount = Money.parse(group(m, "amount"));
			if (player == null || amount == null || amount <= 0) continue;
			if (!VALID_NAME.matcher(player).matches()) continue;
			// Das Muster selbst darf die gesperrten Zeichen nicht enthalten -> nur ausserhalb pruefen
			String nameLower = player.toLowerCase(Locale.ROOT);
			// In einer echten Zahlung steht der Name genau einmal ("[Steve] Steve hat dir ..." = Fake)
			if (lower.indexOf(nameLower) != lower.lastIndexOf(nameLower)) return null;
			String rest = lower.replace(nameLower, "");
			for (String b : blocked) {
				if (rest.contains(b)) return null;
			}
			return new Detected(player, amount);
		}
		return null;
	}

	/** Grobe Pruefung: sieht aus wie eine Zahlung, ist aber keine echte (fuer Fake-Warnungen). */
	public static boolean looksLikePayment(String plain) {
		return LOOKS_LIKE_PAYMENT.matcher(plain).matches();
	}

	private static String group(Matcher m, String name) {
		try {
			return m.group(name);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
