package de.auktionsmod;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Parsen und Formatieren von Geldbetraegen ("1.500", "1,500.50", "2,5k", "1Mio" ...). */
public final class Money {
	public static final double EPS = 0.009;
	private static final DecimalFormat FORMAT = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.GERMANY));

	private Money() {}

	public static Double parse(String raw) {
		if (raw == null) return null;
		String s = raw.trim().replace(" ", "").replace(" ", "").replace("'", "");
		String lower = s.toLowerCase(Locale.ROOT);
		double mult = 1;
		if (lower.endsWith("mio")) {
			mult = 1_000_000;
			s = s.substring(0, s.length() - 3);
		} else if (lower.endsWith("k")) {
			mult = 1_000;
			s = s.substring(0, s.length() - 1);
		} else if (lower.endsWith("m")) {
			mult = 1_000_000;
			s = s.substring(0, s.length() - 1);
		}
		s = s.replaceAll("[^0-9.,]", "");
		if (s.isEmpty() || !Character.isDigit(s.charAt(0))) return null;

		int lastDot = s.lastIndexOf('.');
		int lastComma = s.lastIndexOf(',');
		String norm;
		if (lastDot >= 0 && lastComma >= 0) {
			char dec = lastDot > lastComma ? '.' : ',';
			char thousands = dec == '.' ? ',' : '.';
			norm = s.replace(String.valueOf(thousands), "").replace(dec, '.');
		} else if (lastDot >= 0 || lastComma >= 0) {
			char sep = lastDot >= 0 ? '.' : ',';
			int count = (int) s.chars().filter(c -> c == sep).count();
			int digitsAfter = s.length() - 1 - s.lastIndexOf(sep);
			if (count > 1 || (digitsAfter == 3 && mult == 1)) {
				norm = s.replace(String.valueOf(sep), ""); // Tausendertrenner
			} else {
				norm = s.replace(sep, '.'); // Dezimaltrenner
			}
		} else {
			norm = s;
		}
		try {
			double v = Double.parseDouble(norm) * mult;
			return Double.isFinite(v) ? v : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Fuer die Anzeige: "1.500,5$" */
	public static String fmt(double v) {
		synchronized (FORMAT) {
			return FORMAT.format(v) + AuctionConfig.get().currency;
		}
	}

	/** Fuer Textfelder: ohne Waehrung */
	public static String plain(double v) {
		synchronized (FORMAT) {
			return FORMAT.format(v);
		}
	}

	/** Fuer Befehle (/pay): "1500" oder "1500.50" */
	public static String command(double v) {
		if (Math.abs(v - Math.rint(v)) < 0.0001) return Long.toString(Math.round(v));
		return String.format(Locale.ROOT, "%.2f", v);
	}

	public static String time(long seconds) {
		if (seconds < 0) seconds = 0;
		if (seconds >= 3600) return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
		return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
	}

	public static String duration(int seconds) {
		if (seconds < 60) return seconds + " Sek.";
		if (seconds % 3600 == 0) return (seconds / 3600) + " Std.";
		if (seconds % 60 == 0) return (seconds / 60) + " Min.";
		return time(seconds);
	}
}
