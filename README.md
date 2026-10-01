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

The current folder is mounted at `/work`: `--inp` and `--csv` are read from it and the results go to `out/` (`run.inp`, `run.rpt`, `run.out`).

```
--inp FILE            network (required)
--csv FILE            inflows (series,timestamp,flow) replacing the [INFLOWS] series
--start, --end TIME   period, e.g. 2026-03-23T06:00 (default: the .inp's)
--report-step MIN     5, 15 or 60 (default 15)
--routing-step S      1 to 60 (default 30)
--flow-scale X        multiplies every inflow and dry weather flow (default 1)
--rain-scale X        multiplies the rain gages' time series (default 1)
--option KEY=VALUE    sets an [OPTIONS] line, e.g. IGNORE_RAINFALL=YES (repeatable)
--node A,B            nodes to follow (depth and inflow)
--lib FILE            libswmm5 (default $SWMM_LIB)
--dir DIR             output folder (default out)
```

`--csv` needs an `[INFLOWS]` section in the `.inp`, `--flow-scale` an `[INFLOWS]` or `[DWF]` one and `--rain-scale` a `[RAINGAGES]` one. The CSV's flows are in the `.inp`'s `FLOW_UNITS` (CFS in the EPA examples; `Example6` is `Example3` converted to meters and m³/s; `Example7` is an illustrative model of Lisbon's Monsanto–Santa Apolónia drainage tunnel, with its alignment from the city's `PGDL_tracado` map service and approximate elevations and inflows).

`--option` takes any SWMM option, e.g. `FLOW_ROUTING=KINWAVE`, `ALLOW_PONDING=YES`, `THREADS=4` or `IGNORE_ROUTING=YES`; the period and steps stay with their own flags. See [docs/options.md](docs/options.md) for all of them.

# References

Most examples (1 to 5) was based on https://github.com/USEPA/swmm-nrtestsuite

Example 6 is Example 3 converted to meters and m³/s.

Example 7 takes the tunnel's alignment from https://services.arcgis.com/1dSrzEWVQn5kHHyK/arcgis/rest/services/PGDL_tracado/FeatureServer and its diameter, length and capacity from:

- https://tpf.pt/obra.php?n=&p=Tuneis-Drenagem-Lisboa
- https://www.jf-santamariamaior.pt/planodrenagem/
- https://planodrenagem.lisboa.pt/fileadmin/pgdl/_ficheiros/PlanoGeralDrenagem_2016_2030.pdf