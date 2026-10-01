package swmm4j;

import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static swmm4j.ffi.swmm5.*;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public final class Swmm {

	static final String[] FLOW_UNITS = {"CFS", "GPM", "MGD", "CMS", "LPS", "MLD"};

	private Swmm() {
	}

	public record Node(String name, double depth, double inflow, double overflow) {
	}

	public record Step(LocalDateTime time, double progress, List<Node> flooding, List<Node> followed, String units,
			String length) {
	}

	public record Result(String version, double continuityError, int warnings, Path report, Path output) {
	}

	public static Result run(String inp, Scenario scenario, List<String> follow, Path lib, Path dir,
			Consumer<Step> onStep) throws IOException {
		if (!Files.isRegularFile(lib)) {
			throw new IllegalStateException("libswmm5 not found at " + lib);
		}
		System.load(lib.toAbsolutePath().toString());
		Files.createDirectories(dir);
		Path runInp = Files.writeString(dir.resolve("run.inp"), inp);
		Path report = dir.resolve("run.rpt");
		Path output = dir.resolve("run.out");

		try (Session swmm = Session.open(runInp, report, output)) {
			int units = swmm.flowUnits();
			List<String> names = swmm.nodeNames();
			List<Integer> followed = swmm.nodeIndexes(follow);
			double days = scenario.days();
			double reportDays = scenario.reportStepMin() / 1440.0;
			int reported = 0;
			double elapsed;
			do {
				elapsed = swmm.step();
				double t = elapsed == 0 ? days : elapsed;
				if (t >= (reported + 1) * reportDays - 1e-9) {
					reported = (int) (t / reportDays + 1e-9);
					onStep.accept(new Step(reportTime(scenario, reported), Math.min(t / days, 1),
							swmm.flooding(names), swmm.nodes(names, followed), FLOW_UNITS[units],
							units >= 3 ? "m" : "ft"));
				}
			} while (elapsed > 0);
			return swmm.finish(report, output);
		}
	}

	static LocalDateTime reportTime(Scenario scenario, int reported) {
		return scenario.start().plusMinutes((long) reported * scenario.reportStepMin());
	}

	static final class Session implements AutoCloseable {

		private final Arena arena = Arena.ofConfined();

		private final MemorySegment elapsed = arena.allocate(JAVA_DOUBLE);

		private boolean running;

		private Session() {
		}

		static Session open(Path inp, Path report, Path output) {
			Session session = new Session();
			try {
				session.start(inp, report, output);
			} catch (RuntimeException ex) {
				session.close();
				throw ex;
			}
			return session;
		}

		private void start(Path inp, Path report, Path output) {
			check(swmm_open(arena.allocateFrom(inp.toString()), arena.allocateFrom(report.toString()),
					arena.allocateFrom(output.toString())));
			check(swmm_start(1));
			running = true;
		}

		double step() {
			check(swmm_step(elapsed));
			return elapsed.get(JAVA_DOUBLE, 0);
		}

		int flowUnits() {
			return (int) swmm_getValue(swmm_FLOWUNITS(), 0);
		}

		List<String> nodeNames() {
			int count = swmm_getCount(swmm_NODE());
			MemorySegment buffer = arena.allocate(64);
			List<String> names = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				swmm_getName(swmm_NODE(), i, buffer, 64);
				names.add(buffer.getString(0));
			}
			return names;
		}

		List<Integer> nodeIndexes(List<String> names) {
			List<Integer> indexes = new ArrayList<>();
			for (String name : names) {
				int index = swmm_getIndex(swmm_NODE(), arena.allocateFrom(name));
				if (index < 0) {
					throw new IllegalArgumentException("No node " + name + " in the .inp");
				}
				indexes.add(index);
			}
			return indexes;
		}

		Node node(List<String> names, int i) {
			return new Node(names.get(i), swmm_getValue(swmm_NODE_DEPTH(), i), swmm_getValue(swmm_NODE_INFLOW(), i),
					swmm_getValue(swmm_NODE_OVERFLOW(), i));
		}

		List<Node> nodes(List<String> names, List<Integer> indexes) {
			List<Node> nodes = new ArrayList<>();
			for (int i : indexes) {
				nodes.add(node(names, i));
			}
			return nodes;
		}

		List<Node> flooding(List<String> names) {
			List<Node> flooding = new ArrayList<>();
			for (int i = 0; i < names.size(); i++) {
				Node node = node(names, i);
				if (node.overflow() > 1e-6) {
					flooding.add(node);
				}
			}
			flooding.sort(Comparator.comparingDouble(Node::overflow).reversed());
			return flooding;
		}

		Result finish(Path report, Path output) {
			check(swmm_end());
			running = false;
			MemorySegment errors = arena.allocate(JAVA_FLOAT, 3);
			swmm_getMassBalErr(errors.asSlice(0, 4), errors.asSlice(4, 4), errors.asSlice(8, 4));
			check(swmm_report());
			int v = swmm_getVersion();
			return new Result(v / 10000 + "." + v / 1000 % 10 + "." + v % 1000, errors.getAtIndex(JAVA_FLOAT, 1),
					swmm_getWarnings(), report, output);
		}

		@Override
		public void close() {
			try {
				if (running) {
					swmm_end();
				}
				swmm_close();
			} finally {
				arena.close();
			}
		}

		private void check(int code) {
			if (code != 0) {
				MemorySegment message = arena.allocate(512);
				swmm_getError(message, 512);
				throw new IllegalArgumentException("SWMM error " + code + ": " + message.getString(0).strip());
			}
		}

	}

}
