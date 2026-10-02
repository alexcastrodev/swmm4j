package swmm4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public record Scenario(LocalDateTime start, LocalDateTime end, Duration reportStep, double flowScale,
		double rainScale, Map<String, String> options, Map<String, String> overrides) {

	public Scenario {
		if (start == null || end == null || !end.isAfter(start)) {
			throw new IllegalArgumentException("end must be after start");
		}
		if (reportStep == null || reportStep.isNegative() || reportStep.isZero()) {
			throw new IllegalArgumentException("The report step must be positive");
		}
		if (!(flowScale > 0)) {
			throw new IllegalArgumentException("flowScale must be positive");
		}
		if (!(rainScale > 0)) {
			throw new IllegalArgumentException("rainScale must be positive");
		}
		options.forEach((key, value) -> {
			if (!key.matches("[A-Z_]+") || value.isBlank() || value.contains("\n")) {
				throw new IllegalArgumentException("Invalid option " + key + "=" + value);
			}
			if (InpBuilder.OVERRIDDEN_OPTIONS.contains(key)) {
				throw new IllegalArgumentException(
						key + " is set by --start, --end, --report-step and --routing-step, not --option");
			}
		});
		options = Collections.unmodifiableMap(new TreeMap<>(options));
		overrides = Collections.unmodifiableMap(new LinkedHashMap<>(overrides));
	}

	public static Scenario resolve(String inpText, LocalDateTime start, LocalDateTime end, String reportStep,
			String routingStep, double flowScale, double rainScale, Map<String, String> options) {
		Inp inp = Inp.parse(inpText);
		Map<String, String> overrides = new LinkedHashMap<>();
		if (start != null) {
			overrides.put("START_DATE", Inp.DATE.format(start));
			overrides.put("START_TIME", Inp.TIME.format(start));
			overrides.put("REPORT_START_DATE", Inp.DATE.format(start));
			overrides.put("REPORT_START_TIME", Inp.TIME.format(start));
		}
		if (end != null) {
			overrides.put("END_DATE", Inp.DATE.format(end));
			overrides.put("END_TIME", Inp.TIME.format(end));
		}
		Duration report = inp.reportStep();
		if (reportStep != null) {
			report = Duration.ofMillis(Math.round(Inp.seconds(reportStep, 60) * 1000));
			overrides.put("REPORT_STEP", Inp.clock(report));
		}
		if (routingStep != null) {
			if (!(Inp.seconds(routingStep, 1) > 0)) {
				throw new IllegalArgumentException("The routing step must be positive");
			}
			overrides.put("ROUTING_STEP", routingStep.strip());
		}
		return new Scenario(start != null ? start : inp.dateTime("START"), end != null ? end : inp.dateTime("END"),
				report, flowScale, rainScale, options, overrides);
	}

	public double days() {
		return Duration.between(start, end).toSeconds() / 86400.0;
	}

}
