package com.puttvision.screen

import android.graphics.PointF
import androidx.camera.core.ImageProxy
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Pure downsampled mask so the quad fitter can be regression-tested without CameraX. */
data class V23MaskGrid(
    val width: Int,
    val height: Int,
    val cells: BooleanArray,
    val source: String
) {
    init { require(width > 3 && height > 3 && cells.size == width * height) }
    operator fun get(x: Int, y: Int): Boolean = cells[y * width + x]
}

data class V23Point(val x: Double, val y: Double)

data class V23MatQuad(
    val tl: V23Point,
    val tr: V23Point,
    val br: V23Point,
    val bl: V23Point,
    val confidence: Double,
    val coverage: Double,
    val fillRatio: Double,
    val boundaryRms: Double,
    val source: String
) {
    fun corners(): List<V23Point> = listOf(tl, tr, br, bl)
}

data class V23MarkerlessDetection(
    val cornersPx: List<PointF>,
    val confidence: Double,
    val coverage: Double,
    val source: String,
    val hint: String
) {
    fun realPointsCm(): List<PointF> {
        val half = (V16MatGeometryRuntime.widthCm / 2.0).toFloat()
        val length = V16MatGeometryRuntime.lengthCm.toFloat()
        return listOf(
            PointF(-half, 0f),
            PointF(half, 0f),
            PointF(half, length),
            PointF(-half, length)
        )
    }

    fun fitImagePoints(): List<PointF> {
        if (cornersPx.size != 4) return emptyList()
        // detector: TL,TR,BR,BL -> calibration convention: BL,BR,TR,TL
        return listOf(cornersPx[3], cornersPx[2], cornersPx[1], cornersPx[0])
    }

    fun homography(frame: FrameInfo): Homography? =
        Homography.fromPoints(fitImagePoints(), realPointsCm(), frame)
}

private data class V23Line(val a: Double, val b: Double, val rms: Double, val count: Int)

/** Finds a perspective quadrilateral from a filled mat-like component, not just an axis-aligned box. */
object V23MatQuadFitter {
    fun fit(input: V23MaskGrid): V23MatQuad? {
        val grid = closeSmallHoles(input)
        val components = components(grid)
        if (components.isEmpty()) return null
        return components.mapNotNull { fitComponent(grid, it) }.maxByOrNull { it.confidence }
    }

    private fun closeSmallHoles(input: V23MaskGrid): V23MaskGrid {
        val out = input.cells.copyOf()
        for (y in 1 until input.height - 1) {
            for (x in 1 until input.width - 1) {
                if (input[x, y]) continue
                var neighbors = 0
                for (yy in y - 1..y + 1) for (xx in x - 1..x + 1) {
                    if (xx == x && yy == y) continue
                    if (input[xx, yy]) neighbors++
                }
                if (neighbors >= 6) out[y * input.width + x] = true
            }
        }
        return V23MaskGrid(input.width, input.height, out, input.source)
    }

    private fun components(grid: V23MaskGrid): List<IntArray> {
        val visited = BooleanArray(grid.cells.size)
        val result = ArrayList<IntArray>()
        val minSize = max(80, (grid.width * grid.height * .008).toInt())
        val queue = IntArray(grid.cells.size)
        for (start in grid.cells.indices) {
            if (!grid.cells[start] || visited[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            val members = ArrayList<Int>()
            while (head < tail) {
                val idx = queue[head++]
                members += idx
                val x = idx % grid.width
                val y = idx / grid.width
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until grid.width || ny !in 0 until grid.height) continue
                    val ni = ny * grid.width + nx
                    if (!visited[ni] && grid.cells[ni]) {
                        visited[ni] = true
                        queue[tail++] = ni
                    }
                }
            }
            if (members.size >= minSize) result += members.toIntArray()
        }
        return result.sortedByDescending { it.size }.take(12)
    }

    private fun fitComponent(grid: V23MaskGrid, component: IntArray): V23MatQuad? {
        var minX = grid.width
        var maxX = 0
        var minY = grid.height
        var maxY = 0
        component.forEach { idx ->
            val x = idx % grid.width
            val y = idx / grid.width
            minX = min(minX, x); maxX = max(maxX, x)
            minY = min(minY, y); maxY = max(maxY, y)
        }
        val bw = maxX - minX + 1
        val bh = maxY - minY + 1
        if (bw < grid.width * .10 || bh < grid.height * .22) return null
        if (maxY < grid.height * .58 || minY > grid.height * .79) return null
        val bboxCoverage = bw.toDouble() * bh / (grid.width.toDouble() * grid.height)
        if (bboxCoverage !in .025..0.76) return null
        val fill = component.size.toDouble() / (bw * bh).coerceAtLeast(1)
        if (fill < .24) return null

        val rowMin = IntArray(grid.height) { Int.MAX_VALUE }
        val rowMax = IntArray(grid.height) { Int.MIN_VALUE }
        val colMin = IntArray(grid.width) { Int.MAX_VALUE }
        val colMax = IntArray(grid.width) { Int.MIN_VALUE }
        component.forEach { idx ->
            val x = idx % grid.width
            val y = idx / grid.width
            rowMin[y] = min(rowMin[y], x); rowMax[y] = max(rowMax[y], x)
            colMin[x] = min(colMin[x], y); colMax[x] = max(colMax[x], y)
        }

        val left = ArrayList<V23Point>()
        val right = ArrayList<V23Point>()
        var supportedRows = 0
        for (y in minY..maxY) {
            if (rowMin[y] <= rowMax[y] && rowMax[y] - rowMin[y] >= max(3, bw / 10)) {
                left += V23Point(y.toDouble(), rowMin[y].toDouble()) // independent y -> dependent x
                right += V23Point(y.toDouble(), rowMax[y].toDouble())
                supportedRows++
            }
        }
        val top = ArrayList<V23Point>()
        val bottom = ArrayList<V23Point>()
        var supportedCols = 0
        for (x in minX..maxX) {
            if (colMin[x] <= colMax[x] && colMax[x] - colMin[x] >= max(3, bh / 12)) {
                top += V23Point(x.toDouble(), colMin[x].toDouble()) // independent x -> dependent y
                bottom += V23Point(x.toDouble(), colMax[x].toDouble())
                supportedCols++
            }
        }
        if (left.size < 8 || top.size < 8) return null
        val rowContinuity = supportedRows.toDouble() / bh
        val colContinuity = supportedCols.toDouble() / bw
        if (rowContinuity < .55 || colContinuity < .42) return null

        val l = robustLine(left) ?: return null
        val r = robustLine(right) ?: return null
        val t = robustLine(top) ?: return null
        val b = robustLine(bottom) ?: return null
        val tl = intersectSideEdge(l, t) ?: return null
        val tr = intersectSideEdge(r, t) ?: return null
        val br = intersectSideEdge(r, b) ?: return null
        val bl = intersectSideEdge(l, b) ?: return null

        val corners = listOf(tl, tr, br, bl)
        if (corners.any { it.x !in -grid.width * .10..grid.width * 1.10 || it.y !in -grid.height * .10..grid.height * 1.10 }) return null
        val area = polygonArea(corners)
        val coverage = area / (grid.width.toDouble() * grid.height)
        if (coverage !in .025..0.72) return null
        val topW = distance(tl, tr)
        val bottomW = distance(bl, br)
        val leftH = distance(tl, bl)
        val rightH = distance(tr, br)
        if (topW < grid.width * .07 || bottomW < grid.width * .08) return null
        val widthRatio = topW / bottomW.coerceAtLeast(.01)
        if (widthRatio !in .28..1.38) return null
        val aspect = ((leftH + rightH) * .5) / ((topW + bottomW) * .5).coerceAtLeast(.01)
        if (aspect !in .62..9.0) return null
        val centerX = corners.map { it.x }.average() / grid.width
        if (centerX !in .10..0.90) return null
        val bottomY = (bl.y + br.y) * .5 / grid.height
        if (bottomY < .58) return null

        val rms = sqrt((l.rms*l.rms + r.rms*r.rms + t.rms*t.rms + b.rms*b.rms) / 4.0)
        val rmsNorm = rms / hypot(grid.width.toDouble(), grid.height.toDouble())
        if (rmsNorm > .045) return null

        val geometryScore = when {
            widthRatio in .48..1.12 -> 1.0
            widthRatio in .35..1.25 -> .80
            else -> .55
        }
        val coverageScore = when {
            coverage in .10..0.52 -> 1.0
            coverage in .06..0.62 -> .82
            else -> .62
        }
        val residualScore = (1.0 - rmsNorm / .045).coerceIn(0.0, 1.0)
        val confidence = (
            .14 +
            fill.coerceIn(.24, .92) * .22 +
            rowContinuity.coerceIn(0.0, 1.0) * .16 +
            colContinuity.coerceIn(0.0, 1.0) * .10 +
            geometryScore * .15 +
            coverageScore * .09 +
            residualScore * .14
        ).coerceIn(0.0, .98)
        if (confidence < .64) return null
        return V23MatQuad(tl, tr, br, bl, confidence, coverage, fill, rms, grid.source)
    }

    private fun robustLine(points: List<V23Point>): V23Line? {
        var selected = points
        var fit = leastSquares(selected) ?: return null
        repeat(2) {
            val errors = selected.map { abs(it.y - (fit.a * it.x + fit.b)) }.sorted()
            val median = errors[errors.size / 2]
            val threshold = max(1.25, median * 2.8)
            val filtered = selected.filter { abs(it.y - (fit.a * it.x + fit.b)) <= threshold }
            if (filtered.size >= max(6, points.size / 3)) {
                selected = filtered
                fit = leastSquares(selected) ?: fit
            }
        }
        return fit
    }

    private fun leastSquares(points: List<V23Point>): V23Line? {
        if (points.size < 2) return null
        val mx = points.map { it.x }.average()
        val my = points.map { it.y }.average()
        var den = 0.0
        var num = 0.0
        points.forEach {
            val dx = it.x - mx
            den += dx * dx
            num += dx * (it.y - my)
        }
        if (den < 1e-9) return null
        val a = num / den
        val b = my - a * mx
        val rms = sqrt(points.sumOf { val e = it.y - (a * it.x + b); e * e } / points.size)
        return V23Line(a, b, rms, points.size)
    }

    /** side: x=a*y+b, edge: y=a*x+b. */
    private fun intersectSideEdge(side: V23Line, edge: V23Line): V23Point? {
        val den = 1.0 - edge.a * side.a
        if (abs(den) < 1e-7) return null
        val y = (edge.a * side.b + edge.b) / den
        val x = side.a * y + side.b
        if (!x.isFinite() || !y.isFinite()) return null
        return V23Point(x, y)
    }

    private fun distance(a: V23Point, b: V23Point) = hypot(a.x - b.x, a.y - b.y)
    private fun polygonArea(points: List<V23Point>): Double {
        var sum = 0.0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) * .5
    }
}

/** Builds green and dark-neutral masks directly from YUV_420_888 planes; no full Bitmap allocation. */
object V23YuvMatDetector {
    fun detect(image: ImageProxy): V23MarkerlessDetection? {
        if (image.width < 120 || image.height < 120 || image.planes.size < 3) return null
        val gridW = min(180, max(96, image.width / 3))
        val gridH = max(72, (gridW.toDouble() * image.height / image.width).toInt())
        val green = BooleanArray(gridW * gridH)
        val dark = BooleanArray(gridW * gridH)
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        fun sample(buffer: java.nio.ByteBuffer, rowStride: Int, pixelStride: Int, x: Int, y: Int): Int? {
            val index = y * rowStride + x * pixelStride
            if (index < 0 || index >= buffer.limit()) return null
            return buffer.get(index).toInt() and 0xff
        }

        for (gy in 0 until gridH) {
            val py = ((gy + .5) * image.height / gridH).toInt().coerceIn(0, image.height - 1)
            if (py < image.height * .035) continue
            for (gx in 0 until gridW) {
                val px = ((gx + .5) * image.width / gridW).toInt().coerceIn(0, image.width - 1)
                val yy = sample(yBuffer, yPlane.rowStride, yPlane.pixelStride, px, py) ?: continue
                val ux = px / 2
                val uy = py / 2
                val uu = sample(uBuffer, uPlane.rowStride, uPlane.pixelStride, ux, uy) ?: 128
                val vv = sample(vBuffer, vPlane.rowStride, vPlane.pixelStride, ux, uy) ?: 128
                val u = uu - 128.0
                val v = vv - 128.0
                val r = (yy + 1.402 * v).coerceIn(0.0, 255.0)
                val g = (yy - .344136 * u - .714136 * v).coerceIn(0.0, 255.0)
                val b = (yy + 1.772 * u).coerceIn(0.0, 255.0)
                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))
                val avg = (r + g + b) / 3.0
                val idx = gy * gridW + gx
                green[idx] = g >= 38.0 && g > r * 1.055 && g > b * 1.025 && g - min(r, b) >= 8.0
                dark[idx] = avg in 22.0..108.0 && maxC - minC <= 25.0 && abs(u) <= 22.0 && abs(v) <= 22.0
            }
        }

        val candidates = listOf(
            V23MaskGrid(gridW, gridH, green, "MARKERLESS_GREEN"),
            V23MaskGrid(gridW, gridH, dark, "MARKERLESS_DARK")
        ).mapNotNull { V23MatQuadFitter.fit(it) }
        val best = candidates.maxByOrNull { it.confidence + if (it.source.contains("GREEN")) .035 else 0.0 } ?: return null
        val sx = (image.width - 1).toDouble() / (gridW - 1).coerceAtLeast(1)
        val sy = (image.height - 1).toDouble() / (gridH - 1).coerceAtLeast(1)
        val corners = best.corners().map { PointF((it.x * sx).toFloat(), (it.y * sy).toFloat()) }
        return V23MarkerlessDetection(
            cornersPx = corners,
            confidence = best.confidence,
            coverage = best.coverage,
            source = best.source,
            hint = if (best.confidence >= .84) "매트 자동 인식 완료" else "매트 윤곽 확인중"
        )
    }
}

/** Conservative temporal gate. One weak frame is tolerated; large corner jumps reset immediately. */
class V23MarkerlessStability(
    private val requiredHits: Int = 4
) {
    private var previous: List<PointF>? = null
    private var misses = 0
    var hits: Int = 0
        private set

    fun update(candidate: V23MarkerlessDetection, frameWidth: Int, frameHeight: Int): Int {
        val old = previous
        val diag = hypot(frameWidth.toDouble(), frameHeight.toDouble()).coerceAtLeast(1.0)
        val movement = if (old != null && old.size == candidate.cornersPx.size) {
            old.indices.map { i ->
                hypot(
                    (old[i].x - candidate.cornersPx[i].x).toDouble(),
                    (old[i].y - candidate.cornersPx[i].y).toDouble()
                )
            }.average() / diag
        } else Double.POSITIVE_INFINITY
        hits = if (movement <= .0115) hits + 1 else 1
        previous = candidate.cornersPx.map { PointF(it.x, it.y) }
        misses = 0
        return hits
    }

    fun miss() {
        misses++
        if (misses >= 2) reset()
    }

    fun ready(candidate: V23MarkerlessDetection): Boolean = hits >= requiredHits && candidate.confidence >= .80

    fun reset() {
        previous = null
        hits = 0
        misses = 0
    }
}