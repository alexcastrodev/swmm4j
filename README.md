# swmm4j

Runs an EPA SWMM `.inp` from Java (JDK 25, Foreign Function & Memory API) straight in `libswmm5`, printing the network's state at every report step.

## Run

```sh
docker build -t swmm-cli .
docker run --rm -v "$PWD:/work" swmm-cli --inp examples/Example2.inp
docker run --rm -v "$PWD:/work" swmm-cli --inp examples/Example2.inp --flow-scale 2 --node 82309
docker run --rm -v "$PWD:/work" swmm-cli --inp examples/Example1.inp --csv examples/Example1.csv
docker run --rm -v "$PWD:/work" swmm-cli --inp examples/Example1.inp --rain-scale 2 --option ALLOW_PONDING=YES
docker run --rm -v "$PWD:/work" swmm-cli --inp examples/Example3.inp --csv examples/Example3.csv \
  --start 2001-01-01T00:00 --end 2001-02-01T00:00 --report-step 60 --node KRO1006
docker run --rm -v "$PWD:/work" swmm-cli --inp examples/Example6.inp --csv examples/Example6.csv \
  --start 2001-01-01T00:00 --end 2001-01-02T00:00 --report-step 60 --node KRO1006,SU1
docker run --rm -v "$PWD:/work" swmm-cli --inp examples/Example7.inp --csv examples/Example7.csv \
  --flow-scale 1.3 --node LIBERDADE,ALMIRANTE_REIS
```

The current folder is mounted at `/work`: `--inp` and `--csv` are read from it and the results go to `out/` (`run.inp`, `run.rpt`, `run.out` and `run.json`).

## The output as JSON

Every run also turns `run.out` into `run.json` with EPA's own reader (`libswmm-output`, the `SMO_*` API, built with the engine): there is no flag to turn it on. `--dir` chooses where it goes (default `out/`), so each example can keep its own:

```sh
docker run --rm -v "$PWD:/work" swmm-cli --inp examples/Example2.inp --dir examples/json/Example2    # → examples/json/Example2/run.json
```

The results of the examples above are in `examples/json/` (formatted). `--read` does only the conversion, for a `.out` that already exists:

```sh
docker run --rm -v "$PWD:/work" swmm-cli --read out/run.out --dir out/read    # → out/read/run.json
```

```json
{
  "swmmVersion": "5.2.4",
  "flowUnits": "CFS",
  "reportStepS": 900,
  "times": ["2002-01-01T00:15", "..."],
  "subcatchments": { "1": { "rainfall": [], "snowDepth": [], "evapLoss": [], "infilLoss": [], "runoff": [], "gwOutflow": [], "gwElevation": [], "soilMoisture": [] } },
  "nodes": { "82309": { "depth": [], "head": [], "volume": [], "lateralInflow": [], "inflow": [], "flooding": [] } },
  "links": { "C1": { "flow": [], "depth": [], "velocity": [], "volume": [], "capacity": [] } },
  "system": { "airTemperature": [], "rainfall": [], "runoff": [], "flooding": [], "outfallFlow": [], "...": [] }
}
```

`swmmVersion` is the SWMM that wrote the `.out`. Each series has one value per report time (`times`, the first one a report step after the start), in the `.inp`'s units (flow in `flowUnits`, lengths in ft or m); `capacity` is the fraction of the conduit filled. Pollutants are left out.

## Build and tests

A Maven project (`pom.xml`, Java 25): `jextract` generates the bindings of `swmm5.h` and `swmm_output.h` into `target/generated-sources/jextract`, then Maven compiles and makes `target/swmm-cli.jar` (picocli inside). The `Dockerfile` builds the image without the tests; `Dockerfile.test` is the image that runs them, with the engine and the reader built for the platform:

```sh
docker build -f Dockerfile.test -t swmm-cli-test . && docker run --rm swmm-cli-test    # exit 0 when the spec passes
```

The spec (`src/test/java/swmm4j/SwmmOutputTest.java`, JUnit) runs `Example2` (flows ×4: it floods) and `Example1` (subcatchments and rain) in the real engine and checks the reader: the SWMM version, the units and report times, the elements, that head − depth stays each node's invert, that capacity stays in [0, 1], that the system's flooding is the sum of the nodes', and the errors (a missing file, a file that is not a `.out`, a missing reader). The JSON written is read back with a parser (Jackson) and compared with the `.out`: the keys in order, the version, the units, every time and every value of every series.

# References

Most examples (1 to 5) was based on https://github.com/USEPA/swmm-nrtestsuite

Example 6 is Example 3 converted to meters and m³/s.

Example 7 takes the tunnel's alignment from https://services.arcgis.com/1dSrzEWVQn5kHHyK/arcgis/rest/services/PGDL_tracado/FeatureServer and its diameter, length and capacity from:

- https://tpf.pt/obra.php?n=&p=Tuneis-Drenagem-Lisboa
- https://www.jf-santamariamaior.pt/planodrenagem/
- https://planodrenagem.lisboa.pt/fileadmin/pgdl/_ficheiros/PlanoGeralDrenagem_2016_2030.pdf