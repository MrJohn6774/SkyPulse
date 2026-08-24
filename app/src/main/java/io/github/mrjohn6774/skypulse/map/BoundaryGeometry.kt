package io.github.mrjohn6774.skypulse.map

import io.github.mrjohn6774.skypulse.model.GeoPoint
import org.json.JSONArray
import org.json.JSONObject

/** Converts the supported GeoJSON boundary geometries into drawable latitude/longitude rings. */
internal object BoundaryGeometry {
    fun rings(collection: JSONObject): List<List<GeoPoint>> = buildList {
        val features = collection.getJSONArray("features")
        for (featureIndex in 0 until features.length()) {
            val geometry = features.getJSONObject(featureIndex).getJSONObject("geometry")
            val coordinates = geometry.getJSONArray("coordinates")
            when (geometry.getString("type")) {
                "LineString" -> addRing(coordinates)
                "MultiLineString", "Polygon" -> {
                    for (ringIndex in 0 until coordinates.length()) addRing(coordinates.getJSONArray(ringIndex))
                }
                "MultiPolygon" -> {
                    for (polygonIndex in 0 until coordinates.length()) {
                        val polygon = coordinates.getJSONArray(polygonIndex)
                        for (ringIndex in 0 until polygon.length()) addRing(polygon.getJSONArray(ringIndex))
                    }
                }
            }
        }
    }

    private fun MutableList<List<GeoPoint>>.addRing(coordinates: JSONArray) {
        val ring = buildList {
            for (index in 0 until coordinates.length()) {
                val point = coordinates.getJSONArray(index)
                val longitude = point.getDouble(0)
                val latitude = point.getDouble(1)
                if (latitude in -90.0..90.0 && longitude in -180.0..180.0) {
                    add(GeoPoint(latitude, longitude))
                }
            }
        }
        if (ring.size >= 2) add(ring)
    }
}
