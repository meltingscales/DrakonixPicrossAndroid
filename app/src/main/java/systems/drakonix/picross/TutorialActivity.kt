package systems.drakonix.picross

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.PointF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.TextView
import systems.drakonix.picross.PicrossView.Tool

/**
 * Self-playing walkthrough of a small puzzle. A ghost-finger circle drives the
 * real board through synthetic touches, so the demo uses the actual game rules.
 * Moving between steps replays earlier steps instantly, then animates the new one.
 */
class TutorialActivity : Activity() {

    private sealed interface Action
    private data class UseTool(val tool: Tool) : Action
    private data class Drag(val r0: Int, val c0: Int, val r1: Int, val c1: Int) : Action
    private data object ZoomDemo : Action

    private class Step(val caption: Int, vararg val actions: Action)

    private val steps = listOf(
        Step(R.string.tut_0),
        Step(R.string.tut_1, UseTool(Tool.FILL), Drag(1, 0, 1, 4)),
        Step(R.string.tut_2, Drag(2, 0, 2, 4)),
        Step(R.string.tut_3, UseTool(Tool.MARK), Drag(0, 0, 4, 0)),
        Step(R.string.tut_4, Drag(0, 4, 4, 4)),
        Step(R.string.tut_5, UseTool(Tool.FILL), Drag(3, 1, 3, 3)),
        Step(R.string.tut_6, Drag(0, 1, 0, 1), Drag(0, 3, 0, 3), UseTool(Tool.MARK), Drag(0, 2, 0, 2)),
        Step(R.string.tut_7, ZoomDemo),
        Step(R.string.tut_8, UseTool(Tool.FILL), Drag(4, 2, 4, 2)),
        Step(R.string.tut_9),
    )

    private lateinit var board: PicrossView
    private lateinit var level: Level
    private lateinit var toolBar: ToolBar
    private lateinit var caption: TextView
    private lateinit var counter: TextView
    private lateinit var backButton: Button
    private lateinit var playButton: Button
    private lateinit var nextButton: Button
    private lateinit var tryButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private var animator: ValueAnimator? = null
    // Bumped on every step change so callbacks from an abandoned step do nothing.
    private var generation = 0
    private var index = 0
    private var playing = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutorial)

        level = Levels.parse("tutorial", assets.open("tutorial/heart.txt").bufferedReader().readLines())
        val palette = Themes.current(this)
        findViewById<View>(android.R.id.content).setBackgroundColor(palette.bg)

        board = findViewById(R.id.board)
        board.demoMode = true
        board.setPalette(palette)
        board.setLevel(level)
        toolBar = ToolBar(
            board,
            mapOf(
                Tool.FILL to findViewById(R.id.toolFill),
                Tool.MARK to findViewById(R.id.toolMark),
                Tool.PAN to findViewById(R.id.toolPan),
            ),
            palette,
            interactive = false,
        )

        caption = findViewById(R.id.caption)
        counter = findViewById(R.id.stepCounter)
        backButton = findViewById(R.id.backButton)
        playButton = findViewById(R.id.playButton)
        nextButton = findViewById(R.id.nextButton)
        tryButton = findViewById(R.id.tryButton)
        listOf<TextView>(findViewById(R.id.tutorialTitle), caption).forEach { it.setTextColor(palette.fg) }
        counter.setTextColor(palette.fg.withAlpha(0.6f))
        val plainTint = palette.bg.blendToward(palette.fg, 0.12f)
        listOf(backButton, playButton, nextButton).forEach { it.stylize(plainTint, palette.fg) }
        tryButton.stylize(palette.accent, palette.bg)

        backButton.setOnClickListener { goTo(index - 1) }
        nextButton.setOnClickListener { goTo(index + 1) }
        playButton.setOnClickListener {
            setPlaying(!playing)
            if (playing) goTo(if (index == steps.lastIndex) 0 else index)
        }
        tryButton.setOnClickListener {
            startActivity(Intent(this, GameActivity::class.java).putExtra(GameActivity.EXTRA_LEVEL, 0))
            finish()
        }

        setPlaying(true)
        // Cell positions need the board laid out first.
        board.post { goTo(0) }
    }

    override fun onDestroy() {
        super.onDestroy()
        generation++
        handler.removeCallbacksAndMessages(null)
        animator?.cancel()
    }

    private fun setPlaying(value: Boolean) {
        playing = value
        playButton.setText(if (playing) R.string.tutorial_pause else R.string.tutorial_play)
    }

    private fun goTo(target: Int) {
        index = target.coerceIn(0, steps.lastIndex)
        generation++
        handler.removeCallbacksAndMessages(null)
        animator?.cancel()
        animator = null

        board.setLevel(level)
        board.showGhosts(emptyList(), false)
        toolBar.select(Tool.FILL)
        steps.take(index).forEach { step -> step.actions.forEach(::runInstant) }

        caption.setText(steps[index].caption)
        counter.text = getString(R.string.tutorial_step, index + 1, steps.size)
        backButton.isEnabled = index > 0
        nextButton.isEnabled = index < steps.lastIndex
        tryButton.visibility = if (index == steps.lastIndex) View.VISIBLE else View.GONE

        val gen = generation
        runAnimated(steps[index].actions.toList(), gen) {
            if (!playing) return@runAnimated
            if (index < steps.lastIndex) {
                handler.postDelayed({ goTo(index + 1) }, STEP_PAUSE_MS)
            } else {
                setPlaying(false)
            }
        }
    }

    private fun path(d: Drag): List<Pair<Int, Int>> {
        val cells = mutableListOf(d.r0 to d.c0)
        var r = d.r0
        var c = d.c0
        while (r != d.r1 || c != d.c1) {
            r += Integer.signum(d.r1 - r)
            c += Integer.signum(d.c1 - c)
            cells.add(r to c)
        }
        return cells
    }

    private fun center(cell: Pair<Int, Int>): PointF = board.cellCenter(cell.first, cell.second)

    private fun runInstant(action: Action) {
        when (action) {
            is UseTool -> toolBar.select(action.tool)
            is Drag -> {
                val cells = path(action)
                center(cells.first()).let { board.demoTouch(MotionEvent.ACTION_DOWN, it.x, it.y) }
                cells.drop(1).forEach { cell -> center(cell).let { board.demoTouch(MotionEvent.ACTION_MOVE, it.x, it.y) } }
                center(cells.last()).let { board.demoTouch(MotionEvent.ACTION_UP, it.x, it.y) }
            }
            ZoomDemo -> board.resetView()
        }
    }

    private fun runAnimated(actions: List<Action>, gen: Int, done: () -> Unit) {
        if (gen != generation) return
        if (actions.isEmpty()) {
            done()
            return
        }
        val next = { runAnimated(actions.drop(1), gen, done) }
        when (val action = actions.first()) {
            is UseTool -> {
                toolBar.select(action.tool)
                handler.postDelayed({ next() }, TOOL_PAUSE_MS)
            }
            is Drag -> animateDrag(action, next)
            ZoomDemo -> animateZoom(gen, next)
        }
    }

    /** Ghost finger hovers on the first cell, presses, walks the path, lifts. */
    private fun animateDrag(drag: Drag, next: () -> Unit) {
        val cells = path(drag)
        var t = 0L
        val first = center(cells.first())
        board.showGhosts(listOf(first), false)
        t += 450
        handler.postDelayed({
            board.demoTouch(MotionEvent.ACTION_DOWN, first.x, first.y)
            board.showGhosts(listOf(first), true)
        }, t)
        for (cell in cells.drop(1)) {
            t += 220
            handler.postDelayed({
                val p = center(cell)
                board.demoTouch(MotionEvent.ACTION_MOVE, p.x, p.y)
                board.showGhosts(listOf(p), true)
            }, t)
        }
        t += 350
        handler.postDelayed({
            val p = center(cells.last())
            board.demoTouch(MotionEvent.ACTION_UP, p.x, p.y)
            board.showGhosts(listOf(p), false)
        }, t)
        t += 350
        handler.postDelayed({
            board.showGhosts(emptyList(), false)
            next()
        }, t)
    }

    /** Two ghost fingers pinch out, pan to the far corner, then the view resets. */
    private fun animateZoom(gen: Int, next: () -> Unit) {
        val w = board.width.toFloat()
        val h = board.height.toFloat()
        val cx = w / 2
        val cy = h / 2
        animator = ValueAnimator.ofFloat(0f, 4f).apply {
            duration = 6000
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val v = anim.animatedValue as Float
                // Phases: pinch out, pan, hold, zoom back. p is the pan fraction (0.5 = centered).
                val (z, p) = when {
                    v < 1 -> (1 + v) to 0.5f
                    v < 2 -> 2f to (0.5f + 0.5f * (v - 1))
                    v < 3 -> 2f to 1f
                    else -> (2 - (v - 3)) to (1 - 0.5f * (v - 3))
                }
                board.setViewport(z, w * (1 - z) * p, h * (1 - z) * p)
                val ghosts = when {
                    v < 1 -> (60 + 140 * v).let { s -> listOf(PointF(cx - s, cy - s), PointF(cx + s, cy + s)) }
                    v < 2 -> (-(v - 1) * 250).let { d -> listOf(PointF(cx - 200 + d, cy - 200 + d), PointF(cx + 200 + d, cy + 200 + d)) }
                    else -> emptyList()
                }
                board.showGhosts(ghosts, ghosts.isNotEmpty())
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (gen != generation) return
                    board.resetView()
                    board.showGhosts(emptyList(), false)
                    next()
                }
            })
            start()
        }
    }

    companion object {
        private const val TOOL_PAUSE_MS = 600L
        private const val STEP_PAUSE_MS = 1800L
    }
}
