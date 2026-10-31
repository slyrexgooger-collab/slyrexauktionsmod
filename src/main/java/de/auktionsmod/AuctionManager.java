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
 *  - Jeder Spieler bietet, indem er dir Geld per /pay schickt. Alle Zahlungen eines Spielers werden addiert = sein Gebot.
 *  - Wer am Ende das hoechste Gesamtgebot hat, gewinnt. Alle anderen bekommen ihr Geld zurueck (Knopf oder automatisch).
 *  - Jede erkannte Zahlung wird mit deinem echten Kontostand (/money) abgeglichen. Nur wenn das Geld wirklich
 *    angekommen ist, zaehlt sie. Gefaelschte Nachrichten werden als "Fake" markiert.
 */
public final class AuctionManager {
	public enum Phase { IDLE, STARTING, RUNNING, ENDED, CANCELLED }

	public record Settings(double startPrice, double minIncrement, double buyNowPrice, int durationSeconds,
	                       int antiSnipeSeconds, boolean announce, boolean verify, boolean autoRefund) {}

	private record Outgoing(String text, boolean command, double money) {}

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
	private long endTimeMs;
	private int revision;

	private final List<Payment> payments = new ArrayList<>();
	private final Map<String, String> names = new LinkedHashMap<>();
	private final Map<String, Double> bids = new HashMap<>();
	private final Map<String, Double> lateMoney = new HashMap<>();
	private final Map<String, Double> refunded = new HashMap<>();
	private String leader;
	private String winner;
	private int overtimeTicks;
	private final Set<Integer> announcedMarks = new HashSet<>();

	// Kontostand-Pruefung
	private Double lastBalance;
	private double outgoingSinceCheck;
	private double outgoingAtRequest;
	private boolean awaitingBalance;
	private int awaitTicks;
	private int balanceRetries;
	private int restarts;
	private int balanceCheckIn = -1;
	private boolean paymentDuringRequest;

	// Chat-Warteschlange (Spam-Schutz)
	private final ArrayDeque<Outgoing> outbox = new ArrayDeque<>();
	private int outboxCooldown;
	private int fakeWarnCooldown;

	// ------------------------------------------------------------------ Getter fuer GUI/HUD

	public Phase phase() { return phase; }
	public boolean hasSession() { return phase != Phase.IDLE; }
	public ItemStack item() { return item; }
	public Settings settings() { return settings; }
	public int revision() { return revision; }
	public List<Payment> payments() { return Collections.unmodifiableList(payments); }
	public Double lastBalance() { return lastBalance; }
	public boolean isCheckingBalance() { return awaitingBalance || balanceCheckIn >= 0; }

	public long remainingSeconds() {
		if (phase != Phase.RUNNING) return 0;
		return Math.max(0, (endTimeMs - System.currentTimeMillis() + 999) / 1000);
	}

	public String leaderName() { return leader == null ? null : names.get(leader); }
	public double leaderBid() { return leader == null ? 0 : bids.getOrDefault(leader, 0.0); }
	public String winnerName() { return winner == null ? null : names.get(winner); }
	public double bidOf(String k) { return bids.getOrDefault(k, 0.0); }
	public double lateOf(String k) { return lateMoney.getOrDefault(k, 0.0); }
	public double refundedOf(String k) { return refunded.getOrDefault(k, 0.0); }
	public String nameOf(String k) { return names.getOrDefault(k, k); }
	public boolean isWinner(String k) { return k.equals(winner); }

	public double totalReceived() {
		double s = 0;
		for (Payment p : payments) if (p.status.counts()) s += p.amount;
		return s;
	}

	/** Alle Spieler, die gezahlt haben, sortiert nach Gebot. */
	public List<String> bidderKeys() {
		List<String> list = new ArrayList<>(names.keySet());
		list.sort((a, b) -> Double.compare(bidOf(b) + lateOf(b), bidOf(a) + lateOf(a)));
		return list;
	}

	/** Wie viel muss diesem Spieler noch zurueckgezahlt werden? */
	public double refundDue(String k) {
		if (phase == Phase.RUNNING || phase == Phase.STARTING) return 0;
		double due = (k.equals(winner) ? 0 : bidOf(k)) + lateOf(k) - refundedOf(k);
		return due > Money.EPS ? due : 0;
	}

	public double totalRefundDue() {
		double s = 0;
		for (String k : names.keySet()) s += refundDue(k);
		return s;
	}

	// ------------------------------------------------------------------ Steuerung

	public void start(ItemStack stack, Settings s) {
		clear();
		item = stack.copy();
		settings = s;
		if (s.verify()) {
			phase = Phase.STARTING;
			info(Text.literal("Lese Kontostand mit /" + cfg().balanceCommand + " ...").formatted(Formatting.GRAY));
			requestBalanceNow();
		} else {
			beginRunning();
		}
		changed();
	}

	private void beginRunning() {
		phase = Phase.RUNNING;
		endTimeMs = System.currentTimeMillis() + settings.durationSeconds() * 1000L;
		String me = myName();
		StringBuilder sb = new StringBuilder("[Auktion] ").append(item.getCount()).append("x ").append(item.getName().getString())
				.append(" | Start: ").append(Money.fmt(settings.startPrice()))
				.append(" | +min ").append(Money.fmt(settings.minIncrement()));
		if (settings.buyNowPrice() > 0) sb.append(" | Sofortkauf: ").append(Money.fmt(settings.buyNowPrice()));
		sb.append(" | Dauer: ").append(Money.duration(settings.durationSeconds()))
				.append(" | Bieten: /pay ").append(me).append(" <Betrag>");
		announce(sb.toString());
		announce("[Auktion] Deine Zahlungen werden addiert. Wer nicht gewinnt, bekommt sein Geld zurück.");
		info(Text.literal("Auktion gestartet! ").formatted(Formatting.GREEN)
				.append(Text.literal(lastBalance != null ? "Kontostand: " + Money.fmt(lastBalance) : "Kontostand-Prüfung AUS").formatted(Formatting.GRAY)));
		log("START " + item.getCount() + "x " + item.getName().getString() + " " + settings);
		changed();
	}

	public void endNow() {
		if (phase == Phase.RUNNING) end();
	}

	private void end() {
		phase = Phase.ENDED;
		winner = leader;
		if (winner != null) {
			announce("[Auktion] BEENDET! Gewinner: " + nameOf(winner) + " mit " + Money.fmt(bidOf(winner))
					+ " für " + item.getCount() + "x " + item.getName().getString() + ". Alle anderen bekommen ihr Geld zurück.");
			info(Text.literal("Gewinner: ").formatted(Formatting.GOLD)
					.append(Text.literal(nameOf(winner)).formatted(Formatting.GREEN, Formatting.BOLD))
					.append(Text.literal(" – " + Money.fmt(bidOf(winner)) + ". Übergib ihm/ihr jetzt das Item!").formatted(Formatting.YELLOW)));
		} else {
			announce("[Auktion] BEENDET – es gab kein gültiges Gebot.");
			info(Text.literal("Auktion ohne gültiges Gebot beendet.").formatted(Formatting.YELLOW));
		}
		log("ENDE Gewinner=" + (winner == null ? "-" : nameOf(winner) + " " + Money.command(bidOf(winner))));
		if (settings.autoRefund()) refundAll();
		else if (totalRefundDue() > 0) {
			info(Text.literal("Offene Rückzahlungen: " + Money.fmt(totalRefundDue()) + " – öffne das Menü zum Erstatten.").formatted(Formatting.AQUA));
		}
		changed();
	}

	/** Abbrechen: niemand gewinnt, alle Zahlungen werden erstattet. */
	public void cancel() {
		if (phase == Phase.STARTING) {
			clear();
			return;
		}
		if (phase != Phase.RUNNING) return;
		phase = Phase.CANCELLED;
		leader = null;
		winner = null;
		announce("[Auktion] Die Auktion wurde abgebrochen. Alle Gebote werden zurückerstattet.");
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
		lateMoney.clear();
		refunded.clear();
		leader = null;
		winner = null;
		overtimeTicks = 0;
		announcedMarks.clear();
		lastBalance = null;
		outgoingSinceCheck = 0;
		outgoingAtRequest = 0;
		awaitingBalance = false;
		balanceCheckIn = -1;
		balanceRetries = 0;
		restarts = 0;
		paymentDuringRequest = false;
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
		String cmd = cfg().payCommand.replace("{player}", name).replace("{amount}", Money.command(due));
		refunded.merge(k, due, Double::sum);
		outbox.add(new Outgoing(cmd, true, due));
		info(Text.literal("Erstatte " + Money.fmt(due) + " an " + name).formatted(Formatting.AQUA));
		log("ERSTATTUNG " + name + " " + Money.command(due));
		changed();
	}

	/** Manuelle Entscheidung bei "Unklar". */
	public void manualDecision(Payment p, boolean accept) {
		if (p.status != Payment.Status.UNCLEAR && p.status != Payment.Status.PENDING) return;
		p.manual = true;
		if (accept) {
			p.status = Payment.Status.CONFIRMED;
			apply(p);
		} else {
			p.status = Payment.Status.REJECTED;
		}
		log("MANUELL " + (accept ? "OK " : "FAKE ") + p.player + " " + Money.command(p.amount));
		changed();
	}

	public void checkBalanceManually() {
		if (!awaitingBalance) requestBalanceNow();
	}

	// ------------------------------------------------------------------ Tick

	public void tick(MinecraftClient client) {
		if (client.player == null || client.getNetworkHandler() == null) return;

		if (fakeWarnCooldown > 0) fakeWarnCooldown--;

		// Chat-Ausgang
		if (outboxCooldown > 0) outboxCooldown--;
		else if (!outbox.isEmpty()) {
			Outgoing o = outbox.poll();
			sendNow(o.text(), o.command());
			outgoingSinceCheck += o.money();
			outboxCooldown = Math.max(10, cfg().announceDelayTicks);
		}

		// Kontostand
		if (awaitingBalance) {
			if (++awaitTicks > 100) {
				awaitingBalance = false;
				onBalanceTimeout();
			}
		} else if (balanceCheckIn >= 0 && balanceCheckIn-- == 0) {
			requestBalanceNow();
		}

		if (phase == Phase.RUNNING) {
			long rem = remainingSeconds();
			for (int mark : COUNTDOWN_MARKS) {
				if (rem <= mark && rem > mark - 2 && mark < settings.durationSeconds() && announcedMarks.add(mark)) {
					String lead = leader == null ? "noch kein Gebot (Start " + Money.fmt(settings.startPrice()) + ")"
							: "Höchstgebot: " + nameOf(leader) + " " + Money.fmt(leaderBid());
					announce("[Auktion] Noch " + Money.duration(mark) + "! " + lead + " | /pay " + myName() + " <Betrag>");
				}
			}
			if (System.currentTimeMillis() >= endTimeMs) {
				// Warten, bis noch offene Zahlungen geprueft sind (max. 10 Sekunden)
				if (hasPending() && overtimeTicks++ < 200) return;
				end();
			}
		}
	}

	// ------------------------------------------------------------------ Nachrichten

	/** @return false = Nachricht im Chat ausblenden */
	public boolean onGameMessage(Text message, boolean overlay) {
		if (overlay) return true;
		String plain = PaymentDetector.normalize(message.getString());

		if (awaitingBalance) {
			Double bal = PaymentDetector.matchBalance(plain);
			if (bal != null) {
				awaitingBalance = false;
				onBalance(bal);
				return !cfg().hideBalanceReplies;
			}
		}

		if (phase == Phase.IDLE || phase == Phase.STARTING) return true;

		PaymentDetector.Detected d = PaymentDetector.matchPayment(plain);
		if (d == null) {
			if (PaymentDetector.looksLikePayment(plain)) warnFake(plain, "passt nicht zum Zahlungsformat (z.B. /msg oder Chat)");
			return true;
		}
		if (key(d.player()).equals(key(myName()))) return true;

		boolean late = phase != Phase.RUNNING;
		Payment p = new Payment(d.player(), d.amount(), late, settings.verify() ? Payment.Status.PENDING : Payment.Status.UNCHECKED);
		payments.add(p);
		names.putIfAbsent(p.key(), d.player());
		log("ZAHLUNG " + d.player() + " " + Money.command(d.amount()) + (late ? " (nach Ende)" : "") + " -> " + p.status);

		if (settings.verify()) {
			if (awaitingBalance) paymentDuringRequest = true;
			else if (balanceCheckIn < 0) balanceCheckIn = 10;
		} else {
			apply(p);
		}
		changed();
		return true;
	}

	/** Signierte Spieler-Chatnachricht, die wie eine Zahlung aussieht -> Warnung. */
	public void onPlayerChat(Text message) {
		if (phase == Phase.IDLE) return;
		String plain = PaymentDetector.normalize(message.getString());
		if (PaymentDetector.looksLikePayment(plain)) warnFake(plain, "ist eine Spieler-Chatnachricht");
	}

	private void warnFake(String plain, String why) {
		if (fakeWarnCooldown > 0) return;
		fakeWarnCooldown = 20;
		String shortText = plain.length() > 60 ? plain.substring(0, 60) + "…" : plain;
		info(Text.literal("⚠ Ignoriert (" + why + "): ").formatted(Formatting.RED)
				.append(Text.literal(shortText).formatted(Formatting.GRAY)));
		log("IGNORIERT " + why + ": " + plain);
	}

	// ------------------------------------------------------------------ Gebote

	private void apply(Payment p) {
		String k = p.key();
		if (p.late) {
			lateMoney.merge(k, p.amount, Double::sum);
			info(Text.literal(p.player + " hat nach dem Ende " + Money.fmt(p.amount) + " gezahlt (wird erstattet).").formatted(Formatting.YELLOW));
			if (settings.autoRefund()) refund(k);
			return;
		}
		double total = bids.merge(k, p.amount, Double::sum);
		info(Text.literal("+ " + p.player + " zahlte " + Money.fmt(p.amount)).formatted(Formatting.GREEN)
				.append(Text.literal(" (Gebot gesamt: " + Money.fmt(total) + ")").formatted(Formatting.GRAY)));
		if (phase != Phase.RUNNING) {
			// Zahlung kam waehrend der Auktion, wurde aber erst danach bestaetigt
			if (phase == Phase.CANCELLED || (settings.autoRefund() && !k.equals(winner))) refund(k);
			return;
		}

		if (settings.buyNowPrice() > 0 && total + Money.EPS >= settings.buyNowPrice()) {
			leader = k;
			announce("[Auktion] SOFORTKAUF durch " + p.player + " für " + Money.fmt(total) + "!");
			end();
			return;
		}

		if (leader == null) {
			if (total + Money.EPS >= settings.startPrice()) newLeader(k, total);
			else announce("[Auktion] " + p.player + ": " + Money.fmt(total) + " ist unter dem Startgebot. Zahle noch "
					+ Money.fmt(settings.startPrice() - total) + " dazu.");
		} else if (leader.equals(k)) {
			announce("[Auktion] " + p.player + " erhöht auf " + Money.fmt(total) + "!");
			antiSnipe();
		} else {
			double need = leaderBid() + settings.minIncrement();
			if (total + Money.EPS >= need) newLeader(k, total);
			else announce("[Auktion] " + p.player + ": " + Money.fmt(total) + " reicht nicht (Höchstgebot " + Money.fmt(leaderBid())
					+ "). Zahle noch " + Money.fmt(need - total) + " dazu.");
		}
	}

	private void newLeader(String k, double total) {
		leader = k;
		announce("[Auktion] Neues Höchstgebot: " + nameOf(k) + " mit " + Money.fmt(total) + " | noch " + Money.time(remainingSeconds()));
		antiSnipe();
	}

	private void antiSnipe() {
		int snipe = settings.antiSnipeSeconds();
		if (snipe <= 0) return;
		long minEnd = System.currentTimeMillis() + snipe * 1000L;
		if (endTimeMs < minEnd) {
			endTimeMs = minEnd;
			announcedMarks.removeIf(m -> m <= snipe);
			announce("[Auktion] Verlängert auf " + snipe + " Sekunden (Gebot kurz vor Schluss)!");
		}
	}

	// ------------------------------------------------------------------ Kontostand-Pruefung

	private boolean hasPending() {
		for (Payment p : payments) if (p.status == Payment.Status.PENDING) return true;
		return false;
	}

	private void requestBalanceNow() {
		awaitingBalance = true;
		awaitTicks = 0;
		paymentDuringRequest = false;
		balanceCheckIn = -1;
		outgoingAtRequest = outgoingSinceCheck;
		sendNow(cfg().balanceCommand, true);
	}

	private void onBalanceTimeout() {
		if (phase == Phase.STARTING) {
			info(Text.literal("Konnte den Kontostand nicht lesen! Prüfe \"balanceCommand\" / \"balancePatterns\" in config/auktionsmod.json "
					+ "oder schalte die Kontostand-Prüfung im Auktionsfenster aus.").formatted(Formatting.RED));
			clear();
			return;
		}
		if (++balanceRetries <= 2) {
			requestBalanceNow();
			return;
		}
		balanceRetries = 0;
		for (Payment p : payments) {
			if (p.status == Payment.Status.PENDING) p.status = Payment.Status.UNCLEAR;
		}
		info(Text.literal("Kontostand antwortet nicht – Zahlungen als \"Unklar\" markiert, bitte im Menü prüfen.").formatted(Formatting.RED));
		changed();
	}

	private void onBalance(double newBalance) {
		balanceRetries = 0;
		if (phase == Phase.STARTING) {
			lastBalance = newBalance;
			outgoingSinceCheck -= outgoingAtRequest;
			beginRunning();
			return;
		}
		if (lastBalance == null) {
			lastBalance = newBalance;
			changed();
			return;
		}
		// Kam waehrend der Abfrage eine neue Zahlung an, ist unklar, ob sie schon enthalten ist -> nochmal fragen
		if (paymentDuringRequest && restarts < 5) {
			restarts++;
			requestBalanceNow();
			return;
		}
		restarts = 0;

		double delta = newBalance - lastBalance + outgoingAtRequest;
		lastBalance = newBalance;
		outgoingSinceCheck -= outgoingAtRequest;
		outgoingAtRequest = 0;

		List<Payment> pending = new ArrayList<>();
		for (Payment p : payments) if (p.status == Payment.Status.PENDING) pending.add(p);
		if (pending.isEmpty()) {
			if (delta > Money.EPS) info(Text.literal("Kontostand +" + Money.fmt(delta) + " ohne erkannte Zahlungsnachricht.").formatted(Formatting.GRAY));
			changed();
			return;
		}

		double sum = 0;
		for (Payment p : pending) sum += p.amount;

		if (delta + Money.EPS >= sum) {
			confirm(pending);
		} else if (delta <= Money.EPS) {
			reject(pending);
		} else {
			List<Payment> match = uniqueSubset(pending, delta);
			if (match != null) {
				confirm(match);
				List<Payment> rest = new ArrayList<>(pending);
				rest.removeAll(match);
				reject(rest);
			} else {
				for (Payment p : pending) p.status = Payment.Status.UNCLEAR;
				info(Text.literal("Kontostand +" + Money.fmt(delta) + " passt nicht eindeutig zu den Zahlungen – bitte im Menü manuell bestätigen.")
						.formatted(Formatting.GOLD));
			}
		}
		if (hasPending()) balanceCheckIn = 5;
		changed();
	}

	private void confirm(List<Payment> list) {
		for (Payment p : list) {
			p.status = Payment.Status.CONFIRMED;
			log("ECHT " + p.player + " " + Money.command(p.amount));
			apply(p);
		}
	}

	private void reject(List<Payment> list) {
		for (Payment p : list) {
			p.status = Payment.Status.REJECTED;
			log("FAKE " + p.player + " " + Money.command(p.amount));
			info(Text.literal("✘ FAKE-ZAHLUNG erkannt: " + p.player + " " + Money.fmt(p.amount) + " – dein Kontostand ist nicht gestiegen!")
					.formatted(Formatting.RED, Formatting.BOLD));
		}
	}

	/** Findet genau EINE Teilmenge der offenen Zahlungen, deren Summe dem Kontostand-Zuwachs entspricht. */
	private static List<Payment> uniqueSubset(List<Payment> pending, double delta) {
		int n = Math.min(pending.size(), 14);
		List<Payment> found = null;
		for (int mask = 1; mask < (1 << n); mask++) {
			double s = 0;
			for (int i = 0; i < n; i++) if ((mask & (1 << i)) != 0) s += pending.get(i).amount;
			if (Math.abs(s - delta) <= Money.EPS) {
				if (found != null) return null; // mehrdeutig
				found = new ArrayList<>();
				for (int i = 0; i < n; i++) if ((mask & (1 << i)) != 0) found.add(pending.get(i));
			}
		}
		return found;
	}

	// ------------------------------------------------------------------ Hilfen

	private void announce(String text) {
		if (settings == null || !settings.announce()) return;
		AuctionConfig c = cfg();
		String prefix = c.chatPrefix == null ? "" : c.chatPrefix;
		if (prefix.startsWith("/")) outbox.add(new Outgoing(prefix.substring(1) + text, true, 0));
		else outbox.add(new Outgoing(prefix + text, false, 0));
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
		MutableText msg = Text.literal("[Auktion] ").formatted(Formatting.GOLD).append(text);
		mc.inGameHud.getChatHud().addMessage(msg);
	}

	private String myName() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null ? mc.player.getName().getString() : "";
	}

	private void changed() {
		revision++;
	}

	private static AuctionConfig cfg() {
		return AuctionConfig.get();
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
