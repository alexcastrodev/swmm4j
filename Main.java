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
import swmm4j.InpBuilder;
import swmm4j.Scenario;
import swmm4j.Swmm;

@Command(name = "swmm-cli", sortOptions = false, usageHelpWidth = 100,
		description = "Runs an EPA SWMM .inp and prints the network's state at every report step.")
class Main implements Callable<Integer> {

	static final DateTimeFormatter MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	static final DateTimeFormatter SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	DateTimeFormatter clock = MINUTES;

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

	@Option(names = "--end", paramLabel = "TIME", description = "end of the simulation (default: the .inp's)")
	LocalDateTime end;

	@Option(names = "--report-step", paramLabel = "STEP",
			description = "minutes or HH:MM:SS, e.g. 15 or 00:00:30 (default: the .inp's)")
	String reportStep;

	@Option(names = "--routing-step", paramLabel = "STEP",
			description = "seconds or HH:MM:SS, e.g. 0.5 or 00:00:20 (default: the .inp's)")
	String routingStep;

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
		clock = scenario.reportStep().toSecondsPart() == 0 && scenario.start().getSecond() == 0 ? MINUTES : SECONDS;

		String built = InpBuilder.build(inpText, csvText, scenario, ZoneId.systemDefault());

		System.out.println(summary(scenario));
		Swmm.Result result = simulate(built, scenario);
		System.out.printf("SWMM %s · flow continuity error %.2f %% · %d warnings%n", result.version(),
				result.continuityError(), result.warnings());
		System.out.println("report: " + result.report() + " · output: " + result.output());
		return 0;
	}

	Scenario scenario(String inpText) {
		Map<String, String> inpOptions = new HashMap<>();
		options.forEach((key, value) -> inpOptions.put(key.strip().toUpperCase(Locale.ROOT), value.strip()));
		return Scenario.resolve(inpText, start, end, reportStep, routingStep, flowScale, rainScale, inpOptions);
	}

	Swmm.Result simulate(String inpText, Scenario scenario) throws IOException {
		return Swmm.run(inpText, scenario, nodes, lib, dir, (step) -> System.out.println(line(step)));
	}

	String summary(Scenario s) {
		return String.format("%s → %s, report every %s, flow ×%s, rain ×%s%s", clock.format(s.start()),
				clock.format(s.end()), s.reportStep().toString().substring(2).toLowerCase(Locale.ROOT), s.flowScale(), s.rainScale(),
				s.options().isEmpty() ? "" : ", " + s.options());
	}

	String line(Swmm.Step step) {
		StringBuilder out = new StringBuilder(
				String.format(Locale.ROOT, "%s %4.0f%%", clock.format(step.time()), step.progress() * 100));
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
