package com.randomautoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object ClickerPreferences {
    private const val PREFS_NAME = "random_auto_clicker_prefs"
    private const val KEY_MIN_INTERVAL = "min_interval_ms"
    private const val KEY_MAX_INTERVAL = "max_interval_ms"
    private const val KEY_RADIUS = "random_radius_px"
    private const val KEY_MIN_HOLD = "min_hold_ms"
    private const val KEY_MAX_HOLD = "max_hold_ms"
    private const val KEY_RANDOM_ORDER = "random_point_order"
    private const val KEY_STOP_MODE = "stop_mode"
    private const val KEY_RUN_DURATION = "run_duration_sec"
    private const val KEY_STOP_CYCLES = "stop_cycles"
    private const val KEY_ANTI_DETECT = "anti_detection_enabled"
    private const val KEY_ANTI_INT_JITTER = "anti_detect_interval_jitter_ms"
    private const val KEY_ANTI_RADIUS = "anti_detect_radius_px"
    private const val KEY_ANTI_HOLD_JITTER = "anti_detect_hold_jitter_ms"

    const val MODE_INFINITE = "infinite"
    const val MODE_DURATION = "duration"
    const val MODE_CYCLES = "cycles"

    const val MIN_INTERVAL_MS = 0L
    const val MIN_HOLD_MS = 10L
    const val DEFAULT_MIN_INTERVAL = 40L
    const val DEFAULT_MAX_INTERVAL = 50L
    const val DEFAULT_RADIUS = 100f
    const val DEFAULT_MIN_HOLD = 85L
    const val DEFAULT_MAX_HOLD = 90L
    const val DEFAULT_RANDOM_ORDER = false
    const val DEFAULT_STOP_MODE = "infinite"
    const val DEFAULT_RUN_DURATION = 300L
    const val DEFAULT_STOP_CYCLES = 10L
    const val DEFAULT_ANTI_DETECT = true
    const val DEFAULT_ANTI_INT_JITTER = 5L
    const val DEFAULT_ANTI_RADIUS = 8f
    const val DEFAULT_ANTI_HOLD_JITTER = 5L

    data class Config(
        val minIntervalMs: Long,
        val maxIntervalMs: Long,
        val radiusPx: Float,
        val minHoldMs: Long,
        val maxHoldMs: Long,
        val randomPointOrder: Boolean,
        val stopMode: String,
        val runDurationSec: Long,
        val stopCycles: Long,
        val antiDetectionEnabled: Boolean,
        val antiDetectIntervalJitterMs: Long,
        val antiDetectRadiusPx: Float,
        val antiDetectHoldJitterMs: Long
    ) {
        fun effectiveRadiusPx(): Float =
            if (antiDetectionEnabled) max(radiusPx, antiDetectRadiusPx) else radiusPx
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(context: Context): Config {
        val p = prefs(context)
        val minInt = p.getLong(KEY_MIN_INTERVAL, DEFAULT_MIN_INTERVAL).coerceAtLeast(MIN_INTERVAL_MS)
        val maxInt = p.getLong(KEY_MAX_INTERVAL, DEFAULT_MAX_INTERVAL).coerceAtLeast(minInt)
        val minHold = p.getLong(KEY_MIN_HOLD, DEFAULT_MIN_HOLD).coerceAtLeast(MIN_HOLD_MS)
        val maxHold = p.getLong(KEY_MAX_HOLD, DEFAULT_MAX_HOLD).coerceAtLeast(minHold)
        val radius = p.getFloat(KEY_RADIUS, DEFAULT_RADIUS).coerceIn(0f, 250f)
        val order = p.getBoolean(KEY_RANDOM_ORDER, DEFAULT_RANDOM_ORDER)
        val stopMode = p.getString(KEY_STOP_MODE, DEFAULT_STOP_MODE) ?: MODE_INFINITE
        val duration = p.getLong(KEY_RUN_DURATION, DEFAULT_RUN_DURATION).coerceIn(5L, 86400L)
        val cycles = p.getLong(KEY_STOP_CYCLES, DEFAULT_STOP_CYCLES).coerceIn(1L, 99999L)
        val antiDetect = p.getBoolean(KEY_ANTI_DETECT, DEFAULT_ANTI_DETECT)
        val antiIntJitter = p.getLong(KEY_ANTI_INT_JITTER, DEFAULT_ANTI_INT_JITTER).coerceIn(0L, 500L)
        val antiRad = p.getFloat(KEY_ANTI_RADIUS, DEFAULT_ANTI_RADIUS).coerceIn(0f, 100f)
        val antiHoldJitter = p.getLong(KEY_ANTI_HOLD_JITTER, DEFAULT_ANTI_HOLD_JITTER).coerceIn(0L, 300L)
        return Config(
            minInt, maxInt, radius, minHold, maxHold, order,
            stopMode, duration, cycles,
            antiDetect, antiIntJitter, antiRad, antiHoldJitter
        )
    }

    fun save(
        context: Context,
        rawMinInt: Long,
        rawMaxInt: Long,
        rawRadius: Float,
        rawMinHold: Long,
        rawMaxHold: Long,
        randomOrder: Boolean,
        rawStopMode: String,
        rawDurationSec: Long,
        rawStopCycles: Long,
        antiDetect: Boolean,
        rawAntiIntJitter: Long,
        rawAntiRadius: Float,
        rawAntiHoldJitter: Long
    ): Config {
        val minInt = rawMinInt.coerceAtLeast(MIN_INTERVAL_MS)
        val maxInt = rawMaxInt.coerceAtLeast(minInt)
        val minHold = rawMinHold.coerceAtLeast(MIN_HOLD_MS)
        val maxHold = rawMaxHold.coerceAtLeast(minHold)
        val radius = rawRadius.coerceIn(0f, 250f)
        val stopMode = when (rawStopMode) {
            MODE_DURATION, MODE_CYCLES -> rawStopMode
            else -> MODE_INFINITE
        }
        val duration = rawDurationSec.coerceIn(5L, 86400L)
        val cycles = rawStopCycles.coerceIn(1L, 99999L)
        val antiIntJitter = rawAntiIntJitter.coerceIn(0L, 500L)
        val antiRad = rawAntiRadius.coerceIn(0f, 100f)
        val antiHoldJitter = rawAntiHoldJitter.coerceIn(0L, 300L)

        prefs(context).edit()
            .putLong(KEY_MIN_INTERVAL, minInt)
            .putLong(KEY_MAX_INTERVAL, maxInt)
            .putFloat(KEY_RADIUS, radius)
            .putLong(KEY_MIN_HOLD, minHold)
            .putLong(KEY_MAX_HOLD, maxHold)
            .putBoolean(KEY_RANDOM_ORDER, randomOrder)
            .putString(KEY_STOP_MODE, stopMode)
            .putLong(KEY_RUN_DURATION, duration)
            .putLong(KEY_STOP_CYCLES, cycles)
            .putBoolean(KEY_ANTI_DETECT, antiDetect)
            .putLong(KEY_ANTI_INT_JITTER, antiIntJitter)
            .putFloat(KEY_ANTI_RADIUS, antiRad)
            .putLong(KEY_ANTI_HOLD_JITTER, antiHoldJitter)
            .apply()

        return Config(
            minInt, maxInt, radius, minHold, maxHold, randomOrder,
            stopMode, duration, cycles,
            antiDetect, antiIntJitter, antiRad, antiHoldJitter
        )
    }
}

class FloatingOverlayManager(
    private val context: Context,
    private val onToggleRun: () -> Unit,
    private val onToggleOrder: () -> Boolean,
    private val onCloseRequested: () -> Unit
) {
    companion object {
        const val MAX_POINTS = 15
    }

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var barView: LinearLayout? = null
    private var btnPlayPause: TextView? = null
    private var btnAdd: TextView? = null
    private var btnRemove: TextView? = null
    private var btnOrder: TextView? = null
    private val targets = mutableListOf<TargetEntry>()

    private var visible = false
    private var running = false
    private var radiusPx = 45f
    private var randomOrder = false

    private data class TargetEntry(
        val id: Int,
        val view: TargetCircleView,
        val params: WindowManager.LayoutParams
    )

    fun isOverlayVisible(): Boolean = visible

    fun show(radius: Float, order: Boolean) {
        radiusPx = radius
        randomOrder = order
        if (visible) {
            refreshRadius(radius)
            updateOrderIcon(order)
            return
        }
        createControlBar()
        if (targets.isEmpty()) addPoint()
        visible = true
        updateVisuals()
    }

    fun hide() {
        if (!visible) return
        running = false
        barView?.let { runCatching { wm.removeView(it) } }
        barView = null
        targets.forEach { runCatching { wm.removeView(it.view) } }
        targets.clear()
        visible = false
    }

    fun setRunning(isRun: Boolean) {
        running = isRun
        updateVisuals()
    }

    fun refreshRadius(radius: Float) {
        radiusPx = radius
        targets.forEach { entry ->
            val oldW = if (entry.view.width > 0) entry.view.width else entry.params.width
            val oldH = if (entry.view.height > 0) entry.view.height else entry.params.height
            val cx = entry.params.x + oldW / 2f
            val cy = entry.params.y + oldH / 2f
            val box = boxSize(radius)
            entry.view.update(entry.id, radius, running)
            entry.params.width = box
            entry.params.height = box
            entry.params.x = (cx - box / 2f).roundToInt()
            entry.params.y = (cy - box / 2f).roundToInt()
            runCatching { wm.updateViewLayout(entry.view, entry.params) }
        }
    }

    fun updateOrderIcon(order: Boolean) {
        randomOrder = order
        btnOrder?.text = if (order) "🎲" else "➡️"
        btnOrder?.background = roundedBg(if (order) "#2563EB" else "#1E293B", 10)
    }

    fun getTargetCenters(): List<PointF> {
        val loc = IntArray(2)
        return targets.map { entry ->
            entry.view.getLocationOnScreen(loc)
            PointF(loc[0] + entry.view.width / 2f, loc[1] + entry.view.height / 2f)
        }
    }

    private fun boxSize(radius: Float): Int = max(dp(48), (radius * 2f + dp(16)).roundToInt())

    @SuppressLint("ClickableViewAccessibility")
    private fun createControlBar() {
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = roundedBg("#F20F172A", 16, "#334155")
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(160)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val drag = makeBtn("⠿", "#00000000", "#94A3B8")
        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f
        drag.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> { ix = params.x; iy = params.y; tx = ev.rawX; ty = ev.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    params.x = ix + (ev.rawX - tx).roundToInt()
                    params.y = iy + (ev.rawY - ty).roundToInt()
                    runCatching { wm.updateViewLayout(bar, params) }
                    true
                }
                else -> false
            }
        }
        bar.addView(drag)

        val play = makeBtn("▶", "#16A34A", "#FFFFFF").apply { setOnClickListener { onToggleRun() } }
        btnPlayPause = play; bar.addView(play)

        val add = makeBtn("+", "#1E293B", "#F8FAFC").apply { setOnClickListener { if (!running) addPoint() } }
        btnAdd = add; bar.addView(add)

        val rem = makeBtn("-", "#1E293B", "#F8FAFC").apply { setOnClickListener { if (!running) removePoint() } }
        btnRemove = rem; bar.addView(rem)

        val ord = makeBtn(
            if (randomOrder) "🎲" else "➡️",
            if (randomOrder) "#2563EB" else "#1E293B",
            "#FFFFFF"
        ).apply {
            setOnClickListener { updateOrderIcon(onToggleOrder()) }
        }
        btnOrder = ord; bar.addView(ord)

        val close = makeBtn("✕", "#4C0519", "#FDA4AF", bottomMarginDp = 0).apply {
            setOnClickListener { onCloseRequested() }
        }
        bar.addView(close)

        barView = bar
        wm.addView(bar, params)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addPoint() {
        if (targets.size >= MAX_POINTS) return
        val id = targets.size + 1
        val size = boxSize(radiusPx)
        val view = TargetCircleView(context).apply { update(id, radiusPx, running) }
        val dm = context.resources.displayMetrics
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dm.widthPixels / 2 - size / 2 + ((id - 1) * dp(26)) % dp(140) - dp(24)
            y = dm.heightPixels / 2 - size / 2 + ((id - 1) * dp(32)) % dp(160)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f
        view.setOnTouchListener { _, ev ->
            if (running) return@setOnTouchListener false
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> { ix = params.x; iy = params.y; tx = ev.rawX; ty = ev.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    params.x = ix + (ev.rawX - tx).roundToInt()
                    params.y = iy + (ev.rawY - ty).roundToInt()
                    runCatching { wm.updateViewLayout(view, params) }
                    true
                }
                else -> false
            }
        }
        targets.add(TargetEntry(id, view, params))
        wm.addView(view, params)
    }

    private fun removePoint() {
        if (targets.size <= 1) return
        val last = targets.removeAt(targets.lastIndex)
        runCatching { wm.removeView(last.view) }
    }

    private fun updateVisuals() {
        btnPlayPause?.text = if (running) "⏸" else "▶"
        btnPlayPause?.background = roundedBg(if (running) "#EA580C" else "#16A34A", 10)
        btnAdd?.alpha = if (running) 0.4f else 1f
        btnRemove?.alpha = if (running) 0.4f else 1f
        targets.forEach { entry ->
            entry.view.update(entry.id, radiusPx, running)
            entry.params.flags = if (running) {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            } else {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            }
            runCatching { wm.updateViewLayout(entry.view, entry.params) }
        }
    }

    private fun makeBtn(label: String, bgHex: String, textHex: String, bottomMarginDp: Int = 4): TextView =
        TextView(context).apply {
            text = label
            setTextColor(Color.parseColor(textHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            background = roundedBg(bgHex, 10)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { bottomMargin = dp(bottomMarginDp) }
        }

    private fun roundedBg(fillHex: String, radDp: Int, strokeHex: String? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(fillHex))
            cornerRadius = dp(radDp).toFloat()
            if (strokeHex != null) setStroke(dp(1), Color.parseColor(strokeHex))
        }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics
    ).roundToInt()

    private class TargetCircleView(context: Context) : View(context) {
        private var id = 1; private var radius = 45f; private var run = false
        private val density = context.resources.displayMetrics.density
        private val coreRadius = 15f * density
        private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = Color.WHITE; strokeWidth = 2f * density
        }
        private val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.8f * density
            pathEffect = DashPathEffect(floatArrayOf(6f * density, 4f * density), 0f)
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER; isFakeBoldText = true; textSize = 14f * density
        }

        fun update(n: Int, r: Float, isRun: Boolean) { id = n; radius = r; run = isRun; invalidate() }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f; val cy = height / 2f
            val hex = if (run) "#16A34A" else "#2563EB"
            val fHex = if (run) "#1F16A34A" else "#1F2563EB"
            if (radius > 0f) {
                fill.color = Color.parseColor(fHex); dashed.color = Color.parseColor(hex)
                canvas.drawCircle(cx, cy, radius, fill); canvas.drawCircle(cx, cy, radius, dashed)
            }
            core.color = Color.parseColor(hex)
            canvas.drawCircle(cx, cy, coreRadius, core); canvas.drawCircle(cx, cy, coreRadius, border)
            canvas.drawText(id.toString(), cx, cy - (txt.descent() + txt.ascent()) / 2f, txt)
        }
    }
}

class RandomAutoClickService : AccessibilityService() {
    companion object {
        const val ACTION_APPLY_CONFIG = "com.randomautoclicker.APPLY_CONFIG"
        var instance: RandomAutoClickService? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var sessionId = 0L
    private var seqIndex = 0
    private var tapsInCycle = 0
    private var completedCycles = 0L
    private var startedAtMs = 0L
    private lateinit var cfg: ClickerPreferences.Config
    private var overlay: FloatingOverlayManager? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_APPLY_CONFIG) reloadConfig()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        cfg = ClickerPreferences.load(this)
        overlay = FloatingOverlayManager(
            context = this,
            onToggleRun = { if (running) stopClicking() else startClicking() },
            onToggleOrder = {
                val next = !cfg.randomPointOrder
                cfg = ClickerPreferences.save(
                    this, cfg.minIntervalMs, cfg.maxIntervalMs, cfg.radiusPx,
                    cfg.minHoldMs, cfg.maxHoldMs, next,
                    cfg.stopMode, cfg.runDurationSec, cfg.stopCycles,
                    cfg.antiDetectionEnabled, cfg.antiDetectIntervalJitterMs,
                    cfg.antiDetectRadiusPx, cfg.antiDetectHoldJitterMs
                )
                next
            },
            onCloseRequested = { stopClicking(); overlay?.hide() }
        )
        ContextCompat.registerReceiver(this, receiver, IntentFilter(ACTION_APPLY_CONFIG), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun toggleOverlay() {
        val mgr = overlay ?: return
        cfg = ClickerPreferences.load(this)
        if (mgr.isOverlayVisible()) { stopClicking(); mgr.hide() }
        else mgr.show(cfg.effectiveRadiusPx(), cfg.randomPointOrder)
    }

    fun reloadConfig() {
        cfg = ClickerPreferences.load(this)
        overlay?.refreshRadius(cfg.effectiveRadiusPx())
        overlay?.updateOrderIcon(cfg.randomPointOrder)
    }

    private fun startClicking() {
        cfg = ClickerPreferences.load(this)
        sessionId += 1L
        running = true; seqIndex = 0; tapsInCycle = 0; completedCycles = 0L
        startedAtMs = System.currentTimeMillis()
        overlay?.setRunning(true)
        handler.removeCallbacks(loop)
        handler.post(loop)
    }

    private fun stopClicking() {
        running = false
        sessionId += 1L
        handler.removeCallbacks(loop)
        overlay?.setRunning(false)
    }

    private fun screenBounds(): Pair<Float, Float> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            Pair((b.width() - 4).coerceAtLeast(100).toFloat(), (b.height() - 4).coerceAtLeast(100).toFloat())
        } else {
            val pt = android.graphics.Point()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealSize(pt)
            Pair((pt.x - 4).coerceAtLeast(100).toFloat(), (pt.y - 4).coerceAtLeast(100).toFloat())
        }
    }

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            val activeSession = sessionId
            if (cfg.stopMode == ClickerPreferences.MODE_DURATION &&
                (System.currentTimeMillis() - startedAtMs) / 1000L >= cfg.runDurationSec
            ) {
                stopClicking()
                Toast.makeText(this@RandomAutoClickService, "Da het gio chay tu dong", Toast.LENGTH_SHORT).show()
                return
            }
            val centers = overlay?.getTargetCenters().orEmpty()
            if (centers.isEmpty()) { stopClicking(); return }

            val pt = if (cfg.randomPointOrder && centers.size > 1) {
                centers[Random.nextInt(centers.size)]
            } else {
                val p = centers[seqIndex % centers.size]
                seqIndex = (seqIndex + 1) % centers.size
                p
            }

            val (maxW, maxH) = screenBounds()
            val tap = sampleCircle(pt.x, pt.y, cfg.effectiveRadiusPx())
            val startX = tap.x.coerceIn(4f, maxW)
            val startY = tap.y.coerceIn(4f, maxH)
            val rawDx = if (cfg.antiDetectionEnabled) Random.nextFloat() * 3f - 1.5f else 1f
            val rawDy = if (cfg.antiDetectionEnabled) Random.nextFloat() * 3f - 1.5f else 1f
            val microDx = if (abs(rawDx) < 0.6f) 1f else rawDx
            val microDy = if (abs(rawDy) < 0.6f) 1f else rawDy
            val endX = (startX + microDx).coerceIn(4f, maxW)
            val endY = (startY + microDy).coerceIn(4f, maxH)

            val holdMs = sampleHoldWithAntiDetect()
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, holdMs))
                .build()

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    onGestureDone(activeSession, centers.size, succeeded = true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    onGestureDone(activeSession, centers.size, succeeded = false)
                }
            }, handler)

            if (!dispatched) {
                onGestureDone(activeSession, centers.size, succeeded = false)
            }
        }
    }

    private fun onGestureDone(activeSession: Long, centersCount: Int, succeeded: Boolean) {
        if (!running || activeSession != sessionId) return
        if (succeeded) {
            tapsInCycle += 1
            if (tapsInCycle >= centersCount) {
                tapsInCycle = 0
                completedCycles += 1L
                if (cfg.stopMode == ClickerPreferences.MODE_CYCLES && completedCycles >= cfg.stopCycles) {
                    stopClicking()
                    Toast.makeText(this@RandomAutoClickService, "Da hoan thanh ${cfg.stopCycles} vong", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }
        val delayMs = sampleIntervalWithAntiDetect()
        val waitMs = if (succeeded) delayMs else max(delayMs, 30L)
        if (waitMs <= 0L) {
            handler.post(loop)
        } else {
            handler.postDelayed(loop, waitMs)
        }
    }

    private fun sampleIntervalWithAntiDetect(): Long {
        val base = randLong(cfg.minIntervalMs, cfg.maxIntervalMs)
        if (!cfg.antiDetectionEnabled || cfg.antiDetectIntervalJitterMs <= 0L) return base
        val jitter = randLong(-cfg.antiDetectIntervalJitterMs, cfg.antiDetectIntervalJitterMs)
        return (base + jitter).coerceAtLeast(ClickerPreferences.MIN_INTERVAL_MS)
    }

    private fun sampleHoldWithAntiDetect(): Long {
        val base = randLong(cfg.minHoldMs, cfg.maxHoldMs)
        if (!cfg.antiDetectionEnabled || cfg.antiDetectHoldJitterMs <= 0L) return base
        val jitter = randLong(-cfg.antiDetectHoldJitterMs, cfg.antiDetectHoldJitterMs)
        return (base + jitter).coerceAtLeast(ClickerPreferences.MIN_HOLD_MS)
    }

    private fun randLong(min: Long, max: Long): Long = if (max <= min) min else Random.nextLong(min, max + 1L)

    private fun sampleCircle(cx: Float, cy: Float, rPx: Float): PointF {
        if (rPx <= 0f) return PointF(cx, cy)
        val r = sqrt(Random.nextDouble()) * rPx
        val theta = Random.nextDouble() * 2.0 * PI
        return PointF((cx + r * cos(theta)).toFloat(), (cy + r * sin(theta)).toFloat())
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { stopClicking() }
    override fun onDestroy() {
        super.onDestroy(); stopClicking(); overlay?.hide()
        runCatching { unregisterReceiver(receiver) }; instance = null
    }
}

class MainActivity : AppCompatActivity() {
    private lateinit var statusBox: LinearLayout
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvStatusDesc: TextView
    private lateinit var edtMinInt: EditText
    private lateinit var edtMaxInt: EditText
    private lateinit var seekRadius: SeekBar
    private lateinit var edtRadius: EditText
    private lateinit var edtMinHold: EditText
    private lateinit var edtMaxHold: EditText
    private lateinit var rgStopMode: RadioGroup
    private lateinit var rbInfinite: RadioButton
    private lateinit var rbDuration: RadioButton
    private lateinit var rbCycles: RadioButton
    private lateinit var edtDurationSec: EditText
    private lateinit var edtStopCycles: EditText
    private lateinit var chkAntiDetect: CheckBox
    private lateinit var antiDetectPanel: LinearLayout
    private lateinit var edtAntiIntJitter: EditText
    private lateinit var edtAntiRadius: EditText
    private lateinit var edtAntiHoldJitter: EditText
    private lateinit var chkRandomOrder: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        loadToUi()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        chkRandomOrder.isChecked = ClickerPreferences.load(this).randomPointOrder
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F8FAFC"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = boxBg("#FFFFFF", "#E2E8F0", 16)
        }

        card.addView(label("Random Auto Clicker", 20f, true, "#0F172A"))
        card.addView(label("Thiet lap cham ngau nhien, chong phat hien & ap dung len thanh noi", 13f, false, "#64748B", topDp = 4))

        statusBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = marginLp(topDp = 16)
        }
        tvStatusTitle = label("", 14f, true, "#0F172A")
        tvStatusDesc = label("", 12f, false, "#475569", topDp = 4)
        statusBox.addView(tvStatusTitle); statusBox.addView(tvStatusDesc)
        card.addView(statusBox)

        card.addView(actionBtn("1. Bat Dich vu Tro nang (Accessibility)", "#0F172A", topDp = 14) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        card.addView(actionBtn("2. Hien / An Thanh dieu khien noi", "#2563EB", topDp = 8) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Vui long cap quyen Hien thi tren ung dung khac", Toast.LENGTH_SHORT).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                return@actionBtn
            }
            val svc = RandomAutoClickService.instance
            if (svc == null) {
                Toast.makeText(this, "Hay bam nut 1 de bat Dich vu Tro nang truoc", Toast.LENGTH_SHORT).show()
                return@actionBtn
            }
            svc.toggleOverlay()
            refreshStatus()
        })

        var testTapCount = 0
        lateinit var testPad: TextView
        testPad = TextView(this).apply {
            text = "O thu nghiem cham tai cho: 0 luot (keo diem so 1 vao day de test)"
            isClickable = true
            isFocusable = true
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#1E40AF"))
            background = boxBg("#EFF6FF", "#93C5FD", 12)
            layoutParams = marginLp(topDp = 10)
            setOnTouchListener { v, ev ->
                if (ev.action == MotionEvent.ACTION_DOWN) {
                    testTapCount += 1
                    testPad.text = "Da nhan ${testTapCount} luot cham · Toa do (${ev.rawX.roundToInt()}, ${ev.rawY.roundToInt()})"
                    v.performClick()
                }
                true
            }
        }
        card.addView(testPad)

        card.addView(label("Khoang thoi gian nghi ngau nhien (ms, tu 0 ms)", 14f, true, "#0F172A", topDp = 20))
        edtMinInt = numInput("Min (tu 0 ms)")
        edtMaxInt = numInput("Max (ms)")
        card.addView(twoColRow(edtMinInt, edtMaxInt))

        card.addView(label("Ban kinh cham ngau nhien quanh diem (px)", 14f, true, "#0F172A", topDp = 16))
        seekRadius = SeekBar(this).apply {
            max = 250
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        edtRadius = numInput("px").apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(80), dp(48)).apply { leftMargin = dp(10) }
        }
        seekRadius.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) edtRadius.setText(p.toString())
            }
            override fun onStartTrackingTouch(s: SeekBar?) = Unit
            override fun onStopTrackingTouch(s: SeekBar?) = Unit
        })
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = marginLp(topDp = 8)
            addView(seekRadius); addView(edtRadius)
        })

        card.addView(label("Thoi gian cham - giu ngon tay (ms, tu 10 ms)", 14f, true, "#0F172A", topDp = 16))
        edtMinHold = numInput("Cham Min (ms)")
        edtMaxHold = numInput("Cham Max (ms)")
        card.addView(twoColRow(edtMinHold, edtMaxHold))

        card.addView(label("Dung lai sau (Chon 1 dieu kien)", 14f, true, "#0F172A", topDp = 16))
        rbInfinite = RadioButton(this).apply { id = View.generateViewId(); text = "Chay vo han"; setTextColor(Color.parseColor("#0F172A")) }
        rbDuration = RadioButton(this).apply { id = View.generateViewId(); text = "Khoang thoi gian (giay)"; setTextColor(Color.parseColor("#0F172A")) }
        rbCycles = RadioButton(this).apply { id = View.generateViewId(); text = "So vong"; setTextColor(Color.parseColor("#0F172A")) }
        rgStopMode = RadioGroup(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = marginLp(topDp = 6)
            addView(rbInfinite); addView(rbDuration); addView(rbCycles)
        }
        card.addView(rgStopMode)

        edtDurationSec = numInput("Thoi gian chay (giay, vi du 300)").apply {
            layoutParams = marginLp(topDp = 6, heightDp = 48)
        }
        edtStopCycles = numInput("So vong chay (vi du 10)").apply {
            layoutParams = marginLp(topDp = 6, heightDp = 48)
        }
        card.addView(edtDurationSec)
        card.addView(edtStopCycles)

        rgStopMode.setOnCheckedChangeListener { _, checkedId ->
            edtDurationSec.visibility = if (checkedId == rbDuration.id) View.VISIBLE else View.GONE
            edtStopCycles.visibility = if (checkedId == rbCycles.id) View.VISIBLE else View.GONE
        }

        chkAntiDetect = CheckBox(this).apply {
            text = "Chong phat hien (Anti-detection)"
            setTextColor(Color.parseColor("#0F172A"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = marginLp(topDp = 14)
        }
        card.addView(chkAntiDetect)

        antiDetectPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = boxBg("#ECFDF5", "#A7F3D0", 12)
            layoutParams = marginLp(topDp = 6)
        }
        antiDetectPanel.addView(label("Nhieu nhip nghi (±ms), lech toa do toi thieu (px), giu ngon (±ms):", 11f, false, "#065F46"))
        edtAntiIntJitter = numInput("±ms nghi")
        edtAntiRadius = numInput("px lech")
        edtAntiHoldJitter = numInput("±ms giu")
        val antiRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = marginLp(topDp = 6)
            edtAntiIntJitter.layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
            edtAntiRadius.layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(8) }
            edtAntiHoldJitter.layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(8) }
            addView(edtAntiIntJitter); addView(edtAntiRadius); addView(edtAntiHoldJitter)
        }
        antiDetectPanel.addView(antiRow)
        card.addView(antiDetectPanel)

        chkAntiDetect.setOnCheckedChangeListener { _, isChecked ->
            antiDetectPanel.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        chkRandomOrder = CheckBox(this).apply {
            text = "Cham ngau nhien giua cac diem"
            setTextColor(Color.parseColor("#0F172A"))
            layoutParams = marginLp(topDp = 12)
        }
        card.addView(chkRandomOrder)

        card.addView(actionBtn("Luu & Ap dung cai dat", "#2563EB", topDp = 18) { saveAndApply() })

        scroll.addView(card)
        setContentView(scroll)
    }

    private fun refreshStatus() {
        val ready = RandomAutoClickService.instance != null
        statusBox.background = boxBg(
            if (ready) "#ECFDF5" else "#FEF2F2",
            if (ready) "#A7F3D0" else "#FECACA",
            12
        )
        tvStatusTitle.text = if (ready) "Trang thai Tro nang: Da san sang hoat dong"
        else "Trang thai Tro nang: Chua duoc cap quyen chay ngam"
        tvStatusDesc.text = if (ready) "Dich vu Tro nang dang bat. Ban co the mo thanh noi va chay auto click."
        else "Bam nut 1. Bat Dich vu Tro nang de cap quyen truoc khi chay."
    }

    private fun loadToUi() {
        val c = ClickerPreferences.load(this)
        edtMinInt.setText(c.minIntervalMs.toString())
        edtMaxInt.setText(c.maxIntervalMs.toString())
        val r = c.radiusPx.toInt().coerceIn(0, 250)
        edtRadius.setText(r.toString()); seekRadius.progress = r
        edtMinHold.setText(c.minHoldMs.toString())
        edtMaxHold.setText(c.maxHoldMs.toString())

        edtDurationSec.setText(c.runDurationSec.toString())
        edtStopCycles.setText(c.stopCycles.toString())
        when (c.stopMode) {
            ClickerPreferences.MODE_DURATION -> rgStopMode.check(rbDuration.id)
            ClickerPreferences.MODE_CYCLES -> rgStopMode.check(rbCycles.id)
            else -> rgStopMode.check(rbInfinite.id)
        }
        edtDurationSec.visibility = if (c.stopMode == ClickerPreferences.MODE_DURATION) View.VISIBLE else View.GONE
        edtStopCycles.visibility = if (c.stopMode == ClickerPreferences.MODE_CYCLES) View.VISIBLE else View.GONE

        chkAntiDetect.isChecked = c.antiDetectionEnabled
        antiDetectPanel.visibility = if (c.antiDetectionEnabled) View.VISIBLE else View.GONE
        edtAntiIntJitter.setText(c.antiDetectIntervalJitterMs.toString())
        edtAntiRadius.setText(c.antiDetectRadiusPx.toInt().toString())
        edtAntiHoldJitter.setText(c.antiDetectHoldJitterMs.toString())

        chkRandomOrder.isChecked = c.randomPointOrder
    }

    private fun saveAndApply() {
        val minInt = edtMinInt.text.toString().toLongOrNull() ?: ClickerPreferences.DEFAULT_MIN_INTERVAL
        val maxInt = edtMaxInt.text.toString().toLongOrNull() ?: minInt
        val rad = edtRadius.text.toString().toFloatOrNull() ?: 0f
        val minH = edtMinHold.text.toString().toLongOrNull() ?: ClickerPreferences.DEFAULT_MIN_HOLD
        val maxH = edtMaxHold.text.toString().toLongOrNull() ?: minH
        val stopMode = when (rgStopMode.checkedRadioButtonId) {
            rbDuration.id -> ClickerPreferences.MODE_DURATION
            rbCycles.id -> ClickerPreferences.MODE_CYCLES
            else -> ClickerPreferences.MODE_INFINITE
        }
        val dur = edtDurationSec.text.toString().toLongOrNull() ?: 300L
        val cycles = edtStopCycles.text.toString().toLongOrNull() ?: 10L
        val antiInt = edtAntiIntJitter.text.toString().toLongOrNull() ?: 35L
        val antiRad = edtAntiRadius.text.toString().toFloatOrNull() ?: 8f
        val antiHold = edtAntiHoldJitter.text.toString().toLongOrNull() ?: 15L

        val saved = ClickerPreferences.save(
            this, minInt, maxInt, rad, minH, maxH, chkRandomOrder.isChecked,
            stopMode, dur, cycles, chkAntiDetect.isChecked, antiInt, antiRad, antiHold
        )
        edtMinInt.setText(saved.minIntervalMs.toString())
        edtMaxInt.setText(saved.maxIntervalMs.toString())
        edtRadius.setText(saved.radiusPx.toInt().toString())
        seekRadius.progress = saved.radiusPx.toInt().coerceIn(0, 250)
        edtMinHold.setText(saved.minHoldMs.toString())
        edtMaxHold.setText(saved.maxHoldMs.toString())
        edtDurationSec.setText(saved.runDurationSec.toString())
        edtStopCycles.setText(saved.stopCycles.toString())
        edtAntiIntJitter.setText(saved.antiDetectIntervalJitterMs.toString())
        edtAntiRadius.setText(saved.antiDetectRadiusPx.toInt().toString())
        edtAntiHoldJitter.setText(saved.antiDetectHoldJitterMs.toString())

        sendBroadcast(Intent(RandomAutoClickService.ACTION_APPLY_CONFIG).setPackage(packageName))
        RandomAutoClickService.instance?.reloadConfig()

        Toast.makeText(
            this,
            "Da luu · Chong phat hien: ${if (saved.antiDetectionEnabled) "BAT" else "TAT"} · Ban kinh: ${saved.effectiveRadiusPx().toInt()}px",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun label(text: String, sp: Float, bold: Boolean, hex: String, topDp: Int = 0): TextView =
        TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(Color.parseColor(hex))
            if (bold) setTypeface(typeface, Typeface.BOLD)
            if (topDp > 0) layoutParams = marginLp(topDp = topDp)
        }

    private fun numInput(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        inputType = InputType.TYPE_CLASS_NUMBER
        setTextColor(Color.parseColor("#0F172A"))
        setHintTextColor(Color.parseColor("#94A3B8"))
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = boxBg("#FFFFFF", "#CBD5E1", 12)
    }

    private fun twoColRow(left: View, right: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = marginLp(topDp = 8)
        left.layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
        right.layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(12) }
        addView(left); addView(right)
    }

    private fun actionBtn(text: String, bgHex: String, topDp: Int, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            isClickable = true
            isFocusable = true
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = boxBg(bgHex, bgHex, 12)
            layoutParams = marginLp(topDp = topDp, heightDp = 48)
            setOnClickListener { onClick() }
        }

    private fun boxBg(fillHex: String, strokeHex: String, radDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(fillHex))
            setStroke(dp(1), Color.parseColor(strokeHex))
            cornerRadius = dp(radDp).toFloat()
        }

    private fun marginLp(topDp: Int = 0, heightDp: Int? = null): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            if (heightDp != null) dp(heightDp) else LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(topDp) }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).roundToInt()
}
