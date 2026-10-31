package de.auktionsmod.gui;

import de.auktionsmod.AuctionConfig;
import de.auktionsmod.AuctionManager;
import de.auktionsmod.Money;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/** Fenster zum Einstellen und Starten einer Auktion fuer das Item in der Hand. */
public class AuctionSetupScreen extends Screen {
	private static final int[] DURATIONS = {30, 60, 120, 180, 300, 600, 900, 1800, 3600};
	private static final int[] SNIPES = {0, 10, 15, 30, 60};
	private static final int ROW = 22;

	private final ItemStack stack;
	private TextFieldWidget startField, incField, buyField, balanceCmdField;
	private int durationIdx, snipeIdx;
	private boolean announce, verify, autoRefund;
	private String error;
	private int top, labelX;

	public AuctionSetupScreen(ItemStack held) {
		super(Text.literal("Auktion erstellen"));
		this.stack = held.copy();
		AuctionConfig c = AuctionConfig.get();
		durationIdx = nearest(DURATIONS, c.durationSeconds);
		snipeIdx = nearest(SNIPES, c.antiSnipeSeconds);
		announce = c.announceInChat;
		verify = c.verifyBalance;
		autoRefund = c.autoRefund;
	}

	@Override
	protected void init() {
		AuctionConfig c = AuctionConfig.get();
		int cx = width / 2;
		top = Math.max(4, height / 2 - 122);
		labelX = cx - 150;
		int fieldX = cx + 2, fw = 148;
		int y = top + 40;

		startField = field(fieldX, y, fw, startField, Money.plain(c.startPrice), "z.B. 100 oder 1,5k");
		y += ROW;
		incField = field(fieldX, y, fw, incField, Money.plain(c.minIncrement), "z.B. 10");
		y += ROW;
		buyField = field(fieldX, y, fw, buyField, c.buyNowPrice > 0 ? Money.plain(c.buyNowPrice) : "0", "0 = kein Sofortkauf");
		y += ROW;

		addDrawableChild(ButtonWidget.builder(durationText(), b -> {
					durationIdx = (durationIdx + 1) % DURATIONS.length;
					b.setMessage(durationText());
				}).dimensions(fieldX, y - 1, fw, 20)
				.tooltip(Tooltip.of(Text.literal("Klicken zum Ändern"))).build());
		y += ROW;
		addDrawableChild(ButtonWidget.builder(snipeText(), b -> {
					snipeIdx = (snipeIdx + 1) % SNIPES.length;
					b.setMessage(snipeText());
				}).dimensions(fieldX, y - 1, fw, 20)
				.tooltip(Tooltip.of(Text.literal("Kommt kurz vor Schluss ein neues Höchstgebot, wird die Restzeit auf diesen Wert verlängert."))).build());
		y += ROW;

		String prevCmd = balanceCmdField != null ? balanceCmdField.getText() : c.balanceCommand;
		balanceCmdField = new TextFieldWidget(textRenderer, fieldX, y, fw, 18, Text.literal("Kontostand-Befehl"));
		balanceCmdField.setMaxLength(64);
		balanceCmdField.setText(prevCmd);
		balanceCmdField.setTooltip(Tooltip.of(Text.literal("Befehl ohne / der deinen Kontostand anzeigt, z.B. money, bal, geld")));
		addDrawableChild(balanceCmdField);
		y += ROW + 4;

		int tw = 98;
		addDrawableChild(ButtonWidget.builder(toggleText("Chat-Ansagen", announce), b -> {
					announce = !announce;
					b.setMessage(toggleText("Chat-Ansagen", announce));
				}).dimensions(cx - 150, y, tw, 20)
				.tooltip(Tooltip.of(Text.literal("Start, neue Gebote, Countdown und Gewinner automatisch in den Chat schreiben."))).build());
		addDrawableChild(ButtonWidget.builder(toggleText("Geld-Prüfung", verify), b -> {
					verify = !verify;
					b.setMessage(toggleText("Geld-Prüfung", verify));
				}).dimensions(cx - 49, y, tw, 20)
				.tooltip(Tooltip.of(Text.literal("EMPFOHLEN: Jede Zahlung wird mit deinem echten Kontostand abgeglichen. "
						+ "Nur wenn das Geld wirklich angekommen ist, zählt das Gebot. Gefälschte /msg-Nachrichten werden erkannt."))).build());
		addDrawableChild(ButtonWidget.builder(toggleText("Auto-Erstatt.", autoRefund), b -> {
					autoRefund = !autoRefund;
					b.setMessage(toggleText("Auto-Erstatt.", autoRefund));
				}).dimensions(cx + 52, y, tw, 20)
				.tooltip(Tooltip.of(Text.literal("Am Ende automatisch /pay an alle Überbotenen senden. Aus = per Knopf im Übersichtsmenü."))).build());
		y += ROW + 6;

		addDrawableChild(ButtonWidget.builder(Text.literal("§a§lAuktion starten"), b -> startAuction())
				.dimensions(cx - 150, y, 196, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Abbrechen"), b -> close())
				.dimensions(cx + 50, y, 100, 20).build());

		setInitialFocus(startField);
	}

	private TextFieldWidget field(int x, int y, int w, TextFieldWidget old, String def, String placeholder) {
		String value = old != null ? old.getText() : def;
		TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 18, Text.literal(placeholder));
		f.setMaxLength(16);
		f.setTextPredicate(s -> s.matches("[0-9.,kKmM ]*"));
		f.setText(value);
		f.setPlaceholder(Text.literal(placeholder));
		addDrawableChild(f);
		return f;
	}

	private void startAuction() {
		Double start = Money.parse(startField.getText());
		Double inc = Money.parse(incField.getText());
		String buyRaw = buyField.getText().trim();
		Double buy = buyRaw.isEmpty() ? Double.valueOf(0) : Money.parse(buyRaw);
		String cmd = balanceCmdField.getText().trim();
		if (cmd.startsWith("/")) cmd = cmd.substring(1);

		if (start == null || start <= 0) { error = "Bitte einen gültigen Startpreis eingeben."; return; }
		if (inc == null || inc < 0) { error = "Bitte eine gültige Mindesterhöhung eingeben."; return; }
		if (buy == null || buy < 0) { error = "Ungültiger Sofortkauf-Preis."; return; }
		if (buy > 0 && buy < start) { error = "Sofortkauf muss größer als der Startpreis sein."; return; }
		if (verify && cmd.isEmpty()) { error = "Für die Geld-Prüfung wird ein Kontostand-Befehl benötigt."; return; }
		if (client == null || client.player == null) return;

		AuctionConfig c = AuctionConfig.get();
		c.startPrice = start;
		c.minIncrement = inc;
		c.buyNowPrice = buy;
		c.durationSeconds = DURATIONS[durationIdx];
		c.antiSnipeSeconds = SNIPES[snipeIdx];
		c.announceInChat = announce;
		c.verifyBalance = verify;
		c.autoRefund = autoRefund;
		if (!cmd.isEmpty()) c.balanceCommand = cmd;
		c.save();

		AuctionManager.get().start(stack, new AuctionManager.Settings(start, inc, buy, DURATIONS[durationIdx],
				SNIPES[snipeIdx], announce, verify, autoRefund));
		close();
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.renderBackground(ctx, mouseX, mouseY, delta);
		int cx = width / 2;
		ctx.fill(cx - 158, top - 4, cx + 158, top + 248, 0xC0101018);
		ctx.fill(cx - 158, top - 4, cx + 158, top - 3, 0xFFFFAA00);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		int cx = width / 2;
		ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§6§lAuktion erstellen"), cx, top + 2, 0xFFFFFFFF);

		ctx.drawItem(stack, labelX, top + 15);
		ctx.drawStackOverlay(textRenderer, stack, labelX, top + 15);
		ctx.drawTextWithShadow(textRenderer, stack.getCount() + "x " + stack.getName().getString(), labelX + 20, top + 20, 0xFFFFFFFF);

		String[] labels = {"Startpreis (" + AuctionConfig.get().currency + ")", "Mindesterhöhung", "Sofortkauf-Preis",
				"Dauer", "Anti-Snipe", "Kontostand-Befehl"};
		int y = top + 40;
		for (String l : labels) {
			ctx.drawTextWithShadow(textRenderer, l, labelX, y + 5, 0xFFDDDDDD);
			y += ROW;
		}

		int bottom = top + 232;
		if (error != null) {
			ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(error), cx, bottom, 0xFFFF5555);
		} else {
			ctx.drawCenteredTextWithShadow(textRenderer,
					Text.literal("Spieler bieten mit /pay <dein Name> <Betrag> – Zahlungen werden addiert."), cx, bottom, 0xFF888888);
		}
	}

	private Text durationText() {
		return Text.literal("Dauer: " + Money.duration(DURATIONS[durationIdx]));
	}

	private Text snipeText() {
		int s = SNIPES[snipeIdx];
		return Text.literal(s == 0 ? "Aus" : "Verlängern auf " + s + " Sek.");
	}

	private static Text toggleText(String name, boolean on) {
		return Text.literal(name + ": " + (on ? "§aAN" : "§cAUS"));
	}

	private static int nearest(int[] arr, int value) {
		int best = 0;
		for (int i = 0; i < arr.length; i++) if (Math.abs(arr[i] - value) < Math.abs(arr[best] - value)) best = i;
		return best;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
