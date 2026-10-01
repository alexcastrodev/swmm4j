# Flow units

`FLOW_UNITS` sets the unit of every flow in the `.inp`, and also of the CSV's flows. The first three are US units: with them SWMM reads the whole model in feet, inches and acres. The last three are metric: with them it reads meters, millimeters and hectares.

| Name | Meaning | In L/s | In m³/s | Typical use |
|---|---|---|---|---|
| `CFS` | cubic feet per second | 28.317 | 0.028317 | US storm drainage; the EPA examples (1 to 5) |
| `GPM` | US gallons per minute | 0.06309 | 0.0000631 | small flows: pumps, building plumbing |
| `MGD` | million US gallons per day | 43.813 | 0.043813 | US treatment plants and sanitary sewers |
| `CMS` | cubic meters per second | 1000 | 1 | large sewers, tunnels, rivers; Examples 6 and 7 |
| `LPS` | liters per second | 1 | 0.001 | sewer and drainage networks in Europe and Brazil |
| `MLD` | million liters per day | 11.574 | 0.011574 | treatment and supply systems in metric units |

## Changing the unit

`--option FLOW_UNITS=...` changes how SWMM reads the `.inp`; it converts nothing:

- Within the same system (`CFS`, `GPM`, `MGD`, or `CMS`, `LPS`, `MLD`) lengths keep their unit, but every flow written in the `.inp` (dry weather flows, pump curves, inflows) is read in the new unit. It is safe only when the `.inp` has no flows of its own, as in Example7: `--option FLOW_UNITS=LPS` there takes the CSV in L/s.
- Between systems everything changes: a 1000 ft elevation becomes 1000 m. The model runs, but it is a different network.

## The CSV

The CSV's `flow` column is always in the `.inp`'s `FLOW_UNITS`. The `flow_lps` column name is only an alias for `flow`: its values are not converted from L/s.
