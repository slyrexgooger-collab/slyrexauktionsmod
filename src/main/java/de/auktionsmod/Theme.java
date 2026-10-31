package de.auktionsmod;

/** Farben fuer GUI und HUD – kommen aus der Konfiguration und sind im Spiel einstellbar. */
public final class Theme {
	public record Preset(String name, int accent, int background, int text, int muted) {}

	public static final Preset[] PRESETS = {
			new Preset("Gold", 0xFFAA00, 0x101018, 0xFFFFFF, 0x9A9AA8),
			new Preset("Ozean", 0x3FA9F5, 0x0B1622, 0xEAF6FF, 0x8AA4B8),
			new Preset("Smaragd", 0x2ECC71, 0x0D1A12, 0xEFFFF4, 0x8FB09B),
			new Preset("Rubin", 0xFF4D5E, 0x1A0D10, 0xFFF0F2, 0xB89097),
			new Preset("Lavendel", 0xA77BFF, 0x140F1F, 0xF4EEFF, 0xA299B8),
			new Preset("Pink", 0xFF6FB5, 0x1A0F16, 0xFFF0F8, 0xB896A8),
			new Preset("Eis", 0x7FE7FF, 0x0E1416, 0xFFFFFF, 0x9AB0B6),
			new Preset("Minimal", 0xFFFFFF, 0x000000, 0xFFFFFF, 0x9A9A9A),
	};

	public enum HudPos {
		OBEN_LINKS("Oben links"), OBEN_MITTE("Oben Mitte"), OBEN_RECHTS("Oben rechts"),
		UNTEN_LINKS("Unten links"), UNTEN_RECHTS("Unten rechts");

		public final String label;

		HudPos(String label) {
			this.label = label;
		}

		public static HudPos of(String s) {
			for (HudPos p : values()) if (p.name().equalsIgnoreCase(s)) return p;
			return OBEN_LINKS;
		}
	}

	private static int accent = 0xFFAA00, background = 0x101018, text = 0xFFFFFF, muted = 0x9A9AA8,
			good = 0x55FF55, bad = 0xFF5555, opacity = 85;

	private Theme() {}

	public static void reload(AuctionConfig c) {
		accent = parse(c.accentColor, 0xFFAA00);
		background = parse(c.backgroundColor, 0x101018);
		text = parse(c.textColor, 0xFFFFFF);
		muted = parse(c.mutedColor, 0x9A9AA8);
		good = parse(c.goodColor, 0x55FF55);
		bad = parse(c.badColor, 0xFF5555);
		opacity = Math.max(0, Math.min(100, c.backgroundOpacity));
	}

	public static void applyPreset(AuctionConfig c, Preset p) {
		c.theme = p.name();
		c.accentColor = hex(p.accent());
		c.backgroundColor = hex(p.background());
		c.textColor = hex(p.text());
		c.mutedColor = hex(p.muted());
		reload(c);
	}

	// ---- ARGB-Farben (Alpha immer gesetzt, sonst unsichtbar ab 1.21.6)
	public static int accent() { return 0xFF000000 | accent; }
	public static int accentRgb() { return accent; }
	public static int accent(int alpha) { return (alpha << 24) | accent; }
	public static int text() { return 0xFF000000 | text; }
	public static int muted() { return 0xFF000000 | muted; }
	public static int good() { return 0xFF000000 | good; }
	public static int bad() { return 0xFF000000 | bad; }

	public static int background() {
		return ((int) (opacity * 2.55f) << 24) | background;
	}

	/** Etwas hellere Flaeche fuer Karten/Listen */
	public static int card() {
		int a = Math.min(255, (int) (opacity * 2.55f) + 20);
		return (a << 24) | mix(background, 0xFFFFFF, 0.07f);
	}

	public static int cardHover() {
		return 0xFF000000 | mix(background, accent, 0.18f);
	}

	public static int border() {
		return (0x90 << 24) | mix(background, accent, 0.55f);
	}

	public static int mix(int a, int b, float t) {
		int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return (r << 16) | (g << 8) | bl;
	}

	public static int parse(String hex, int fallback) {
		if (hex == null) return fallback;
		String s = hex.trim();
		if (s.startsWith("#")) s = s.substring(1);
		if (s.length() != 6) return fallback;
		try {
			return Integer.parseInt(s, 16) & 0xFFFFFF;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	public static boolean isHex(String hex) {
		return hex != null && hex.trim().matches("#?[0-9a-fA-F]{6}");
	}

	public static String hex(int rgb) {
		return String.format("#%06X", rgb & 0xFFFFFF);
	}
}
