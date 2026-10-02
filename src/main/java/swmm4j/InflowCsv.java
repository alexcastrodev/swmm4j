package swmm4j;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class InflowCsv {

	static final Pattern DAY_FIRST = Pattern
			.compile("(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})[ T](\\d{1,2}):(\\d{2})(?::(\\d{2}))?");

	private InflowCsv() {
	}

	static Map<String, TreeMap<LocalDateTime, Double>> read(String text, ZoneId zone) {
		String[] lines = text.replace("\uFEFF", "").split("\\R");
		String separator = lines[0].contains(";") ? ";" : ",";
		List<String> header = Arrays.stream(lines[0].split(separator, -1))
				.map((name) -> cell(name).toLowerCase(Locale.ROOT)).toList();
		int seriesCol = column(header, "series", "inflow_id");
		int timeCol = column(header, "timestamp", "ts");
		int flowCol = column(header, "flow_lps", "flow");
		Map<String, TreeMap<LocalDateTime, Double>> bySeries = new LinkedHashMap<>();
		for (int i = 1; i < lines.length; i++) {
			if (lines[i].isBlank()) {
				continue;
			}
			int line = i + 1;
			String[] cells = lines[i].split(separator, -1);
			if (cells.length < header.size()) {
				throw new IllegalArgumentException(
						"CSV line " + line + ": " + cells.length + " fields, the header has " + header.size());
			}
			String name = cell(cells[seriesCol]);
			LocalDateTime t = time(cell(cells[timeCol]), zone, line);
			double flow = number(cell(cells[flowCol]), line);
			if (!(flow >= 0)) {
				throw new IllegalArgumentException("CSV line " + line + ": flow must be >= 0");
			}
			if (bySeries.computeIfAbsent(name, (k) -> new TreeMap<>()).put(t, flow) != null) {
				throw new IllegalArgumentException("CSV line " + line + ": repeated time " + t + " for " + name);
			}
		}
		return bySeries;
	}

	private static int column(List<String> header, String... names) {
		for (String name : names) {
			if (header.contains(name)) {
				return header.indexOf(name);
			}
		}
		throw new IllegalArgumentException(
				"The CSV has no column " + String.join(" / ", names) + " (header: " + String.join(",", header) + ")");
	}

	private static String cell(String raw) {
		String c = raw.strip();
		return c.length() >= 2 && c.startsWith("\"") && c.endsWith("\"") ? c.substring(1, c.length() - 1).strip() : c;
	}

	private static double number(String cell, int line) {
		try {
			return Double.parseDouble(cell.replace(',', '.'));
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException("CSV line " + line + ": invalid number " + cell);
		}
	}

	private static LocalDateTime time(String value, ZoneId zone, int line) {
		String iso = value.replace(' ', 'T');
		try {
			return OffsetDateTime.parse(iso).atZoneSameInstant(zone).toLocalDateTime();
		} catch (DateTimeParseException ignored) {
		}
		try {
			return LocalDateTime.parse(iso);
		} catch (DateTimeParseException ignored) {
		}
		Matcher m = DAY_FIRST.matcher(value);
		if (m.matches()) {
			return LocalDateTime.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)),
					Integer.parseInt(m.group(1)), Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)),
					m.group(6) == null ? 0 : Integer.parseInt(m.group(6)));
		}
		throw new IllegalArgumentException("CSV line " + line + ": invalid time " + value
				+ " (expected e.g. 2026-03-23T08:00, 2026-03-23T08:00Z or 23/03/2026 08:00)");
	}

}
