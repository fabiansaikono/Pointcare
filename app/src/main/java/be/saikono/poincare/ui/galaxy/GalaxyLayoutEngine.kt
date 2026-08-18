package be.saikono.poincare.ui.galaxy

import java.util.ArrayDeque
import java.util.PriorityQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

internal const val GALAXY_START_NODE_ID = "galaxy:start"

enum class GalaxyOrbKind {
    THEORY,
    PRACTICE,
    TEST,
    PORTAL
}

internal fun galaxyOrbNodeId(lessonId: String, kind: GalaxyOrbKind): String =
    "$lessonId:${kind.name.lowercase()}"

internal data class GalaxyOrbNodeRef(
    val nodeId: String,
    val orb: GalaxyPhaseOrbUi
)

internal fun galaxyOrbNodes(lesson: GalaxyLessonNodeUi): List<GalaxyOrbNodeRef> {
    val orbs = lesson.phaseOrbs.ifEmpty {
        val kinds = if (lesson.portalDestination != null) {
            listOf(GalaxyOrbKind.PORTAL)
        } else {
            listOf(GalaxyOrbKind.THEORY, GalaxyOrbKind.PRACTICE, GalaxyOrbKind.TEST)
        }
        kinds.map { kind -> GalaxyPhaseOrbUi(kind, lesson.state, lesson.progressPercent) }
    }
    val totalsByKind = orbs.groupingBy { it.kind }.eachCount()
    val occurrencesByKind = mutableMapOf<GalaxyOrbKind, Int>()
    return orbs.map { orb ->
        val occurrence = (occurrencesByKind[orb.kind] ?: 0) + 1
        occurrencesByKind[orb.kind] = occurrence
        val baseId = galaxyOrbNodeId(lesson.id, orb.kind)
        GalaxyOrbNodeRef(
            nodeId = if (totalsByKind[orb.kind] == 1) baseId else "$baseId:$occurrence",
            orb = orb
        )
    }
}

internal fun galaxyOrbNodeId(lesson: GalaxyLessonNodeUi, orb: GalaxyPhaseOrbUi): String =
    galaxyOrbNodes(lesson).firstOrNull { node ->
        node.orb === orb || (
            node.orb.kind == orb.kind &&
                node.orb.stageId == orb.stageId &&
                node.orb.state == orb.state &&
                node.orb.progressPercent == orb.progressPercent
            )
    }?.nodeId ?: galaxyOrbNodeId(lesson.id, orb.kind)

internal fun galaxyOrbKinds(lesson: GalaxyLessonNodeUi): List<GalaxyOrbKind> =
    galaxyOrbNodes(lesson).map { it.orb.kind }

private val galaxyLessonOrder = compareBy<GalaxyLessonNodeUi>({ it.sequence }, { it.id })

/**
 * Orders the current lessons from their live prerequisite graph. Sequence and id are
 * deterministic tie breakers only, so newly added or edited dependencies automatically
 * move their lesson without requiring layout-specific curriculum rules.
 */
internal fun orderGalaxyLessonsForSphere(
    lessons: List<GalaxyLessonNodeUi>
): List<GalaxyLessonNodeUi> {
    if (lessons.size < 2) return lessons
    val lessonsById = lessons.associateBy { it.id }
    val lessonIds = lessonsById.keys
    val internalPrerequisites = lessons.associate { lesson ->
        lesson.id to lesson.prerequisiteLessonIds.filterTo(linkedSetOf()) { it in lessonIds }
    }
    val indegree = internalPrerequisites.mapValuesTo(mutableMapOf()) { (_, prerequisites) ->
        prerequisites.size
    }
    val dependants = mutableMapOf<String, MutableList<GalaxyLessonNodeUi>>()
    lessons.forEach { lesson ->
        internalPrerequisites.getValue(lesson.id).forEach { prerequisiteId ->
            dependants.getOrPut(prerequisiteId, ::mutableListOf).add(lesson)
        }
    }
    val ready = PriorityQueue(galaxyLessonOrder)
    lessons.filterTo(ready) { indegree.getValue(it.id) == 0 }
    val ordered = ArrayList<GalaxyLessonNodeUi>(lessons.size)
    while (ready.isNotEmpty()) {
        val lesson = ready.remove()
        ordered += lesson
        dependants[lesson.id].orEmpty().forEach { dependant ->
            val remaining = indegree.getValue(dependant.id) - 1
            indegree[dependant.id] = remaining
            if (remaining == 0) ready += dependant
        }
    }

    // A malformed future catalog cycle must not hide lessons. Keep every remaining
    // lesson visible in deterministic order; catalog validation can report the cycle.
    if (ordered.size != lessons.size) {
        val orderedIds = ordered.mapTo(hashSetOf()) { it.id }
        ordered += lessons.filterNot { it.id in orderedIds }.sortedWith(galaxyLessonOrder)
    }
    return ordered
}

/**
 * Keeps lesson phases together while splitting only when the next complete
 * lesson would exceed the largest exact formula shape.
 */
internal fun <T> splitGalaxyFormulaSegments(
    items: List<T>,
    nodeCount: (T) -> Int
): List<List<T>> {
    if (items.isEmpty()) return emptyList()
    val maximumShapeNodes = GALAXY_SHAPES.keys.max()
    val segments = mutableListOf<List<T>>()
    var current = mutableListOf<T>()
    var currentNodeCount = 0
    items.forEach { item ->
        val itemNodeCount = nodeCount(item)
        require(itemNodeCount in 1..maximumShapeNodes) {
            "A single Galaxy item must expose 1..$maximumShapeNodes nodes, got $itemNodeCount"
        }
        if (current.isNotEmpty() && currentNodeCount + itemNodeCount > maximumShapeNodes) {
            segments += current
            current = mutableListOf()
            currentNodeCount = 0
        }
        current += item
        currentNodeCount += itemNodeCount
    }
    if (current.isNotEmpty()) segments += current
    return segments
}

/** Removes ancestor shortcuts while retaining every immediate branch in a live DAG. */
private fun reducedGalaxyGraphEdges(
    edges: Set<Pair<String, String>>
): List<Pair<String, String>> {
    val candidates = edges
        .filter { (source, target) -> source != target }
        .sortedWith(compareBy<Pair<String, String>>({ it.first }, { it.second }))
    if (candidates.size < 2) return candidates
    val adjacency = candidates.groupBy({ it.first }, { it.second })

    fun hasAlternatePath(excluded: Pair<String, String>): Boolean {
        val visited = hashSetOf(excluded.first)
        val queue = ArrayDeque<String>()
        adjacency[excluded.first].orEmpty()
            .filterNot { it == excluded.second }
            .forEach(queue::addLast)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current == excluded.second) return true
            if (!visited.add(current)) continue
            adjacency[current].orEmpty().forEach(queue::addLast)
        }
        return false
    }

    return candidates.filterNot(::hasAlternatePath)
}

data class GalaxyPoint(val x: Float, val y: Float)

internal const val GALAXY_LESSON_ORB_DIAMETER_DP = 72f
internal const val GALAXY_LESSON_CORRIDOR_WIDTH_MULTIPLIER = 1.15f

/** Uniform themed corridor behind one lesson's complete phase route. */
internal fun galaxyLessonCorridorWidthDp(
    orbWidthDp: Float = GALAXY_LESSON_ORB_DIAMETER_DP
): Float = orbWidthDp.coerceAtLeast(0f) * GALAXY_LESSON_CORRIDOR_WIDTH_MULTIPLIER

data class GalaxyNodePosition(
    val nodeId: String,
    val lessonId: String,
    val kind: GalaxyOrbKind,
    val trackId: String,
    val grade: Int,
    val point: GalaxyPoint,
    val formulaPlacement: GalaxyFormulaNodePlacement? = null
)

data class GalaxyFormulaNodePlacement(
    val sphereKey: String,
    val pointId: String,
    val sequence: Int,
    val layerCode: String,
    val connectToPointId: String,
    val outgoingPath: GalaxyConnectionPath?,
    val rotationDegrees: Double,
    val mirrorHorizontal: Boolean,
    val mirrorVertical: Boolean
)

data class GalaxyTrackSpherePosition(
    val trackId: String,
    val grade: Int,
    val centre: GalaxyPoint,
    val radius: Float
)

data class GalaxyCheckpointPosition(
    val checkpointId: String,
    val targetLessonId: String,
    val point: GalaxyPoint
)

data class GalaxyConnection(
    val fromNodeId: String,
    val toNodeId: String
)

sealed interface GalaxyConnectionPath {
    data class Arc(
        val centre: GalaxyPoint,
        val radius: Float,
        val startAngleDegrees: Float,
        val sweepAngleDegrees: Float
    ) : GalaxyConnectionPath

    data class CubicBezier(
        val control1: GalaxyPoint,
        val control2: GalaxyPoint
    ) : GalaxyConnectionPath
}

/** Returns every incoming edge that the selected node transitively depends on. */
internal fun galaxyDependencyConnections(
    selectedNodeId: String,
    connections: Collection<GalaxyConnection>
): Set<GalaxyConnection> {
    val incomingByNode = connections.groupBy(GalaxyConnection::toNodeId)
    val pending = ArrayDeque<String>().apply { addLast(selectedNodeId) }
    val visitedNodes = hashSetOf<String>()
    val dependencyEdges = linkedSetOf<GalaxyConnection>()
    while (pending.isNotEmpty()) {
        val nodeId = pending.removeFirst()
        if (!visitedNodes.add(nodeId)) continue
        incomingByNode[nodeId].orEmpty().forEach { connection ->
            if (dependencyEdges.add(connection)) pending.addLast(connection.fromNodeId)
        }
    }
    return dependencyEdges
}

internal fun galaxyFormulaConnectionPath(
    source: GalaxyNodePosition,
    target: GalaxyNodePosition
): GalaxyConnectionPath? {
    val from = source.formulaPlacement ?: return null
    val to = target.formulaPlacement ?: return null
    if (from.sphereKey != to.sphereKey || to.sequence != from.sequence + 1) return null
    if (from.connectToPointId != to.pointId) return null
    return from.outgoingPath
}

private fun normalizeGalaxyAngle(angle: Double): Double = atan2(sin(angle), cos(angle))

/**
 * Rotates and, when useful, mirrors one complete deterministic shape so its
 * entry and exit orbs face the neighbouring sphere groups. Individual formula
 * points are never redistributed.
 */
internal fun orientedGalaxyFormulaGeometry(
    shapeNumber: Int,
    incomingAngleRadians: Double,
    outgoingAngleRadians: Double
): GalaxyFormulaGeometry {
    data class Candidate(
        val geometry: GalaxyFormulaGeometry,
        val score: Double
    )

    fun nodeAngle(node: GalaxyNodeGeometry): Double = atan2(
        node.y - GALAXY_FORMULA_CENTER,
        node.x - GALAXY_FORMULA_CENTER
    )

    return listOf(false, true).map { mirrorHorizontal ->
        val mirrored = GalaxyGeometryGenerator.generate(
            shapeNumber = shapeNumber,
            mirrorHorizontal = mirrorHorizontal
        )
        val startOffset = normalizeGalaxyAngle(incomingAngleRadians - nodeAngle(mirrored.nodes.first()))
        val exitOffset = normalizeGalaxyAngle(outgoingAngleRadians - nodeAngle(mirrored.nodes.last()))
        val rotationRadians = normalizeGalaxyAngle(
            startOffset + normalizeGalaxyAngle(exitOffset - startOffset) / 2.0
        )
        val geometry = GalaxyGeometryGenerator.generate(
            shapeNumber = shapeNumber,
            rotationDegrees = Math.toDegrees(rotationRadians),
            mirrorHorizontal = mirrorHorizontal
        )
        val startError = normalizeGalaxyAngle(nodeAngle(geometry.nodes.first()) - incomingAngleRadians)
        val exitError = normalizeGalaxyAngle(nodeAngle(geometry.nodes.last()) - outgoingAngleRadians)
        Candidate(
            geometry = geometry,
            score = startError * startError + exitError * exitError
        )
    }.minWith(compareBy<Candidate>({ it.score }, { it.geometry.transform.mirrorHorizontal })).geometry
}

data class GalaxyCategoryLayout(
    val width: Float,
    val height: Float,
    val startPoint: GalaxyPoint,
    val nodes: List<GalaxyNodePosition>,
    val checkpointNodes: List<GalaxyCheckpointPosition>,
    val trackSpheres: List<GalaxyTrackSpherePosition>,
    val connections: List<GalaxyConnection>,
    val checkpointConnections: List<GalaxyConnection>,
    val startConnections: List<GalaxyConnection>,
    val connectionPaths: Map<GalaxyConnection, GalaxyConnectionPath>
)

data class GalaxyUniverseLayout(
    val centre: GalaxyPoint,
    val categoryCentres: Map<String, GalaxyPoint>
)

object GalaxyLayoutEngine {
    private const val CONTENT_MARGIN = 86f
    private const val SPHERE_GAP = 58f
    private const val START_CLEARANCE = 110f
    private const val STORY_SPHERE_DISTANCE = 340f
    private const val GOLDEN_ANGLE = 2.3999632

    fun layoutUniverse(categoryIds: List<String>, width: Float, height: Float): GalaxyUniverseLayout {
        val centre = GalaxyPoint(width / 2f, height / 2f)
        if (categoryIds.isEmpty()) return GalaxyUniverseLayout(centre, emptyMap())
        if (width >= height * 1.55f) {
            val horizontalRange = (width - 116f).coerceAtLeast(1f)
            val positions = categoryIds.mapIndexed { index, id ->
                var x = 58f + horizontalRange * (index + 0.5f) / categoryIds.size
                if (abs(x - centre.x) < 100f) {
                    x += if (x < centre.x) -36f else 36f
                }
                id to GalaxyPoint(
                    x = x.coerceIn(58f, width - 58f),
                    y = if (index % 2 == 0) 58f else height - 70f
                )
            }.toMap(linkedMapOf())
            return GalaxyUniverseLayout(centre, positions)
        }
        val baseRadius = minOf(width, height) * if (categoryIds.size <= 4) 0.31f else 0.37f
        val positions = linkedMapOf<String, GalaxyPoint>()
        categoryIds.forEachIndexed { index, id ->
            var bestCandidate = centre
            var bestClearance = -1f
            for (attempt in 0 until 72) {
                val angle = -PI / 2.0 + index * GOLDEN_ANGLE + attempt * 0.43
                val radialDistance = baseRadius * (0.82f + (attempt % 7) * 0.12f)
                val candidate = GalaxyPoint(
                    x = (centre.x + cos(angle).toFloat() * radialDistance).coerceIn(58f, width - 58f),
                    y = (centre.y + sin(angle).toFloat() * radialDistance).coerceIn(58f, height - 70f)
                )
                val clearance = positions.values.minOfOrNull { placed ->
                    hypot(candidate.x - placed.x, candidate.y - placed.y)
                } ?: Float.MAX_VALUE
                if (clearance > bestClearance) {
                    bestCandidate = candidate
                    bestClearance = clearance
                }
                if (clearance >= 108f) break
            }
            positions[id] = bestCandidate
        }
        return GalaxyUniverseLayout(centre, positions)
    }

    fun layoutCategory(
        tracks: List<GalaxyTrackUi>,
        checkpoints: List<GalaxyCheckpointNodeUi> = emptyList()
    ): GalaxyCategoryLayout {
        data class TrackInstance(
            val track: GalaxyTrackUi,
            val grade: Int,
            val lessons: List<GalaxyLessonNodeUi>,
            val radius: Float,
            val segmentIndex: Int,
            val formulaGeometry: GalaxyFormulaGeometry?
        )
        data class RawSphere(val instance: TrackInstance, val centre: GalaxyPoint)

        val unorderedInstances = tracks.flatMap { track ->
            val lessonGroups = if (isGalaxyStoryTrack(track.id) && track.lessons.isNotEmpty()) {
                listOf(track.lessons.minOf { it.grade } to track.lessons)
            } else {
                track.lessons.groupBy { it.grade }.toSortedMap().toList()
            }
            lessonGroups.flatMap { (grade, lessons) ->
                val orderedLessons = orderGalaxyLessonsForSphere(lessons)
                val segments = if (isGalaxyStoryTrack(track.id)) {
                    listOf(orderedLessons)
                } else {
                    splitGalaxyFormulaSegments(orderedLessons) { lesson -> galaxyOrbNodes(lesson).size }
                }
                segments.mapIndexed { segmentIndex, segmentLessons ->
                    val orbCount = segmentLessons.sumOf { lesson -> galaxyOrbNodes(lesson).size }
                    val formulaGeometry = if (isGalaxyStoryTrack(track.id)) {
                        null
                    } else {
                        val shapeNumber = requireNotNull(galaxyFormulaShapeNumber(orbCount)) {
                            "Lesson sphere ${track.id} grade $grade segment $segmentIndex has " +
                                "$orbCount nodes; exact Galaxy shapes require 3..60 in steps of 3"
                        }
                        GalaxyGeometryGenerator.generate(shapeNumber)
                    }
                    TrackInstance(
                        track = track,
                        grade = grade,
                        lessons = segmentLessons,
                        radius = if (isGalaxyStoryTrack(track.id)) {
                            storySphereRadius(orbCount)
                        } else {
                            requireNotNull(formulaGeometry).shape.containerRadius() + 56f
                        },
                        segmentIndex = segmentIndex,
                        formulaGeometry = formulaGeometry
                    )
                }
            }
        }
        if (unorderedInstances.isEmpty()) return emptyCategoryLayout()

        fun instanceKey(instance: TrackInstance): String =
            "${instance.track.id}:${instance.grade}:${instance.segmentIndex}"
        val instanceKeyByLessonId = unorderedInstances.flatMap { instance ->
            instance.lessons.map { lesson -> lesson.id to instanceKey(instance) }
        }.toMap()
        val curriculumDependencyEdges = unorderedInstances.flatMap { target ->
            val targetKey = instanceKey(target)
            target.lessons.flatMap { lesson ->
                lesson.prerequisiteLessonIds.mapNotNull { prerequisiteId ->
                    val sourceKey = instanceKeyByLessonId[prerequisiteId] ?: return@mapNotNull null
                    (sourceKey to targetKey).takeIf { sourceKey != targetKey }
                }
            }
        }.toSet()
        val segmentContinuityEdges = unorderedInstances
            .groupBy { instance -> "${instance.track.id}:${instance.grade}" }
            .values
            .flatMap { sameTrackInstances ->
                sameTrackInstances.sortedBy(TrackInstance::segmentIndex).zipWithNext { source, target ->
                    instanceKey(source) to instanceKey(target)
                }
            }
            .toSet()
        val dependencyEdges = curriculumDependencyEdges + segmentContinuityEdges
        val originalIndex = unorderedInstances.withIndex().associate { (index, instance) ->
            instanceKey(instance) to index
        }
        val indegree = unorderedInstances.associate { instance ->
            val key = instanceKey(instance)
            key to dependencyEdges.count { it.second == key }
        }.toMutableMap()
        val dependants = dependencyEdges.groupBy({ it.first }, { it.second })
        val instanceByKey = unorderedInstances.associateBy(::instanceKey)
        val readyInstances = PriorityQueue(
            compareBy<TrackInstance>({ originalIndex.getValue(instanceKey(it)) }, { instanceKey(it) })
        )
        unorderedInstances.filterTo(readyInstances) { indegree.getValue(instanceKey(it)) == 0 }
        val instances = mutableListOf<TrackInstance>()
        while (readyInstances.isNotEmpty()) {
            val instance = readyInstances.remove()
            instances += instance
            dependants[instanceKey(instance)].orEmpty().forEach { dependantKey ->
                val remaining = indegree.getValue(dependantKey) - 1
                indegree[dependantKey] = remaining
                if (remaining == 0) instanceByKey[dependantKey]?.let(readyInstances::add)
            }
        }
        if (instances.size != unorderedInstances.size) {
            val orderedKeys = instances.mapTo(hashSetOf(), ::instanceKey)
            instances += unorderedInstances.filterNot { instanceKey(it) in orderedKeys }
        }

        val rawSpheres = mutableListOf<RawSphere>()
        val rawSphereByInstanceKey = mutableMapOf<String, RawSphere>()
        val predecessorKeys = dependencyEdges.groupBy({ it.second }, { it.first })
        val successorKeys = dependencyEdges.groupBy({ it.first }, { it.second })
        val instanceIndexByKey = instances.withIndex().associate { (index, instance) ->
            instanceKey(instance) to index
        }
        val rootKeys = instances
            .filter { predecessorKeys[instanceKey(it)].isNullOrEmpty() }
            .map(::instanceKey)
        val rootIndexByKey = rootKeys.withIndex().associate { (index, key) -> key to index }
        val angleOffsets = listOf(0.0, 0.16, -0.16, 0.32, -0.32, 0.50, -0.50, 0.70, -0.70)
        val dependencyDepthByKey = mutableMapOf<String, Int>()
        instances.forEach { instance ->
            val key = instanceKey(instance)
            dependencyDepthByKey[key] = predecessorKeys[key].orEmpty()
                .mapNotNull(dependencyDepthByKey::get)
                .maxOrNull()
                ?.plus(1)
                ?: 0
        }

        fun pointToSegmentDistance(
            point: GalaxyPoint,
            start: GalaxyPoint,
            end: GalaxyPoint
        ): Float {
            val dx = end.x - start.x
            val dy = end.y - start.y
            val lengthSquared = dx * dx + dy * dy
            if (lengthSquared <= 0.001f) return hypot(point.x - start.x, point.y - start.y)
            val projection = (
                (point.x - start.x) * dx + (point.y - start.y) * dy
                ) / lengthSquared
            val t = projection.coerceIn(0f, 1f)
            return hypot(point.x - (start.x + dx * t), point.y - (start.y + dy * t))
        }

        fun obstructionCount(
            sources: List<Pair<String?, GalaxyPoint>>,
            target: GalaxyPoint
        ): Int = sources.sumOf { (sourceKey, source) ->
            rawSpheres.count { placed ->
                instanceKey(placed.instance) != sourceKey &&
                    pointToSegmentDistance(placed.centre, source, target) < placed.instance.radius + 28f
            }
        }

        fun segmentsCross(
            firstStart: GalaxyPoint,
            firstEnd: GalaxyPoint,
            secondStart: GalaxyPoint,
            secondEnd: GalaxyPoint
        ): Boolean {
            fun side(start: GalaxyPoint, end: GalaxyPoint, point: GalaxyPoint): Float =
                (end.x - start.x) * (point.y - start.y) -
                    (end.y - start.y) * (point.x - start.x)
            val firstA = side(firstStart, firstEnd, secondStart)
            val firstB = side(firstStart, firstEnd, secondEnd)
            val secondA = side(secondStart, secondEnd, firstStart)
            val secondB = side(secondStart, secondEnd, firstEnd)
            return firstA * firstB < -0.001f && secondA * secondB < -0.001f
        }

        fun crossingCount(
            sources: List<Pair<String?, GalaxyPoint>>,
            targetKey: String,
            target: GalaxyPoint
        ): Int {
            val placedEdges = dependencyEdges.mapNotNull { (sourceKey, existingTargetKey) ->
                val source = rawSphereByInstanceKey[sourceKey] ?: return@mapNotNull null
                val existingTarget = rawSphereByInstanceKey[existingTargetKey] ?: return@mapNotNull null
                Triple(sourceKey to existingTargetKey, source.centre, existingTarget.centre)
            }
            return sources.sumOf { (sourceKey, source) ->
                placedEdges.count { (edgeKeys, edgeStart, edgeEnd) ->
                    sourceKey != null &&
                        sourceKey != edgeKeys.first &&
                        sourceKey != edgeKeys.second &&
                        targetKey != edgeKeys.first &&
                        targetKey != edgeKeys.second &&
                        segmentsCross(source, target, edgeStart, edgeEnd)
                }
            }
        }

        instances.forEachIndexed { index, instance ->
            val key = instanceKey(instance)
            val predecessorKeyList = predecessorKeys[key].orEmpty()
            val predecessors = predecessorKeyList
                .mapNotNull(rawSphereByInstanceKey::get)
            val isRoot = predecessors.isEmpty()
            val anchor = predecessors.map(RawSphere::centre).takeIf { it.isNotEmpty() }
                ?.averagePoint() ?: GalaxyPoint(0f, 0f)
            val baseAngle = if (isRoot) {
                val rootIndex = rootIndexByKey[key] ?: index
                val rootCount = rootKeys.size.coerceAtLeast(1)
                val rootSpacing = minOf(0.60, PI / (rootCount + 1.0))
                -PI / 2.0 + (rootIndex - (rootCount - 1) / 2.0) * rootSpacing
            } else {
                val outwardAngle = atan2(anchor.y, anchor.x).toDouble()
                val branchOffsets = predecessorKeyList.mapNotNull { predecessorKey ->
                    val siblings = successorKeys[predecessorKey].orEmpty()
                        .sortedBy { successorKey -> instanceIndexByKey[successorKey] ?: Int.MAX_VALUE }
                    val siblingIndex = siblings.indexOf(key).takeIf { it >= 0 } ?: return@mapNotNull null
                    (siblingIndex - (siblings.size - 1) / 2.0) * 0.44
                }
                val branchOffset = branchOffsets.average().takeIf { it.isFinite() } ?: 0.0
                val followsSinglePath = predecessorKeyList.size == 1 &&
                    successorKeys[predecessorKeyList.single()].orEmpty().size == 1
                val organicTurn = if (followsSinglePath) {
                    val turns = listOf(0.36, -0.30, -0.38, 0.34)
                    val depth = dependencyDepthByKey[key] ?: 1
                    turns[(depth - 1).coerceAtLeast(0) % turns.size]
                } else {
                    0.0
                }
                outwardAngle + branchOffset + organicTurn
            }
            val baseDistance = if (isRoot) {
                instance.radius + START_CLEARANCE + 180f +
                    if (isGalaxyStoryTrack(instance.track.id)) STORY_SPHERE_DISTANCE else 0f
            } else {
                predecessors.maxOf { it.instance.radius } + instance.radius + SPHERE_GAP + 110f
            }
            val routeSources = predecessors
                .map { predecessor -> instanceKey(predecessor.instance) to predecessor.centre }
                .ifEmpty { listOf(null to GalaxyPoint(0f, 0f)) }
            var candidate: GalaxyPoint? = null
            var candidateScore = Double.POSITIVE_INFINITY
            for (distanceStep in 0 until 28) {
                val distance = baseDistance + distanceStep * 82f
                angleOffsets.forEach { angleOffset ->
                    val angle = baseAngle + angleOffset
                    val proposed = GalaxyPoint(
                        x = anchor.x + cos(angle).toFloat() * distance,
                        y = anchor.y + sin(angle).toFloat() * distance
                    )
                    val overlapsStart = hypot(proposed.x, proposed.y) < instance.radius + START_CLEARANCE
                    val overlapsSphere = rawSpheres.any { placed ->
                        hypot(
                            proposed.x - placed.centre.x,
                            proposed.y - placed.centre.y
                        ) < instance.radius + placed.instance.radius + SPHERE_GAP
                    }
                    if (!overlapsStart && !overlapsSphere) {
                        val obstructions = obstructionCount(routeSources, proposed)
                        val crossings = crossingCount(routeSources, key, proposed)
                        val score = obstructions * 10_000.0 +
                            crossings * 5_000.0 +
                            abs(angleOffset) * 500.0 +
                            distanceStep * 18.0
                        if (score < candidateScore) {
                            candidate = proposed
                            candidateScore = score
                        }
                    }
                }
            }
            val resolvedCandidate = requireNotNull(candidate) {
                "Unable to place Galaxy sphere $key without overlapping another sphere"
            }
            RawSphere(instance, resolvedCandidate).also { sphere ->
                rawSpheres += sphere
                rawSphereByInstanceKey[key] = sphere
            }
        }

        val rawSphereByLessonId = rawSpheres.flatMap { sphere ->
            sphere.instance.lessons.map { it.id to sphere }
        }.toMap()
        val rawNodes = rawSpheres.flatMap { sphere ->
            val lessonOrbs = sphere.instance.lessons.flatMap { lesson ->
                galaxyOrbNodes(lesson).map { node -> lesson to node }
            }
            val sphereKey = instanceKey(sphere.instance)
            val incomingCentres = predecessorKeys[sphereKey].orEmpty()
                .mapNotNull(rawSphereByInstanceKey::get)
                .map(RawSphere::centre)
            val incomingPoint = incomingCentres.takeIf { it.isNotEmpty() }?.averagePoint()
                ?: GalaxyPoint(0f, 0f)
            val outgoingCentres = successorKeys[sphereKey].orEmpty()
                .mapNotNull(rawSphereByInstanceKey::get)
                .map(RawSphere::centre)
            val outgoingPoint = outgoingCentres.takeIf { it.isNotEmpty() }?.averagePoint()
                ?: GalaxyPoint(sphere.centre.x * 2f, sphere.centre.y * 2f)
            val entryAngle = atan2(
                incomingPoint.y - sphere.centre.y,
                incomingPoint.x - sphere.centre.x
            ).toDouble()
            val exitAngle = atan2(
                outgoingPoint.y - sphere.centre.y,
                outgoingPoint.x - sphere.centre.x
            ).toDouble()
            val formulaGeometry = sphere.instance.formulaGeometry
                ?.takeIf { geometry -> geometry.nodes.size == lessonOrbs.size }
                ?.let { geometry ->
                    orientedGalaxyFormulaGeometry(
                        shapeNumber = geometry.shape.number,
                        incomingAngleRadians = entryAngle,
                        outgoingAngleRadians = exitAngle
                    )
                }
            val positionedOrbs = if (isGalaxyStoryTrack(sphere.instance.track.id)) {
                storyThemePolygonPoints(
                    centre = sphere.centre,
                    themeCount = lessonOrbs.size,
                    sphereRadius = sphere.instance.radius,
                    entryAngle = entryAngle
                ).map { point -> point to null }
            } else {
                requireNotNull(formulaGeometry) {
                    "Formula geometry is required for non-story sphere $sphereKey"
                }
                val formulaConnections = formulaGeometry.connections.associateBy { it.fromPointId }
                fun GalaxyFormulaPoint.inSphere() = GalaxyPoint(
                    x = sphere.centre.x + (x - GALAXY_FORMULA_CENTER).toFloat(),
                    y = sphere.centre.y + (y - GALAXY_FORMULA_CENTER).toFloat()
                )
                formulaGeometry.nodes.map { geometryNode ->
                    val outgoingPath = when (val connection = formulaConnections[geometryNode.pointId]) {
                        is GalaxyFormulaConnection.Arc -> GalaxyConnectionPath.Arc(
                            centre = connection.centre.inSphere(),
                            radius = connection.radius.toFloat(),
                            startAngleDegrees = connection.startAngleDegrees.toFloat(),
                            sweepAngleDegrees = connection.sweepAngleDegrees.toFloat()
                        )
                        is GalaxyFormulaConnection.CubicBezier -> GalaxyConnectionPath.CubicBezier(
                            control1 = connection.control1.inSphere(),
                            control2 = connection.control2.inSphere()
                        )
                        null -> null
                    }
                    GalaxyFormulaPoint(geometryNode.x, geometryNode.y).inSphere() to GalaxyFormulaNodePlacement(
                        sphereKey = instanceKey(sphere.instance),
                        pointId = geometryNode.pointId,
                        sequence = geometryNode.sequence,
                        layerCode = geometryNode.layer.code,
                        connectToPointId = geometryNode.connectToId,
                        outgoingPath = outgoingPath,
                        rotationDegrees = formulaGeometry.transform.rotationDegrees,
                        mirrorHorizontal = formulaGeometry.transform.mirrorHorizontal,
                        mirrorVertical = formulaGeometry.transform.mirrorVertical
                    )
                }
            }
            lessonOrbs.mapIndexed { orbIndex, (lesson, node) ->
                GalaxyNodePosition(
                    nodeId = node.nodeId,
                    lessonId = lesson.id,
                    kind = node.orb.kind,
                    trackId = sphere.instance.track.id,
                    grade = lesson.grade,
                    point = positionedOrbs[orbIndex].first,
                    formulaPlacement = positionedOrbs[orbIndex].second
                )
            }
        }
        val rawNodeById = rawNodes.associateBy { it.nodeId }
        val rawLessonById = rawSpheres
            .flatMap { sphere -> sphere.instance.lessons }
            .associateBy { it.id }
        fun firstNodeId(lessonId: String): String? =
            rawLessonById[lessonId]?.let(::galaxyOrbNodes)?.firstOrNull()?.nodeId
        fun lastTestNodeId(lessonId: String): String? =
            rawLessonById[lessonId]
                ?.let(::galaxyOrbNodes)
                ?.lastOrNull { it.orb.kind == GalaxyOrbKind.TEST }
                ?.nodeId

        val rawCheckpointNodes = checkpoints.mapNotNull { checkpoint ->
            val target = firstNodeId(checkpoint.targetLessonId)?.let(rawNodeById::get)
                ?: return@mapNotNull null
            val targetSphere = rawSphereByLessonId[checkpoint.targetLessonId] ?: return@mapNotNull null
            val sourcePoints = checkpoint.prerequisiteLessonIds.mapNotNull { prerequisiteId ->
                lastTestNodeId(prerequisiteId)?.let(rawNodeById::get)?.point
            }
            val source = sourcePoints.takeIf { it.isNotEmpty() }?.averagePoint() ?: GalaxyPoint(0f, 0f)
            var directionX = source.x - targetSphere.centre.x
            var directionY = source.y - targetSphere.centre.y
            var directionLength = hypot(directionX, directionY)
            if (directionLength < 1f) {
                directionX = -targetSphere.centre.x
                directionY = -targetSphere.centre.y
                directionLength = hypot(directionX, directionY).coerceAtLeast(1f)
            }
            directionX /= directionLength
            directionY /= directionLength
            var point = GalaxyPoint(
                x = targetSphere.centre.x + directionX * (targetSphere.instance.radius + 78f),
                y = targetSphere.centre.y + directionY * (targetSphere.instance.radius + 78f)
            )
            var attempts = 0
            while (attempts < 8 && (
                    rawNodes.any { hypot(point.x - it.point.x, point.y - it.point.y) < 92f } ||
                        hypot(point.x, point.y) < 88f
                    )
            ) {
                val rotation = (attempts + 1) * PI / 9.0
                val rotatedX = directionX * cos(rotation).toFloat() - directionY * sin(rotation).toFloat()
                val rotatedY = directionX * sin(rotation).toFloat() + directionY * cos(rotation).toFloat()
                point = GalaxyPoint(
                    x = targetSphere.centre.x + rotatedX * (targetSphere.instance.radius + 84f),
                    y = targetSphere.centre.y + rotatedY * (targetSphere.instance.radius + 84f)
                )
                attempts += 1
            }
            GalaxyCheckpointPosition(checkpoint.id, checkpoint.targetLessonId, point)
        }

        val minimumX = minOf(
            -48f,
            rawSpheres.minOf { it.centre.x - it.instance.radius },
            rawCheckpointNodes.minOfOrNull { it.point.x - 42f } ?: Float.MAX_VALUE
        )
        val maximumX = maxOf(
            48f,
            rawSpheres.maxOf { it.centre.x + it.instance.radius },
            rawCheckpointNodes.maxOfOrNull { it.point.x + 42f } ?: -Float.MAX_VALUE
        )
        val minimumY = minOf(
            -48f,
            rawSpheres.minOf { it.centre.y - it.instance.radius },
            rawCheckpointNodes.minOfOrNull { it.point.y - 42f } ?: Float.MAX_VALUE
        )
        val maximumY = maxOf(
            48f,
            rawSpheres.maxOf { it.centre.y + it.instance.radius },
            rawCheckpointNodes.maxOfOrNull { it.point.y + 42f } ?: -Float.MAX_VALUE
        )
        val shiftX = CONTENT_MARGIN - minimumX
        val shiftY = CONTENT_MARGIN - minimumY
        fun GalaxyPoint.shifted() = GalaxyPoint(x + shiftX, y + shiftY)
        fun GalaxyConnectionPath.shifted(): GalaxyConnectionPath = when (this) {
            is GalaxyConnectionPath.Arc -> copy(centre = centre.shifted())
            is GalaxyConnectionPath.CubicBezier -> copy(
                control1 = control1.shifted(),
                control2 = control2.shifted()
            )
        }

        val nodes = rawNodes.map { node ->
            node.copy(
                point = node.point.shifted(),
                formulaPlacement = node.formulaPlacement?.let { placement ->
                    placement.copy(outgoingPath = placement.outgoingPath?.shifted())
                }
            )
        }
        val checkpointNodes = rawCheckpointNodes.map { it.copy(point = it.point.shifted()) }
        val trackSpheres = rawSpheres.map { sphere ->
            GalaxyTrackSpherePosition(
                trackId = sphere.instance.track.id,
                grade = sphere.instance.grade,
                centre = sphere.centre.shifted(),
                radius = sphere.instance.radius
            )
        }
        val visibleLessonIds = nodes.mapTo(linkedSetOf()) { it.lessonId }
        val checkpointByTargetId = checkpoints.associateBy { it.targetLessonId }
        val checkpointNodeIds = checkpointNodes.mapTo(linkedSetOf()) { it.checkpointId }
        val lessons = rawSpheres.flatMap { it.instance.lessons }
        fun sphereKey(lessonId: String): String? =
            rawSphereByLessonId[lessonId]?.let { sphere -> instanceKey(sphere.instance) }
        val firstLessonIdBySphere = rawSpheres.associate { sphere ->
            instanceKey(sphere.instance) to sphere.instance.lessons.first().id
        }
        val lastLessonIdBySphere = rawSpheres.associate { sphere ->
            instanceKey(sphere.instance) to sphere.instance.lessons.last().id
        }
        val phaseConnections = lessons.flatMap { lesson ->
            galaxyOrbNodes(lesson).zipWithNext { from, to ->
                GalaxyConnection(from.nodeId, to.nodeId)
            }
        }
        val internalPrerequisiteConnections = rawSpheres.flatMap { sphere ->
            val sphereLessonIds = sphere.instance.lessons.mapTo(linkedSetOf()) { it.id }
            val rawEdges = sphere.instance.lessons.flatMap { targetLesson ->
                if (targetLesson.id in checkpointByTargetId || targetLesson.portalDestination != null) {
                    emptyList()
                } else {
                    targetLesson.prerequisiteLessonIds
                        .filter { it in sphereLessonIds }
                        .map { prerequisiteId -> prerequisiteId to targetLesson.id }
                }
            }.toSet()
            reducedGalaxyGraphEdges(rawEdges).mapNotNull { (sourceLessonId, targetLessonId) ->
                val fromNodeId = lastTestNodeId(sourceLessonId) ?: return@mapNotNull null
                val toNodeId = firstNodeId(targetLessonId) ?: return@mapNotNull null
                GalaxyConnection(fromNodeId, toNodeId)
            }
        }
        val rawSphereEdges = (rawSpheres.flatMap { targetSphere ->
            val targetGroup = instanceKey(targetSphere.instance)
            val targetLesson = targetSphere.instance.lessons.first()
            if (targetLesson.id in checkpointByTargetId || targetLesson.portalDestination != null) {
                emptyList()
            } else {
                targetLesson.prerequisiteLessonIds.mapNotNull { prerequisiteId ->
                    if (prerequisiteId !in visibleLessonIds) return@mapNotNull null
                    val sourceGroup = sphereKey(prerequisiteId) ?: return@mapNotNull null
                    (sourceGroup to targetGroup).takeIf { sourceGroup != targetGroup }
                }
            }
        } + segmentContinuityEdges).toSet()
        val sphereBoundaryConnections = reducedGalaxyGraphEdges(rawSphereEdges).mapNotNull { (sourceGroup, targetGroup) ->
            val sourceLessonId = lastLessonIdBySphere[sourceGroup] ?: return@mapNotNull null
            val targetLessonId = firstLessonIdBySphere[targetGroup] ?: return@mapNotNull null
            val fromNodeId = lastTestNodeId(sourceLessonId) ?: return@mapNotNull null
            val toNodeId = firstNodeId(targetLessonId) ?: return@mapNotNull null
            GalaxyConnection(fromNodeId, toNodeId)
        }
        val connections = (
            phaseConnections + internalPrerequisiteConnections + sphereBoundaryConnections
            ).distinct()
        val checkpointConnections = checkpoints.flatMap { checkpoint ->
            if (checkpoint.id !in checkpointNodeIds) return@flatMap emptyList()
            checkpoint.prerequisiteLessonIds.mapNotNull { prerequisiteId ->
                if (prerequisiteId !in visibleLessonIds) return@mapNotNull null
                lastTestNodeId(prerequisiteId)?.let { GalaxyConnection(it, checkpoint.id) }
            } + listOfNotNull(
                firstNodeId(checkpoint.targetLessonId)?.let { GalaxyConnection(checkpoint.id, it) }
            )
        }.distinct()
        val startConnections = buildList {
            rawSpheres.forEach { sphere ->
                val sphereLessonIds = sphere.instance.lessons.mapTo(linkedSetOf()) { it.id }
                val rootLesson = sphere.instance.lessons.first()
                val isGraphRoot = rootLesson.prerequisiteLessonIds.none { it in sphereLessonIds }
                val hasNoPrerequisites = rootLesson.prerequisiteLessonIds.isEmpty()
                if (isGraphRoot &&
                    sphere.instance.segmentIndex == 0 &&
                    rootLesson.id !in checkpointByTargetId &&
                    rootLesson.portalDestination == null &&
                    hasNoPrerequisites
                ) {
                    firstNodeId(rootLesson.id)?.let { destinationId ->
                        add(GalaxyConnection(GALAXY_START_NODE_ID, destinationId))
                    }
                }
            }
            checkpoints.filter { it.prerequisiteLessonIds.isEmpty() && it.id in checkpointNodeIds }
                .forEach { checkpoint -> add(GalaxyConnection(GALAXY_START_NODE_ID, checkpoint.id)) }
        }.distinct()
        val nodesById = nodes.associateBy(GalaxyNodePosition::nodeId)
        val connectionPaths = connections.mapNotNull { connection ->
            val source = nodesById[connection.fromNodeId] ?: return@mapNotNull null
            val target = nodesById[connection.toNodeId] ?: return@mapNotNull null
            galaxyFormulaConnectionPath(source, target)?.let { path -> connection to path }
        }.toMap()

        return GalaxyCategoryLayout(
            width = max(420f, maximumX - minimumX + CONTENT_MARGIN * 2f),
            height = max(320f, maximumY - minimumY + CONTENT_MARGIN * 2f),
            startPoint = GalaxyPoint(shiftX, shiftY),
            nodes = nodes,
            checkpointNodes = checkpointNodes,
            trackSpheres = trackSpheres,
            connections = connections,
            checkpointConnections = checkpointConnections,
            startConnections = startConnections,
            connectionPaths = connectionPaths
        )
    }

    private fun emptyCategoryLayout() = GalaxyCategoryLayout(
        width = 420f,
        height = 320f,
        startPoint = GalaxyPoint(210f, 160f),
        nodes = emptyList(),
        checkpointNodes = emptyList(),
        trackSpheres = emptyList(),
        connections = emptyList(),
        checkpointConnections = emptyList(),
        startConnections = emptyList(),
        connectionPaths = emptyMap()
    )

    private fun storySphereRadius(themeCount: Int): Float {
        if (themeCount <= 0) return 158f
        val minimumPolygonRadius = 110f
        val minimumThemeGap = GALAXY_LESSON_ORB_DIAMETER_DP * 1.35f
        val polygonRadius = when (themeCount) {
            1 -> 0f
            2 -> minimumPolygonRadius
            else -> max(
                minimumPolygonRadius,
                (minimumThemeGap / (2f * sin(PI / themeCount).toFloat()))
            )
        }
        return max(158f, polygonRadius + 56f)
    }

    private fun storyThemePolygonPoints(
        centre: GalaxyPoint,
        themeCount: Int,
        sphereRadius: Float,
        entryAngle: Double
    ): List<GalaxyPoint> {
        if (themeCount <= 0) return emptyList()
        if (themeCount == 1) return listOf(centre)
        val polygonRadius = (sphereRadius - 56f).coerceAtLeast(72f)
        return List(themeCount) { index ->
            val angle = entryAngle + 2.0 * PI * index / themeCount
            GalaxyPoint(
                x = centre.x + cos(angle).toFloat() * polygonRadius,
                y = centre.y + sin(angle).toFloat() * polygonRadius
            )
        }
    }

    private fun List<GalaxyPoint>.averagePoint(): GalaxyPoint = GalaxyPoint(
        x = sumOf { it.x.toDouble() }.toFloat() / size,
        y = sumOf { it.y.toDouble() }.toFloat() / size
    )
}
