package com.dayknit.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/** 시제품 B: 할일 위젯을 "이미지 스냅샷"으로 — 앱에서 PNG 렌더(픽셀 자유). 색 링·구분선·정밀 정렬. */
object TodoImageRenderer {

    private data class Item(val group: String?, val title: String, val color: Int, val time: String?)

    private val items = listOf(
        Item("오늘", "기상·운동", 0xFF22A06B.toInt(), "07:00"),
        Item(null, "팀 스탠드업", 0xFF4772FA.toInt(), "09:30"),
        Item(null, "점심 약속", 0xFFF2802E.toInt(), "12:00"),
        Item(null, "디자인 리뷰", 0xFFE0508F.toInt(), "14:00"),
        Item(null, "결혼기념일", 0xFFE5544B.toInt(), null),
        Item("내일", "치과 예약", 0xFF6D4AFA.toInt(), "11:00"),
        Item(null, "프로젝트 마감", 0xFFE5544B.toInt(), null),
        Item("6.13(금)", "주간 회의", 0xFF4772FA.toInt(), "10:00"),
    )

    fun render(ctx: Context, wPx: Int, hPx: Int): Bitmap {
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Float) = v * d
        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val reg = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

        // 헤더
        val headerH = dp(46f)
        p.color = 0xFF4772FA.toInt()
        c.drawRect(0f, 0f, wPx.toFloat(), headerH, p)
        p.color = Color.WHITE; p.typeface = bold; p.textSize = dp(16f)
        c.drawText("할 일", dp(16f), headerH / 2 + dp(6f), p)
        p.typeface = reg; p.textSize = dp(15f); p.textAlign = Paint.Align.RIGHT
        c.drawText("⚙    +", wPx - dp(14f), headerH / 2 + dp(6f), p)
        p.textAlign = Paint.Align.LEFT

        var y = headerH + dp(14f)
        val padX = dp(16f)
        for (it in items) {
            it.group?.let { g ->
                p.color = 0xFF4772FA.toInt(); p.typeface = bold; p.textSize = dp(12f)
                c.drawText(g, padX, y + dp(10f), p)
                y += dp(20f)
            }
            // 색 링(테두리 원)
            val cy = y + dp(11f); val cx = padX + dp(8f)
            p.style = Paint.Style.STROKE; p.strokeWidth = dp(2f); p.color = it.color
            c.drawCircle(cx, cy, dp(7.5f), p)
            p.style = Paint.Style.FILL
            // 제목
            p.color = 0xFF1F2328.toInt(); p.typeface = reg; p.textSize = dp(14.5f)
            val titleX = padX + dp(24f)
            // 우측 시간
            var rightLimit = wPx - padX
            if (it.time != null) {
                p.color = 0xFF8A9099.toInt(); p.textSize = dp(12.5f); p.textAlign = Paint.Align.RIGHT
                c.drawText(it.time, wPx - padX, y + dp(15f), p)
                p.textAlign = Paint.Align.LEFT
                rightLimit = wPx - padX - dp(54f)
            }
            p.color = 0xFF1F2328.toInt(); p.textSize = dp(14.5f)
            val title = ellipsize(p, it.title, rightLimit - titleX)
            c.drawText(title, titleX, y + dp(15f), p)
            y += dp(30f)
            // 구분선
            p.color = 0x11000000; p.strokeWidth = dp(0.8f)
            c.drawLine(titleX, y - dp(6f), wPx - padX, y - dp(6f), p)
        }
        return bmp
    }

    private fun ellipsize(p: Paint, text: String, maxW: Float): String {
        if (p.measureText(text) <= maxW) return text
        var s = text
        while (s.length > 1 && p.measureText("$s…") > maxW) s = s.dropLast(1)
        return "$s…"
    }
}
