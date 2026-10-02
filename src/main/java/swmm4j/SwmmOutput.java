package swmm4j;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static swmm4j.out.swmm_output.*;

import java.io.IOException;
import java.io.Writer;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns a SWMM binary output (.out) into JSON with EPA's own reader, libswmm-output (the SMO_* API),
 * read period by period: the report times, then every non-pollutant series of each subcatchment,
 * node and link, and of the whole system.
 */
public final class SwmmOutput {

	static final String[] FLOW_UNITS = {"CFS", "GPM", "MGD", "CMS", "LPS", "MLD"};

	/** SWMM's dates are days since 30 Dec 1899. */
	static final LocalDateTime EPOCH = LocalDateTime.of(1899, 12, 30, 0, 0);

	/** The JSON names of each element type's attributes, in the SMO_*Attribute order (pollutants left out). */
	static final String[] SUBCATCH = {"rainfall", "snowDepth", "evapLoss", "infilLoss", "runoff", "gwOutflow",
			"gwElevation", "soilMoisture"};

	static final String[] NODE = {"depth", "head", "volume", "lateralInflow", "inflow", "flooding"};

	static final String[] LINK = {"flow", "depth", "velocity", "volume", "capacity"};

	static final String[] SYSTEM = {"airTemperature", "rainfall", "snowDepth", "evapInfilLoss", "runoff",
			"dryWeatherInflow", "groundwaterInflow", "rdiiInflow", "directInflow", "lateralInflow", "flooding",
			"outfallFlow", "storageVolume", "evaporation"};

	/**
	 * EPA's messages for what SMO_open returns: on these the reader has already closed and freed the
	 * handle, so its own message (SMO_checkError) can no longer be asked for.
	 */
	static final Map<Integer, String> OPEN_ERRORS = Map.of(434, "unable to open binary output file", 435,
			"invalid file - not created by SWMM", 436, "invalid file - contains no results");

	private SwmmOutput() {
	}

	/**
	 * What a .out holds: the report times, then for each subcatchment, node and link (and the system) one
	 * series per attribute, {@code values[attribute][element][period]} in the order of {@link #SUBCATCH},
	 * {@link #NODE}, {@link #LINK} and {@link #SYSTEM}.
	 *
	 * @param version
	 *            the SWMM that wrote it, e.g. 5.2.4
	 */
	public record Output(String version, String flowUnits, int reportStepS, List<LocalDateTime> times,
			List<String> subcatchments, List<String> nodes, List<String> links, float[][][] subcatchValues,
			float[][][] nodeValues, float[][][] linkValues, float[][][] systemValues) {

		public float[] node(String id, String attribute) {
			return nodeValues[List.of(NODE).indexOf(attribute)][nodes.indexOf(id)];
		}

		public float[] link(String id, String attribute) {
			return linkValues[List.of(LINK).indexOf(attribute)][links.indexOf(id)];
		}

		public float[] system(String attribute) {
			return systemValues[List.of(SYSTEM).indexOf(attribute)][0];
		}

	}

	/** What the JSON holds, for the summary line. */
	public record Written(Path json, String version, int periods, int nodes, int links) {
	}

	/** Reads the .out and writes it as JSON. */
	public static Written toJson(Path out, Path lib, Path json) throws IOException {
		Output o = read(out, lib);
		write(o, json);
		return new Written(json, o.version(), o.times().size(), o.nodes().size(), o.links().size());
	}

	public static Output read(Path out, Path lib) {
		if (!Files.isRegularFile(lib)) {
			throw new IllegalStateException("libswmm-output not found at " + lib);
		}
		if (!Files.isRegularFile(out)) {
			throw new IllegalArgumentException("No SWMM output at " + out);
		}
		System.load(lib.toAbsolutePath().toString());
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment slot = arena.allocate(ADDRESS);
			check(MemorySegment.NULL, SMO_init(slot), arena);
			MemorySegment handle = slot.get(ADDRESS, 0);
			boolean open = true;
			try {
				int opened = SMO_open(handle, arena.allocateFrom(out.toString()));
				if (opened > 400) {
					open = false; // SMO_open closed and freed it
					throw new IllegalStateException("SWMM output error " + opened + ": "
							+ OPEN_ERRORS.getOrDefault(opened, "cannot read " + out));
				}
				MemorySegment number = arena.allocate(JAVA_INT);
				check(handle, SMO_getVersion(handle, number), arena);
				int v = number.get(JAVA_INT, 0); // 52004 → 5.2.4
				String version = v / 10000 + "." + v / 1000 % 10 + "." + v % 1000;
				check(handle, SMO_getFlowUnits(handle, number), arena);
				int units = number.get(JAVA_INT, 0);
				check(handle, SMO_getTimes(handle, SMO_reportStep(), number), arena);
				int reportStep = number.get(JAVA_INT, 0);
				check(handle, SMO_getTimes(handle, SMO_numPeriods(), number), arena);
				int periods = number.get(JAVA_INT, 0);
				MemorySegment days = arena.allocate(JAVA_DOUBLE);
				check(handle, SMO_getStartDate(handle, days), arena);
				LocalDateTime start = EPOCH.plusSeconds(Math.round(days.get(JAVA_DOUBLE, 0) * 86_400));
				List<LocalDateTime> times = new ArrayList<>(periods);
				for (int p = 0; p < periods; p++) {
					// the first period is one report step after the report start
					times.add(start.plusSeconds((long) reportStep * (p + 1)));
				}

				int[] size = projectSize(handle, arena);
				List<String> subcatchments = names(handle, SMO_subcatch(), size[0], arena);
				List<String> nodes = names(handle, SMO_node(), size[1], arena);
				List<String> links = names(handle, SMO_link(), size[2], arena);

				float[][][] subcatchValues = new float[SUBCATCH.length][subcatchments.size()][periods];
				float[][][] nodeValues = new float[NODE.length][nodes.size()][periods];
				float[][][] linkValues = new float[LINK.length][links.size()][periods];
				float[][][] systemValues = new float[SYSTEM.length][1][periods];
				for (int p = 0; p < periods; p++) {
					for (int a = 0; a < SUBCATCH.length && !subcatchments.isEmpty(); a++) {
						scatter(values(handle, p, a, Kind.SUBCATCH, arena), subcatchValues[a], p);
					}
					for (int a = 0; a < NODE.length; a++) {
						scatter(values(handle, p, a, Kind.NODE, arena), nodeValues[a], p);
					}
					for (int a = 0; a < LINK.length; a++) {
						scatter(values(handle, p, a, Kind.LINK, arena), linkValues[a], p);
					}
				}
				// the whole period at once: SMO_getSystemAttribute (5.2.4) hands back a pointer to its stack
				for (int a = 0; a < SYSTEM.length && periods > 0; a++) {
					systemValues[a][0] = systemSeries(handle, a, periods, arena);
				}
				return new Output(version, units >= 0 && units < FLOW_UNITS.length ? FLOW_UNITS[units] : "?", reportStep,
						times, subcatchments, nodes, links, subcatchValues, nodeValues, linkValues, systemValues);
			} finally {
				if (open) {
					SMO_close(slot);
				}
			}
		}
	}

	public static void write(Output o, Path json) throws IOException {
		Path parent = json.toAbsolutePath().getParent();
		Files.createDirectories(parent);
		try (Writer w = Files.newBufferedWriter(json)) {
			w.write("{\"swmmVersion\":");
			string(w, o.version());
			w.write(",\"flowUnits\":");
			string(w, o.flowUnits());
			w.write(",\"reportStepS\":" + o.reportStepS() + ",\"times\":[");
			for (int p = 0; p < o.times().size(); p++) {
				w.write(p == 0 ? "" : ",");
				string(w, o.times().get(p).toString());
			}
			w.write("],\"subcatchments\":");
			elements(w, o.subcatchments(), SUBCATCH, o.subcatchValues());
			w.write(",\"nodes\":");
			elements(w, o.nodes(), NODE, o.nodeValues());
			w.write(",\"links\":");
			elements(w, o.links(), LINK, o.linkValues());
			w.write(",\"system\":");
			series(w, SYSTEM, o.systemValues(), 0);
			w.write("}\n");
		}
	}

	enum Kind {
		SUBCATCH, NODE, LINK
	}

	/** One attribute of every element of a kind at one period. */
	private static float[] values(MemorySegment handle, int period, int attribute, Kind kind, Arena arena) {
		MemorySegment array = arena.allocate(ADDRESS);
		MemorySegment length = arena.allocate(JAVA_INT);
		int code = switch (kind) {
			case SUBCATCH -> SMO_getSubcatchAttribute(handle, period, attribute, array, length);
			case NODE -> SMO_getNodeAttribute(handle, period, attribute, array, length);
			case LINK -> SMO_getLinkAttribute(handle, period, attribute, array, length);
		};
		check(handle, code, arena);
		int n = length.get(JAVA_INT, 0);
		MemorySegment data = array.get(ADDRESS, 0).reinterpret((long) n * Float.BYTES);
		float[] values = data.toArray(JAVA_FLOAT);
		SMO_free(array);
		return values;
	}

	/** One system attribute over every period. */
	private static float[] systemSeries(MemorySegment handle, int attribute, int periods, Arena arena) {
		MemorySegment array = arena.allocate(ADDRESS);
		MemorySegment length = arena.allocate(JAVA_INT);
		check(handle, SMO_getSystemSeries(handle, attribute, 0, periods, array, length), arena);
		float[] values = array.get(ADDRESS, 0).reinterpret((long) length.get(JAVA_INT, 0) * Float.BYTES).toArray(JAVA_FLOAT);
		SMO_free(array);
		return values;
	}

	private static int[] projectSize(MemorySegment handle, Arena arena) {
		MemorySegment array = arena.allocate(ADDRESS);
		MemorySegment length = arena.allocate(JAVA_INT);
		check(handle, SMO_getProjectSize(handle, array, length), arena);
		int n = length.get(JAVA_INT, 0);
		int[] size = array.get(ADDRESS, 0).reinterpret((long) n * Integer.BYTES).toArray(JAVA_INT);
		SMO_free(array);
		return size;
	}

	private static List<String> names(MemorySegment handle, int type, int count, Arena arena) {
		List<String> names = new ArrayList<>(count);
		MemorySegment name = arena.allocate(ADDRESS);
		MemorySegment length = arena.allocate(JAVA_INT);
		for (int i = 0; i < count; i++) {
			check(handle, SMO_getElementName(handle, type, i, name, length), arena);
			names.add(name.get(ADDRESS, 0).reinterpret(length.get(JAVA_INT, 0) + 1L).getString(0));
			SMO_free(name);
		}
		return names;
	}

	private static void scatter(float[] values, float[][] series, int period) {
		for (int i = 0; i < values.length && i < series.length; i++) {
			series[i][period] = values[i];
		}
	}

	private static void check(MemorySegment handle, int code, Arena arena) {
		if (code == 0) {
			return;
		}
		String message = "SWMM output error " + code;
		if (!MemorySegment.NULL.equals(handle)) {
			MemorySegment buffer = arena.allocate(ADDRESS);
			SMO_checkError(handle, buffer);
			MemorySegment text = buffer.get(ADDRESS, 0);
			if (!MemorySegment.NULL.equals(text)) {
				message += ": " + text.reinterpret(1024).getString(0).strip();
				SMO_free(buffer);
			}
		}
		throw new IllegalStateException(message);
	}

	/** {"id": {"attr": [..], ...}, ...} */
	private static void elements(Writer w, List<String> ids, String[] attrs, float[][][] values) throws IOException {
		w.write('{');
		for (int i = 0; i < ids.size(); i++) {
			w.write(i == 0 ? "" : ",");
			string(w, ids.get(i));
			w.write(':');
			series(w, attrs, values, i);
		}
		w.write('}');
	}

	private static void series(Writer w, String[] attrs, float[][][] values, int element) throws IOException {
		w.write('{');
		for (int a = 0; a < attrs.length; a++) {
			w.write(a == 0 ? "\"" : ",\"");
			w.write(attrs[a]);
			w.write("\":[");
			float[] v = values[a][element];
			for (int p = 0; p < v.length; p++) {
				w.write(p == 0 ? "" : ",");
				w.write(Float.isFinite(v[p]) ? Float.toString(v[p]) : "null");
			}
			w.write(']');
		}
		w.write('}');
	}

	static void string(Writer w, String s) throws IOException {
		w.write('"');
		for (char c : s.toCharArray()) {
			switch (c) {
				case '"' -> w.write("\\\"");
				case '\\' -> w.write("\\\\");
				default -> {
					if (c < 0x20) {
						w.write(String.format("\\u%04x", (int) c));
					} else {
						w.write(c);
					}
				}
			}
		}
		w.write('"');
	}

}
