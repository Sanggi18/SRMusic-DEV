package com.example.ui.settings.about

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.example.core.model.AboutHeaderSurface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.android.awaitFrame

@Composable
fun HeaderSurfaceCanvas(
    surface: AboutHeaderSurface,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val renderer = remember(surface) {
        when (surface) {
            AboutHeaderSurface.DEFAULT -> null
            AboutHeaderSurface.SNOW -> SnowRenderer()
            AboutHeaderSurface.MAZE -> MazeRenderer()
            AboutHeaderSurface.CIRCUIT -> CircuitRenderer()
            AboutHeaderSurface.CODE_RAIN -> MatrixRenderer()
        }
    }

    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var frame by remember { mutableFloatStateOf(0f) }

    if (renderer != null) {
        LaunchedEffect(renderer, canvasSize) {
            if (canvasSize.isEmpty()) return@LaunchedEffect
            var last = 0L
            while (true) {
                if (!renderer.isAnimating) {
                    last = 0L
                    awaitFrame()
                    continue
                }
                withFrameNanos { now ->
                    val dt = if (last == 0L) 16f else (now - last) / 1_000_000f
                    last = now
                    renderer.update(dt.coerceAtMost(64f), canvasSize)
                    frame += 1f
                }
            }
        }
    }

    val haptic = LocalHapticFeedback.current
    Canvas(
        modifier = modifier
            .clipToBounds()
            .then(
                if (renderer != null) {
                    Modifier
                        .pointerInput(surface) {
                            detectTapGestures(
                                onDoubleTap = if (renderer.hasVariants) {
                                    {
                                        renderer.onDoubleTap()
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    }
                                } else {
                                    null
                                },
                                onTap = { offset ->
                                    renderer.onTap(offset, size.toSize())
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                },
                                onLongPress = { offset ->
                                    renderer.onLongPress(offset, size.toSize())
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onPress = {
                                    tryAwaitRelease()
                                    renderer.onRelease()
                                }
                            )
                        }
                        .pointerInput(surface) {
                            detectTransformGestures(panZoomLock = false) { centroid, pan, gestureZoom, _ ->
                                if (gestureZoom != 1f) {
                                    renderer.scale *= gestureZoom
                                }
                                if (pan != Offset.Zero) {
                                    renderer.onDrag(pan, centroid, size.toSize())
                                }
                            }
                        }
                } else {
                    Modifier
                }
            )
    ) {
        if (canvasSize != size) canvasSize = size
        @Suppress("UNUSED_EXPRESSION")
        frame
        renderer?.run { draw(accentColor) }
    }
}

private interface AmbienceRenderer {
    var scale: Float
        get() = 1f
        set(_) {}

    var speed: Float
        get() = 1f
        set(_) {}

    val hasVariants: Boolean
        get() = false

    val isAnimating: Boolean
        get() = true

    fun update(dt: Float, size: Size)
    fun DrawScope.draw(tint: Color)

    fun onTap(offset: Offset, size: Size) {}
    fun onDoubleTap() {}
    fun onLongPress(offset: Offset, size: Size) {}
    fun onRelease() {}
    fun onDrag(pan: Offset, centroid: Offset, size: Size) {}
}

private class SnowRenderer(private val random: Random = Random.Default) : AmbienceRenderer {

    private class Flake(
        var x: Float,
        var y: Float,
        var fullRadius: Float,
        var fallSpeed: Float,
        var swayPhase: Float,
        var swayRate: Float,
        var angle: Float,
        var spin: Float,
        var growth: Float = 1f
    ) {
        val radius: Float
            get() = fullRadius * growth
    }

    private class Shard(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val length: Float,
        val angle: Float,
        var age: Float = 0f
    )

    private val flakes = ArrayList<Flake>()
    private val shards = ArrayList<Shard>()
    private var bounds = Size.Zero

    override var scale: Float = 1f
        set(value) {
            val next = value.coerceIn(MIN_SCALE, MAX_SCALE)
            if (next == field) return
            val ratio = next / field
            field = next
            for (flake in flakes) flake.fullRadius *= ratio
        }

    override var speed: Float = 1f
        set(value) {
            field = value.coerceIn(MIN_SPEED, MAX_SPEED)
        }

    override fun update(dt: Float, size: Size) {
        if (size.isEmpty()) return
        if (bounds != size) {
            bounds = size
            flakes.clear()
            repeat(BASE_FLAKES) { flakes.add(spawn(size, randomY = true)) }
        }

        val sec = dt / 1000f
        val target = (BASE_FLAKES / scale).roundToInt().coerceIn(8, 36)
        while (flakes.size < target) flakes.add(spawn(size, randomY = false))

        val flakeIter = flakes.iterator()
        while (flakeIter.hasNext()) {
            val flake = flakeIter.next()
            if (flake.growth < 1f) {
                flake.growth = (flake.growth + dt / GROW_MS).coerceAtMost(1f)
            }
            flake.swayPhase += flake.swayRate * sec * speed
            flake.x += sin(flake.swayPhase) * (flake.fullRadius * 1.4f) * sec * speed
            flake.y += flake.fallSpeed * sec * speed
            flake.angle += flake.spin * sec * speed

            if (flake.y - flake.fullRadius > size.height) {
                if (flakes.size > target) {
                    flakeIter.remove()
                } else {
                    val fresh = spawn(size, randomY = false)
                    flake.x = fresh.x
                    flake.y = fresh.y
                    flake.fullRadius = fresh.fullRadius
                    flake.fallSpeed = fresh.fallSpeed
                    flake.growth = 1f
                }
            }
        }

        val shardIter = shards.iterator()
        while (shardIter.hasNext()) {
            val shard = shardIter.next()
            shard.x += shard.vx * sec
            shard.y += shard.vy * sec
            shard.vy += 40f * sec
            shard.age += dt
            if (shard.age >= SHARD_LIFE_MS) shardIter.remove()
        }
    }

    override fun DrawScope.draw(tint: Color) {
        for (flake in flakes) {
            val alpha = 0.24f * flake.growth
            drawCrystal(flake.x, flake.y, flake.radius, flake.angle, tint.copy(alpha = alpha))
        }
        for (shard in shards) {
            val life = (1f - shard.age / SHARD_LIFE_MS).coerceIn(0f, 1f)
            val dx = cos(shard.angle) * shard.length * 0.5f * life
            val dy = sin(shard.angle) * shard.length * 0.5f * life
            drawLine(
                color = tint.copy(alpha = 0.42f * life * life),
                start = Offset(shard.x - dx, shard.y - dy),
                end = Offset(shard.x + dx, shard.y + dy),
                strokeWidth = 1.4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }

    private fun DrawScope.drawCrystal(
        cx: Float,
        cy: Float,
        radius: Float,
        rotationDeg: Float,
        color: Color
    ) {
        if (radius < 1f) return
        val base = rotationDeg * (PI.toFloat() / 180f)
        val width = (radius * 0.16f).coerceAtLeast(1.1f)
        for (arm in 0 until 3) {
            val theta = base + arm * (PI.toFloat() / 3f)
            val dx = cos(theta) * radius
            val dy = sin(theta) * radius
            drawLine(
                color = color,
                start = Offset(cx - dx, cy - dy),
                end = Offset(cx + dx, cy + dy),
                strokeWidth = width,
                cap = StrokeCap.Round
            )
            for (side in intArrayOf(-1, 1)) {
                val bx = cx + dx * 0.55f * side
                val by = cy + dy * 0.55f * side
                for (branch in floatArrayOf(0.55f, -0.55f)) {
                    val bTheta = theta + branch
                    val bLen = radius * 0.34f * side
                    drawLine(
                        color = color,
                        start = Offset(bx, by),
                        end = Offset(bx + cos(bTheta) * bLen, by + sin(bTheta) * bLen),
                        strokeWidth = width * 0.75f,
                        cap = StrokeCap.Round
                    )
                }
                drawCircle(
                    color = color,
                    radius = width * 0.7f,
                    center = Offset(cx + dx * side, cy + dy * side)
                )
            }
        }
    }

    override fun onTap(offset: Offset, size: Size) {
        val hit = flakes
            .filter { it.growth > 0.5f }
            .minByOrNull { hypot(it.x - offset.x, it.y - offset.y) }
            ?.takeIf { hypot(it.x - offset.x, it.y - offset.y) <= it.fullRadius * 2.2f }
        if (hit != null) {
            burst(hit)
            flakes.remove(hit)
        } else if (flakes.size < 40) {
            val born = spawn(size, randomY = true)
            born.x = offset.x
            born.y = offset.y
            born.growth = 0f
            flakes.add(born)
        }
    }

    private fun burst(flake: Flake) {
        val base = flake.angle * (PI.toFloat() / 180f)
        for (arm in 0 until 6) {
            val theta = base + arm * (PI.toFloat() / 3f)
            val spd = flake.radius * (2.2f + random.nextFloat() * 1.6f)
            shards.add(
                Shard(
                    x = flake.x + cos(theta) * flake.radius * 0.3f,
                    y = flake.y + sin(theta) * flake.radius * 0.3f,
                    vx = cos(theta) * spd,
                    vy = sin(theta) * spd - flake.radius * 0.6f,
                    length = flake.radius * 0.85f,
                    angle = theta
                )
            )
        }
    }

    private fun spawn(size: Size, randomY: Boolean): Flake {
        val r = size.height * (0.018f + random.nextFloat() * 0.030f) * scale
        return Flake(
            x = random.nextFloat() * size.width,
            y = if (randomY) random.nextFloat() * size.height else -r - random.nextFloat() * r * 2f,
            fullRadius = r,
            fallSpeed = 10f + random.nextFloat() * 22f,
            swayPhase = random.nextFloat() * (2f * PI.toFloat()),
            swayRate = 0.3f + random.nextFloat() * 0.6f,
            angle = random.nextFloat() * 360f,
            spin = (random.nextFloat() - 0.5f) * 24f
        )
    }

    private companion object {
        const val BASE_FLAKES = 22
        const val MIN_SCALE = 0.4f
        const val MAX_SCALE = 3f
        const val MIN_SPEED = 0.2f
        const val MAX_SPEED = 5f
        const val SHARD_LIFE_MS = 1100f
        const val GROW_MS = 1500f
    }
}

private class MazeRenderer(private val random: Random = Random.Default) : AmbienceRenderer {

    private var cols = BASE_COLS
    private var rows = BASE_ROWS
    private var right = Array(0) { BooleanArray(0) }
    private var down = Array(0) { BooleanArray(0) }
    private val leftDoors = HashSet<Int>()
    private val rightDoors = HashSet<Int>()

    private var cellX = 0
    private var cellY = 0
    private var prevX = -1
    private var prevY = 0
    private var targetX = 0
    private var targetY = 0
    private var stepProgress = 0f
    private var pauseMs = 0f
    private var dragged = 0f
    private val trail = ArrayDeque<Pair<Float, Float>>()

    override var scale: Float = 1f
        set(value) {
            val next = value.coerceIn(MIN_SCALE, MAX_SCALE)
            if (next == field) return
            field = next
            val nextCols = (BASE_COLS / next).roundToInt().coerceIn(6, 24)
            val nextRows = (BASE_ROWS / next).roundToInt().coerceIn(3, 10)
            if (nextCols != cols || nextRows != rows) {
                cols = nextCols
                rows = nextRows
                carve()
            }
        }

    override fun update(dt: Float, size: Size) {
        if (size.isEmpty()) return
        if (right.isEmpty()) carve()

        if (pauseMs > 0f) {
            pauseMs -= dt
            if (pauseMs <= 0f) beginRun()
            return
        }

        stepProgress += dt * SPEED / 1000f
        while (stepProgress >= 1f) {
            stepProgress -= 1f
            prevX = cellX
            prevY = cellY
            cellX = targetX
            cellY = targetY

            if (cellX < 0 || cellX >= cols) {
                trail.clear()
                pauseMs = 700f + random.nextFloat() * 900f
                return
            }

            trail.addFirst(cellX.toFloat() to cellY.toFloat())
            while (trail.size > TRAIL) trail.removeLast()

            val next = chooseNext(cellX, cellY, prevX, prevY)
            targetX = next.first
            targetY = next.second
        }
    }

    override fun DrawScope.draw(tint: Color) {
        if (right.isEmpty()) return
        val pad = 8.dp.toPx()
        val w = (size.width - pad * 2f) / cols
        val h = (size.height - pad * 2f) / rows
        val wallColor = tint.copy(alpha = 0.16f)
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Square)

        for (y in 0 until rows) {
            val y0 = pad + y * h
            if (y !in leftDoors) {
                drawLine(wallColor, Offset(pad, y0), Offset(pad, y0 + h), stroke.width)
            }
            if (y !in rightDoors) {
                val rx = pad + cols * w
                drawLine(wallColor, Offset(rx, y0), Offset(rx, y0 + h), stroke.width)
            }
        }
        drawLine(wallColor, Offset(pad, pad), Offset(pad + cols * w, pad), stroke.width)
        val by = pad + rows * h
        drawLine(wallColor, Offset(pad, by), Offset(pad + cols * w, by), stroke.width)

        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val x0 = pad + x * w
                val y0 = pad + y * h
                if (x < cols - 1 && right[y][x]) {
                    drawLine(wallColor, Offset(x0 + w, y0), Offset(x0 + w, y0 + h), stroke.width)
                }
                if (y < rows - 1 && down[y][x]) {
                    drawLine(wallColor, Offset(x0, y0 + h), Offset(x0 + w, y0 + h), stroke.width)
                }
            }
        }

        if (pauseMs > 0f) return

        trail.forEachIndexed { index, (tx, ty) ->
            val age = 1f - index.toFloat() / TRAIL
            drawCircle(
                color = tint.copy(alpha = 0.10f * age * age),
                radius = minOf(w, h) * 0.16f,
                center = Offset(pad + (tx + 0.5f) * w, pad + (ty + 0.5f) * h)
            )
        }

        val headX = cellX + (targetX - cellX) * stepProgress
        val headY = cellY + (targetY - cellY) * stepProgress
        drawCircle(
            color = tint.copy(alpha = 0.42f),
            radius = minOf(w, h) * 0.17f,
            center = Offset(pad + (headX + 0.5f) * w, pad + (headY + 0.5f) * h)
        )
    }

    override fun onTap(offset: Offset, size: Size) {
        if (right.isEmpty()) return
        val pad = size.height * 0.04f
        val x = ((offset.x - pad) / ((size.width - pad * 2f) / cols)).toInt().coerceIn(0, cols - 1)
        val y = ((offset.y - pad) / ((size.height - pad * 2f) / rows)).toInt().coerceIn(0, rows - 1)
        pauseMs = 0f
        cellX = x
        cellY = y
        prevX = x
        prevY = y
        val next = chooseNext(x, y, -99, -99)
        targetX = next.first
        targetY = next.second
        stepProgress = 0f
    }

    override fun onDrag(pan: Offset, centroid: Offset, size: Size) {
        if (abs(pan.x) <= abs(pan.y)) return
        dragged += pan.x
        if (abs(dragged) >= size.width * 0.12f) {
            dragged = 0f
            carve()
        }
    }

    private fun carve() {
        right = Array(rows) { BooleanArray(cols) { true } }
        down = Array(rows) { BooleanArray(cols) { true } }
        val visited = Array(rows) { BooleanArray(cols) }

        fun dfs(x: Int, y: Int) {
            visited[y][x] = true
            for ((dx, dy) in DIRS.shuffled(random)) {
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until cols && ny in 0 until rows && !visited[ny][nx]) {
                    removeWall(x, y, nx, ny)
                    dfs(nx, ny)
                }
            }
        }
        dfs(random.nextInt(cols), random.nextInt(rows))

        for (y in 0 until rows) {
            for (x in 0 until cols) {
                if (openNeighbours(x, y).size <= 1 && random.nextFloat() < BRAID) {
                    val candidates = DIRS.map { (dx, dy) -> (x + dx) to (y + dy) }
                        .filter { (nx, ny) -> nx in 0 until cols && ny in 0 until rows }
                        .filter { (nx, ny) -> !canMove(x, y, nx, ny) }
                    candidates.randomOrNull(random)?.let { (nx, ny) -> removeWall(x, y, nx, ny) }
                }
            }
        }

        leftDoors.clear()
        rightDoors.clear()
        val rowsBag = (0 until rows).shuffled(random)
        leftDoors.addAll(rowsBag.take(2 + random.nextInt(2)))
        rightDoors.addAll((0 until rows).shuffled(random).take(2 + random.nextInt(2)))

        trail.clear()
        beginRun()
    }

    private fun beginRun() {
        val fromLeft = random.nextBoolean()
        if (fromLeft) {
            val y = leftDoors.random(random)
            prevX = -1
            prevY = y
            cellX = -1
            cellY = y
            targetX = 0
            targetY = y
        } else {
            val y = rightDoors.random(random)
            prevX = cols
            prevY = y
            cellX = cols
            cellY = y
            targetX = cols - 1
            targetY = y
        }
        stepProgress = 0f
    }

    private fun chooseNext(x: Int, y: Int, fromX: Int, fromY: Int): Pair<Int, Int> {
        val exits = openNeighbours(x, y).toMutableList()
        if (x == 0 && y in leftDoors && fromX != -1) exits.add(-1 to y)
        if (x == cols - 1 && y in rightDoors && fromX != cols) exits.add(cols to y)
        val forward = exits.filterNot { it.first == fromX && it.second == fromY }
        return (forward.ifEmpty { exits }).randomOrNull(random) ?: (fromX to fromY)
    }

    private fun openNeighbours(x: Int, y: Int): List<Pair<Int, Int>> = buildList {
        for ((dx, dy) in DIRS) {
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until cols && ny in 0 until rows && canMove(x, y, nx, ny)) {
                add(nx to ny)
            }
        }
    }

    private fun canMove(x: Int, y: Int, nx: Int, ny: Int): Boolean = when {
        nx == x + 1 -> !right[y][x]
        nx == x - 1 -> !right[y][nx]
        ny == y + 1 -> !down[y][x]
        ny == y - 1 -> !down[ny][x]
        else -> false
    }

    private fun removeWall(x: Int, y: Int, nx: Int, ny: Int) {
        when {
            nx == x + 1 -> right[y][x] = false
            nx == x - 1 -> right[y][nx] = false
            ny == y + 1 -> down[y][x] = false
            ny == y - 1 -> down[ny][x] = false
        }
    }

    private companion object {
        const val BASE_COLS = 13
        const val BASE_ROWS = 5
        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 2.5f
        const val SPEED = 3.4f
        const val BRAID = 0.45f
        const val TRAIL = 14
        val DIRS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    }
}

private class CircuitRenderer(private val random: Random = Random.Default) : AmbienceRenderer {

    private class Segment(
        val x0: Float,
        val y0: Float,
        val x1: Float,
        val y1: Float,
        val length: Float,
        val startDistance: Float
    ) {
        val ux: Float = if (length > 0f) (x1 - x0) / length else 0f
        val uy: Float = if (length > 0f) (y1 - y0) / length else 0f
    }

    private class Pad(
        val x: Float,
        val y: Float,
        val distance: Float,
        val endpoint: Boolean
    )

    private class Trace(
        val segments: List<Segment>,
        val totalLength: Float,
        val pads: List<Pad>,
        val revealDelayMs: Float
    )

    private class Pulse(
        val trace: Trace,
        var head: Float,
        val speed: Float,
        val startAt: Float = 0f
    )

    private val traces = ArrayList<Trace>()
    private val pulses = ArrayList<Pulse>()
    private val tracePath = Path()
    private val tailPath = Path()
    private val tailEdge = ArrayList<Offset>(TAIL_STEPS + 2)
    private val litPads = ArrayList<Pair<Pad, Float>>()
    private var bounds = Size.Zero
    private var reveal = 0f
    private var pulseCooldownMs = 600f
    private var routeAgeMs = 0f
    private var dragAccumulator = 0f

    override var scale: Float = 1f
        set(value) {
            val next = value.coerceIn(MIN_SCALE, MAX_SCALE)
            if (next == field) return
            field = next
            if (!bounds.isEmpty()) route(bounds)
        }

    override var speed: Float = 1f
        set(value) {
            field = value.coerceIn(MIN_SPEED, MAX_SPEED)
        }

    override fun update(dt: Float, size: Size) {
        if (size.isEmpty()) return
        if (bounds != size || traces.isEmpty()) {
            bounds = size
            route(size)
        }

        val sec = dt / 1000f
        reveal += dt / 420f
        routeAgeMs += dt * speed
        if (routeAgeMs >= ROUTE_INTERVAL_MS && pulses.isEmpty()) {
            route(size)
            return
        }

        pulseCooldownMs -= dt * speed
        if (pulseCooldownMs <= 0f && traces.isNotEmpty() && pulses.size < 2) {
            val chosen = traces.random(random)
            pulses.add(
                Pulse(
                    trace = chosen,
                    head = 0f,
                    speed = size.width * PULSE_SPEED * (0.9f + random.nextFloat() * 0.25f)
                )
            )
            pulseCooldownMs = PULSE_INTERVAL_MS * (0.7f + random.nextFloat() * 0.6f)
        }

        val iterator = pulses.iterator()
        while (iterator.hasNext()) {
            val pulse = iterator.next()
            pulse.head += pulse.speed * sec * speed
            val tailLength = size.width * TAIL_LENGTH
            if (pulse.head - tailLength > pulse.trace.totalLength) {
                iterator.remove()
            }
        }
    }

    override fun DrawScope.draw(tint: Color) {
        val traceWidth = (1.5.dp.toPx() * scale.coerceIn(0.7f, 1.5f))
        val padRadius = traceWidth * 1.9f
        val holeRadius = traceWidth * 0.75f
        val traceColor = tint.copy(alpha = 0.24f)
        val traceStroke = Stroke(width = traceWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val padStroke = Stroke(width = traceWidth * 0.85f)

        for (trace in traces) {
            val progress = (reveal - trace.revealDelayMs / 420f).coerceIn(0f, 1f)
            if (progress <= 0f) continue
            val visibleLength = trace.totalLength * smoothstep(progress)
            tracePath.reset()
            var started = false
            for (seg in trace.segments) {
                if (seg.startDistance >= visibleLength) break
                if (!started) {
                    tracePath.moveTo(seg.x0, seg.y0)
                    started = true
                }
                val segVisible = (visibleLength - seg.startDistance).coerceAtMost(seg.length)
                val t = if (seg.length > 0f) segVisible / seg.length else 1f
                tracePath.lineTo(
                    seg.x0 + (seg.x1 - seg.x0) * t,
                    seg.y0 + (seg.y1 - seg.y0) * t
                )
            }
            drawPath(path = tracePath, color = traceColor, style = traceStroke)

            for (pad in trace.pads) {
                if (pad.distance > visibleLength) continue
                val center = Offset(pad.x, pad.y)
                if (pad.endpoint) {
                    drawCircle(color = traceColor, radius = padRadius, center = center, style = padStroke)
                    drawCircle(color = tint.copy(alpha = 0.14f), radius = holeRadius, center = center)
                } else {
                    drawCircle(color = tint.copy(alpha = 0.28f), radius = traceWidth * 1.25f, center = center)
                }
            }
        }

        val tailLength = size.width * TAIL_LENGTH
        for (pulse in pulses) {
            drawPulse(pulse, tailLength, traceWidth, padRadius, tint)
        }
    }

    private fun DrawScope.drawPulse(
        pulse: Pulse,
        tailLength: Float,
        traceWidth: Float,
        padRadius: Float,
        tint: Color
    ) {
        val trace = pulse.trace
        val headDist = pulse.head.coerceAtMost(trace.totalLength)
        val tailDist = (pulse.head - tailLength).coerceAtLeast(pulse.startAt)
        val activeSpan = headDist - tailDist
        if (activeSpan <= 1f) return

        val fade = when {
            pulse.head > trace.totalLength -> 1f - (pulse.head - trace.totalLength) / tailLength
            headDist - pulse.startAt < tailLength -> (headDist - pulse.startAt) / tailLength
            else -> 1f
        }.coerceIn(0f, 1f)

        litPads.clear()
        for (pad in trace.pads) {
            if (pad.distance < pulse.startAt) continue
            val delta = pulse.head - pad.distance
            if (delta in -padRadius..(tailLength * 0.85f)) {
                val sigma = if (delta < 0f) padRadius else tailLength * 0.42f
                val heat = exp(-(delta * delta) / (2f * sigma * sigma)) * fade
                if (heat > 0.02f) litPads.add(pad to heat)
            }
        }

        val swellSigma = padRadius * 3.2f
        val halfHeadWidth = traceWidth * (TAIL_WIDTH * 0.5f)
        val step = activeSpan / TAIL_STEPS
        tailPath.reset()
        tailEdge.clear()

        var s = tailDist
        for (i in 0..TAIL_STEPS) {
            val seg = segmentAt(trace, s)
            val along = (s - seg.startDistance).coerceIn(0f, seg.length)
            val px = seg.x0 + seg.ux * along
            val py = seg.y0 + seg.uy * along
            val nx = -seg.uy
            val ny = seg.ux

            val progress = ((s - tailDist) / activeSpan).coerceIn(0f, 1f)
            var halfWidth = halfHeadWidth * progress * progress
            for ((pad, _) in litPads) {
                val d = s - pad.distance
                val influence = exp(-(d * d) / (2f * swellSigma * swellSigma))
                halfWidth += padRadius * TAIL_SWELL * influence * progress
            }

            val left = Offset(px + nx * halfWidth, py + ny * halfWidth)
            val right = Offset(px - nx * halfWidth, py - ny * halfWidth)
            if (i == 0) tailPath.moveTo(left.x, left.y) else tailPath.lineTo(left.x, left.y)
            tailEdge.add(right)
            s = (s + step).coerceAtMost(headDist)
        }
        for (i in tailEdge.lastIndex downTo 0) {
            val pt = tailEdge[i]
            tailPath.lineTo(pt.x, pt.y)
        }
        tailPath.close()

        val headPt = pointAt(trace, headDist)
        val tailPt = pointAt(trace, tailDist)
        val brushSpan = hypot(headPt.x - tailPt.x, headPt.y - tailPt.y)
        if (brushSpan > 1f) {
            drawPath(
                path = tailPath,
                brush = Brush.linearGradient(
                    0.0f to tint.copy(alpha = 0.85f * fade),
                    0.5f to tint.copy(alpha = 0.38f * fade),
                    1.0f to Color.Transparent,
                    start = headPt,
                    end = tailPt
                )
            )
        } else {
            drawPath(path = tailPath, color = tint.copy(alpha = 0.45f * fade))
        }

        for ((pad, heat) in litPads) {
            val center = Offset(pad.x, pad.y)
            val radius = if (pad.endpoint) padRadius else traceWidth * 1.35f
            drawCircle(color = tint.copy(alpha = 0.20f * heat), radius = radius * 1.9f, center = center)
            drawCircle(
                color = tint.copy(alpha = 0.55f * heat),
                radius = radius,
                center = center,
                style = Stroke(width = traceWidth * 0.9f)
            )
        }

        if (pulse.head <= trace.totalLength) {
            drawCircle(color = tint.copy(alpha = 0.22f * fade), radius = halfHeadWidth * 1.6f, center = headPt)
            drawCircle(color = tint.copy(alpha = 0.90f * fade), radius = halfHeadWidth, center = headPt)
        }
    }

    override fun onTap(offset: Offset, size: Size) {
        var bestTrace: Trace? = null
        var bestSeg: Segment? = null
        var bestDist = Float.MAX_VALUE
        for (trace in traces) {
            for (seg in trace.segments) {
                val mx = (seg.x0 + seg.x1) * 0.5f
                val my = (seg.y0 + seg.y1) * 0.5f
                val d = hypot(mx - offset.x, my - offset.y)
                if (d < bestDist) {
                    bestDist = d
                    bestTrace = trace
                    bestSeg = seg
                }
            }
        }
        val chosen = bestTrace ?: return
        val start = bestSeg?.startDistance ?: 0f
        pulses.add(
            Pulse(
                trace = chosen,
                head = start,
                speed = size.width * PULSE_SPEED * 1.15f,
                startAt = start
            )
        )
    }

    override fun onDrag(pan: Offset, centroid: Offset, size: Size) {
        if (abs(pan.x) > abs(pan.y) * 1.5f) {
            dragAccumulator += pan.x
            val threshold = size.width * REROUTE_FRACTION
            if (threshold > 0f && abs(dragAccumulator) >= threshold) {
                dragAccumulator = 0f
                route(size)
            }
        } else if (abs(pan.y) > abs(pan.x) * 1.5f && size.height > 0f) {
            speed *= 1f - (pan.y / size.height) * 1.6f
        }
    }

    private fun route(size: Size) {
        traces.clear()
        pulses.clear()
        reveal = 0f
        routeAgeMs = 0f

        val lanes = (BASE_LANES / scale).roundToInt().coerceIn(5, 22)
        val columns = (BASE_COLUMNS / scale).roundToInt().coerceIn(6, 26)
        val padX = size.width * 0.04f
        val padY = size.height * 0.14f
        val usableW = (size.width - padX * 2f).coerceAtLeast(1f)
        val usableH = (size.height - padY * 2f).coerceAtLeast(1f)
        val laneGap = if (lanes > 1) usableH / (lanes - 1) else usableH
        val columnWidth = usableW / columns

        val hUsed = Array(columns) { BooleanArray(lanes) }
        val vUsed = Array(columns + 1) { BooleanArray(max(1, lanes - 1)) }

        fun hFree(c0: Int, c1: Int, lane: Int): Boolean {
            for (c in c0 until c1) if (hUsed[c][lane]) return false
            return true
        }

        fun markH(c0: Int, c1: Int, lane: Int) {
            for (c in c0 until c1) hUsed[c][lane] = true
        }

        fun vFree(col: Int, a: Int, b: Int): Boolean {
            val lo = min(a, b)
            val hi = max(a, b)
            for (e in lo until hi) if (vUsed[col][e]) return false
            return true
        }

        fun markV(col: Int, a: Int, b: Int) {
            val lo = min(a, b)
            val hi = max(a, b)
            for (e in lo until hi) vUsed[col][e] = true
        }

        fun canStepTo(col: Int, fromLane: Int, toLane: Int): Boolean =
            toLane != fromLane &&
                toLane in 0 until lanes &&
                !hUsed[col][toLane] &&
                vFree(col, fromLane, toLane)

        val homeOrder = (0 until lanes).shuffled(random)
        var built = 0
        for (homeLane in homeOrder) {
            val startCol = if (random.nextFloat() < EDGE_START_CHANCE) {
                0
            } else {
                random.nextInt((columns / 2).coerceAtLeast(1))
            }
            if (startCol >= columns - 1 || hUsed[startCol][homeLane]) continue

            val waypoints = ArrayList<Pair<Int, Int>>()
            var col = startCol
            var lane = homeLane
            hUsed[col][lane] = true
            waypoints.add(col to lane)
            col++

            while (col < columns) {
                val blocked = hUsed[col][lane]
                val wantJog = col < columns - 1 && random.nextFloat() < JOG_CHANCE
                if (blocked || wantJog) {
                    val minLane = (homeLane - HOME_DRIFT).coerceAtLeast(0)
                    val maxLane = (homeLane + HOME_DRIFT).coerceAtMost(lanes - 1)
                    val stepRange = if (blocked) minLane..maxLane else (lane - 1)..(lane + 1)
                    val target = stepRange
                        .filter { it in minLane..maxLane && canStepTo(col, lane, it) }
                        .minByOrNull { abs(it - homeLane) * HOME_PULL + random.nextFloat() }
                    if (target != null) {
                        markV(col, lane, target)
                        waypoints.add(col to lane)
                        lane = target
                        waypoints.add(col to lane)
                    } else if (blocked) {
                        val reachMax = min(columns - 1, col + SIDESTEP_REACH)
                        var bypassed = false
                        outer@ for ( detour in (minLane..maxLane).shuffled(random)) {
                            if (detour == lane || !vFree(col, lane, detour)) continue
                            for (reach in (col + 1)..reachMax) {
                                if (!hFree(col, reach, detour)) break
                                if (vFree(reach, detour, lane) && !hUsed[reach][lane]) {
                                    markV(col, lane, detour)
                                    markH(col, reach, detour)
                                    markV(reach, detour, lane)
                                    waypoints.add(col to lane)
                                    waypoints.add(col to detour)
                                    waypoints.add(reach to detour)
                                    waypoints.add(reach to lane)
                                    col = reach
                                    bypassed = true
                                    break@outer
                                }
                            }
                        }
                        if (!bypassed) break
                    }
                }
                hUsed[col][lane] = true
                col++
                if (col - startCol >= 3 && col < columns && random.nextFloat() < TERMINATE_CHANCE) {
                    break
                }
            }
            waypoints.add(col to lane)
            if (col - startCol < 2) continue

            val pixelCorners = waypoints.map { (c, l) ->
                Offset(padX + c * columnWidth, padY + l * laneGap)
            }
            val trace = buildMitredTrace(
                corners = pixelCorners,
                chamfer = min(laneGap * 0.45f, columnWidth * 0.32f),
                revealDelayMs = built * 55f
            )
            if (trace.segments.isNotEmpty()) {
                traces.add(trace)
                built++
            }
        }
    }

    private fun buildMitredTrace(
        corners: List<Offset>,
        chamfer: Float,
        revealDelayMs: Float
    ): Trace {
        val points = ArrayList<Offset>(corners.size * 2)
        val cornerIndices = HashSet<Int>()
        points.add(corners.first())
        for (i in 1 until corners.lastIndex) {
            val prev = corners[i - 1]
            val curr = corners[i]
            val next = corners[i + 1]
            val inVec = curr - prev
            val outVec = next - curr
            val inLen = hypot(inVec.x, inVec.y)
            val outLen = hypot(outVec.x, outVec.y)
            val cut = minOf(chamfer, inLen * 0.45f, outLen * 0.45f)
            if (cut > 1f) {
                points.add(curr - inVec * (cut / inLen))
                cornerIndices.add(points.lastIndex)
                points.add(curr + outVec * (cut / outLen))
            } else {
                points.add(curr)
            }
        }
        points.add(corners.last())

        val segments = ArrayList<Segment>(points.size - 1)
        val pads = ArrayList<Pad>()
        var distance = 0f
        pads.add(Pad(points.first().x, points.first().y, 0f, endpoint = true))
        for (i in 0 until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            val len = hypot(b.x - a.x, b.y - a.y)
            if (len > 0.5f) {
                segments.add(Segment(a.x, a.y, b.x, b.y, len, distance))
                if (i in cornerIndices && random.nextFloat() < 0.35f) {
                    pads.add(
                        Pad(
                            x = (a.x + b.x) * 0.5f,
                            y = (a.y + b.y) * 0.5f,
                            distance = distance + len * 0.5f,
                            endpoint = false
                        )
                    )
                }
                distance += len
            }
        }
        pads.add(Pad(points.last().x, points.last().y, distance, endpoint = true))
        return Trace(segments, distance, pads, revealDelayMs)
    }

    private fun segmentAt(trace: Trace, distance: Float): Segment {
        val target = distance.coerceIn(0f, trace.totalLength)
        for (seg in trace.segments) {
            if (target <= seg.startDistance + seg.length) return seg
        }
        return trace.segments.last()
    }

    private fun pointAt(trace: Trace, distance: Float): Offset {
        val seg = segmentAt(trace, distance)
        val along = (distance - seg.startDistance).coerceIn(0f, seg.length)
        return Offset(seg.x0 + seg.ux * along, seg.y0 + seg.uy * along)
    }

    private fun smoothstep(t: Float): Float = t * t * (3f - 2f * t)

    private companion object {
        const val MIN_SCALE = 0.4f
        const val MAX_SCALE = 3f
        const val MIN_SPEED = 0.2f
        const val MAX_SPEED = 5f
        const val BASE_LANES = 10
        const val BASE_COLUMNS = 12
        const val JOG_CHANCE = 0.42f
        const val HOME_DRIFT = 2
        const val HOME_PULL = 0.75f
        const val EDGE_START_CHANCE = 0.6f
        const val TERMINATE_CHANCE = 0.3f
        const val SIDESTEP_REACH = 3
        const val PULSE_SPEED = 0.3f
        const val TAIL_LENGTH = 0.11f
        const val TAIL_STEPS = 20
        const val TAIL_WIDTH = 5.2f
        const val TAIL_SWELL = 0.12f
        const val PULSE_INTERVAL_MS = 5_000f
        const val ROUTE_INTERVAL_MS = 60_000f
        const val REROUTE_FRACTION = 0.25f
    }
}

private class MatrixRenderer(
    private val random: Random = Random.Default
) : AmbienceRenderer {

    private class Column(
        var xNorm: Float,
        var z: Float,
        var y: Float,
        var speed: Float,
        val length: Int,
        val glyphs: CharArray,
        var mutateCooldownMs: Float
    )

    private class HeldGlyph(
        val char: Char,
        val x: Float,
        val y: Float,
        var lift: Float = 0f,
        var pulsePhase: Float = 0f
    ) {
        val buffer = charArrayOf(char)
    }

    private val columns = ArrayList<Column>(MAX_COLUMNS)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.LEFT
    }
    private var bounds = Size.Zero
    private var alphabetIndex = 0
    private var columnCount = COLUMNS
    private var lanesTaken = 0
    private var dragAccumulator = 0f
    private var frozen = false
    private var motion = 1f
    private var heldGlyph: HeldGlyph? = null

    override val hasVariants: Boolean = true

    override var scale: Float = 1f
        set(value) {
            val next = value.coerceIn(MIN_SCALE, MAX_SCALE)
            if (next == field) return
            field = next
            columnCount = (COLUMNS / next).roundToInt().coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        }

    override var speed: Float = 1f
        set(value) {
            field = value.coerceIn(MIN_SPEED, MAX_SPEED)
        }

    override fun update(dt: Float, size: Size) {
        if (size.isEmpty()) return
        if (bounds != size || columns.isEmpty()) {
            bounds = size
            lanesTaken = 0
            columns.clear()
            repeat(columnCount) { columns.add(spawnColumn(size, initialScatter = true)) }
            columns.sortBy { it.z }
        }
        reconcileColumnCount(size)

        val sec = dt / 1000f
        val targetMotion = if (frozen) 0f else 1f
        motion += (targetMotion - motion) * (dt / 180f).coerceIn(0f, 1f)
        heldGlyph?.let { held ->
            val targetLift = if (frozen) 1f else 0f
            held.lift += (targetLift - held.lift) * (dt / 160f).coerceIn(0f, 1f)
            held.pulsePhase += sec * 3.2f
            if (!frozen && held.lift < 0.02f) heldGlyph = null
        }

        val effectiveSec = sec * speed * motion
        val glyphStep = glyphStepPx(size)

        for (col in columns) {
            val step = glyphStep * (0.68f + 0.46f * col.z)
            col.y += col.speed * effectiveSec
            col.mutateCooldownMs -= dt * motion
            if (col.mutateCooldownMs <= 0f) {
                col.glyphs[random.nextInt(col.glyphs.size)] = randomGlyph()
                col.mutateCooldownMs = 120f + random.nextFloat() * 260f
            }
            val tailTop = col.y - col.length * step
            if (tailTop > size.height) {
                resetColumn(col, size)
            }
        }
    }

    override fun DrawScope.draw(tint: Color) {
        val baseStep = glyphStepPx(size)
        val dimFactor = 1f - (heldGlyph?.lift ?: 0f) * 0.45f
        val nativeCanvas = drawContext.canvas.nativeCanvas

        val headColorInt = tint.toArgb()
        val tailColorInt = tint.copy(
            red = tint.red * 0.85f,
            green = (tint.green * 1.05f).coerceAtMost(1f),
            blue = tint.blue * 0.85f,
            alpha = 1f
        ).toArgb()
        val headRgb = headColorInt and 0x00FFFFFF
        val tailRgb = tailColorInt and 0x00FFFFFF

        textPaint.textAlign = Paint.Align.LEFT

        for (col in columns) {
            val depthScale = 0.68f + 0.46f * col.z
            val step = baseStep * depthScale
            textPaint.textSize = step * GLYPH_TEXT_SCALE
            val depthAlpha = (0.36f + 0.64f * col.z) * dimFactor
            val x = col.xNorm * size.width
            val headRow = (col.y / step).toInt()
            val glyphs = col.glyphs
            val glyphCount = glyphs.size

            for (i in 0 until col.length) {
                val gy = (headRow - i) * step
                if (gy < -step || gy > size.height + step) continue

                val glyphIdx = (headRow - i).mod(glyphCount)
                val tailProgress = i.toFloat() / col.length
                val alpha = if (i == 0) {
                    0.62f * depthAlpha
                } else {
                    val inv = 1f - tailProgress
                    inv * inv * 0.30f * depthAlpha
                }
                val alphaInt = (alpha * 255f).toInt().coerceIn(0, 255)
                if (alphaInt <= 2) continue

                textPaint.color = (alphaInt shl 24) or (if (i == 0) headRgb else tailRgb)
                nativeCanvas.drawText(glyphs, glyphIdx, 1, x, gy, textPaint)
            }
        }

        heldGlyph?.let { held ->
            val lift = held.lift
            val pulse = 1f + 0.06f * sin(held.pulsePhase)
            val bigStep = baseStep * (1f + 1.1f * lift) * pulse
            val center = Offset(held.x, held.y)
            drawCircle(
                color = tint.copy(alpha = 0.16f * lift),
                radius = bigStep * 1.1f,
                center = center
            )
            val alphaInt = ((0.85f * lift).coerceIn(0f, 1f) * 255f).toInt()
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = bigStep * GLYPH_TEXT_SCALE
            textPaint.color = (alphaInt shl 24) or headRgb
            val baselineY = center.y - (textPaint.descent() + textPaint.ascent()) * 0.5f
            nativeCanvas.drawText(held.buffer, 0, 1, center.x, baselineY, textPaint)
        }
    }

    override fun onTap(offset: Offset, size: Size) {
        if (columns.isEmpty() || size.width <= 0f) return
        val xNorm = (offset.x / size.width).coerceIn(0f, 0.98f)
        val nearest = columns.minByOrNull { abs(it.xNorm - xNorm) } ?: return
        nearest.xNorm = xNorm
        nearest.z = 0.95f
        nearest.y = offset.y
        nearest.speed = size.height * (0.42f + random.nextFloat() * 0.25f)
        for (i in nearest.glyphs.indices) nearest.glyphs[i] = randomGlyph()
    }

    override fun onDoubleTap() {
        alphabetIndex = (alphabetIndex + 1) % ALPHABETS.size
        for (col in columns) {
            for (i in col.glyphs.indices) col.glyphs[i] = randomGlyph()
        }
    }

    override fun onLongPress(offset: Offset, size: Size) {
        frozen = true
        val baseStep = glyphStepPx(size)
        val nearest = columns.minByOrNull { abs(it.xNorm * size.width - offset.x) }
        val char = nearest?.let { col ->
            val step = baseStep * (0.68f + 0.46f * col.z)
            val row = (offset.y / step).toInt()
            col.glyphs[row.mod(col.glyphs.size)]
        } ?: randomGlyph()
        heldGlyph = HeldGlyph(char = char, x = offset.x, y = offset.y)
    }

    override fun onRelease() {
        frozen = false
    }

    override fun onDrag(pan: Offset, centroid: Offset, size: Size) {
        if (abs(pan.x) > abs(pan.y) * 1.5f) {
            dragAccumulator += pan.x
            val threshold = size.width * RESHUFFLE_FRACTION
            if (threshold > 0f && abs(dragAccumulator) >= threshold) {
                dragAccumulator = 0f
                reshuffle(size)
            }
        } else if (abs(pan.y) > abs(pan.x) * 1.5f && size.height > 0f) {
            speed *= 1f - (pan.y / size.height) * 1.8f
        }
    }

    private fun reshuffle(size: Size) {
        lanesTaken = random.nextInt(MAX_COLUMNS)
        for (col in columns) {
            col.xNorm = nextLaneX()
            col.y = random.nextFloat() * size.height * 1.3f - size.height * 0.3f
            for (i in col.glyphs.indices) col.glyphs[i] = randomGlyph()
        }
    }

    private fun reconcileColumnCount(size: Size) {
        if (columns.size == columnCount) return
        while (columns.size < columnCount) {
            columns.add(spawnColumn(size, initialScatter = true))
        }
        while (columns.size > columnCount) {
            columns.removeAt(columns.lastIndex)
        }
        columns.sortBy { it.z }
    }

    private fun glyphStepPx(size: Size): Float {
        val columnPitch = size.width / columnCount
        return (columnPitch / GLYPH_ADVANCE) * GLYPH_HEIGHT
    }

    private fun spawnColumn(size: Size, initialScatter: Boolean): Column {
        val z = sqrt(random.nextFloat())
        val step = glyphStepPx(size) * (0.68f + 0.46f * z)
        val length = 5 + random.nextInt(6)
        val startY = if (initialScatter) {
            random.nextFloat() * (size.height + length * step) - length * step * 0.5f
        } else {
            -random.nextFloat() * size.height * 0.5f
        }
        return Column(
            xNorm = nextLaneX(),
            z = z,
            y = startY,
            speed = size.height * (0.14f + 0.34f * z) * (0.85f + random.nextFloat() * 0.3f),
            length = length,
            glyphs = CharArray(length) { randomGlyph() },
            mutateCooldownMs = random.nextFloat() * 200f
        )
    }

    private fun resetColumn(col: Column, size: Size) {
        col.z = sqrt(random.nextFloat())
        col.xNorm = nextLaneX()
        col.y = -random.nextFloat() * size.height * 0.35f
        col.speed = size.height * (0.14f + 0.34f * col.z) * (0.85f + random.nextFloat() * 0.3f)
        for (i in col.glyphs.indices) col.glyphs[i] = randomGlyph()
    }

    private fun nextLaneX(): Float {
        val jitter = (random.nextFloat() - 0.5f) * (0.4f / columnCount)
        val norm = ((lanesTaken * 0.618_034f) % 1f + jitter).mod(1f)
        lanesTaken++
        return norm * 0.97f
    }

    private fun randomGlyph(): Char {
        val alphabet = ALPHABETS[alphabetIndex]
        return alphabet[random.nextInt(alphabet.length)]
    }

    private companion object {
        const val COLUMNS = 36
        const val MIN_COLUMNS = 20
        const val MAX_COLUMNS = 60
        const val GLYPH_ADVANCE = 0.70f
        const val GLYPH_HEIGHT = 0.86f
        const val GLYPH_TEXT_SCALE = 0.94f
        const val MIN_SCALE = COLUMNS.toFloat() / MAX_COLUMNS
        const val MAX_SCALE = COLUMNS.toFloat() / MIN_COLUMNS
        const val RESHUFFLE_FRACTION = 0.25f
        const val MIN_SPEED = 0.15f
        const val MAX_SPEED = 6f

        val ALPHABETS = arrayOf(
            buildString {
                for (c in 'ｱ'..'ﾝ') append(c)
                for (c in '0'..'9') append(c)
                append("SRMUSIC")
            },
            "0123456789ABCDEF",
            "{}[]()<>/\\|;:=+-*&^%$#@!?~",
            buildString {
                for (c in 'A'..'Z') append(c)
                for (c in '0'..'9') append(c)
            }
        )
    }
}
