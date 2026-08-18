#!/usr/bin/env node
// Dependency-free Node counterpart to build_boundaries.py for environments without Python.
import fs from "node:fs";
import path from "node:path";

const args = Object.fromEntries(process.argv.slice(2).reduce((pairs, value, index, all) => {
  if (value.startsWith("--")) pairs.push([value.slice(2), all[index + 1]]);
  return pairs;
}, []));
for (const required of ["fir-input", "tracon-dir", "output-dir"]) {
  if (!args[required]) throw new Error(`Missing --${required}`);
}

const allowed = new Set(["Polygon", "MultiPolygon", "LineString", "MultiLineString"]);
const firKeys = new Set(["id", "oceanic", "label_lon", "label_lat", "region", "division"]);
const traconKeys = new Set(["id", "prefix", "suffix", "name", "label_lon", "label_lat"]);
const read = file => JSON.parse(fs.readFileSync(file, "utf8").replace(/^\uFEFF/, ""));

function features(document, file) {
  if (document.type === "Feature") return [document];
  if (document.type === "FeatureCollection" && Array.isArray(document.features)) return document.features;
  throw new Error(`${file}: expected a GeoJSON Feature or FeatureCollection`);
}

function validateCoordinates(value, file) {
  if (!Array.isArray(value) || !value.length) throw new Error(`${file}: empty geometry coordinates`);
  if (typeof value[0] === "number") {
    if (value.length < 2 || value[0] < -180 || value[0] > 180 || value[1] < -90 || value[1] > 90) {
      throw new Error(`${file}: invalid longitude/latitude coordinate`);
    }
    return;
  }
  value.forEach(child => validateCoordinates(child, file));
}

function normalize(feature, keys, file) {
  const geometry = feature.geometry;
  if (!geometry || !allowed.has(geometry.type)) throw new Error(`${file}: unsupported geometry`);
  validateCoordinates(geometry.coordinates, file);
  const properties = Object.fromEntries(Object.entries(feature.properties ?? {}).filter(([key]) => keys.has(key)));
  if (!properties.id) throw new Error(`${file}: feature is missing properties.id`);
  return { type: "Feature", properties, geometry };
}

function walkJson(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const target = path.join(directory, entry.name);
    return entry.isDirectory() ? walkJson(target) : entry.name.endsWith(".json") ? [target] : [];
  }).sort();
}

function atomicWrite(file, document) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const temporary = `${file}.tmp`;
  fs.writeFileSync(temporary, `${JSON.stringify(document)}\n`, "utf8");
  fs.renameSync(temporary, file);
}

const firFile = path.resolve(args["fir-input"]);
const traconDir = path.resolve(args["tracon-dir"]);
const fir = features(read(firFile), firFile).map(feature => normalize(feature, firKeys, firFile));
const tracon = walkJson(traconDir).flatMap(file => features(read(file), file).map(feature => normalize(feature, traconKeys, file)));
if (!tracon.length) throw new Error(`${traconDir}: no TRACON features found`);

atomicWrite(path.join(args["output-dir"], "fir_boundaries.geojson"), { type: "FeatureCollection", name: "VATSpy FIR boundaries", features: fir });
atomicWrite(path.join(args["output-dir"], "tracon_boundaries.geojson"), { type: "FeatureCollection", name: "SimAware TRACON boundaries", features: tracon });
console.log(`FIR features: ${fir.length}`);
console.log(`TRACON features: ${tracon.length}`);
