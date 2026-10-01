package swmm4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public record Scenario(LocalDateTime start, LocalDateTime end, int reportStepMin, int routingStepS, double flowScale,
		double rainScale, Map<String, String> options) {

	static final Set<Integer> REPORT_STEPS = Set.of(5, 15, 60);

	static final Duration MAX_DURATION = Duration.ofDays(31);

	public Scenario {
		if (start == null || end == null || !end.isAfter(start)) {
			throw new IllegalArgumentException("end must be after start");
		}
		if (Duration.between(start, end).compareTo(MAX_DURATION) > 0) {
			throw new IllegalArgumentException("A simulation lasts at most 31 days");
		}
		if (!REPORT_STEPS.contains(reportStepMin)) {
			throw new IllegalArgumentException("reportStepMin must be 5, 15 or 60");
		}
		if (routingStepS < 1 || routingStepS > 60) {
			throw new IllegalArgumentException("routingStepS must be between 1 and 60");
		}
		if (!(flowScale > 0 && flowScale <= 20)) {
			throw new IllegalArgumentException("flowScale must be in ]0, 20]");
		}
		if (!(rainScale > 0 && rainScale <= 20)) {
			throw new IllegalArgumentException("rainScale must be in ]0, 20]");
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
	}

	public double days() {
		return Duration.between(start, end).toSeconds() / 86400.0;
	}

}
