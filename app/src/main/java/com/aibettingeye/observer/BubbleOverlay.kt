package com.aibettingeye.observer

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.aibettingeye.MainActivity
import kotlin.math.abs

/**
 * Hep açık duran baloncuk. Bir maçın üstüne sürükleyip bırakınca maçı tanır,
 * baloncuğa dokununca analizi uygulamada açar.
 */
class BubbleOverlay(
    private val ctx: Context,
    private val probe: (Int, Int) -> DetectedMatch?,
    private val pageMatch: () -> DetectedMatch?
) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = ctx.resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())
    private fun dp(v: Int) = (v * density).toInt()

    private val amber = Color.parseColor("#F2B84B")
    private val teal = Color.parseColor("#5FD0C6")
    private val ink = Color.parseColor("#0E1320")
    private val panelBg = Color.parseColor("#161D2E")
    private val dim = Color.parseColor("#9AA5BC")

    private val root = LinearLayout(ctx)
    private val bubble = TextView(ctx)
    private val panel = LinearLayout(ctx)
    private val title = TextView(ctx)
    private val sub = TextView(ctx)

    private val lp = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    )

    private var attached = false
    private var locked: DetectedMatch? = null
    private val collapse = Runnable { panel.visibility = View.GONE; resize() }

    init {
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = dp(8)
        lp.y = dp(260)

        root.orientation = LinearLayout.VERTICAL

        bubble.text = "◎"
        bubble.textSize = 24f
        bubble.gravity = Gravity.CENTER
        bubble.setTextColor(ink)
        bubble.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(amber)
        }
        bubble.layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))

        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(dp(12), dp(10), dp(12), dp(10))
        panel.background = GradientDrawable().apply {
            setColor(panelBg)
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), Color.parseColor("#2A3556"))
        }
        panel.visibility = View.GONE
        panel.minimumWidth = dp(200)
        val plp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        plp.topMargin = dp(6)
        panel.layoutParams = plp

        title.setTextColor(Color.WHITE)
        title.textSize = 15f
        title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
        sub.setTextColor(dim)
        sub.textSize = 12f
        panel.addView(title)
        panel.addView(sub)
        panel.setOnClickListener { locked?.let { analyze(it) } }

        root.addView(bubble)
        root.addView(panel)

        bubble.setOnTouchListener(object : View.OnTouchListener {
            var startX = 0
            var startY = 0
            var touchX = 0f
            var touchY = 0f
            var moved = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = lp.x
                        startY = lp.y
                        touchX = e.rawX
                        touchY = e.rawY
                        moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - touchX).toInt()
                        val dy = (e.rawY - touchY).toInt()
                        if (!moved && (abs(dx) > dp(6) || abs(dy) > dp(6))) {
                            moved = true
                            clearLock()
                        }
                        if (moved) {
                            lp.x = startX + dx
                            lp.y = startY + dy
                            resize()
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (moved) onDrop() else onTap()
                    }
                }
                return true
            }
        })
    }

    private fun resize() {
        try { if (attached) wm.updateViewLayout(root, lp) } catch (_: Exception) {}
    }

    private fun center(): IntArray {
        val loc = IntArray(2)
        bubble.getLocationOnScreen(loc)
        return intArrayOf(loc[0] + bubble.width / 2, loc[1] + bubble.height / 2)
    }

    private fun clearLock() {
        locked = null
        handler.removeCallbacks(collapse)
        (bubble.background as GradientDrawable).setColor(amber)
        panel.visibility = View.GONE
        resize()
    }

    private fun showPanel(t: String, s: String, autoHideMs: Long) {
        title.text = t
        sub.text = s
        panel.visibility = View.VISIBLE
        resize()
        handler.removeCallbacks(collapse)
        if (autoHideMs > 0) handler.postDelayed(collapse, autoHideMs)
    }

    private fun onDrop() {
        val c = center()
        val m = probe(c[0], c[1])
        if (m != null) {
            locked = m
            (bubble.background as GradientDrawable).setColor(teal)
            val extra = buildList {
                if (m.time.isNotEmpty()) add(m.time)
                if (m.odds.size >= 3) add("Oranlar " + m.odds.take(3).joinToString(" "))
            }
            showPanel("${m.home} - ${m.away}", (if (extra.isEmpty()) "" else extra.joinToString("   ") + "\n") + "Analiz için baloncuğa dokun", 0)
        } else {
            showPanel("Maç bulunamadı", "Baloncuğu bir maç satırının üstüne bırak.", 3500)
        }
    }

    private fun onTap() {
        val m = locked ?: run {
            val c = center()
            probe(c[0], c[1]) ?: pageMatch()
        }
        if (m != null) analyze(m)
        else showPanel("Maç bulunamadı", "Baloncuğu bir maçın üstüne sürükleyip bırak, sonra dokun.", 3500)
    }

    private fun analyze(m: DetectedMatch) {
        ObserverState.onDetected(m)
        ObserverState.selected.value = m
        val i = Intent(ctx, MainActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        ctx.startActivity(i)
        clearLock()
    }

    fun show() {
        if (attached) return
        try {
            wm.addView(root, lp)
            attached = true
        } catch (_: Exception) {
        }
    }

    fun hide() {
        if (!attached) return
        try { wm.removeView(root) } catch (_: Exception) {}
        attached = false
        clearLock()
    }
}
