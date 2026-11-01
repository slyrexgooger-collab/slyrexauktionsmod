package de.auktionsmod.gui;

import de.auktionsmod.AuctionConfig;
import de.auktionsmod.AuctionManager;
import de.auktionsmod.Money;
import de.auktionsmod.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/** Fenster zum Einstellen und Starten einer Auktion fuer das Item in der Hand. */
public class AuctionSetupScreen extends AnimatedScreen {
	private static final int[] DURATIONS = {30, 60, 120, 180, 300, 600, 900, 1800, 3600};
	private static final int[] SNIPES = {0, 10, 15, 30, 60};
	private static final int W = 372, H = 210, ROW = 22;

	private final ItemStack stack;
	private TextFieldWidget startField, incField, buyField;
	private int durationIdx, snipeIdx;
	private boolean announce;
	private String error;
	private int x0, y0;

	public AuctionSetupScreen(ItemStack held) {
		super(Text.literal("Neue Auktion"));
		this.stack = held.copy();
		AuctionConfig c = AuctionConfig.get();
		durationIdx = nearest(DURATIONS, c.durationSeconds);
		snipeIdx = nearest(SNIPES, c.antiSnipeSeconds);
		announce = c.announceInChat;
	}

	@Override
	protected void init() {
		AuctionConfig c = AuctionConfig.get();
		x0 = (width - W) / 2;
		y0 = Math.max(4, (height - H) / 2);
		int cx = x0 + 236, cw = 128;
		int y = y0 + 26;

		startField = field(cx, y, cw, startField, Money.plain(c.startPrice), "z.B. 100 oder 1,5k");
		y += ROW;
		incField = field(cx, y, cw, incField, Money.plain(c.minIncrement), "z.B. 10");
		y += ROW;
		buyField = field(cx, y, cw, buyField, c.buyNowPrice > 0 ? Money.plain(c.buyNowPrice) : "0", "0 = aus");
		y += ROW;

		addDrawableChild(ButtonWidget.builder(durationText(), b -> {
			durationIdx = (durationIdx + 1) % DURATIONS.length;
			b.setMessage(durationText());
		}).dimensions(cx, y - 1, cw, 20).tooltip(Tooltip.of(Text.literal("Klicken zum Ändern"))).build());
		y += ROW;
		addDrawableChild(ButtonWidget.builder(snipeText(), b -> {
			snipeIdx = (snipeIdx + 1) % SNIPES.length;
			b.setMessage(snipeText());
		}).dimensions(cx, y - 1, cw, 20).tooltip(Tooltip.of(Text.literal(
				"Kommt kurz vor Schluss ein neues Höchstgebot, wird die Restzeit auf diesen Wert verlängert."))).build());
		y += ROW;
		addDrawableChild(ButtonWidget.builder(onOff(announce), b -> {
			announce = !announce;
			b.setMessage(onOff(announce));
		}).dimensions(cx, y - 1, cw, 20).tooltip(Tooltip.of(Text.literal(
				"Start, neue Gebote, Countdown und Gewinner automatisch in den Chat schreiben."))).build());

		int by = y0 + H - 26;
		addDrawableChild(ButtonWidget.builder(Text.literal("Design"), b -> {
			if (client != null) client.setScreen(new DesignScreen(this));
		}).dimensions(x0 + 8, by, 80, 20).tooltip(Tooltip.of(Text.literal("Farben, HUD-Position und -Größe anpassen"))).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Abbrechen"), b -> close())
				.dimensions(x0 + W - 8 - 120 - 4 - 80, by, 80, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("§lAuktion starten"), b -> startAuction())
				.dimensions(x0 + W - 8 - 120, by, 120, 20).build());

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

		if (start == null || start <= 0) { error = "Bitte einen gültigen Startpreis eingeben."; return; }
		if (inc == null || inc < 0) { error = "Bitte eine gültige Mindesterhöhung eingeben."; return; }
		if (buy == null || buy < 0) { error = "Ungültiger Sofortkauf-Preis."; return; }
		if (buy > 0 && buy < start) { error = "Sofortkauf muss mindestens der Startpreis sein."; return; }
		if (client == null || client.player == null) return;

		AuctionConfig c = AuctionConfig.get();
		c.startPrice = start;
		c.minIncrement = inc;
		c.buyNowPrice = buy;
		c.durationSeconds = DURATIONS[durationIdx];
		c.antiSnipeSeconds = SNIPES[snipeIdx];
		c.announceInChat = announce;
		c.save();

		AuctionManager.get().start(stack, new AuctionManager.Settings(start, inc, buy, DURATIONS[durationIdx],
				SNIPES[snipeIdx], announce));
		close();
	}

	@Override
	protected void renderPanel(DrawContext ctx, int mouseX, int mouseY, float delta) {
		Ui.window(ctx, x0, y0, x0 + W, y0 + H);
		Ui.card(ctx, x0 + 8, y0 + 24, x0 + 128, y0 + H - 32);
		ctx.fill(x0 + 8, y0 + H - 30, x0 + W - 8, y0 + H - 29, Theme.border());
	}

	@Override
	protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
		String me = AuctionManager.myName();

		// Kopfzeile mit Auktionator
		Ui.text(ctx, textRenderer, "§lNeue Auktion", x0 + 8, y0 + 9, Theme.accent());
		String sellerLabel = "Auktionator: ";
		int sx = x0 + W - 8 - textRenderer.getWidth(sellerLabel + me) - 12;
		Ui.text(ctx, textRenderer, sellerLabel, sx, y0 + 9, Theme.muted());
		Ui.head(ctx, me, sx + textRenderer.getWidth(sellerLabel), y0 + 8, 9);
		Ui.text(ctx, textRenderer, me, sx + textRenderer.getWidth(sellerLabel) + 12, y0 + 9, Theme.text());

		// Item-Karte
		int cardCx = x0 + 68;
		// Item schwebt leicht auf und ab
		float bob = (float) Math.sin(System.currentTimeMillis() / 600.0) * 2f;
		Ui.bigItem(ctx, stack, cardCx - 24, Math.round(y0 + 34 + bob), 3f);
		int ty = y0 + 90;
		List<OrderedText> lines = textRenderer.wrapLines(stack.getName(), 110);
		for (int i = 0; i < Math.min(3, lines.size()); i++) {
			ctx.drawCenteredTextWithShadow(textRenderer, lines.get(i), cardCx, ty, Theme.text());
			ty += 10;
		}
		Ui.textCenter(ctx, textRenderer, "Menge: " + stack.getCount(), cardCx, ty + 2, Theme.muted());
		Ui.textCenter(ctx, textRenderer, "Bieten mit:", cardCx, y0 + H - 58, Theme.muted());
		Ui.textCenter(ctx, textRenderer, textRenderer.trimToWidth("/pay " + me + " <Betrag>", 114), cardCx, y0 + H - 47, Theme.accent());

		// Beschriftungen
		String cur = AuctionConfig.get().currency;
		String[] labels = {"Startpreis (" + cur + ")", "Mindesterhöhung", "Sofortkauf (" + cur + ")",
				"Dauer", "Anti-Snipe", "Chat-Ansagen"};
		int y = y0 + 26;
		for (String l : labels) {
			Ui.text(ctx, textRenderer, l, x0 + 136, y + 5, Theme.text());
			y += ROW;
		}

		if (error != null) {
			Ui.textCenter(ctx, textRenderer, error, x0 + 250, y0 + H - 44, Theme.bad());
		} else {
			Ui.textCenter(ctx, textRenderer, "Gebote unter dem Mindestgebot zählen nicht.", x0 + 250, y0 + H - 44, Theme.muted());
		}
	}

	private Text durationText() {
		return Text.literal(Money.duration(DURATIONS[durationIdx]));
	}

	private Text snipeText() {
		int s = SNIPES[snipeIdx];
		return Text.literal(s == 0 ? "Aus" : "Auf " + s + " Sek.");
	}

	private static Text onOff(boolean on) {
		return Text.literal(on ? "§aAN" : "§cAUS");
	}

	private static int nearest(int[] arr, int value) {
		int best = 0;
		for (int i = 0; i < arr.length; i++) if (Math.abs(arr[i] - value) < Math.abs(arr[best] - value)) best = i;
		return best;
	}

}
