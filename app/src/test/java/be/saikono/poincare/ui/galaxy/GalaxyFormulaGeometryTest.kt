package be.saikono.poincare.ui.galaxy

import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GalaxyFormulaGeometryTest {
    private val tolerance = 0.001

    @Test
    fun referenceCoordinatesMatchTheCanonicalFormula() {
        val references = mapOf(
            "S03-O-01" to GalaxyFormulaPoint(479.289321881, 479.289321881),
            "S03-O-03" to GalaxyFormulaPoint(620.710678119, 620.710678119),
            "S06-O-01" to GalaxyFormulaPoint(475.000000000, 420.096189432),
            "S09-O-01" to GalaxyFormulaPoint(473.463313527, 365.224093498),
            "S09-M-03" to GalaxyFormulaPoint(620.710678119, 620.710678119),
            "S24-O-01" to GalaxyFormulaPoint(472.117673115, 208.775230736),
            "S24-M-08" to GalaxyFormulaPoint(347.745751406, 696.946313073),
            "S24-I-05" to GalaxyFormulaPoint(475.000000000, 679.903810568),
            "S36-C-04" to GalaxyFormulaPoint(479.289321881, 620.710678119),
            "S60-I-13" to GalaxyFormulaPoint(234.660896234, 398.140691309),
            "S60-C-10" to GalaxyFormulaPoint(347.745751406, 403.053686927)
        )
        val actual = GALAXY_SHAPES.values
            .flatMap(::buildNodes)
            .associateBy(GalaxyNodeGeometry::pointId)

        references.forEach { (pointId, expected) ->
            val point = actual.getValue(pointId)
            assertEquals("$pointId x", expected.x, point.x, tolerance)
            assertEquals("$pointId y", expected.y, point.y, tolerance)
        }
    }

    @Test
    fun everyShapeGeneratesItsExactRouteAndInvariants() {
        assertEquals((3..60 step 3).toList(), GALAXY_SHAPES.keys.sorted())
        GALAXY_SHAPES.forEach { (shapeNumber, shape) ->
            val geometry = GalaxyGeometryGenerator.generate(shapeNumber)
            assertEquals(shapeNumber, shape.layers.sumOf(GalaxyLayerSpec::visibleCorners))
            assertTrue(shape.layers.all { it.visibleCorners <= it.sides })
            assertTrue(shape.layers.all { it.size == it.sides * 50.0 })
            assertEquals(shapeNumber, geometry.nodes.size)
            assertEquals(shapeNumber - 1, geometry.connections.size)
            assertEquals(1, geometry.nodes.count(GalaxyNodeGeometry::isStart))
            assertEquals(1, geometry.nodes.count(GalaxyNodeGeometry::isExit))
            assertEquals(shapeNumber, geometry.nodes.map(GalaxyNodeGeometry::pointId).distinct().size)
            assertTrue(geometry.nodes.all { it.x.isFinite() && it.y.isFinite() })
            geometry.nodes.forEach { node ->
                val layer = shape.layers.single { it.layer == node.layer }
                assertEquals(
                    layer.size / 2.0,
                    hypot(node.x - GALAXY_FORMULA_CENTER, node.y - GALAXY_FORMULA_CENTER),
                    tolerance
                )
            }
            geometry.nodes.dropLast(1).forEachIndexed { index, node ->
                assertEquals(geometry.nodes[index + 1].pointId, node.connectToId)
            }
            assertEquals(GALAXY_EXIT_SENTINEL, geometry.nodes.last().connectToId)
            assertEquals(shape.number, geometry.shape.number)
            assertEquals(GALAXY_FORMULA_CANVAS_SIZE, geometry.canonicalCanvasSize, tolerance)
        }

        assertEquals(630, GALAXY_SHAPES.values.sumOf { buildNodes(it).size })
        assertEquals(610, GALAXY_SHAPES.keys.sumOf { GalaxyGeometryGenerator.generate(it).connections.size })
        assertEquals(
            20,
            GALAXY_SHAPES.values.sumOf { shape ->
                buildNodes(shape).count { it.connectToId == GALAXY_EXIT_SENTINEL }
            }
        )
    }

    @Test
    fun routeIdsAndDirectionsAreDerivedFromLayerConfiguration() {
        val route = generateRoute(GALAXY_SHAPES.getValue(24)).map(::pointId)

        assertEquals("S24-O-01", route.first())
        assertEquals("S24-O-11", route[10])
        assertEquals("S24-M-08", route[11])
        assertEquals("S24-M-01", route[18])
        assertEquals("S24-I-01", route[19])
        assertEquals("S24-I-05", route.last())

        val shape60Route = generateRoute(GALAXY_SHAPES.getValue(60)).map(::pointId)
        assertEquals("S60-C-10", shape60Route[50])
        assertEquals("S60-C-01", shape60Route.last())
    }

    @Test
    fun connectionsUseMinorArcsWithinLayersAndBezierAcrossLayers() {
        val geometry = GalaxyGeometryGenerator.generate(9)
        val first = geometry.connections.first() as GalaxyFormulaConnection.Arc
        assertEquals(200.0, first.radius, tolerance)
        assertEquals(45.0, first.sweepAngleDegrees, tolerance)
        assertEquals("S09-O-01", first.fromPointId)
        assertEquals("S09-O-02", first.toPointId)

        val transition = geometry.connections[5]
        assertTrue(transition is GalaxyFormulaConnection.CubicBezier)
        transition as GalaxyFormulaConnection.CubicBezier
        assertEquals("S09-O-06", transition.fromPointId)
        assertEquals("S09-M-03", transition.toPointId)
        assertNotEquals(transition.start, transition.control1)
        assertNotEquals(transition.end, transition.control2)
        assertFalse(geometry.connections.any { it.toPointId == GALAXY_EXIT_SENTINEL })
    }

    @Test
    fun rotationAndMirroringPreserveIdentityAndTransformTheCompleteGeometry() {
        val canonical = GalaxyGeometryGenerator.generate(36)
        val transformed = GalaxyGeometryGenerator.generate(
            shapeNumber = 36,
            rotationDegrees = 90.0,
            mirrorHorizontal = true,
            mirrorVertical = false
        )

        assertEquals(canonical.nodes.map(GalaxyNodeGeometry::pointId), transformed.nodes.map(GalaxyNodeGeometry::pointId))
        assertEquals(canonical.nodes.map(GalaxyNodeGeometry::sequence), transformed.nodes.map(GalaxyNodeGeometry::sequence))
        assertEquals(canonical.nodes.map(GalaxyNodeGeometry::connectToId), transformed.nodes.map(GalaxyNodeGeometry::connectToId))
        assertEquals(canonical.nodes.map(GalaxyNodeGeometry::layer), transformed.nodes.map(GalaxyNodeGeometry::layer))
        assertEquals(canonical.nodes.map(GalaxyNodeGeometry::corner), transformed.nodes.map(GalaxyNodeGeometry::corner))
        assertEquals(GalaxyFormulaTransform(90.0, true, false), transformed.transform)
        assertFalse(canonical.nodes.zip(transformed.nodes).all { (first, second) ->
            abs(first.x - second.x) < tolerance && abs(first.y - second.y) < tolerance
        })

        val canonicalArc = canonical.connections.first() as GalaxyFormulaConnection.Arc
        val transformedArc = transformed.connections.first() as GalaxyFormulaConnection.Arc
        assertEquals(-canonicalArc.sweepAngleDegrees, transformedArc.sweepAngleDegrees, tolerance)
    }

    @Test
    fun viewportUsesOneUniformScaleAndCentresTheCanonicalMap() {
        val landscape = galaxyViewportTransform(2200.0, 1100.0)
        assertEquals(1.0, landscape.scale, tolerance)
        assertEquals(550.0, landscape.offsetX, tolerance)
        assertEquals(0.0, landscape.offsetY, tolerance)
        assertEquals(
            GalaxyFormulaPoint(1100.0, 550.0),
            landscape.apply(GalaxyFormulaPoint(550.0, 550.0))
        )

        val portrait = galaxyViewportTransform(550.0, 1650.0)
        assertEquals(0.5, portrait.scale, tolerance)
        assertEquals(0.0, portrait.offsetX, tolerance)
        assertEquals(550.0, portrait.offsetY, tolerance)
        assertEquals(
            GalaxyFormulaPoint(275.0, 825.0),
            portrait.apply(GalaxyFormulaPoint(550.0, 550.0))
        )
    }

    @Test
    fun repeatedGenerationIsDeterministicAndUnsupportedCountsFail() {
        val first = GalaxyGeometryGenerator.generate(60, 17.5, mirrorHorizontal = true)
        val second = GalaxyGeometryGenerator.generate(60, 17.5, mirrorHorizontal = true)
        assertEquals(first, second)
        assertThrows(IllegalArgumentException::class.java) {
            GalaxyGeometryGenerator.generate(4)
        }
    }
}
