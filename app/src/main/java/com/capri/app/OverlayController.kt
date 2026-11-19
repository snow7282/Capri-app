package com.capri.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.min

private val ACCENT = Color.parseColor("#FF2E88")

/**
 * Draws Capri's floating bubble and pop-up cards over other apps.
 * This only works because YOU grant "Display over other apps" in system settings.
 */
class OverlayController(private val ctx: Context) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var bubble: View? = null
    private var card: View? = null
    private val autoHide = Runnable { removeCard() }

    fun canDraw(): Boolean = Settings.canDrawOverlays(ctx)

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun baseParams(): WindowManager.LayoutParams {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        return lp
    }

    private fun openChat() {
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        try {
            ctx.startActivity(i)
        } catch (_: Exception) {
        }
    }

    // ---- the draggable bubble ----

    fun showBubble() {
        if (bubble != null || !canDraw()) return
        val size = dp(54)
        val tv = TextView(ctx)
        tv.text = "C"
        tv.setTextColor(Color.WHITE)
        tv.textSize = 22f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.gravity = Gravity.CENTER
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(Color.parseColor("#E61A1A2E"))
        bg.setStroke(dp(2), ACCENT)
        tv.background = bg

        val lp = baseParams()
        lp.width = size
        lp.height = size
        lp.x = Prefs.bubbleX
        lp.y = Prefs.bubbleY
        tv.setOnTouchListener(DragListener(lp))
        try {
            wm.addView(tv, lp)
            bubble = tv
        } catch (_: Exception) {
        }
    }

    private inner class DragListener(private val lp: WindowManager.LayoutParams) : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var moved = false

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
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    if (moved) {
                        lp.x = startX + dx
                        lp.y = startY + dy
                        try {
                            wm.updateViewLayout(v, lp)
                        } catch (_: Exception) {
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        Prefs.bubbleX = lp.x
                        Prefs.bubbleY = lp.y
                    } else {
                        openChat()
                    }
                }
            }
            return true
        }
    }

    // ---- the pop-up card ----

    fun showCard(message: String) {
        if (!canDraw()) return
        removeCard()

        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(12), dp(16), dp(14))
        val bg = GradientDrawable()
        bg.cornerRadius = dp(18).toFloat()
        bg.setColor(Color.parseColor("#F2121220"))
        bg.setStroke(dp(1), ACCENT)
        box.background = bg

        val name = TextView(ctx)
        name.text = "CAPRI"
        name.setTextColor(ACCENT)
        name.textSize = 11f
        name.typeface = Typeface.DEFAULT_BOLD
        name.letterSpacing = 0.15f
        box.addView(name)

        val body = TextView(ctx)
        body.text = Fmt.styled(message)
        body.setTextColor(Color.WHITE)
        body.textSize = 15f
        body.setPadding(0, dp(4), 0, 0)
        box.addView(body)

        box.setOnClickListener {
            removeCard()
            openChat()
        }

        val lp = baseParams()
        lp.width = min(ctx.resources.displayMetrics.widthPixels - dp(32), dp(360))
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.y = dp(48)
        try {
            wm.addView(box, lp)
            card = box
            handler.postDelayed(autoHide, 8000L + message.length * 40L)
        } catch (_: Exception) {
        }
    }

    private fun removeCard() {
        handler.removeCallbacks(autoHide)
        card?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        card = null
    }

    fun hideAll() {
        removeCard()
        bubble?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        bubble = null
    }
}
