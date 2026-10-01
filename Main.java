import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import swmm4j.Inp;
import swmm4j.InpBuilder;
import swmm4j.Scenario;
import swmm4j.Swmm;

@Command(name = "swmm-cli", sortOptions = false, usageHelpWidth = 100,
		description = "Runs an EPA SWMM .inp and prints the network's state at every report step.")
class Main implements Callable<Integer> {

	static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	@Option(names = { "-h", "--help" }, usageHelp = true, description = "shows this help")
	boolean help;

	@Option(names = "--inp", required = true, paramLabel = "FILE",
			description = "network (relative to the mounted folder)")
	Path inp;

	@Option(names = "--csv", paramLabel = "FILE",
			description = "inflows (series,timestamp,flow) replacing the [INFLOWS] series")
	Path csv;

	@Option(names = "--start", paramLabel = "TIME", description = "e.g. 2026-03-23T06:00 (default: the .inp's)")
	LocalDateTime start;

	@Option(names = "--end", paramLabel = "TIME", description = "default: the .inp's")
	LocalDateTime end;

	@Option(names = "--report-step", paramLabel = "MIN", defaultValue = "15",
			description = "5, 15 or 60 (default ${DEFAULT-VALUE})")
	int reportStep;

	@Option(names = "--routing-step", paramLabel = "S", defaultValue = "30",
			description = "1 to 60 (default ${DEFAULT-VALUE})")
	int routingStep;

	@Option(names = "--flow-scale", paramLabel = "X", defaultValue = "1",
			description = "multiplies every inflow and dry weather flow (default ${DEFAULT-VALUE})")
	double flowScale;

	@Option(names = "--rain-scale", paramLabel = "X", defaultValue = "1",
			description = "multiplies the rainfall (default ${DEFAULT-VALUE})")
	double rainScale;

	@Option(names = "--option", paramLabel = "KEY=VALUE",
			description = "sets an [OPTIONS] line, e.g. IGNORE_RAINFALL=YES (repeatable)")
	Map<String, String> options = new HashMap<>();

	@Option(names = "--node", paramLabel = "A,B", split = "\\s*,\\s*", splitSynopsisLabel = ",", description = "nodes to follow (depth and inflow)")
	List<String> nodes = List.of();

	@Option(names = "--lib", paramLabel = "FILE", defaultValue = "${env:SWMM_LIB:-/opt/swmm/libswmm5.so}",
			description = "libswmm5 (default $SWMM_LIB)")
	Path lib;

	@Option(names = "--dir", paramLabel = "DIR", defaultValue = "out",
			description = "where run.inp, run.rpt and run.out go (default ${DEFAULT-VALUE})")
	Path dir;

	public static void main(String[] args) {
		System.exit(new CommandLine(new Main()).setExecutionExceptionHandler((ex, cmd, parsed) -> {
			if (!(ex instanceof IllegalArgumentException || ex instanceof IllegalStateException)) {
				throw ex;
			}
			cmd.getErr().println(ex.getMessage());
			return 1;
		}).execute(args));
	}

	@Override
	public Integer call() throws Exception {
		String inpText = Files.readString(inp);
		String csvText = csv == null ? null : Files.readString(csv);
		Scenario scenario = scenario(inpText);

		String built = InpBuilder.build(inpText, csvText, scenario, ZoneId.systemDefault());

		System.out.println(summary(scenario));
		Swmm.Result result = simulate(built, scenario);
		System.out.printf("SWMM %s · flow continuity error %.2f %% · %d warnings%n", result.version(),
				result.continuityError(), result.warnings());
		System.out.println("report: " + result.report() + " · output: " + result.output());
		return 0;
	}

	Scenario scenario(String inpText) {
		Inp parsed = Inp.parse(inpText);
		LocalDateTime from = start != null ? start : parsed.dateTime("START");
		LocalDateTime to = end != null ? end : parsed.dateTime("END");
		Map<String, String> inpOptions = new HashMap<>();
		options.forEach((key, value) -> inpOptions.put(key.strip().toUpperCase(Locale.ROOT), value.strip()));
		return new Scenario(from, to, reportStep, routingStep, flowScale, rainScale, inpOptions);
	}

	Swmm.Result simulate(String inpText, Scenario scenario) throws IOException {
		return Swmm.run(inpText, scenario, nodes, lib, dir, (step) -> System.out.println(line(step)));
	}

	static String summary(Scenario s) {
		return String.format("%s → %s, report every %d min, flow ×%s, rain ×%s%s", CLOCK.format(s.start()),
				CLOCK.format(s.end()), s.reportStepMin(), s.flowScale(), s.rainScale(),
				s.options().isEmpty() ? "" : ", " + s.options());
	}

	static String line(Swmm.Step step) {
		StringBuilder out = new StringBuilder(
				String.format(Locale.ROOT, "%s %4.0f%%", CLOCK.format(step.time()), step.progress() * 100));
		List<Swmm.Node> flooding = step.flooding();
		if (flooding.isEmpty()) {
			out.append("  no flooding");
		} else {
			out.append("  flooding:");
			for (Swmm.Node n : flooding.subList(0, Math.min(5, flooding.size()))) {
				out.append(String.format(Locale.ROOT, " %s %.2f %s", n.name(), n.overflow(), step.units()));
			}
			if (flooding.size() > 5) {
				out.append(" (+").append(flooding.size() - 5).append(')');
			}
		}
		for (Swmm.Node n : step.followed()) {
			out.append(String.format(Locale.ROOT, "  | %s depth %.2f %s inflow %.2f %s", n.name(), n.depth(),
					step.length(), n.inflow(), step.units()));
		}
		return out.toString();
	}

}
