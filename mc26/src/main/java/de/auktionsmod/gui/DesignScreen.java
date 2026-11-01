package de.auktionsmod.gui;

import de.auktionsmod.AuctionConfig;
import de.auktionsmod.Theme;
import net.minecraft.minecraft.gui.GuiGraphicsExtractor;
import net.minecraft.minecraft.gui.screens.Screen;
import net.minecraft.minecraft.gui.components.Tooltip;
import net.minecraft.minecraft.gui.components.Button;
import net.minecraft.minecraft.gui.components.EditBox;
import de.auktionsmod.compat.Compat;
import net.minecraft.network.chat.Component;

/** Design-Einstellungen mit Live-Vorschau des HUD. */
public class DesignScreen extends AnimatedScreen {
	private static final int W = 404, H = 262, ROW = 19;
	private static final int[] OPACITY = {30, 40, 50, 60, 70, 80, 85, 90, 95, 100};
	private static final int[] SCALES = {50, 60, 75, 90, 100, 110, 125, 150, 175, 200};

	private final Screen parent;
	private int x0, y0;
	private EditBox accentField, bgField, tagField, currencyField;

	public DesignScreen(Screen parent) {
		super(Component.literal("Design"));
		this.parent = parent;
	}

	private static AuctionConfig c() {
		return AuctionConfig.get();
	}

	@Override
	protected void init() {
		AuctionConfig c = c();
		x0 = (width - W) / 2;
		y0 = Math.max(4, (height - H) / 2);
		int cx = x0 + 92, cw = 120;
		int y = y0 + 24;

		addRenderableWidget(Button.builder(Component.literal(c.theme), b -> {
			int i = presetIndex(c.theme);
			Theme.Preset p = Theme.PRESETS[(i + 1) % Theme.PRESETS.length];
			Theme.applyPreset(c, p);
			rebuildWidgets();
		}).bounds(cx, y, cw, 18).tooltip(Tooltip.create(Component.literal("Klicken für das nächste Farbthema"))).build());
		y += ROW;

		accentField = colorField(cx, y, c.accentColor, s -> { c.accentColor = s; c.theme = "Eigenes"; });
		y += ROW;
		bgField = colorField(cx, y, c.backgroundColor, s -> { c.backgroundColor = s; c.theme = "Eigenes"; });
		y += ROW;

		addRenderableWidget(Button.builder(Component.literal(c.backgroundOpacity + " %"), b -> {
			c.backgroundOpacity = next(OPACITY, c.backgroundOpacity);
			Theme.reload(c);
			b.setMessage(Component.literal(c.backgroundOpacity + " %"));
		}).bounds(cx, y, cw, 18).build());
		y += ROW;

		addRenderableWidget(Button.builder(Component.literal(Theme.HudPos.of(c.hudPosition).label), b -> {
			Theme.HudPos[] all = Theme.HudPos.values();
			Theme.HudPos nextPos = all[(Theme.HudPos.of(c.hudPosition).ordinal() + 1) % all.length];
			c.hudPosition = nextPos.name();
			b.setMessage(Component.literal(nextPos.label));
		}).bounds(cx, y, cw, 18).build());
		y += ROW;

		addRenderableWidget(Button.builder(Component.literal(c.hudScale + " %"), b -> {
			c.hudScale = next(SCALES, c.hudScale);
			b.setMessage(Component.literal(c.hudScale + " %"));
		}).bounds(cx, y, cw, 18).build());
		y += ROW;

		addRenderableWidget(Button.builder(Component.literal(linesText(c.hudLines)), b -> {
			c.hudLines = (c.hudLines + 1) % 9;
			b.setMessage(Component.literal(linesText(c.hudLines)));
		}).bounds(cx, y, cw, 18).build());
		y += ROW;

		addRenderableWidget(Button.builder(Component.literal("HUD " + onOff(c.showHud)), b -> {
			c.showHud = !c.showHud;
			b.setMessage(Component.literal("HUD " + onOff(c.showHud)));
		}).bounds(cx, y, 59, 18).tooltip(Tooltip.create(Component.literal("Info-Box während der Auktion anzeigen"))).build());
		addRenderableWidget(Button.builder(Component.literal("Name " + onOff(c.hudShowSeller)), b -> {
			c.hudShowSeller = !c.hudShowSeller;
			b.setMessage(Component.literal("Name " + onOff(c.hudShowSeller)));
		}).bounds(cx + 61, y, 59, 18).tooltip(Tooltip.create(Component.literal("Auktionator im HUD anzeigen"))).build());
		y += ROW;

		addRenderableWidget(Button.builder(Component.literal("Anim. " + onOff(c.winAnimation)), b -> {
			c.winAnimation = !c.winAnimation;
			b.setMessage(Component.literal("Anim. " + onOff(c.winAnimation)));
		}).bounds(cx, y, 59, 18).tooltip(Tooltip.create(Component.literal("Konfetti-Animation, wenn jemand gewinnt"))).build());
		addRenderableWidget(Button.builder(Component.literal("Sound " + onOff(c.winSound)), b -> {
			c.winSound = !c.winSound;
			b.setMessage(Component.literal("Sound " + onOff(c.winSound)));
		}).bounds(cx + 61, y, 59, 18).build());
		y += ROW;

		tagField = new EditBox(font, cx, y, cw, 18, Component.literal("Chat-Tag"));
		tagField.setMaxLength(24);
		tagField.setValue(c.chatTag);
		tagField.setResponder(s -> c.chatTag = s);
		addRenderableWidget(tagField);
		y += ROW;

		currencyField = new EditBox(font, cx, y, cw, 18, Component.literal("Währung"));
		currencyField.setMaxLength(8);
		currencyField.setValue(c.currency);
		currencyField.setResponder(s -> c.currency = s);
		addRenderableWidget(currencyField);

		int by = y0 + H - 26;
		addRenderableWidget(Button.builder(Component.literal("Zurücksetzen"), b -> {
			AuctionConfig d = new AuctionConfig();
			Theme.applyPreset(c, Theme.PRESETS[0]);
			c.goodColor = d.goodColor;
			c.badColor = d.badColor;
			c.backgroundOpacity = d.backgroundOpacity;
			c.hudPosition = d.hudPosition;
			c.hudScale = d.hudScale;
			c.hudLines = d.hudLines;
			c.hudOffsetX = d.hudOffsetX;
			c.hudOffsetY = d.hudOffsetY;
			c.showHud = d.showHud;
			c.hudShowSeller = d.hudShowSeller;
			c.winAnimation = d.winAnimation;
			c.winSound = d.winSound;
			c.chatTag = d.chatTag;
			c.currency = d.currency;
			Theme.reload(c);
			rebuildWidgets();
		}).bounds(x0 + 8, by, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Animation testen"), b -> {
			if (!c.winAnimation) c.winAnimation = true;
			String me = de.auktionsmod.AuctionManager.myName();
			WinAnimation.start(me.isEmpty() ? "Alex" : me, 1500, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
			rebuildWidgets();
		}).bounds(x0 + W / 2 - 55, by, 110, 20).build());
		addRenderableWidget(Button.builder(Component.literal("§lFertig"), b -> onClose())
				.bounds(x0 + W - 8 - 100, by, 100, 20).build());
	}

	private EditBox colorField(int x, int y, String value, java.util.function.Consumer<String> onValid) {
		EditBox f = new EditBox(font, x, y, 96, 18, Component.literal("#RRGGBB"));
		f.setMaxLength(7);
		f.setValue(value);
		f.setResponder(s -> {
			if (Theme.isHex(s)) {
				onValid.accept(s.startsWith("#") ? s.toUpperCase() : "#" + s.toUpperCase());
				Theme.reload(c());
			}
		});
		f.setTooltip(Tooltip.create(Component.literal("Farbe als Hex-Code, z.B. #FFAA00")));
		addRenderableWidget(f);
		return f;
	}

	@Override
	public void onClose() {
		c().save();
		Compat.setScreen(parent);
	}

	@Override
	protected void renderPanel(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		Ui.window(ctx, x0, y0, x0 + W, y0 + H);
		Ui.card(ctx, x0 + 220, y0 + 22, x0 + W - 8, y0 + H - 32);
		ctx.fill(x0 + 8, y0 + H - 30, x0 + W - 8, y0 + H - 29, Theme.border());
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		var tr = font;
		Ui.text(ctx, tr, "§lDesign", x0 + 8, y0 + 8, Theme.accent());
		Ui.textRight(ctx, tr, "Änderungen sind sofort sichtbar", x0 + W - 8, y0 + 8, Theme.muted());

		String[] labels = {"Farbthema", "Akzentfarbe", "Hintergrund", "Deckkraft", "HUD-Position", "HUD-Größe",
				"HUD-Zeilen", "Anzeigen", "Gewinn-Effekt", "Chat-Tag", "Währung"};
		int y = y0 + 24;
		for (String l : labels) {
			Ui.text(ctx, tr, l, x0 + 8, y + 5, Theme.text());
			y += ROW;
		}
		// Farbfelder neben den Hex-Eingaben
		swatch(ctx, x0 + 92 + 100, y0 + 24 + ROW, Theme.accent());
		swatch(ctx, x0 + 92 + 100, y0 + 24 + ROW * 2, 0xFF000000 | Theme.parse(c().backgroundColor, 0));

		// Vorschau
		int pw = W - 8 - 220;
		Ui.textCenter(ctx, tr, "Vorschau", x0 + 220 + pw / 2, y0 + 26, Theme.muted());
		AuctionHud.Model demo = AuctionHud.preview();
		float scale = Math.min(1f, (pw - 12) / (float) AuctionHud.WIDTH);
		int hx = x0 + 220 + (pw - Math.round(AuctionHud.WIDTH * scale)) / 2;
		AuctionHud.draw(ctx, tr, demo, hx, y0 + 38, scale);

		String tag = c().chatTag == null ? "" : c().chatTag;
		int chatY = y0 + H - 66;
		Ui.text(ctx, tr, tr.plainSubstrByWidth(tag, pw - 12), x0 + 226, chatY, Theme.accent());
		Ui.text(ctx, tr, tr.plainSubstrByWidth("+ Alex bietet 1.500" + c().currency, pw - 12), x0 + 226, chatY + 10, Theme.good());
		Ui.textCenter(ctx, tr, tr.plainSubstrByWidth("Texte: config/auktionsmod.json", pw - 8), x0 + 220 + pw / 2, y0 + H - 42, Theme.muted());
	}

	private static void swatch(GuiGraphicsExtractor ctx, int x, int y, int color) {
		ctx.fill(x, y + 1, x + 18, y + 17, 0xFF000000);
		ctx.fill(x + 1, y + 2, x + 17, y + 16, color);
	}

	private static int presetIndex(String name) {
		for (int i = 0; i < Theme.PRESETS.length; i++) if (Theme.PRESETS[i].name().equalsIgnoreCase(name)) return i;
		return -1;
	}

	private static int next(int[] values, int current) {
		for (int v : values) if (v > current) return v;
		return values[0];
	}

	private static String linesText(int n) {
		return n == 0 ? "Keine" : n + " Gebote";
	}

	private static String onOff(boolean on) {
		return on ? "§aAN" : "§cAUS";
	}

}
