package com.capri.app

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Shows the chat as left/right bubbles. Roles: "user", "assistant", or "error". */
class ChatAdapter(private val items: List<ChatMsg>) : RecyclerView.Adapter<ChatAdapter.VH>() {

    class VH(val frame: FrameLayout, val tv: TextView) : RecyclerView.ViewHolder(frame)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val d = ctx.resources.displayMetrics.density

        val frame = FrameLayout(ctx)
        frame.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        frame.setPadding((10 * d).toInt(), (4 * d).toInt(), (10 * d).toInt(), (4 * d).toInt())

        val tv = TextView(ctx)
        tv.textSize = 15f
        tv.setTextColor(Color.WHITE)
        tv.setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
        tv.maxWidth = (ctx.resources.displayMetrics.widthPixels * 0.8).toInt()
        frame.addView(
            tv,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        return VH(frame, tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val m = items[position]
        val d = holder.tv.context.resources.displayMetrics.density
        val lp = holder.tv.layoutParams as FrameLayout.LayoutParams

        val bg = GradientDrawable()
        bg.cornerRadius = 16 * d
        when (m.role) {
            "user" -> {
                lp.gravity = Gravity.END
                bg.setColor(Color.parseColor("#2A2A44"))
            }
            "assistant" -> {
                lp.gravity = Gravity.START
                bg.setColor(Color.parseColor("#4A1230"))
            }
            else -> {
                lp.gravity = Gravity.CENTER_HORIZONTAL
                bg.setColor(Color.parseColor("#3B1F1F"))
            }
        }
        holder.tv.layoutParams = lp
        holder.tv.background = bg
        holder.tv.text = Fmt.styled(m.content)
    }

    override fun getItemCount(): Int = items.size
}
