package swmm4j;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class InpBuilder {

	static final Set<String> OVERRIDDEN_OPTIONS = Set.of("START_DATE", "START_TIME", "END_DATE", "END_TIME",
			"REPORT_START_DATE", "REPORT_START_TIME", "REPORT_STEP", "ROUTING_STEP");

	private InpBuilder() {
	}

	public static String build(String text, String csv, Scenario scenario, ZoneId zone) {
		Inp inp = Inp.parse(text);
		validate(inp, csv, scenario);
		List<String> inflows = inp.section("INFLOWS");
		Set<String> series = inflows == null ? Set.of() : flowSeries(inflows);

		inp.set("OPTIONS", options(scenario));
		if (scenario.flowScale() != 1) {
			scaleFlows(inp, scenario.flowScale());
		}
		if (scenario.rainScale() != 1) {
			scaleRain(inp.sectionOrAdd("ADJUSTMENTS"), scenario.rainScale());
		}
		if (csv != null) {
			importCsv(inp.sectionOrAdd("TIMESERIES"), csv, series, zone);
		}
		inp.set("REPORT", ordered("NODES", "ALL", "LINKS", "ALL"));
		return inp.toString();
	}

	private static void validate(Inp inp, String csv, Scenario scenario) {
		boolean inflows = inp.section("INFLOWS") != null;
		if (inp.section("OPTIONS") == null) {
			throw new IllegalArgumentException("The .inp has no [OPTIONS] section");
		}
		if (!inflows && inp.section("DWF") == null && scenario.flowScale() != 1) {
			throw new IllegalArgumentException("The .inp has no [INFLOWS] or [DWF] section to scale");
		}
		if (!inflows && csv != null) {
			throw new IllegalArgumentException("The .inp has no [INFLOWS] section to feed from a CSV");
		}
		if (inp.section("RAINGAGES") == null && scenario.rainScale() != 1) {
			throw new IllegalArgumentException("The .inp has no [RAINGAGES] section to scale");
		}
	}

	private static Map<String, String> options(Scenario s) {
		Map<String, String> options = ordered("START_DATE", Inp.DATE.format(s.start()), "START_TIME",
				Inp.TIME.format(s.start()), "REPORT_START_DATE", Inp.DATE.format(s.start()), "REPORT_START_TIME",
				Inp.TIME.format(s.start()), "END_DATE", Inp.DATE.format(s.end()), "END_TIME", Inp.TIME.format(s.end()),
				"REPORT_STEP",
				String.format(Locale.ROOT, "%02d:%02d:00", s.reportStepMin() / 60, s.reportStepMin() % 60),
				"ROUTING_STEP", Integer.toString(s.routingStepS()));
		options.putAll(s.options());
		return options;
	}

	private static Map<String, String> ordered(String... keyValues) {
		Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			map.put(keyValues[i], keyValues[i + 1]);
		}
		return map;
	}

	private static void scaleFlows(Inp inp, double scale) {
		if (inp.section("INFLOWS") != null) {
			scaleInflows(inp.section("INFLOWS"), scale);
		}
		if (inp.section("DWF") != null) {
			scaleDwf(inp.section("DWF"), scale);
		}
	}

	private static Set<String> flowSeries(List<String> inflows) {
		Set<String> series = new LinkedHashSet<>();
		for (String line : inflows) {
			if (!Inp.isData(line)) {
				continue;
			}
			String[] t = Inp.tokens(line);
			if (t.length < 3) {
				throw new IllegalArgumentException("Invalid [INFLOWS] line: " + line.strip());
			}
			if (isFlow(t) && !t[2].equals("\"\"")) {
				series.add(t[2]);
			}
		}
		return series;
	}

	private static void scaleInflows(List<String> lines, double scale) {
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			if (!Inp.isData(line) || !isFlow(Inp.tokens(line))) {
				continue;
			}
			List<String> t = new ArrayList<>(List.of(Inp.tokens(line)));
			while (t.size() < 6) {
				t.add(t.size() == 3 ? "FLOW" : "1.0");
			}
			t.set(5, times(t.get(5), scale));
			if (t.size() > 6) {
				t.set(6, times(t.get(6), scale));
			}
			lines.set(i, String.join("  ", t));
		}
	}

	private static void scaleDwf(List<String> lines, double scale) {
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			if (Inp.isData(line)) {
				String[] t = Inp.tokens(line);
				if (t.length >= 3 && isFlow(t)) {
					t[2] = times(t[2], scale);
					lines.set(i, String.join("  ", t));
				}
			}
		}
	}

	private static void scaleRain(List<String> adjustments, double scale) {
		for (int i = 0; i < adjustments.size(); i++) {
			String line = adjustments.get(i);
			if (Inp.isData(line) && Inp.firstToken(line).equalsIgnoreCase("RAINFALL")) {
				String[] t = Inp.tokens(line);
				for (int k = 1; k < t.length; k++) {
					t[k] = times(t[k], scale);
				}
				adjustments.set(i, String.join("  ", t));
				return;
			}
		}
		Inp.addBeforeBlankTail(adjustments,
				List.of("RAINFALL  " + String.join("  ", Collections.nCopies(12, Double.toString(scale)))));
	}

	private static void importCsv(List<String> timeseries, String csv, Set<String> series, ZoneId zone) {
		Map<String, TreeMap<LocalDateTime, Double>> bySeries = InflowCsv.read(csv, zone);
		for (String name : bySeries.keySet()) {
			if (!series.contains(name)) {
				throw new IllegalArgumentException(
						"The CSV has series " + name + ", which is not used in [INFLOWS] " + series);
			}
		}
		for (String name : series) {
			if (!bySeries.containsKey(name)) {
				throw new IllegalArgumentException("Series " + name + " of [INFLOWS] has no rows in the CSV");
			}
		}
		timeseries.removeIf((line) -> Inp.isData(line) && series.contains(Inp.firstToken(line)));
		timeseries.add(";;Name           Date       Time   Value (from the CSV)");
		bySeries.forEach((name, points) -> points.forEach((t, flow) -> timeseries.add(String.format(Locale.ROOT,
				"%-16s %s %s %s", name, Inp.DATE.format(t), Inp.TIME.format(t), flow))));
		timeseries.add("");
	}

	private static boolean isFlow(String[] tokens) {
		return tokens[1].equalsIgnoreCase("FLOW");
	}

	private static String times(String number, double scale) {
		return Double.toString(Double.parseDouble(number) * scale);
	}

}
