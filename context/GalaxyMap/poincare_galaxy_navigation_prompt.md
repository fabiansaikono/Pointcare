# Poincaré Galaxy Navigation — Final Deterministic Geometry Prompt

Implement the final deterministic formula geometry used inside each classic Poincaré lesson sphere group.

This specification is authoritative for formula points, Point IDs, route order, internal curves, rotation and mirroring, and replaces all earlier polygon/orb position rules. Dynamic curriculum projection, sphere-group placement, cross-group routing, state styling and interaction are governed by `docs/GALAXY_NAVIGATION_ARCHITECTURE.md`.

The layout must be deterministic, formula-based and reproducible. Do not use force-directed layouts, automatic spacing, collision-based repositioning, random positioning, snapping or any algorithm that moves individual nodes away from their mathematically calculated polygon positions.

The implementation must support rotation and mirroring of the complete generated shape while preserving Point IDs, route sequence and connections.

---

## 1. Canonical coordinate system

All Galaxy shapes are generated inside one canonical coordinate system:

```text
Canvas: 1100 × 1100
Origin: (0,0) at top-left
Center: (550,550)
+X: right
+Y: down
```

All polygon layers belonging to a shape share exactly the same center:

```text
CENTER_X = 550
CENTER_Y = 550
```

The final layout may be uniformly scaled into the available UI viewport, but relative geometry must never change.

---

## 2. Polygon size rule

The polygon container size is derived only from its number of sides:

```text
containerSizePx = sides × 50
```

Examples:

```text
4 sides  = 200 px
6 sides  = 300 px
8 sides  = 400 px
10 sides = 500 px
12 sides = 600 px
14 sides = 700 px
16 sides = 800 px
18 sides = 900 px
20 sides = 1000 px
22 sides = 1100 px
```

The circumradius is:

```text
radius = containerSizePx / 2
```

Do not stretch or distort polygons to fill their square container.

---

## 3. Layer names and route directions

Layer order is always:

```text
1 = Outer
2 = Middle
3 = Inner
4 = Core
```

Point ID layer codes:

```text
Outer  = O
Middle = M
Inner  = I
Core   = C
```

Traversal direction is fixed by layer:

```text
Outer  = FORWARD
Middle = REVERSE
Inner  = FORWARD
Core   = REVERSE
```

Meaning:

```text
FORWARD:
corner 1 → corner 2 → ... → visibleCorners

REVERSE:
visibleCorners → ... → corner 2 → corner 1
```

---

## 4. Final ShapeSpec configuration

The following configuration is the only static geometry data that should be required.

Each layer definition is:

```text
LayerSpec(layer, sides, visibleCorners)
```

### Shape 3

```text
Outer: 4 sides, 3 visible
```

### Shape 6

```text
Outer: 6 sides, 6 visible
```

### Shape 9

```text
Outer:  8 sides, 6 visible
Middle: 4 sides, 3 visible
```

### Shape 12

```text
Outer:  10 sides, 7 visible
Middle:  6 sides, 5 visible
```

### Shape 15

```text
Outer:  10 sides, 9 visible
Middle:  6 sides, 6 visible
```

### Shape 18

```text
Outer:  12 sides, 8 visible
Middle:  8 sides, 6 visible
Inner:   4 sides, 4 visible
```

### Shape 21

```text
Outer:  12 sides, 10 visible
Middle:  8 sides, 7 visible
Inner:   4 sides, 4 visible
```

### Shape 24

```text
Outer:  14 sides, 11 visible
Middle: 10 sides, 8 visible
Inner:   6 sides, 5 visible
```

### Shape 27

```text
Outer:  14 sides, 12 visible
Middle: 10 sides, 9 visible
Inner:   6 sides, 6 visible
```

### Shape 30

```text
Outer:  16 sides, 13 visible
Middle: 12 sides, 10 visible
Inner:   8 sides, 7 visible
```

### Shape 33

```text
Outer:  16 sides, 14 visible
Middle: 12 sides, 11 visible
Inner:   8 sides, 8 visible
```

### Shape 36

```text
Outer:  16 sides, 14 visible
Middle: 12 sides, 11 visible
Inner:   8 sides, 7 visible
Core:    4 sides, 4 visible
```

### Shape 39

```text
Outer:  18 sides, 15 visible
Middle: 14 sides, 11 visible
Inner:  10 sides, 8 visible
Core:    6 sides, 5 visible
```

### Shape 42

```text
Outer:  18 sides, 15 visible
Middle: 14 sides, 12 visible
Inner:  10 sides, 9 visible
Core:    6 sides, 6 visible
```

### Shape 45

```text
Outer:  18 sides, 16 visible
Middle: 14 sides, 13 visible
Inner:  10 sides, 10 visible
Core:    6 sides, 6 visible
```

### Shape 48

```text
Outer:  20 sides, 17 visible
Middle: 16 sides, 14 visible
Inner:  12 sides, 10 visible
Core:    8 sides, 7 visible
```

### Shape 51

```text
Outer:  20 sides, 18 visible
Middle: 16 sides, 14 visible
Inner:  12 sides, 11 visible
Core:    8 sides, 8 visible
```

### Shape 54

```text
Outer:  22 sides, 18 visible
Middle: 18 sides, 15 visible
Inner:  14 sides, 12 visible
Core:   10 sides, 9 visible
```

### Shape 57

```text
Outer:  22 sides, 19 visible
Middle: 18 sides, 16 visible
Inner:  14 sides, 13 visible
Core:   10 sides, 9 visible
```

### Shape 60

```text
Outer:  22 sides, 20 visible
Middle: 18 sides, 17 visible
Inner:  14 sides, 13 visible
Core:   10 sides, 10 visible
```

Validation rule:

```text
sum(visibleCorners for all layers) == shapeNumber
```

This must be true for every ShapeSpec.

---

## 5. Recommended data model

Use a compact data model equivalent to:

```kotlin
enum class GalaxyLayer(val code: String) {
    OUTER("O"),
    MIDDLE("M"),
    INNER("I"),
    CORE("C")
}

enum class Direction {
    FORWARD,
    REVERSE
}

data class LayerSpec(
    val layer: GalaxyLayer,
    val sides: Int,
    val visibleCorners: Int
) {
    val sizePx: Double
        get() = sides * 50.0
}

data class ShapeSpec(
    val shapeNumber: Int,
    val layers: List<LayerSpec>
)

data class PointRef(
    val shapeNumber: Int,
    val layer: GalaxyLayer,
    val corner: Int
)

data class GalaxyPoint(
    val x: Double,
    val y: Double
)

data class GalaxyNodeGeometry(
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
```

Do not store hundreds of hard-coded X/Y coordinates.

---

## 6. Exact polygon coordinate formula

Every polygon is regular and uses a flat-top orientation.

Corners are numbered clockwise.

For:

```text
n = number of polygon sides
s = container size
c = corner number, starting at 1
```

calculate:

```text
radius = s / 2

startAngleDegrees =
    -90 - (180 / n)

angleDegrees =
    startAngleDegrees
    + ((c - 1) × (360 / n))

angleRadians =
    angleDegrees × π / 180

x =
    550 + radius × cos(angleRadians)

y =
    550 + radius × sin(angleRadians)
```

Kotlin equivalent:

```kotlin
fun polygonPoint(
    sides: Int,
    corner: Int
): GalaxyPoint {

    require(sides >= 3)
    require(corner in 1..sides)

    val size = sides * 50.0
    val radius = size / 2.0

    val angleDegrees =
        -90.0 -
        (180.0 / sides) +
        ((corner - 1) * (360.0 / sides))

    val angle = Math.toRadians(angleDegrees)

    return GalaxyPoint(
        x = 550.0 + radius * Math.cos(angle),
        y = 550.0 + radius * Math.sin(angle)
    )
}
```

This function is authoritative.

---

## 7. Generate Point IDs

Point IDs must be generated rather than manually stored.

Format:

```text
S{shapeNumber as 2 digits}-{layerCode}-{corner as 2 digits}
```

Examples:

```text
S03-O-01
S24-M-08
S36-C-04
S60-I-13
```

Kotlin:

```kotlin
fun pointId(
    shapeNumber: Int,
    layer: GalaxyLayer,
    corner: Int
): String =
    "S%02d-%s-%02d".format(
        shapeNumber,
        layer.code,
        corner
    )
```

---

## 8. Generate visible corners

The `visibleCorners` value in ShapeSpec is authoritative.

Do not infer visibility from polygon side count.

Generate visible corners as:

```kotlin
fun directionFor(layer: GalaxyLayer): Direction =
    when (layer) {
        GalaxyLayer.OUTER -> Direction.FORWARD
        GalaxyLayer.MIDDLE -> Direction.REVERSE
        GalaxyLayer.INNER -> Direction.FORWARD
        GalaxyLayer.CORE -> Direction.REVERSE
    }

fun orderedVisibleCorners(
    layer: LayerSpec
): List<Int> {

    return when (directionFor(layer.layer)) {

        Direction.FORWARD ->
            (1..layer.visibleCorners).toList()

        Direction.REVERSE ->
            (layer.visibleCorners downTo 1).toList()
    }
}
```

Corners not returned by this function are construction geometry only and must not be shown as orbs.

---

## 9. Generate the complete route

The visible route is created by concatenating layers in this order:

```text
Outer → Middle → Inner → Core
```

Only layers present in the ShapeSpec are included.

```kotlin
fun generateRoute(
    shape: ShapeSpec
): List<PointRef> {

    return shape.layers.flatMap { layer ->

        orderedVisibleCorners(layer).map { corner ->

            PointRef(
                shapeNumber = shape.shapeNumber,
                layer = layer.layer,
                corner = corner
            )
        }
    }
}
```

Validation:

```text
generateRoute(shape).size == shape.shapeNumber
```

---

## 10. Generate connections

Do not maintain a separate connection table.

Connections are generated from the route:

```text
route[0] → route[1]
route[1] → route[2]
...
route[n-2] → route[n-1]
route[n-1] → 453
```

`453` is the logical exit sentinel.

Kotlin:

```kotlin
const val EXIT_SENTINEL = "453"
```

The final node:

```text
connectToId = "453"
isExit = true
```

Do not give `453` an X/Y coordinate.

Do not draw a line from the final orb to a fabricated exit position.

---

## 11. Build final node geometry

Use the route plus formula-generated coordinates:

```kotlin
fun generateNodes(
    shape: ShapeSpec
): List<GalaxyNodeGeometry> {

    val route = generateRoute(shape)

    val layerMap =
        shape.layers.associateBy { it.layer }

    return route.mapIndexed { index, ref ->

        val point =
            polygonPoint(
                sides = layerMap.getValue(ref.layer).sides,
                corner = ref.corner
            )

        GalaxyNodeGeometry(
            pointId =
                pointId(
                    ref.shapeNumber,
                    ref.layer,
                    ref.corner
                ),

            sequence = index + 1,

            layer = ref.layer,

            corner = ref.corner,

            x = point.x,

            y = point.y,

            connectToId =
                if (index < route.lastIndex)
                    pointId(
                        route[index + 1].shapeNumber,
                        route[index + 1].layer,
                        route[index + 1].corner
                    )
                else
                    EXIT_SENTINEL,

            isStart = index == 0,

            isExit = index == route.lastIndex
        )
    }
}
```

---

## 12. Curved same-layer connections

Do not draw straight polygon edges.

For two consecutive nodes on the same layer, render the connection as a circular arc on that layer's circumcircle.

The layer radius is:

```text
radius = sides × 50 / 2
```

For SVG-style arc semantics:

```text
largeArcFlag = 0

sweepFlag =
    1 for FORWARD
    0 for REVERSE
```

Equivalent SVG path:

```text
M sourceX sourceY
A radius radius 0 0 sweepFlag targetX targetY
```

Because connections are between consecutive visible corners, always use the minor arc.

---

## 13. Curved cross-layer connections

When the next route node belongs to another layer, use a smooth cubic Bézier transition.

The endpoints are immutable:

```text
P0 = exact source node position
P3 = exact target node position
```

Calculate traversal tangents.

For point angle `θ`:

```text
FORWARD tangent:
(-sin θ, cos θ)

REVERSE tangent:
(sin θ, -cos θ)
```

Then:

```text
distance =
    distance(P0, P3)

handleLength =
    clamp(
        distance × 0.40,
        35,
        90
    )

P1 =
    P0 + sourceTangent × handleLength

P2 =
    P3 - targetTangent × handleLength
```

Render:

```text
C P1.x P1.y,
  P2.x P2.y,
  P3.x P3.y
```

The curve may never move either endpoint away from the mathematically generated orb position.

---

## 14. Rotation and mirroring

Every complete Galaxy shape may be rotated and/or mirrored.

Support:

```text
rotationDegrees
mirrorHorizontal
mirrorVertical
```

Transforms apply to the complete shape, not to individual layers independently.

These values must remain unchanged after transformation:

```text
Point ID
layer
corner
sequence
lesson mapping
connectToId
isStart
isExit
```

Only geometry changes.

Use `(550,550)` as the transform center.

Recommended transform order:

```text
1. Generate canonical coordinates
2. Mirror
3. Rotate
4. Uniformly scale into viewport
5. Translate into viewport
```

Kotlin:

```kotlin
fun transformPoint(
    point: GalaxyPoint,
    rotationDegrees: Double,
    mirrorHorizontal: Boolean,
    mirrorVertical: Boolean
): GalaxyPoint {

    var dx = point.x - 550.0
    var dy = point.y - 550.0

    if (mirrorHorizontal) {
        dx = -dx
    }

    if (mirrorVertical) {
        dy = -dy
    }

    val angle =
        Math.toRadians(rotationDegrees)

    val rotatedX =
        dx * Math.cos(angle) -
        dy * Math.sin(angle)

    val rotatedY =
        dx * Math.sin(angle) +
        dy * Math.cos(angle)

    return GalaxyPoint(
        x = 550.0 + rotatedX,
        y = 550.0 + rotatedY
    )
}
```

If possible, render the canonical node/path group first and transform the complete group as one unit.

---

## 15. Responsive viewport scaling

The canonical geometry must remain 1100 × 1100.

Never regenerate node positions based on screen dimensions.

Given:

```text
availableWidth
availableHeight
```

calculate:

```text
scale =
    min(
        availableWidth / 1100,
        availableHeight / 1100
    )

renderWidth =
    1100 × scale

renderHeight =
    1100 × scale

offsetX =
    (availableWidth - renderWidth) / 2

offsetY =
    (availableHeight - renderHeight) / 2

screenX =
    offsetX + transformedX × scale

screenY =
    offsetY + transformedY × scale
```

The center of the orb must be located at `(screenX, screenY)`.

Never apply different X and Y scale factors.

---

## 16. Rendering

The polygon outlines are invisible construction geometry.

Draw only:

```text
1. Galaxy background
2. Connection curves
3. Optional connection glow
4. Visible orb circles
5. Orb state / progress
6. Sequence or lesson icon
```

Only generated route nodes are visible.

Do not show unused polygon corners.

---

## 17. Poincaré lesson projection

Geometry is independent from lesson type.

Map the ordered overview orbs supplied by the Galaxy model to route order:

```text
route[0] = overview orb 1
route[1] = overview orb 2
...
```

The current app identifies Theorie, Oefeningen and Toets with universal icons. Color and border weight communicate runtime state according to `docs/GALAXY_NAVIGATION_ARCHITECTURE.md`; orb type does not define a separate geometry or position rule.

The same geometry must support visual states such as:

```text
locked
available
current
completed
failed
mastered
```

These states may change orb styling but must never alter geometry.

---

## 18. Start and exit

Formula-route start node:

```text
route.first()
```

Formula-route exit node:

```text
route.last()
```

The exit node is the final visible overview orb in this formula shape. This is distinct from the global category startorb rendered by the navigation layer.

Its:

```text
connectToId = "453"
```

`453` itself is not rendered as a polygon node.

---

## 19. Validation requirements

For every ShapeSpec:

```text
sum(layer.visibleCorners) == shapeNumber
generateRoute(shape).size == shapeNumber
```

Also validate:

```text
exactly one start node
exactly one exit node
no duplicate Point IDs
no closed loops
all generated coordinates are finite
all layers share center (550,550)
every non-final node connects to the next route node
final node connects to 453
visibleCorners <= sides for every layer
```

Global final configuration:

```text
20 shapes
630 visible nodes
610 rendered node-to-node connections
20 logical exit links to 453
```

---

## 20. Do not use automatic graph-layout systems

Do not use:

```text
force-directed layout
grid layout
automatic node spacing
constraint redistribution
collision-based movement
random positioning
graph beautification
```

The polygon geometry itself is the intended navigation design.

---

## 21. Recommended implementation architecture

Keep geometry generation separate from UI state.

Example:

```kotlin
class GalaxyGeometryGenerator {

    fun generate(
        shapeNumber: Int,
        rotationDegrees: Double = 0.0,
        mirrorHorizontal: Boolean = false,
        mirrorVertical: Boolean = false
    ): GalaxyGeometry
}
```

`GalaxyGeometry` should contain:

```text
nodes
connections
canonicalCanvasSize
transform metadata
```

The renderer should consume this deterministic geometry rather than calculate an independent layout.

---

## 22. Final acceptance criteria

The implementation is accepted only if:

1. Shape positioning is driven by the exact polygon formula.
2. Polygon size is always `sides × 50`.
3. The canonical canvas is always `1100 × 1100`.
4. All layers share center `(550,550)`.
5. Visible-corner counts come only from the final ShapeSpec configuration.
6. Point IDs are generated deterministically.
7. Routes are generated from layer order + visible corners + fixed traversal direction.
8. Connections are generated from route order.
9. Same-layer connections are circular arcs.
10. Cross-layer connections are smooth deterministic cubic Béziers.
11. `453` remains a coordinate-free logical exit sentinel.
12. Rotation and mirroring preserve node identity and route order.
13. Device size only causes uniform scaling of the complete canonical geometry.
14. Repeated calls with identical inputs return identical geometry.
15. No automatic node repositioning is used.
