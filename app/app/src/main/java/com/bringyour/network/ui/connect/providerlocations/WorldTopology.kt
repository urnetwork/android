package com.bringyour.network.ui.connect.providerlocations

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One country from the world topology: its ISO-3166-1 numeric id (zero
 * padded, e.g. "840" is the USA) and its outline rings in lon/lat degrees.
 * Each ring is a packed FloatArray of [lon0, lat0, lon1, lat1, ...] and is
 * closed (first point equals last point). MultiPolygon countries contribute
 * all of their rings, flattened.
 */
internal class CountryShape(
    val isoNumeric: String,
    val rings: List<FloatArray>,
)

/**
 * The world map decoded from quantized TopoJSON (world-110m.json in assets).
 * Only `objects.countries` is decoded; `land` and `bbox` are ignored.
 *
 * TopoJSON stores shared borders once as delta-encoded quantized integer
 * arcs; polygons reference arcs by index, with a negative index i meaning
 * arc ~i traversed in reverse. See https://github.com/topojson/topojson.
 */
internal class WorldTopology(
    val countries: List<CountryShape>,
) {
    companion object {
        fun decode(json: String): WorldTopology {
            val root = Json.parseToJsonElement(json).jsonObject

            val transform = root.getValue("transform").jsonObject
            val scale = transform.getValue("scale").jsonArray
            val translate = transform.getValue("translate").jsonArray
            val scaleX = scale[0].jsonPrimitive.double
            val scaleY = scale[1].jsonPrimitive.double
            val translateX = translate[0].jsonPrimitive.double
            val translateY = translate[1].jsonPrimitive.double

            // Decode every arc once. Each arc is a list of [x, y] integer
            // points where the first point is absolute (quantized) and every
            // later point is a delta; the running sums dequantize to degrees
            // as lon = x * scale[0] + translate[0], lat = y * scale[1] +
            // translate[1]. Packed as [lon0, lat0, lon1, lat1, ...].
            val arcsJson = root.getValue("arcs").jsonArray
            val arcs = Array(arcsJson.size) { i ->
                val arc = arcsJson[i].jsonArray
                val points = FloatArray(arc.size * 2)
                var x = 0
                var y = 0
                for (j in arc.indices) {
                    val point = arc[j].jsonArray
                    x += point[0].jsonPrimitive.int
                    y += point[1].jsonPrimitive.int
                    points[2 * j] = (x * scaleX + translateX).toFloat()
                    points[2 * j + 1] = (y * scaleY + translateY).toFloat()
                }
                points
            }

            val geometries = root.getValue("objects").jsonObject
                .getValue("countries").jsonObject
                .getValue("geometries").jsonArray
            val countries = ArrayList<CountryShape>(geometries.size)
            for (element in geometries) {
                val geometry = element.jsonObject
                val id = geometry["id"]?.jsonPrimitive?.content ?: ""
                val arcIndexes = geometry.getValue("arcs").jsonArray
                val rings = when (val type =
                    geometry.getValue("type").jsonPrimitive.content) {
                    // a Polygon is a list of rings, each a list of arc indexes
                    "Polygon" -> arcIndexes.map { stitchRing(it.jsonArray, arcs) }
                    // a MultiPolygon is a list of polygons
                    "MultiPolygon" -> arcIndexes.flatMap { polygon ->
                        polygon.jsonArray.map { stitchRing(it.jsonArray, arcs) }
                    }
                    else -> throw IllegalArgumentException(
                        "unsupported geometry type $type"
                    )
                }
                countries.add(CountryShape(id, rings))
            }
            return WorldTopology(countries)
        }

        /**
         * Concatenates the referenced arcs into one closed ring. A negative
         * index i references arc ~i reversed. After orientation, each arc's
         * first point equals the previous arc's last point, so the duplicate
         * is dropped when stitching.
         */
        private fun stitchRing(
            arcIndexes: JsonArray,
            arcs: Array<FloatArray>,
        ): FloatArray {
            var pointCount = 0
            for (element in arcIndexes) {
                val index = element.jsonPrimitive.int
                pointCount += arcs[if (index >= 0) index else index.inv()].size / 2
            }
            pointCount -= arcIndexes.size - 1
            val ring = FloatArray(pointCount * 2)
            var write = 0
            for (k in arcIndexes.indices) {
                val index = arcIndexes[k].jsonPrimitive.int
                val skipSharedEndpoint = k > 0
                if (index >= 0) {
                    val arc = arcs[index]
                    val from = if (skipSharedEndpoint) 1 else 0
                    for (p in from until arc.size / 2) {
                        ring[write++] = arc[2 * p]
                        ring[write++] = arc[2 * p + 1]
                    }
                } else {
                    val arc = arcs[index.inv()]
                    val last = arc.size / 2 - 1
                    val from = if (skipSharedEndpoint) last - 1 else last
                    for (p in from downTo 0) {
                        ring[write++] = arc[2 * p]
                        ring[write++] = arc[2 * p + 1]
                    }
                }
            }
            return ring
        }
    }
}
