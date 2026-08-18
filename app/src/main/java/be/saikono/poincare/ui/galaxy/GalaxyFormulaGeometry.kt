package be.saikono.poincare.ui.galaxy

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

internal const val GALAXY_FORMULA_CANVAS_SIZE = 1100.0
internal const val GALAXY_FORMULA_CENTER = 550.0
internal const val GALAXY_EXIT_SENTINEL = "453"

internal enum class GalaxyLayer(val code: String) {
    OUTER("O"),
    MIDDLE("M"),
    INNER("I"),
    CORE("C")
}

internal enum class GalaxyTraversalDirection {
    FORWARD,
    REVERSE
}

internal data class GalaxyLayerSpec(
    val layer: GalaxyLayer,
    val sides: Int,
    val visibleCorners: Int
) {
    init {
        require(sides >= 3) { "A Galaxy polygon requires at least 3 sides, got $sides" }
        require(visibleCorners in 1..sides) {
            "${layer.name} visible corners must be within 1..$sides, got $visibleCorners"
        }
    }

    val size: Double
        get() = sides * 50.0

    val direction: GalaxyTraversalDirection
        get() = directionFor(layer)
}

internal data class GalaxyShapeSpec(
    val number: Int,
    val layers: List<GalaxyLayerSpec>
) {
    init {
        require(layers.isNotEmpty()) { "Galaxy shape $number must contain at least one layer" }
        require(layers.map(GalaxyLayerSpec::layer).distinct().size == layers.size) {
            "Galaxy shape $number contains duplicate layers"
        }
        val expectedLayerOrder = listOf(
            GalaxyLayer.OUTER,
            GalaxyLayer.MIDDLE,
            GalaxyLayer.INNER,
            GalaxyLayer.CORE
        ).take(layers.size)
        require(layers.map(GalaxyLayerSpec::layer) == expectedLayerOrder) {
            "Galaxy shape $number layers must follow Outer, Middle, Inner, Core order"
        }
        require(layers.sumOf(GalaxyLayerSpec::visibleCorners) == number) {
            "Galaxy shape $number visible-corner sum must equal its shape number"
        }
    }
}

internal data class GalaxyPointRef(
    val shape: Int,
    val layer: GalaxyLayer,
    val corner: Int
)

internal data class GalaxyFormulaPoint(
    val x: Double,
    val y: Double
)

internal data class GalaxyNodeGeometry(
    val pointId: String,
    val sequence: Int,
    val layer: GalaxyLayer,
    val corner: Int,
    val x: Double,
    val y: Double,
    val connectToId: String,
    val isStart: Boolean,
    val isExit: Boolean
)

internal sealed interface GalaxyFormulaConnection {
    val fromPointId: String
    val toPointId: String

    data class Arc(
        override val fromPointId: String,
        override val toPointId: String,
        val centre: GalaxyFormulaPoint,
        val radius: Double,
        val startAngleDegrees: Double,
        val sweepAngleDegrees: Double,
        val start: GalaxyFormulaPoint,
        val end: GalaxyFormulaPoint
    ) : GalaxyFormulaConnection

    data class CubicBezier(
        override val fromPointId: String,
        override val toPointId: String,
        val start: GalaxyFormulaPoint,
        val control1: GalaxyFormulaPoint,
        val control2: GalaxyFormulaPoint,
        val end: GalaxyFormulaPoint
    ) : GalaxyFormulaConnection
}

internal data class GalaxyFormulaTransform(
    val rotationDegrees: Double,
    val mirrorHorizontal: Boolean,
    val mirrorVertical: Boolean
)

internal data class GalaxyFormulaGeometry(
    val shape: GalaxyShapeSpec,
    val nodes: List<GalaxyNodeGeometry>,
    val connections: List<GalaxyFormulaConnection>,
    val canonicalCanvasSize: Double,
    val transform: GalaxyFormulaTransform
)

internal data class GalaxyViewportTransform(
    val scale: Double,
    val offsetX: Double,
    val offsetY: Double
) {
    fun apply(point: GalaxyFormulaPoint): GalaxyFormulaPoint = GalaxyFormulaPoint(
        x = offsetX + point.x * scale,
        y = offsetY + point.y * scale
    )
}

internal val GALAXY_SHAPES: Map<Int, GalaxyShapeSpec> = listOf(
    GalaxyShapeSpec(3, listOf(
        GalaxyLayerSpec(GalaxyLayer.OUTER, 4, 3)
    )),
    GalaxyShapeSpec(6, listOf(
        GalaxyLayerSpec(GalaxyLayer.OUTER, 6, 6)
    )),
    GalaxyShapeSpec(9, listOf(
        GalaxyLayerSpec(GalaxyLayer.OUTER, 8, 6),
        GalaxyLayerSpec(GalaxyLayer.MIDDLE, 4, 3)
    )),
    GalaxyShapeSpec(12, listOf(
        GalaxyLayerSpec(GalaxyLayer.OUTER, 10, 7),
        GalaxyLayerSpec(GalaxyLayer.MIDDLE, 6, 5)
    )),
    GalaxyShapeSpec(15, listOf(
        GalaxyLayerSpec(GalaxyLayer.OUTER, 10, 9),
        GalaxyLayerSpec(GalaxyLayer.MIDDLE, 6, 6)
    )),
    GalaxyShapeSpec(18, listOf(
        GalaxyLayerSpec(GalaxyLayer.OUTER, 12, 8),
        GalaxyLayerSpec(GalaxyLayer.MIDDLE, 8, 6),
        GalaxyLayerSpec(GalaxyLayer.INNER, 4, 4)
    )),
    GalaxyShapeSpec(21, listOf(
        GalaxyLayerSpec(GalaxyLayer.OUTER, 12, 10),
        GalaxyLayerSpec(GalaxyLayer.MIDDLE, 8, 7),
        GalaxyLayerSpec(GalaxyLayer.INNER, 4, 4)
    )),
    GalaxyShapeSpec(24, listOf(
        GalaxyLayerSpec(GalaxyLayer.OUTER, 14, 11),
        GalaxyLayerSpec(GalaxyLayer.MIDDLE, 10, 8),
        GalaxyLayerSpec(GalaxyLayer.INNER, 6, 5)
    )),
    GalaxyShapeSpec(27, listOf(
        GalaxyLayerSpec(GalaxyLayer.OUTER, 14, 12),
        GalaxyLayerSpec(GalaxyLayer.MIDDLE, 10, 9),
        GalaxyLayerSpec(GalaxyLayer.INNER, 6, 6)
    )),
    GalaxyShapeSpec(30, listOf(
        GalaxyLayerSpec(GalaxyLayer.OUTER, 16, 13),
        GalaxyLayerSpec(GalaxyLayer.MIDDLE, 12, 10),
        GalaxyLayerSpec(GalaxyLayer.INNER, 8, 7)
    )),
    GalaxyShapeSpec(33, listOf(
        GalaxyLayerSpec(GalaxyLayer.OUTER, 16, 14),
        GalaxyLayerSpec(GalaxyLayer.MIDDLE, 12, 11),
        GalaxyLayerSpec(GalaxyLayer.INNER, 8, 8)
    )),
    GalaxyShapeSpec(36, listOf(
        GalaxyLayerSpec(GalaxyLayer.OUTER, 16, 14),
        GalaxyLayerSpec(GalaxyLayer.MIDDLE, 12, 11),
        GalaxyLayerSpec(GalaxyLayer.INNER, 8, 7),
        GalaxyLayerSpec(GalaxyLayer.CORE, 4, 4)
    )),
    GalaxyShapeSpec(39, listOf(
        GalaxyLayerSpec(GalaxyLayer.OUTER, 18, 15),
        GalaxyLayerSpec(GalaxyLayer.MIDDLE, 14, 11),
        GalaxyLayerSpec(GalaxyLayer.INNER, 10, 8),
        GalaxyLayerSpec(GalaxyLayer.CORE, 6, 5)
    )),
    GalaxyShapeSpec(42, listOf(
        GalaxyLayerSpec(GalaxyLayer.OUTER, 18, 15),
        GalaxyLayerSpec(GalaxyLayer.MIDDLE, 14, 12),
        GalaxyLayerSpec(GalaxyLayer.INNER, 10, 9),
        GalaxyLayerSpec(GalaxyLayer.CORE, 6, 6)
    )),
    GalaxyShapeSpec(45, listOf(
        GalaxyLayerSpec(GalaxyLayer.OUTER, 18, 16),
        GalaxyLayerSpec(GalaxyLayer.MIDDLE, 14, 13),
        GalaxyLayerSpec(GalaxyLayer.INNER, 10, 10),
        GalaxyLayerSpec(GalaxyLayer.CORE, 6, 6)
    )),
    GalaxyShapeSpec(48, listOf(
        GalaxyLayerSpec(GalaxyLayer.OUTER, 20, 17),
        GalaxyLayerSpec(GalaxyLayer.MIDDLE, 16, 14),
        GalaxyLayerSpec(GalaxyLayer.INNER, 12, 10),
        GalaxyLayerSpec(GalaxyLayer.CORE, 8, 7)
    )),
    GalaxyShapeSpec(51, listOf(
        GalaxyLayerSpec(GalaxyLayer.OUTER, 20, 18),
        GalaxyLayerSpec(GalaxyLayer.MIDDLE, 16, 14),
        GalaxyLayerSpec(GalaxyLayer.INNER, 12, 11),
        GalaxyLayerSpec(GalaxyLayer.CORE, 8, 8)
    )),
    GalaxyShapeSpec(54, listOf(
        GalaxyLayerSpec(GalaxyLayer.OUTER, 22, 18),
        GalaxyLayerSpec(GalaxyLayer.MIDDLE, 18, 15),
        GalaxyLayerSpec(GalaxyLayer.INNER, 14, 12),
        GalaxyLayerSpec(GalaxyLayer.CORE, 10, 9)
    )),
    GalaxyShapeSpec(57, listOf(
        GalaxyLayerSpec(GalaxyLayer.OUTER, 22, 19),
        GalaxyLayerSpec(GalaxyLayer.MIDDLE, 18, 16),
        GalaxyLayerSpec(GalaxyLayer.INNER, 14, 13),
        GalaxyLayerSpec(GalaxyLayer.CORE, 10, 9)
    )),
    GalaxyShapeSpec(60, listOf(
        GalaxyLayerSpec(GalaxyLayer.OUTER, 22, 20),
        GalaxyLayerSpec(GalaxyLayer.MIDDLE, 18, 17),
        GalaxyLayerSpec(GalaxyLayer.INNER, 14, 13),
        GalaxyLayerSpec(GalaxyLayer.CORE, 10, 10)
    ))
).associateBy(GalaxyShapeSpec::number).also { shapes ->
    require(shapes.keys.sorted() == (3..60 step 3).toList()) {
        "Galaxy shapes must cover every multiple of 3 from 3 through 60"
    }
}

internal fun polygonPoint(
    sides: Int,
    corner: Int
): GalaxyFormulaPoint {
    require(sides >= 3)
    require(corner in 1..sides)
    val size = sides * 50.0
    val radius = size / 2.0
    val angleDegrees = -90.0 - (180.0 / sides) + ((corner - 1) * (360.0 / sides))
    val angle = Math.toRadians(angleDegrees)
    return GalaxyFormulaPoint(
        x = GALAXY_FORMULA_CENTER + radius * cos(angle),
        y = GALAXY_FORMULA_CENTER + radius * sin(angle)
    )
}

internal fun directionFor(layer: GalaxyLayer): GalaxyTraversalDirection = when (layer) {
    GalaxyLayer.OUTER -> GalaxyTraversalDirection.FORWARD
    GalaxyLayer.MIDDLE -> GalaxyTraversalDirection.REVERSE
    GalaxyLayer.INNER -> GalaxyTraversalDirection.FORWARD
    GalaxyLayer.CORE -> GalaxyTraversalDirection.REVERSE
}

internal fun pointId(
    shapeNumber: Int,
    layer: GalaxyLayer,
    corner: Int
): String = "S%02d-%s-%02d".format(shapeNumber, layer.code, corner)

internal fun pointId(ref: GalaxyPointRef): String = pointId(ref.shape, ref.layer, ref.corner)

internal fun orderedVisibleCorners(layer: GalaxyLayerSpec): List<Int> = when (layer.direction) {
    GalaxyTraversalDirection.FORWARD -> (1..layer.visibleCorners).toList()
    GalaxyTraversalDirection.REVERSE -> (layer.visibleCorners downTo 1).toList()
}

internal fun generateRoute(shape: GalaxyShapeSpec): List<GalaxyPointRef> =
    shape.layers.flatMap { layer ->
        orderedVisibleCorners(layer).map { corner ->
            GalaxyPointRef(shape.number, layer.layer, corner)
        }
    }.also { route ->
        require(route.size == shape.number) {
            "Galaxy shape ${shape.number} route must contain ${shape.number} nodes, got ${route.size}"
        }
    }

internal fun buildNodes(shape: GalaxyShapeSpec): List<GalaxyNodeGeometry> {
    val route = generateRoute(shape)
    val layerByType = shape.layers.associateBy(GalaxyLayerSpec::layer)
    return route.mapIndexed { index, ref ->
        val layer = layerByType.getValue(ref.layer)
        val position = polygonPoint(layer.sides, ref.corner)
        GalaxyNodeGeometry(
            pointId = pointId(ref),
            sequence = index + 1,
            layer = ref.layer,
            corner = ref.corner,
            x = position.x,
            y = position.y,
            connectToId = if (index < route.lastIndex) pointId(route[index + 1]) else GALAXY_EXIT_SENTINEL,
            isStart = index == 0,
            isExit = index == route.lastIndex
        )
    }
}

internal fun transformPoint(
    point: GalaxyFormulaPoint,
    rotationDegrees: Double,
    mirrorHorizontal: Boolean,
    mirrorVertical: Boolean
): GalaxyFormulaPoint {
    var dx = point.x - GALAXY_FORMULA_CENTER
    var dy = point.y - GALAXY_FORMULA_CENTER
    if (mirrorHorizontal) dx = -dx
    if (mirrorVertical) dy = -dy
    val angle = Math.toRadians(rotationDegrees)
    val rotatedX = dx * cos(angle) - dy * sin(angle)
    val rotatedY = dx * sin(angle) + dy * cos(angle)
    return GalaxyFormulaPoint(
        x = GALAXY_FORMULA_CENTER + rotatedX,
        y = GALAXY_FORMULA_CENTER + rotatedY
    )
}

internal fun galaxyViewportTransform(
    availableWidth: Double,
    availableHeight: Double
): GalaxyViewportTransform {
    require(availableWidth >= 0.0 && availableHeight >= 0.0)
    val scale = min(
        availableWidth / GALAXY_FORMULA_CANVAS_SIZE,
        availableHeight / GALAXY_FORMULA_CANVAS_SIZE
    )
    return GalaxyViewportTransform(
        scale = scale,
        offsetX = (availableWidth - GALAXY_FORMULA_CANVAS_SIZE * scale) / 2.0,
        offsetY = (availableHeight - GALAXY_FORMULA_CANVAS_SIZE * scale) / 2.0
    )
}

internal object GalaxyGeometryGenerator {
    fun generate(
        shapeNumber: Int,
        rotationDegrees: Double = 0.0,
        mirrorHorizontal: Boolean = false,
        mirrorVertical: Boolean = false
    ): GalaxyFormulaGeometry {
        val shape = requireNotNull(GALAXY_SHAPES[shapeNumber]) {
            "Unsupported Galaxy shape $shapeNumber; expected one of ${GALAXY_SHAPES.keys.sorted()}"
        }
        val canonicalNodes = buildNodes(shape)
        val transformedNodes = canonicalNodes.map { node ->
            val transformed = transformPoint(
                GalaxyFormulaPoint(node.x, node.y),
                rotationDegrees,
                mirrorHorizontal,
                mirrorVertical
            )
            node.copy(x = transformed.x, y = transformed.y)
        }
        val layerByType = shape.layers.associateBy(GalaxyLayerSpec::layer)
        val canonicalById = canonicalNodes.associateBy(GalaxyNodeGeometry::pointId)
        val transformedById = transformedNodes.associateBy(GalaxyNodeGeometry::pointId)
        val mirrorReversesSweep = mirrorHorizontal.xor(mirrorVertical)
        val connections = canonicalNodes.zipWithNext { source, target ->
            val sourceLayer = layerByType.getValue(source.layer)
            val targetLayer = layerByType.getValue(target.layer)
            val transformedSource = transformedById.getValue(source.pointId).toPoint()
            val transformedTarget = transformedById.getValue(target.pointId).toPoint()
            if (source.layer == target.layer) {
                val centre = transformPoint(
                    GalaxyFormulaPoint(GALAXY_FORMULA_CENTER, GALAXY_FORMULA_CENTER),
                    rotationDegrees,
                    mirrorHorizontal,
                    mirrorVertical
                )
                val baseSweep = when (sourceLayer.direction) {
                    GalaxyTraversalDirection.FORWARD -> 360.0 / sourceLayer.sides
                    GalaxyTraversalDirection.REVERSE -> -360.0 / sourceLayer.sides
                }
                val startAngle = Math.toDegrees(
                    atan2(transformedSource.y - centre.y, transformedSource.x - centre.x)
                )
                GalaxyFormulaConnection.Arc(
                    fromPointId = source.pointId,
                    toPointId = target.pointId,
                    centre = centre,
                    radius = sourceLayer.size / 2.0,
                    startAngleDegrees = startAngle,
                    sweepAngleDegrees = if (mirrorReversesSweep) -baseSweep else baseSweep,
                    start = transformedSource,
                    end = transformedTarget
                )
            } else {
                val canonicalSource = canonicalById.getValue(source.pointId).toPoint()
                val canonicalTarget = canonicalById.getValue(target.pointId).toPoint()
                val sourceTangent = traversalTangent(canonicalSource, sourceLayer.direction)
                val targetTangent = traversalTangent(canonicalTarget, targetLayer.direction)
                val distance = hypot(
                    canonicalTarget.x - canonicalSource.x,
                    canonicalTarget.y - canonicalSource.y
                )
                val handleLength = (distance * 0.40).coerceIn(35.0, 90.0)
                val canonicalControl1 = GalaxyFormulaPoint(
                    canonicalSource.x + sourceTangent.x * handleLength,
                    canonicalSource.y + sourceTangent.y * handleLength
                )
                val canonicalControl2 = GalaxyFormulaPoint(
                    canonicalTarget.x - targetTangent.x * handleLength,
                    canonicalTarget.y - targetTangent.y * handleLength
                )
                GalaxyFormulaConnection.CubicBezier(
                    fromPointId = source.pointId,
                    toPointId = target.pointId,
                    start = transformedSource,
                    control1 = transformPoint(
                        canonicalControl1,
                        rotationDegrees,
                        mirrorHorizontal,
                        mirrorVertical
                    ),
                    control2 = transformPoint(
                        canonicalControl2,
                        rotationDegrees,
                        mirrorHorizontal,
                        mirrorVertical
                    ),
                    end = transformedTarget
                )
            }
        }
        return GalaxyFormulaGeometry(
            shape = shape,
            nodes = transformedNodes,
            connections = connections,
            canonicalCanvasSize = GALAXY_FORMULA_CANVAS_SIZE,
            transform = GalaxyFormulaTransform(rotationDegrees, mirrorHorizontal, mirrorVertical)
        )
    }

    private fun GalaxyNodeGeometry.toPoint() = GalaxyFormulaPoint(x, y)

    private fun traversalTangent(
        point: GalaxyFormulaPoint,
        direction: GalaxyTraversalDirection
    ): GalaxyFormulaPoint {
        val theta = atan2(
            point.y - GALAXY_FORMULA_CENTER,
            point.x - GALAXY_FORMULA_CENTER
        )
        return when (direction) {
            GalaxyTraversalDirection.FORWARD -> GalaxyFormulaPoint(-sin(theta), cos(theta))
            GalaxyTraversalDirection.REVERSE -> GalaxyFormulaPoint(sin(theta), -cos(theta))
        }
    }
}

internal fun galaxyFormulaShapeNumber(nodeCount: Int): Int? = nodeCount.takeIf(GALAXY_SHAPES::containsKey)

internal fun GalaxyShapeSpec.containerRadius(): Float =
    (layers.maxOf(GalaxyLayerSpec::size) / 2.0).toFloat()
