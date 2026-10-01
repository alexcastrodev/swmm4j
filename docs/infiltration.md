# Infiltration

`INFILTRATION` picks how SWMM computes how much rain soaks into the subcatchments' soil; what does not infiltrate runs off into the network. It only matters for `.inp` files with `[SUBCATCHMENTS]` (Examples 1, 2, 4 and 5).

| Name | Meaning |
|---|---|
| `HORTON` | Infiltration capacity starts high on dry soil and decays exponentially with time while it rains, down to a minimum; it recovers as the soil dries. SWMM's default. |
| `MODIFIED_HORTON` | Like Horton, but the decay follows the volume already infiltrated instead of time, so light rain does not use up the soil's capacity. |
| `GREEN_AMPT` | Physical model of a wetting front moving down through the soil, from its suction head, saturated conductivity and initial moisture deficit. |
| `MODIFIED_GREEN_AMPT` | Like Green-Ampt, but it does not use up the surface layer's moisture deficit too early during initial periods of light rain. |
| `CURVE_NUMBER` | SCS/NRCS method: one number (CN, 30 to 100), tabulated by soil type and land use, sets how much infiltrates; the higher the CN, the less infiltrates. |

## Columns of `[INFILTRATION]`

Each method reads different columns, one line per subcatchment:

| Method | Columns |
|---|---|
| `HORTON`, `MODIFIED_HORTON` | MaxRate (in/h or mm/h), MinRate, Decay (1/h), DryTime (days), MaxInfil (in or mm, 0 for no limit) |
| `GREEN_AMPT`, `MODIFIED_GREEN_AMPT` | Suction (in or mm), Conductivity (in/h or mm/h), InitialDeficit (fraction, 0 to 1) |
| `CURVE_NUMBER` | CurveNumber, Conductivity (no longer used), DryTime (days) |

`--option INFILTRATION=...` changes only the method's name, not the columns. Switching between `HORTON` and `MODIFIED_HORTON`, or between `GREEN_AMPT` and `MODIFIED_GREEN_AMPT`, is safe; switching between families needs the `[INFILTRATION]` section rewritten (Example1 with `INFILTRATION=GREEN_AMPT` fails with SWMM error 235).
