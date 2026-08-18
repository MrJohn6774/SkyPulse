#!/usr/bin/env python3
"""Normalize VATSpy FIR and SimAware TRACON data for SkyPulse assets.

Both inputs are CC BY-SA 4.0. The generated files retain only rendering and
attribution-relevant properties and remain CC BY-SA 4.0 derived datasets.
"""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
from typing import Any, Iterable


ALLOWED_GEOMETRIES = {"Polygon", "MultiPolygon", "LineString", "MultiLineString"}
FIR_PROPERTIES = {"id", "oceanic", "label_lon", "label_lat", "region", "division"}
TRACON_PROPERTIES = {"id", "prefix", "suffix", "name", "label_lon", "label_lat"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fir-input", type=Path, required=True)
    parser.add_argument("--tracon-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    return parser.parse_args()


def load_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8-sig") as source:
        return json.load(source)


def iter_features(document: dict[str, Any], source: Path) -> Iterable[dict[str, Any]]:
    kind = document.get("type")
    if kind == "Feature":
        yield document
    elif kind == "FeatureCollection":
        features = document.get("features")
        if not isinstance(features, list):
            raise ValueError(f"{source}: FeatureCollection.features is not a list")
        yield from features
    else:
        raise ValueError(f"{source}: expected Feature or FeatureCollection, got {kind!r}")


def validate_coordinates(value: Any, source: Path) -> None:
    if not isinstance(value, list) or not value:
        raise ValueError(f"{source}: geometry has empty/non-list coordinates")
    if isinstance(value[0], (int, float)):
        if len(value) < 2 or not -180 <= value[0] <= 180 or not -90 <= value[1] <= 90:
            raise ValueError(f"{source}: invalid longitude/latitude coordinate {value!r}")
        return
    for child in value:
        validate_coordinates(child, source)


def normalize_feature(feature: dict[str, Any], keep: set[str], source: Path) -> dict[str, Any]:
    geometry = feature.get("geometry")
    if not isinstance(geometry, dict) or geometry.get("type") not in ALLOWED_GEOMETRIES:
        raise ValueError(f"{source}: unsupported geometry {geometry!r}")
    validate_coordinates(geometry.get("coordinates"), source)
    properties = feature.get("properties") or {}
    normalized_properties = {key: properties[key] for key in keep if key in properties}
    if not normalized_properties.get("id"):
        raise ValueError(f"{source}: feature is missing properties.id")
    return {
        "type": "Feature",
        "properties": normalized_properties,
        "geometry": geometry,
    }


def atomic_write(path: Path, document: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    with temporary.open("w", encoding="utf-8", newline="\n") as target:
        json.dump(document, target, ensure_ascii=False, separators=(",", ":"))
        target.write("\n")
        target.flush()
        os.fsync(target.fileno())
    temporary.replace(path)


def build_fir(path: Path) -> dict[str, Any]:
    document = load_json(path)
    features = [normalize_feature(item, FIR_PROPERTIES, path) for item in iter_features(document, path)]
    return {"type": "FeatureCollection", "name": "VATSpy FIR boundaries", "features": features}


def build_tracon(directory: Path) -> dict[str, Any]:
    features: list[dict[str, Any]] = []
    for path in sorted(directory.rglob("*.json")):
        document = load_json(path)
        features.extend(normalize_feature(item, TRACON_PROPERTIES, path) for item in iter_features(document, path))
    if not features:
        raise ValueError(f"{directory}: no TRACON JSON features found")
    return {"type": "FeatureCollection", "name": "SimAware TRACON boundaries", "features": features}


def main() -> None:
    args = parse_args()
    fir = build_fir(args.fir_input)
    tracon = build_tracon(args.tracon_dir)
    atomic_write(args.output_dir / "fir_boundaries.geojson", fir)
    atomic_write(args.output_dir / "tracon_boundaries.geojson", tracon)
    print(f"FIR features: {len(fir['features'])}")
    print(f"TRACON features: {len(tracon['features'])}")


if __name__ == "__main__":
    main()
