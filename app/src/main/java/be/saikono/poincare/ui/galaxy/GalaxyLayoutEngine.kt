package be.saikono.poincare.ui.galaxy

import java.util.ArrayDeque
import java.util.PriorityQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

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

/**
 * Redistributes the existing ordered route without changing its shape or endpoints.
 * Gaps inside a lesson are only modestly shorter than transitions between lessons,
 * keeping one continuous journey instead of producing visibly isolated clusters.
 */
internal fun coherentGalaxyLessonOrbPoints(
    basePoints: List<GalaxyPoint>,
    lessonIds: List<String>,
    intraLessonGapWeight: Float = 0.86f
): List<GalaxyPoint> {
    if (basePoints.size < 3 || basePoints.size != lessonIds.size) return basePoints
    val segmentLengths = basePoints.zipWithNext { from, to ->
        hypot(to.x - from.x, to.y - from.y)
    }
    val routeLength = segmentLengths.sum()
    if (routeLength < 1f) return basePoints

    val internalWeight = intraLessonGapWeight.coerceIn(0.72f, 0.98f)
    val gapWeights = lessonIds.zipWithNext { fromLesson, toLesson ->
        if (fromLesson == toLesson) internalWeight else 1f
    }
    val weightedUnit = routeLength / gapWeights.sum()
    val targetDistances = buildList {
        add(0f)
        var distance = 0f
        gapWeights.forEach { weight ->
            distance += weightedUnit * weight
            add(distance)
        }
    }

    var segmentIndex = 0
    var segmentStartDistance = 0f
    return targetDistances.mapIndexed { index, targetDistance ->
        when (index) {
            0 -> basePoints.first()
            targetDistances.lastIndex -> basePoints.last()
            else -> {
                while (
                    segmentIndex < segmentLengths.lastIndex &&
                    targetDistance > segmentStartDistance + segmentLengths[segmentIndex]
                ) {
                    segmentStartDistance += segmentLengths[segmentIndex]
                    segmentIndex += 1
                }
                val segmentLength = segmentLengths[segmentIndex]
                if (segmentLength < 0.001f) {
                    basePoints[segmentIndex + 1]
                } else {
                    val progress = ((targetDistance - segmentStartDistance) / segmentLength).coerceIn(0f, 1f)
                    val from = basePoints[segmentIndex]
                    val to = basePoints[segmentIndex + 1]
                    GalaxyPoint(
                        x = from.x + (to.x - from.x) * progress,
                        y = from.y + (to.y - from.y) * progress
                    )
                }
            }
        }
    }
}

/** Builds a rounded contour around the actual orb positions of one lesson. */
internal fun galaxyLessonGroupOutline(
    nodePoints: List<GalaxyPoint>,
    orbPadding: Float,
    samplesPerOrb: Int = 16
): List<GalaxyPoint> {
    if (nodePoints.isEmpty()) return emptyList()
    val sampleCount = samplesPerOrb.coerceAtLeast(8)
    val sampledPoints = nodePoints.flatMap { point ->
        List(sampleCount) { sample ->
            val angle = 2.0 * PI * sample / sampleCount
            GalaxyPoint(
                x = point.x + cos(angle).toFloat() * orbPadding,
                y = point.y + sin(angle).toFloat() * orbPadding
            )
        }
    }.distinct()
    if (sampledPoints.size <= 2) return sampledPoints

    val sorted = sampledPoints.sortedWith(compareBy<GalaxyPoint>({ it.x }, { it.y }))
    fun cross(origin: GalaxyPoint, first: GalaxyPoint, second: GalaxyPoint): Float =
        (first.x - origin.x) * (second.y - origin.y) -
            (first.y - origin.y) * (second.x - origin.x)

    fun appendHull(points: Iterable<GalaxyPoint>): MutableList<GalaxyPoint> {
        val hull = mutableListOf<GalaxyPoint>()
        points.forEach { point ->
            while (hull.size >= 2 && cross(hull[hull.lastIndex - 1], hull.last(), point) <= 0f) {
                hull.removeAt(hull.lastIndex)
            }
            hull += point
        }
        return hull
    }

    val lower = appendHull(sorted)
    val upper = appendHull(sorted.asReversed())
    return lower.dropLast(1) + upper.dropLast(1)
}

data class GalaxyNodePosition(
    val nodeId: String,
    val lessonId: String,
    val kind: GalaxyOrbKind,
    val trackId: String,
    val grade: Int,
    val point: GalaxyPoint
)

data class GalaxyTrackLabelPosition(
    val trackId: String,
    val grade: Int,
    val point: GalaxyPoint
)

data class GalaxyGradeSectionPosition(
    val grade: Int,
    val point: GalaxyPoint,
    val width: Float,
    val height: Float
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
    val fromLessonId: String,
    val toLessonId: String
)

/** Returns every incoming edge that the selected node transitively depends on. */
internal fun galaxyDependencyConnections(
    selectedNodeId: String,
    connections: Collection<GalaxyConnection>
): Set<GalaxyConnection> {
    val incomingByNode = connections.groupBy(GalaxyConnection::toLessonId)
    val pending = ArrayDeque<String>().apply { addLast(selectedNodeId) }
    val visitedNodes = hashSetOf<String>()
    val dependencyEdges = linkedSetOf<GalaxyConnection>()
    while (pending.isNotEmpty()) {
        val nodeId = pending.removeFirst()
        if (!visitedNodes.add(nodeId)) continue
        incomingByNode[nodeId].orEmpty().forEach { connection ->
            if (dependencyEdges.add(connection)) pending.addLast(connection.fromLessonId)
        }
    }
    return dependencyEdges
}

data class GalaxyCategoryLayout(
    val width: Float,
    val height: Float,
    val startPoint: GalaxyPoint,
    val nodes: List<GalaxyNodePosition>,
    val checkpointNodes: List<GalaxyCheckpointPosition>,
    val trackLabels: List<GalaxyTrackLabelPosition>,
    val trackSpheres: List<GalaxyTrackSpherePosition>,
    val gradeSections: List<GalaxyGradeSectionPosition>,
    val connections: List<GalaxyConnection>,
    val checkpointConnections: List<GalaxyConnection>,
    val startConnections: List<GalaxyConnection>
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
            val radius: Float
        )
        data class RawSphere(val instance: TrackInstance, val centre: GalaxyPoint)

        val unorderedInstances = tracks.flatMap { track ->
            val lessonGroups = if (isGalaxyStoryTrack(track.id) && track.lessons.isNotEmpty()) {
                listOf(track.lessons.minOf { it.grade } to track.lessons)
            } else {
                track.lessons.groupBy { it.grade }.toSortedMap().toList()
            }
            lessonGroups.map { (grade, lessons) ->
                val orderedLessons = orderGalaxyLessonsForSphere(lessons)
                TrackInstance(
                    track = track,
                    grade = grade,
                    lessons = orderedLessons,
                    radius = sphereRadius(orderedLessons.sumOf { lesson -> galaxyOrbKinds(lesson).size })
                )
            }
        }
        if (unorderedInstances.isEmpty()) return emptyCategoryLayout()

        fun instanceKey(instance: TrackInstance): String = "${instance.track.id}:${instance.grade}"
        val instanceKeyByLessonId = unorderedInstances.flatMap { instance ->
            instance.lessons.map { lesson -> lesson.id to instanceKey(instance) }
        }.toMap()
        val dependencyEdges = unorderedInstances.flatMap { target ->
            val targetKey = instanceKey(target)
            target.lessons.flatMap { lesson ->
                lesson.prerequisiteLessonIds.mapNotNull { prerequisiteId ->
                    val sourceKey = instanceKeyByLessonId[prerequisiteId] ?: return@mapNotNull null
                    (sourceKey to targetKey).takeIf { sourceKey != targetKey }
                }
            }
        }.toSet()
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
        instances.forEachIndexed { index, instance ->
            val predecessors = predecessorKeys[instanceKey(instance)].orEmpty()
                .mapNotNull(rawSphereByInstanceKey::get)
            val predecessorCentre = predecessors.map { it.centre }
                .takeIf { it.isNotEmpty() }
                ?.averagePoint()
            var radialDistance = 330f + sqrt(index.toFloat()) * 150f +
                if (isGalaxyStoryTrack(instance.track.id)) STORY_SPHERE_DISTANCE else 0f
            if (predecessors.isNotEmpty()) {
                radialDistance = maxOf(
                    radialDistance,
                    predecessors.maxOf { predecessor ->
                        hypot(predecessor.centre.x, predecessor.centre.y) +
                            predecessor.instance.radius + instance.radius + SPHERE_GAP
                    }
                )
            }
            val angle = predecessorCentre?.let { centre ->
                atan2(centre.y, centre.x).toDouble() + if (index % 2 == 0) 0.58 else -0.58
            } ?: (-PI / 2.0 + index * GOLDEN_ANGLE)
            var candidate: GalaxyPoint
            var attempts = 0
            do {
                candidate = GalaxyPoint(
                    x = cos(angle).toFloat() * radialDistance,
                    y = sin(angle).toFloat() * radialDistance
                )
                radialDistance += 54f
                attempts += 1
            } while (
                attempts < 18 && (
                    hypot(candidate.x, candidate.y) < instance.radius + START_CLEARANCE ||
                        rawSpheres.any { placed ->
                            hypot(
                                candidate.x - placed.centre.x,
                                candidate.y - placed.centre.y
                            ) < instance.radius + placed.instance.radius + SPHERE_GAP
                        }
                    )
            )
            RawSphere(instance, candidate).also { sphere ->
                rawSpheres += sphere
                rawSphereByInstanceKey[instanceKey(instance)] = sphere
            }
        }

        val rawSphereByLessonId = rawSpheres.flatMap { sphere ->
            sphere.instance.lessons.map { it.id to sphere }
        }.toMap()
        val rawNodes = rawSpheres.flatMap { sphere ->
            val lessonOrbs = sphere.instance.lessons.flatMap { lesson ->
                galaxyOrbNodes(lesson).map { node -> lesson to node }
            }
            val rootLesson = sphere.instance.lessons.first()
            val incomingCentres = rootLesson.prerequisiteLessonIds
                .mapNotNull(rawSphereByLessonId::get)
                .filterNot { it === sphere }
                .map { it.centre }
            val incomingPoint = incomingCentres.takeIf { it.isNotEmpty() }?.averagePoint()
                ?: GalaxyPoint(0f, 0f)
            val outgoingCentres = rawSpheres
                .filterNot { it === sphere }
                .filter { candidate ->
                    candidate.instance.lessons.first().prerequisiteLessonIds.any { prerequisiteId ->
                        rawSphereByLessonId[prerequisiteId] === sphere
                    }
                }
                .map { it.centre }
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
            val baseOrbPoints = zigZagOrbPoints(
                centre = sphere.centre,
                orbCount = lessonOrbs.size,
                sphereRadius = sphere.instance.radius,
                entryAngle = entryAngle,
                exitAngle = exitAngle
            )
            val orbPoints = coherentGalaxyLessonOrbPoints(
                basePoints = baseOrbPoints,
                lessonIds = lessonOrbs.map { (lesson, _) -> lesson.id }
            )
            lessonOrbs.mapIndexed { orbIndex, (lesson, node) ->
                GalaxyNodePosition(
                    nodeId = node.nodeId,
                    lessonId = lesson.id,
                    kind = node.orb.kind,
                    trackId = sphere.instance.track.id,
                    grade = lesson.grade,
                    point = orbPoints[orbIndex]
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

        val nodes = rawNodes.map { it.copy(point = it.point.shifted()) }
        val checkpointNodes = rawCheckpointNodes.map { it.copy(point = it.point.shifted()) }
        val trackSpheres = rawSpheres.map { sphere ->
            GalaxyTrackSpherePosition(
                trackId = sphere.instance.track.id,
                grade = sphere.instance.grade,
                centre = sphere.centre.shifted(),
                radius = sphere.instance.radius
            )
        }
        val trackLabels = rawSpheres.map { sphere ->
            GalaxyTrackLabelPosition(
                trackId = sphere.instance.track.id,
                grade = sphere.instance.grade,
                point = sphere.centre.shifted()
            )
        }
        val gradeSections = instances.map { it.grade }.distinct().sorted().map { grade ->
            val gradeSpheres = trackSpheres.filter { it.grade == grade }
            val left = gradeSpheres.minOf { it.centre.x - it.radius }
            val right = gradeSpheres.maxOf { it.centre.x + it.radius }
            val top = gradeSpheres.minOf { it.centre.y - it.radius }
            val bottom = gradeSpheres.maxOf { it.centre.y + it.radius }
            GalaxyGradeSectionPosition(
                grade = grade,
                point = GalaxyPoint(left, top),
                width = right - left,
                height = bottom - top
            )
        }

        val visibleLessonIds = nodes.mapTo(linkedSetOf()) { it.lessonId }
        val checkpointByTargetId = checkpoints.associateBy { it.targetLessonId }
        val checkpointNodeIds = checkpointNodes.mapTo(linkedSetOf()) { it.checkpointId }
        val lessons = rawSpheres.flatMap { it.instance.lessons }
        fun sphereKey(lessonId: String): String? = rawSphereByLessonId[lessonId]?.let { sphere ->
            "${sphere.instance.track.id}:${sphere.instance.grade}"
        }
        val firstLessonIdBySphere = rawSpheres.associate { sphere ->
            "${sphere.instance.track.id}:${sphere.instance.grade}" to sphere.instance.lessons.first().id
        }
        val lastLessonIdBySphere = rawSpheres.associate { sphere ->
            "${sphere.instance.track.id}:${sphere.instance.grade}" to sphere.instance.lessons.last().id
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
        val rawSphereEdges = rawSpheres.flatMap { targetSphere ->
            val targetGroup = "${targetSphere.instance.track.id}:${targetSphere.instance.grade}"
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
        }.toSet()
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

        return GalaxyCategoryLayout(
            width = max(420f, maximumX - minimumX + CONTENT_MARGIN * 2f),
            height = max(320f, maximumY - minimumY + CONTENT_MARGIN * 2f),
            startPoint = GalaxyPoint(shiftX, shiftY),
            nodes = nodes,
            checkpointNodes = checkpointNodes,
            trackLabels = trackLabels,
            trackSpheres = trackSpheres,
            gradeSections = gradeSections,
            connections = connections,
            checkpointConnections = checkpointConnections,
            startConnections = startConnections
        )
    }

    private fun emptyCategoryLayout() = GalaxyCategoryLayout(
        width = 420f,
        height = 320f,
        startPoint = GalaxyPoint(210f, 160f),
        nodes = emptyList(),
        checkpointNodes = emptyList(),
        trackLabels = emptyList(),
        trackSpheres = emptyList(),
        gradeSections = emptyList(),
        connections = emptyList(),
        checkpointConnections = emptyList(),
        startConnections = emptyList()
    )

    private fun sphereRadius(orbCount: Int): Float {
        val interiorCount = (orbCount - 2).coerceAtLeast(1)
        val columns = ceil(sqrt(interiorCount.toDouble())).toInt().coerceAtLeast(1)
        val rows = ceil(interiorCount.toDouble() / columns).toInt().coerceAtLeast(1)
        val halfWidth = (columns - 1) * 40f
        val halfHeight = (rows - 1) * 40f
        return max(158f, hypot(halfWidth, halfHeight) + 92f)
    }

    /**
     * Builds a deterministic serpentine route through the middle of a sphere.
     * The entry and exit stay on the outer range; intermediate orbs zigzag over
     * bounded chords instead of expanding in concentric rings.
     */
    private fun zigZagOrbPoints(
        centre: GalaxyPoint,
        orbCount: Int,
        sphereRadius: Float,
        entryAngle: Double,
        exitAngle: Double
    ): List<GalaxyPoint> {
        if (orbCount <= 0) return emptyList()
        val outerRadius = (sphereRadius - 50f).coerceAtLeast(84f)
        fun pointAt(angle: Double, radius: Float) = GalaxyPoint(
            x = centre.x + cos(angle).toFloat() * radius,
            y = centre.y + sin(angle).toFloat() * radius
        )
        if (orbCount == 1) return listOf(pointAt(entryAngle, outerRadius))

        val points = MutableList(orbCount) { centre }
        points[0] = pointAt(entryAngle, outerRadius)
        points[orbCount - 1] = pointAt(exitAngle, outerRadius)
        val interiorCount = orbCount - 2
        if (interiorCount == 0) return points

        val columns = ceil(sqrt(interiorCount.toDouble())).toInt().coerceAtLeast(1)
        val rows = ceil(interiorCount.toDouble() / columns).toInt().coerceAtLeast(1)
        val interiorRadius = (outerRadius - 42f).coerceAtLeast(42f)
        val rotation = entryAngle - PI
        var placed = 0
        for (row in 0 until rows) {
            val itemsOnRow = minOf(columns, interiorCount - placed)
            val yFraction = if (rows == 1) 0f else -0.72f + 1.44f * row / (rows - 1)
            val localY = yFraction * interiorRadius
            val chordHalf = sqrt(
                (interiorRadius * interiorRadius - localY * localY).coerceAtLeast(0f)
            ) * 0.78f
            val rowXs = List(itemsOnRow) { column ->
                if (itemsOnRow == 1) 0f
                else -chordHalf + 2f * chordHalf * column / (itemsOnRow - 1)
            }.let { xs -> if (row % 2 == 0) xs else xs.reversed() }
            rowXs.forEach { localX ->
                val jitteredY = localY + sin((placed + 1) * GOLDEN_ANGLE).toFloat() * 7f
                val distance = hypot(localX, jitteredY)
                val scale = if (distance > interiorRadius) interiorRadius / distance else 1f
                val boundedX = localX * scale
                val boundedY = jitteredY * scale
                val rotatedX = boundedX * cos(rotation).toFloat() - boundedY * sin(rotation).toFloat()
                val rotatedY = boundedX * sin(rotation).toFloat() + boundedY * cos(rotation).toFloat()
                points[placed + 1] = GalaxyPoint(centre.x + rotatedX, centre.y + rotatedY)
                placed += 1
            }
        }
        return points
    }

    private fun List<GalaxyPoint>.averagePoint(): GalaxyPoint = GalaxyPoint(
        x = sumOf { it.x.toDouble() }.toFloat() / size,
        y = sumOf { it.y.toDouble() }.toFloat() / size
    )
}
