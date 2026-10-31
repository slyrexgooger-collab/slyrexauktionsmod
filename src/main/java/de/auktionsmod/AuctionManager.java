package de.auktionsmod;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Ablauf einer Auktion ("Bieten per /pay"):
 *  - Spieler bieten, indem sie dem Auktionator Geld per /pay schicken. Gueltige Zahlungen eines Spielers werden addiert.
 *  - Eine Zahlung ist nur gueltig, wenn das Gesamtgebot danach das Mindestgebot erreicht
 *    (Startpreis bzw. Hoechstgebot + Mindesterhoehung). Ungueltige Zahlungen werden NICHT angezeigt und
 *    NICHT gezaehlt – sie landen nur in der Rueckzahlungsliste.
 *  - Am Ende gewinnt das hoechste Gebot. Alle anderen bekommen ihr Geld zurueck.
 */
public final class AuctionManager {
	public enum Phase { IDLE, RUNNING, ENDED, CANCELLED }

	public record Settings(double startPrice, double minIncrement, double buyNowPrice, int durationSeconds,
	                       int antiSnipeSeconds, boolean announce, boolean autoRefund) {}

	private record Outgoing(String text, boolean command) {}

	private static final AuctionManager INSTANCE = new AuctionManager();
	private static final int[] COUNTDOWN_MARKS = {1800, 600, 300, 120, 60, 30, 10, 5};

	public static AuctionManager get() {
		return INSTANCE;
	}

	public static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	private Phase phase = Phase.IDLE;
	private ItemStack item = ItemStack.EMPTY;
	private Settings settings;
	private String seller = "";
	private long startTimeMs;
	private long endTimeMs;
	private int revision;

	private final List<Payment> payments = new ArrayList<>();
	private final Map<String, String> names = new LinkedHashMap<>();
	private final Map<String, Double> bids = new HashMap<>();
	private final Map<String, Double> invalidMoney = new HashMap<>();
	private final Map<String, Double> refunded = new HashMap<>();
	private String leader;
	private String winner;
	private final Set<Integer> announcedMarks = new HashSet<>();

	private final ArrayDeque<Outgoing> outbox = new ArrayDeque<>();
	private int outboxCooldown;
	private int fakeWarnCooldown;

	// ================================================================ Getter fuer GUI/HUD

	public Phase phase() { return phase; }
	public boolean hasSession() { return phase != Phase.IDLE; }
	public ItemStack item() { return item; }
	public Settings settings() { return settings; }
	public String seller() { return seller; }
	public int revision() { return revision; }

	public long remainingSeconds() {
		if (phase != Phase.RUNNING) return 0;
		return Math.max(0, (endTimeMs - System.currentTimeMillis() + 999) / 1000);
	}

	/** 1 = gerade gestartet, 0 = vorbei */
	public float timeFraction() {
		if (phase != Phase.RUNNING) return 0;
		long total = Math.max(1, endTimeMs - startTimeMs);
		return Math.max(0, Math.min(1, (endTimeMs - System.currentTimeMillis()) / (float) total));
	}

	public String leaderName() { return leader == null ? null : names.get(leader); }
	public double leaderBid() { return leader == null ? 0 : bids.getOrDefault(leader, 0.0); }
	public String winnerName() { return winner == null ? null : names.get(winner); }
	public double bidOf(String k) { return bids.getOrDefault(k, 0.0); }
	public double invalidOf(String k) { return invalidMoney.getOrDefault(k, 0.0); }
	public double refundedOf(String k) { return refunded.getOrDefault(k, 0.0); }
	public String nameOf(String k) { return names.getOrDefault(k, k); }
	public boolean isWinner(String k) { return k.equals(winner); }
	public boolean isLeader(String k) { return k.equals(leader); }

	/** Mindestens noetiges Gesamtgebot fuer die Fuehrung. */
	public double nextMinimum() {
		if (settings == null) return 0;
		return leader == null ? settings.startPrice() : leaderBid() + Math.max(settings.minIncrement(), 0.01);
	}

	/** Nur gueltige Gebote, neueste zuerst. */
	public List<Payment> validBids() {
		List<Payment> out = new ArrayList<>();
		for (int i = payments.size() - 1; i >= 0; i--) if (payments.get(i).valid) out.add(payments.get(i));
		return out;
	}

	public double totalValid() {
		double s = 0;
		for (Payment p : payments) if (p.valid) s += p.amount;
		return s;
	}

	/** Spieler mit gueltigem Gebot, sortiert nach Gebot. */
	public List<String> ranking() {
		List<String> list = new ArrayList<>();
		for (String k : names.keySet()) if (bidOf(k) > 0) list.add(k);
		list.sort((a, b) -> Double.compare(bidOf(b), bidOf(a)));
		return list;
	}

	/** Spieler, denen etwas zusteht oder schon erstattet wurde. */
	public List<String> refundList() {
		List<String> list = new ArrayList<>();
		for (String k : names.keySet()) if (refundDue(k) > 0 || refundedOf(k) > 0) list.add(k);
		list.sort((a, b) -> Double.compare(refundDue(b), refundDue(a)));
		return list;
	}

	/** Offener Rueckzahlungsbetrag. Ungueltige Zahlungen sind sofort erstattbar, Gebote erst nach dem Ende. */
	public double refundDue(String k) {
		double due = invalidOf(k) - refundedOf(k);
		if (phase == Phase.ENDED || phase == Phase.CANCELLED) {
			due += k.equals(winner) ? 0 : bidOf(k);
		}
		return due > Money.EPS ? due : 0;
	}

	public double totalRefundDue() {
		double s = 0;
		for (String k : names.keySet()) s += refundDue(k);
		return s;
	}

	// ================================================================ Steuerung

	public void start(ItemStack stack, Settings s) {
		clear();
		item = stack.copy();
		settings = s;
		seller = myName();
		phase = Phase.RUNNING;
		startTimeMs = System.currentTimeMillis();
		endTimeMs = startTimeMs + s.durationSeconds() * 1000L;

		AuctionConfig c = AuctionConfig.get();
		String buyPart = s.buyNowPrice() > 0 ? fill(c.msgBuyNowPart, Map.of("amount", Money.fmt(s.buyNowPrice()))) : "";
		announce(c.msgStart, Map.of("buynow", buyPart));
		announce(c.msgInfo, Map.of());
		info(Text.literal("Auktion gestartet! Spieler bieten mit /pay " + seller + " <Betrag>").formatted(Formatting.GREEN));
		log("START " + seller + ": " + item.getCount() + "x " + item.getName().getString() + " " + s);
		changed();
	}

	public void endNow() {
		if (phase == Phase.RUNNING) end();
	}

	private void end() {
		phase = Phase.ENDED;
		winner = leader;
		AuctionConfig c = AuctionConfig.get();
		if (winner != null) {
			announce(c.msgEnd, Map.of("player", nameOf(winner), "amount", Money.fmt(bidOf(winner))));
			info(Text.literal("Gewinner: ").formatted(Formatting.GOLD)
					.append(Text.literal(nameOf(winner)).formatted(Formatting.GREEN, Formatting.BOLD))
					.append(Text.literal(" – " + Money.fmt(bidOf(winner)) + ". Übergib jetzt das Item!").formatted(Formatting.YELLOW)));
		} else {
			announce(c.msgEndNoBid, Map.of());
			info(Text.literal("Auktion ohne gültiges Gebot beendet.").formatted(Formatting.YELLOW));
		}
		log("ENDE Gewinner=" + (winner == null ? "-" : nameOf(winner) + " " + Money.command(bidOf(winner))));
		if (settings.autoRefund()) refundAll();
		else if (totalRefundDue() > 0) {
			info(Text.literal("Offene Rückzahlungen: " + Money.fmt(totalRefundDue()) + " – Menü öffnen (O) → Rückzahlungen.").formatted(Formatting.AQUA));
		}
		changed();
	}

	/** Abbrechen: niemand gewinnt, alle Zahlungen werden erstattet. */
	public void cancel() {
		if (phase != Phase.RUNNING) return;
		phase = Phase.CANCELLED;
		leader = null;
		winner = null;
		announce(AuctionConfig.get().msgCancel, Map.of());
		log("ABBRUCH");
		refundAll();
		changed();
	}

	public void clear() {
		phase = Phase.IDLE;
		item = ItemStack.EMPTY;
		payments.clear();
		names.clear();
		bids.clear();
		invalidMoney.clear();
		refunded.clear();
		leader = null;
		winner = null;
		announcedMarks.clear();
		outbox.removeIf(o -> !o.command()); // offene Ansagen verwerfen, Rueckzahlungen behalten
		changed();
	}

	public void refundAll() {
		for (String k : new ArrayList<>(names.keySet())) refund(k);
	}

	public void refund(String k) {
		double due = refundDue(k);
		if (due <= 0) return;
		String name = nameOf(k);
		if (!PaymentDetector.VALID_NAME.matcher(name).matches()) return; // Schutz vor Befehls-Injection
		String cmd = AuctionConfig.get().payCommand.replace("{player}", name).replace("{amount}", Money.command(due));
		refunded.merge(k, due, Double::sum);
		outbox.add(new Outgoing(cmd, true));
		info(Text.literal("Erstatte " + Money.fmt(due) + " an " + name).formatted(Formatting.AQUA));
		log("ERSTATTUNG " + name + " " + Money.command(due));
		changed();
	}

	// ================================================================ Tick

	public void tick(MinecraftClient client) {
		if (client.player == null || client.getNetworkHandler() == null) return;
		if (fakeWarnCooldown > 0) fakeWarnCooldown--;

		if (outboxCooldown > 0) outboxCooldown--;
		else if (!outbox.isEmpty()) {
			Outgoing o = outbox.poll();
			sendNow(o.text(), o.command());
			outboxCooldown = Math.max(10, AuctionConfig.get().announceDelayTicks);
		}

		if (phase == Phase.RUNNING) {
			long rem = remainingSeconds();
			for (int mark : COUNTDOWN_MARKS) {
				if (rem <= mark && rem > mark - 2 && mark < settings.durationSeconds() && announcedMarks.add(mark)) {
					String lead = leader == null ? "Noch kein Gebot (Start " + Money.fmt(settings.startPrice()) + ")"
							: "Höchstgebot: " + nameOf(leader) + " " + Money.fmt(leaderBid());
					announce(AuctionConfig.get().msgCountdown, Map.of("time", Money.duration(mark), "leader", lead));
				}
			}
			if (System.currentTimeMillis() >= endTimeMs) end();
		}
	}

	// ================================================================ Nachrichten

	/** Systemnachricht vom Server. */
	public boolean onGameMessage(Text message, boolean overlay) {
		if (overlay || phase == Phase.IDLE) return true;
		String plain = PaymentDetector.normalize(message.getString());

		PaymentDetector.Detected d = PaymentDetector.matchPayment(plain);
		if (d == null) {
			if (PaymentDetector.looksLikePayment(plain)) warnFake(plain, "Privatnachricht/Chat");
			return true;
		}
		if (key(d.player()).equals(key(myName()))) return true;

		Payment p = new Payment(d.player(), d.amount());
		payments.add(p);
		names.putIfAbsent(p.key(), d.player());
		apply(p);
		log("ZAHLUNG " + d.player() + " " + Money.command(d.amount()) + (p.valid ? " GÜLTIG" : " UNGÜLTIG"));
		changed();
		return true;
	}

	/** Signierte Spieler-Chatnachricht, die wie eine Zahlung aussieht -> nur Warnung. */
	public void onPlayerChat(Text message) {
		if (phase == Phase.IDLE) return;
		String plain = PaymentDetector.normalize(message.getString());
		if (PaymentDetector.looksLikePayment(plain)) warnFake(plain, "Spieler-Chat");
	}

	private void warnFake(String plain, String why) {
		log("IGNORIERT (" + why + "): " + plain);
		if (fakeWarnCooldown > 0) return;
		fakeWarnCooldown = 20;
		String shortText = plain.length() > 60 ? plain.substring(0, 60) + "…" : plain;
		info(Text.literal("✘ Keine echte Zahlung (" + why + "): ").formatted(Formatting.RED)
				.append(Text.literal(shortText).formatted(Formatting.GRAY)));
	}

	// ================================================================ Gebote

	private void apply(Payment p) {
		String k = p.key();
		AuctionConfig c = AuctionConfig.get();

		if (phase != Phase.RUNNING) {
			markInvalid(p, "nach Ende");
			if (settings.autoRefund() || phase == Phase.CANCELLED) refund(k);
			return;
		}

		double prev = bidOf(k);
		double total = prev + p.amount;
		boolean buyNow = settings.buyNowPrice() > 0 && total + Money.EPS >= settings.buyNowPrice();
		double need; // mindestens noetige Einzelzahlung
		if (leader == null) need = settings.startPrice() - prev;
		else if (leader.equals(k)) need = Math.max(settings.minIncrement(), 0.01);
		else need = leaderBid() + Math.max(settings.minIncrement(), 0.01) - prev;

		if (!buyNow && p.amount + Money.EPS < need) {
			markInvalid(p, "unter Mindestgebot");
			announce(c.msgInvalid, Map.of("player", p.player, "paid", Money.fmt(p.amount), "need", Money.fmt(need)));
			if (settings.autoRefund()) refund(k);
			return;
		}

		p.valid = true;
		p.totalAfter = total;
		bids.put(k, total);
		info(Text.literal("+ " + p.player + " bietet " + Money.fmt(total)).formatted(Formatting.GREEN)
				.append(Text.literal(" (Zahlung " + Money.fmt(p.amount) + ")").formatted(Formatting.GRAY)));

		boolean wasLeader = k.equals(leader);
		leader = k;
		if (buyNow) {
			announce(c.msgBuyNow, Map.of("player", p.player, "amount", Money.fmt(total)));
			end();
			return;
		}
		if (wasLeader) announce(c.msgRaise, Map.of("player", p.player, "amount", Money.fmt(total)));
		else announce(c.msgNewBid, Map.of("player", p.player, "amount", Money.fmt(total)));
		antiSnipe();
	}

	private void markInvalid(Payment p, String why) {
		p.valid = false;
		invalidMoney.merge(p.key(), p.amount, Double::sum);
		info(Text.literal("Ungültig (" + why + "): " + p.player + " " + Money.fmt(p.amount) + " – wird erstattet.").formatted(Formatting.GRAY));
	}

	private void antiSnipe() {
		int snipe = settings.antiSnipeSeconds();
		if (snipe <= 0) return;
		long minEnd = System.currentTimeMillis() + snipe * 1000L;
		if (endTimeMs < minEnd) {
			endTimeMs = minEnd;
			announcedMarks.removeIf(m -> m <= snipe);
			announce(AuctionConfig.get().msgExtended, Map.of("seconds", Integer.toString(snipe)));
		}
	}

	// ================================================================ Hilfen

	/** Ersetzt Platzhalter: erst die uebergebenen, dann die allgemeinen. */
	private String fill(String template, Map<String, String> vars) {
		if (template == null) return "";
		String s = template;
		for (Map.Entry<String, String> e : vars.entrySet()) s = s.replace("{" + e.getKey() + "}", e.getValue());
		AuctionConfig c = AuctionConfig.get();
		s = s.replace("{tag}", c.chatTag == null ? "" : c.chatTag)
				.replace("{seller}", seller)
				.replace("{item}", item.isEmpty() ? "" : item.getName().getString())
				.replace("{count}", Integer.toString(item.getCount()))
				.replace("{time}", Money.time(remainingSeconds()))
				.replace("{duration}", settings == null ? "" : Money.duration(settings.durationSeconds()))
				.replace("{start}", settings == null ? "" : Money.fmt(settings.startPrice()))
				.replace("{inc}", settings == null ? "" : Money.fmt(settings.minIncrement()))
				.replace("{next}", Money.fmt(nextMinimum()));
		return s.trim();
	}

	private void announce(String template, Map<String, String> vars) {
		if (settings == null || !settings.announce() || template == null || template.isBlank()) return;
		String text = fill(template, vars);
		String prefix = AuctionConfig.get().chatPrefix == null ? "" : AuctionConfig.get().chatPrefix;
		if (prefix.startsWith("/")) outbox.add(new Outgoing(prefix.substring(1) + text, true));
		else outbox.add(new Outgoing(prefix + text, false));
	}

	private void sendNow(String text, boolean command) {
		ClientPlayNetworkHandler nh = MinecraftClient.getInstance().getNetworkHandler();
		if (nh == null) return;
		if (text.length() > 255) text = text.substring(0, 255);
		if (command) nh.sendChatCommand(text.startsWith("/") ? text.substring(1) : text);
		else nh.sendChatMessage(text);
	}

	public static void info(Text text) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.inGameHud == null) return;
		String tag = AuctionConfig.get().chatTag == null ? "" : AuctionConfig.get().chatTag;
		MutableText msg = Text.literal(tag + " ").styled(s -> s.withColor(Theme.accentRgb())).append(text);
		mc.inGameHud.getChatHud().addMessage(msg);
	}

	public static String myName() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null ? mc.player.getName().getString() : "";
	}

	private void changed() {
		revision++;
	}

	private static void log(String line) {
		try {
			Path dir = FabricLoader.getInstance().getGameDir().resolve("auktionsmod");
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("zahlungen.log"), LocalDateTime.now().withNano(0) + " " + line + System.lineSeparator(),
					StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (Exception e) {
			AuktionsMod.LOGGER.warn("Log fehlgeschlagen", e);
		}
	}
}
