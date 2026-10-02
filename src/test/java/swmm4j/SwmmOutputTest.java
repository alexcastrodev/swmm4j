package swmm4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("SwmmOutput: the EPA .out as data and as JSON")
class SwmmOutputTest {

	static final Path ENGINE = Path.of(System.getenv().getOrDefault("SWMM_LIB", "/opt/swmm/libswmm5.so"));

	static final Path READER = Path.of(System.getenv().getOrDefault("SWMM_OUTPUT_LIB", "/opt/swmm/libswmm-output.so"));

	@TempDir
	static Path dir;

	static SwmmOutput.Output flooding;

	static Scenario floodingScenario;

	static SwmmOutput.Output rain;

	@BeforeAll
	static void run() throws IOException {
		floodingScenario = scenario("examples/Example2.inp", 4);
		flooding = SwmmOutput.read(simulate("examples/Example2.inp", floodingScenario, "x4"), READER);
		rain = SwmmOutput.read(simulate("examples/Example1.inp", scenario("examples/Example1.inp", 1), "ex1"), READER);
	}

	static Scenario scenario(String inp, double flowScale) throws IOException {
		return Scenario.resolve(Files.readString(Path.of(inp)), null, null, null, null, flowScale, 1, Map.of());
	}

	static Path simulate(String inp, Scenario scenario, String name) throws IOException {
		String built = InpBuilder.build(Files.readString(Path.of(inp)), null, scenario, ZoneId.of("UTC"));
		return Swmm.run(built, scenario, List.of(), ENGINE, dir.resolve(name), (step) -> {
		}).output();
	}

	@Test
	@DisplayName("the SWMM version that wrote the file, the units and the report times")
	void header() {
		assertEquals("5.2.4", flooding.version());
		assertEquals("CFS", flooding.flowUnits());
		assertEquals(floodingScenario.reportStep().toSeconds(), flooding.reportStepS());
		assertEquals(floodingScenario.start().plusSeconds(flooding.reportStepS()), flooding.times().getFirst());
		assertEquals(floodingScenario.end(), flooding.times().getLast());
		for (int p = 1; p < flooding.times().size(); p++) {
			assertEquals(Duration.ofSeconds(flooding.reportStepS()),
					Duration.between(flooding.times().get(p - 1), flooding.times().get(p)));
		}
	}

	@Test
	@DisplayName("the network elements, with one series per attribute and per time")
	void elements() {
		assertEquals(10, flooding.nodes().size());
		assertEquals(9, flooding.links().size());
		assertTrue(flooding.subcatchments().isEmpty());
		assertTrue(flooding.nodes().contains("82309"));
		assertEquals(flooding.times().size(), flooding.node("82309", "depth").length);
		assertEquals(flooding.times().size(), flooding.system("flooding").length);
		assertEquals(8, rain.subcatchments().size());
		assertTrue(max(rain.system("rainfall")) > 0, "Example1 has rain");
		assertTrue(max(rain.subcatchValues()[List.of(SwmmOutput.SUBCATCH).indexOf("runoff")][0]) > 0);
	}

	@Test
	@DisplayName("consistent values: head − depth = node invert, capacity in [0, 1], system flooding = sum of node flooding")
	void physics() {
		for (String node : flooding.nodes()) {
			float[] head = flooding.node(node, "head");
			float[] depth = flooding.node(node, "depth");
			for (int p = 1; p < head.length; p++) {
				assertEquals(head[0] - depth[0], head[p] - depth[p], 1e-3, node);
			}
		}
		for (String link : flooding.links()) {
			for (float c : flooding.link(link, "capacity")) {
				assertTrue(c >= 0 && c <= 1, link + " capacity " + c);
			}
		}
		float[] system = flooding.system("flooding");
		assertTrue(max(system) > 0, "Example2 ×4 floods");
		for (int p = 0; p < system.length; p++) {
			float sum = 0;
			for (String node : flooding.nodes()) {
				sum += flooding.node(node, "flooding")[p];
			}
			assertEquals(system[p], sum, 1e-3 * Math.max(1, sum));
		}
	}

	@Test
	@DisplayName("the JSON, read by a parser, has the version, the units, the times and every series equal to the .out")
	void json() throws IOException {
		for (SwmmOutput.Output o : List.of(flooding, rain)) {
			Path json = dir.resolve(o.links().size() + ".json");
			SwmmOutput.write(o, json);
			JsonNode root = JsonMapper.builder().build().readTree(json.toFile());

			assertEquals(List.of("swmmVersion", "flowUnits", "reportStepS", "times", "subcatchments", "nodes", "links",
					"system"), root.propertyNames().stream().toList());
			assertEquals("5.2.4", root.get("swmmVersion").asString());
			assertEquals(o.flowUnits(), root.get("flowUnits").asString());
			assertEquals(o.reportStepS(), root.get("reportStepS").intValue());
			assertEquals(o.times().size(), root.get("times").size());
			for (int p = 0; p < o.times().size(); p++) {
				assertEquals(o.times().get(p), LocalDateTime.parse(root.get("times").get(p).asString()));
			}
			elements(root.get("subcatchments"), o.subcatchments(), SwmmOutput.SUBCATCH, o.subcatchValues());
			elements(root.get("nodes"), o.nodes(), SwmmOutput.NODE, o.nodeValues());
			elements(root.get("links"), o.links(), SwmmOutput.LINK, o.linkValues());
			series(root.get("system"), SwmmOutput.SYSTEM, o.systemValues(), 0, "system");
		}
	}

	static void elements(JsonNode json, List<String> ids, String[] attrs, float[][][] values) {
		assertEquals(ids, json.propertyNames().stream().toList());
		for (int e = 0; e < ids.size(); e++) {
			series(json.get(ids.get(e)), attrs, values, e, ids.get(e));
		}
	}

	static void series(JsonNode json, String[] attrs, float[][][] values, int element, String id) {
		assertEquals(List.of(attrs), json.propertyNames().stream().toList(), id);
		for (int a = 0; a < attrs.length; a++) {
			JsonNode array = json.get(attrs[a]);
			float[] expected = values[a][element];
			assertEquals(expected.length, array.size(), id + "." + attrs[a]);
			for (int p = 0; p < expected.length; p++) {
				JsonNode v = array.get(p);
				if (Float.isFinite(expected[p])) {
					assertEquals(expected[p], v.floatValue(), id + "." + attrs[a] + "[" + p + "]");
				} else {
					assertTrue(v.isNull(), id + "." + attrs[a] + "[" + p + "] is not a number: null");
				}
			}
		}
	}

	@Test
	@DisplayName("names are written as valid JSON strings")
	void escaping() throws IOException {
		String name = "a\"b\\c\n";
		StringWriter w = new StringWriter();
		SwmmOutput.string(w, name);
		assertEquals("\"a\\\"b\\\\c\\u000a\"", w.toString());
		assertEquals(name, JsonMapper.builder().build().readTree(w.toString()).asString());
	}

	@Test
	@DisplayName("rejects a missing file, a file that is not .out and a missing reader")
	void errors() {
		assertThrows(IllegalArgumentException.class, () -> SwmmOutput.read(dir.resolve("nope.out"), READER));
		IllegalStateException notOut = assertThrows(IllegalStateException.class,
				() -> SwmmOutput.read(Path.of("examples/Example2.inp"), READER));
		assertTrue(notOut.getMessage().startsWith("SWMM output error"), notOut.getMessage());
		assertThrows(IllegalStateException.class,
				() -> SwmmOutput.read(dir.resolve("x4/run.out"), Path.of("/nope/libswmm-output.so")));
	}

	static float max(float[] values) {
		float max = Float.NEGATIVE_INFINITY;
		for (float v : values) {
			max = Math.max(max, v);
		}
		return max;
	}

}
