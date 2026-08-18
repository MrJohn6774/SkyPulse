# Boundary asset generation

SkyPulse ships normalized offline fallbacks derived from the VATSpy Data Project and
the SimAware TRACON Project. Both datasets are distributed under CC BY-SA 4.0.

Regenerate after checking out the upstream repositories:

```powershell
python tools/build_boundaries.py `
  --fir-input .upstream/vatspy_data/Boundaries.geojson `
  --tracon-dir .upstream/simaware_tracon/Boundaries `
  --output-dir app/src/main/assets
```

On a machine with Node.js but no Python, use the equivalent dependency-free runner:

```powershell
node tools/build_boundaries.mjs `
  --fir-input .upstream/vatspy_data/Boundaries.geojson `
  --tracon-dir .upstream/simaware_tracon/Boundaries `
  --output-dir app/src/main/assets
```

The tool recursively reads TRACON features, validates supported geometry and coordinate
ranges, strips unused properties, and atomically replaces the two output files.
