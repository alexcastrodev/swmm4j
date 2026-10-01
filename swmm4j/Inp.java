package swmm4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Inp {

	static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ROOT);

	static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

	private final Map<String, List<String>> sections = new LinkedHashMap<>();

	private Inp() {
	}

	public static Inp parse(String text) {
		Inp inp = new Inp();
		List<String> current = inp.add("");
		for (String line : text.split("\\R", -1)) {
			String trimmed = line.strip();
			if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
				current = inp.add(trimmed.substring(1, trimmed.length() - 1));
			} else {
				current.add(line);
			}
		}
		return inp;
	}

	List<String> section(String name) {
		return sections.get(name.toUpperCase(Locale.ROOT));
	}

	List<String> sectionOrAdd(String name) {
		List<String> lines = section(name);
		return lines != null ? lines : add(name);
	}

	void set(String section, Map<String, String> values) {
		List<String> lines = sectionOrAdd(section);
		lines.removeIf((line) -> values.containsKey(firstToken(line).toUpperCase(Locale.ROOT)));
		List<String> added = new ArrayList<>();
		values.forEach((key, value) -> added.add(String.format(Locale.ROOT, "%-20s %s", key, value)));
		addBeforeBlankTail(lines, added);
	}

	public String option(String key) {
		for (String line : sectionOrEmpty("OPTIONS")) {
			String[] t = tokens(line);
			if (isData(line) && t.length > 1 && t[0].equalsIgnoreCase(key)) {
				return t[1];
			}
		}
		return null;
	}

	public LocalDateTime dateTime(String prefix) {
		String date = option(prefix + "_DATE");
		String time = option(prefix + "_TIME");
		if (date == null || time == null) {
			throw new IllegalArgumentException("The .inp has no " + prefix + "_DATE / " + prefix + "_TIME: pass --start and --end");
		}
		return LocalDate.parse(date, DateTimeFormatter.ofPattern("M/d/yyyy"))
				.atTime(LocalTime.parse(time, DateTimeFormatter.ofPattern("H:mm[:ss]")));
	}

	@Override
	public String toString() {
		StringBuilder out = new StringBuilder();
		sections.forEach((name, lines) -> {
			if (!name.isEmpty()) {
				out.append('[').append(name).append("]\n");
			}
			lines.forEach((line) -> out.append(line).append('\n'));
		});
		return out.toString();
	}

	private List<String> add(String name) {
		List<String> lines = new ArrayList<>();
		sections.put(name.toUpperCase(Locale.ROOT), lines);
		return lines;
	}

	private List<String> sectionOrEmpty(String name) {
		List<String> lines = section(name);
		return lines != null ? lines : List.of();
	}

	static void addBeforeBlankTail(List<String> section, List<String> lines) {
		int end = section.size();
		while (end > 0 && section.get(end - 1).isBlank()) {
			end--;
		}
		section.addAll(end, lines);
		if (end == section.size() - lines.size()) {
			section.add("");
		}
	}

	static boolean isData(String line) {
		String trimmed = line.strip();
		return !trimmed.isEmpty() && !trimmed.startsWith(";");
	}

	static String[] tokens(String line) {
		return line.strip().split("\\s+");
	}

	static String firstToken(String line) {
		return tokens(line)[0];
	}

}
