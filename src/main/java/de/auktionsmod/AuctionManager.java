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
 *  - Als Zahlung zaehlen nur Systemnachrichten, die komplett dem Zahlungsformat entsprechen. Alles mit
 *    "flüstert", "->", "[MSG]" usw. (Privatnachrichten) wird verworfen.
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
	private long endTimeMs;
	private int revision;

	private final List<Payment> payments = new ArrayList<>();
	private final Map<String, String> names = new LinkedHashMap<>();
	private final Map<String, Double> bids = new HashMap<>();
	private final Map<String, Double> lateMoney = new HashMap<>();
	private final Map<String, Double> refunded = new HashMap<>();
	private String leader;
	private String winner;
	private final Set<Integer> announcedMarks = new HashSet<>();

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
		for (Payment p : payments) s += p.amount;
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
		if (phase == Phase.RUNNING) return 0;
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
		phase = Phase.RUNNING;
		endTimeMs = System.currentTimeMillis() + s.durationSeconds() * 1000L;
		String me = myName();
		StringBuilder sb = new StringBuilder("[Auktion] ").append(item.getCount()).append("x ").append(item.getName().getString())
				.append(" | Start: ").append(Money.fmt(s.startPrice()))
				.append(" | +min ").append(Money.fmt(s.minIncrement()));
		if (s.buyNowPrice() > 0) sb.append(" | Sofortkauf: ").append(Money.fmt(s.buyNowPrice()));
		sb.append(" | Dauer: ").append(Money.duration(s.durationSeconds()))
				.append(" | Bieten: /pay ").append(me).append(" <Betrag>");
		announce(sb.toString());
		announce("[Auktion] Deine Zahlungen werden addiert. Wer nicht gewinnt, bekommt sein Geld zurück.");
		info(Text.literal("Auktion gestartet!").formatted(Formatting.GREEN));
		log("START " + item.getCount() + "x " + item.getName().getString() + " " + s);
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
					.append(Text.literal(" – " + Money.fmt(bidOf(winner)) + ". Übergib jetzt das Item!").formatted(Formatting.YELLOW)));
		} else {
			announce("[Auktion] BEENDET – es gab kein gültiges Gebot.");
			info(Text.literal("Auktion ohne gültiges Gebot beendet.").formatted(Formatting.YELLOW));
		}
		log("ENDE Gewinner=" + (winner == null ? "-" : nameOf(winner) + " " + Money.command(bidOf(winner))));
		if (settings.autoRefund()) refundAll();
		else if (totalRefundDue() > 0) {
			info(Text.literal("Offene Rückzahlungen: " + Money.fmt(totalRefundDue()) + " – öffne das Menü (O) zum Erstatten.").formatted(Formatting.AQUA));
		}
		changed();
	}

	/** Abbrechen: niemand gewinnt, alle Zahlungen werden erstattet. */
	public void cancel() {
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

	// ------------------------------------------------------------------ Tick

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
					String lead = leader == null ? "noch kein Gebot (Start " + Money.fmt(settings.startPrice()) + ")"
							: "Höchstgebot: " + nameOf(leader) + " " + Money.fmt(leaderBid());
					announce("[Auktion] Noch " + Money.duration(mark) + "! " + lead + " | /pay " + myName() + " <Betrag>");
				}
			}
			if (System.currentTimeMillis() >= endTimeMs) end();
		}
	}

	// ------------------------------------------------------------------ Nachrichten

	/** Systemnachricht vom Server. */
	public boolean onGameMessage(Text message, boolean overlay) {
		if (overlay || phase == Phase.IDLE) return true;
		String plain = PaymentDetector.normalize(message.getString());

		PaymentDetector.Detected d = PaymentDetector.matchPayment(plain);
		if (d == null) {
			if (PaymentDetector.looksLikePayment(plain)) warnFake(plain, "Privatnachricht/Chat, keine echte Zahlung");
			return true;
		}
		if (key(d.player()).equals(key(myName()))) return true;

		boolean late = phase != Phase.RUNNING;
		Payment p = new Payment(d.player(), d.amount(), late);
		payments.add(p);
		names.putIfAbsent(p.key(), d.player());
		log("ZAHLUNG " + d.player() + " " + Money.command(d.amount()) + (late ? " (nach Ende)" : ""));
		apply(p);
		changed();
		return true;
	}

	/** Signierte Spieler-Chatnachricht, die wie eine Zahlung aussieht -> nur Warnung. */
	public void onPlayerChat(Text message) {
		if (phase == Phase.IDLE) return;
		String plain = PaymentDetector.normalize(message.getString());
		if (PaymentDetector.looksLikePayment(plain)) warnFake(plain, "Spieler-Chatnachricht, keine echte Zahlung");
	}

	private void warnFake(String plain, String why) {
		log("IGNORIERT (" + why + "): " + plain);
		if (fakeWarnCooldown > 0) return;
		fakeWarnCooldown = 20;
		String shortText = plain.length() > 60 ? plain.substring(0, 60) + "…" : plain;
		info(Text.literal("✘ Ignoriert (" + why + "): ").formatted(Formatting.RED)
				.append(Text.literal(shortText).formatted(Formatting.GRAY)));
	}

	// ------------------------------------------------------------------ Gebote

	private void apply(Payment p) {
		String k = p.key();
		if (p.late) {
			lateMoney.merge(k, p.amount, Double::sum);
			info(Text.literal(p.player + " hat nach dem Ende " + Money.fmt(p.amount) + " gezahlt (wird erstattet).").formatted(Formatting.YELLOW));
			if (settings.autoRefund() || phase == Phase.CANCELLED) refund(k);
			return;
		}
		double total = bids.merge(k, p.amount, Double::sum);
		info(Text.literal("+ " + p.player + " zahlte " + Money.fmt(p.amount)).formatted(Formatting.GREEN)
				.append(Text.literal(" (Gebot gesamt: " + Money.fmt(total) + ")").formatted(Formatting.GRAY)));

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

	// ------------------------------------------------------------------ Hilfen

	private void announce(String text) {
		if (settings == null || !settings.announce()) return;
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
