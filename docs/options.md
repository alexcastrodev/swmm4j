# Options

`--option KEY=VALUE` writes a line in the `.inp`'s `[OPTIONS]`, replacing the one already there. It can be repeated, and the key is case-insensitive:

```sh
docker run --rm -v "$PWD:/work" swmm-cli --inp examples/Example1.inp \
  --option IGNORE_RAINFALL=YES --option FLOW_ROUTING=KINWAVE --option threads=4
```

These are the options SWMM 5.2.4 reads (`project_readOption` in its `project.c`). The default is what SWMM uses when the `.inp` does not set the option; the `.inp` itself may set another value.

## Set by their own flags

`--option` refuses these. Without their flag the `.inp`'s value is kept:

| Option | Flag |
|---|---|
| `START_DATE`, `START_TIME`, `REPORT_START_DATE`, `REPORT_START_TIME` | `--start` (the report starts with the simulation) |
| `END_DATE`, `END_TIME` | `--end` |
| `REPORT_STEP` | `--report-step`, in minutes or `HH:MM:SS` |
| `ROUTING_STEP` | `--routing-step`, in seconds or `HH:MM:SS` |

## Processes

| Option | Values | Default | |
|---|---|---|---|
| `IGNORE_RAINFALL` | `YES`, `NO` | `NO` | no rain or runoff, only the inflows |
| `IGNORE_SNOWMELT` | `YES`, `NO` | `NO` | |
| `IGNORE_GROUNDWATER` | `YES`, `NO` | `NO` | |
| `IGNORE_RDII` | `YES`, `NO` | `NO` | rainfall-dependent infiltration/inflow |
| `IGNORE_ROUTING` | `YES`, `NO` | `NO` | runoff only, nothing flows in the network |
| `IGNORE_QUALITY` | `YES`, `NO` | `NO` | no pollutants |

## Routing

| Option | Values | Default | |
|---|---|---|---|
| `FLOW_ROUTING` | `STEADY`, `KINWAVE`, `DYNWAVE`, `NONE` | `DYNWAVE` | `NONE` is the same as `IGNORE_ROUTING=YES` |
| `ALLOW_PONDING` | `YES`, `NO` | `NO` | flooded water ponds over the node and returns, instead of leaving the model |
| `SKIP_STEADY_STATE` | `YES`, `NO` | `NO` | skips routing while inflows and outflows stay steady |
| `SYS_FLOW_TOL` | percent | `5` | steady state tolerance on the system's inflow and outflow |
| `LAT_FLOW_TOL` | percent | `5` | steady state tolerance on each node's lateral inflow |
| `FORCE_MAIN_EQUATION` | `H-W`, `D-W` | `H-W` | Hazen-Williams or Darcy-Weisbach for force mains |
| `RULE_STEP` | `HH:MM:SS` | `00:00:00` | how often the control rules run; 0 is every routing step |

## Dynamic wave

Only used with `FLOW_ROUTING=DYNWAVE`.

| Option | Values | Default | |
|---|---|---|---|
| `VARIABLE_STEP` | `0` to `2` | `0.75` | safety factor of the variable time step; `0` keeps the routing step fixed |
| `MINIMUM_STEP` | seconds | `0.5` | smallest variable time step |
| `LENGTHENING_STEP` | seconds | `0` | lengthens short conduits so they are stable at this step; `0` does not |
| `INERTIAL_DAMPING` | `NONE`, `PARTIAL`, `FULL` | `PARTIAL` | |
| `NORMAL_FLOW_LIMITED` | `SLOPE`, `FROUDE`, `BOTH`, `NONE` | `BOTH` | |
| `SURCHARGE_METHOD` | `EXTRAN`, `SLOT` | `EXTRAN` | |
| `MIN_SURFAREA` | ft² or m² | `12.566` ft² | smallest surface area of a node |
| `MIN_SLOPE` | percent, below 100 | `0` | smallest conduit slope; `0` uses none |
| `MAX_TRIALS` | count | `8` | trials per time step |
| `HEAD_TOLERANCE` | ft or m | `0.005` ft | convergence tolerance on the nodes' heads |
| `SLOPE_WEIGHTING` | `YES`, `NO` | `YES` | |
| `THREADS` | count | `1` | `0` uses every core; SWMM goes back to 1 when the network has fewer than 4 links per thread |

## Runoff

| Option | Values | Default | |
|---|---|---|---|
| `WET_STEP` | `HH:MM:SS` | `00:05:00` | runoff step while it rains or water is ponded |
| `DRY_STEP` | `HH:MM:SS` | `01:00:00` | runoff step in dry periods |
| `DRY_DAYS` | days | `0` | dry days before the simulation (pollutant buildup) |
| `SWEEP_START` | `MM/DD` | `01/01` | street sweeping season |
| `SWEEP_END` | `MM/DD` | `12/31` | |

## Change with care

These change how SWMM reads the rest of the `.inp`, and the CLI converts nothing:

| Option | Values | Default | |
|---|---|---|---|
| `FLOW_UNITS` | `CFS`, `GPM`, `MGD`, `CMS`, `LPS`, `MLD` | `CFS` | also switches lengths to feet (`CFS`, `GPM`, `MGD`) or meters; see [flow_units.md](flow_units.md) |
| `INFILTRATION` | `HORTON`, `MODIFIED_HORTON`, `GREEN_AMPT`, `MODIFIED_GREEN_AMPT`, `CURVE_NUMBER` | `HORTON` | each method reads different `[INFILTRATION]` parameters; see [infiltration.md](infiltration.md) |
| `LINK_OFFSETS` | `DEPTH`, `ELEVATION` | `DEPTH` | how the links' offsets are read |
| `TEMPDIR` | folder | the system's | inside the container |
| `COMPATIBILITY` | `3`, `4`, `5` | `4` | read but not used by SWMM 5.2 |
