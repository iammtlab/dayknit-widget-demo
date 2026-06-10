package com.dayknit.widget

import android.content.Context
import android.content.res.Configuration
import java.util.Calendar

/** 위젯 인스턴스별 설정/상태 (PREFS = "dayknit_widget", WidgetPlugin.kt 정의). */
object WidgetConfig {
    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun currentYearMonth(ctx: Context, id: Int): String {
        p(ctx).getString("ym_$id", null)?.let { return it }
        val c = Calendar.getInstance()
        return "%04d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }
    fun setYearMonth(ctx: Context, id: Int, ym: String) {
        p(ctx).edit().putString("ym_$id", ym).apply()
    }

    /** 배경 투명도 alpha 0~255 (기본 불투명). */
    fun alpha(ctx: Context, id: Int): Int = p(ctx).getInt("alpha_$id", 255).coerceIn(0, 255)

    /** 상단바 색상 테마 (#rrggbb). 기본 브랜드 파랑. */
    fun headerColor(ctx: Context, id: Int): String = p(ctx).getString("hcolor_$id", "#4772FA") ?: "#4772FA"

    /** 글자 크기: "small" | "normal" | "large". */
    fun fontScale(ctx: Context, id: Int): String = p(ctx).getString("fscale_$id", "normal") ?: "normal"

    /** 타임라인(데이뷰) 표시 일수: 1/3/5/7 (기본 3). */
    fun days(ctx: Context, id: Int): Int = p(ctx).getInt("days_$id", 3).coerceIn(1, 7)
    fun setDays(ctx: Context, id: Int, v: Int) = p(ctx).edit().putInt("days_$id", v.coerceIn(1, 7)).apply()

    /** 타임라인 시작 시각(0~23, 기본 8). */
    fun startHour(ctx: Context, id: Int): Int = p(ctx).getInt("sh_$id", 8).coerceIn(0, 23)
    fun setStartHour(ctx: Context, id: Int, v: Int) = p(ctx).edit().putInt("sh_$id", v.coerceIn(0, 23)).apply()

    /** 타임라인 끝 시각(1~24, 기본 22). 시작보다 최소 2시간 뒤로 보정. */
    fun endHour(ctx: Context, id: Int): Int {
        val s = startHour(ctx, id)
        return p(ctx).getInt("eh_$id", 22).coerceIn(s + 2, 24)
    }
    fun setEndHour(ctx: Context, id: Int, v: Int) = p(ctx).edit().putInt("eh_$id", v.coerceIn(1, 24)).apply()

    /** 폰 배경(테마): "auto"(시스템) | "light" | "dark". */
    fun themePref(ctx: Context, id: Int): String = p(ctx).getString("theme_$id", "auto") ?: "auto"
    fun setThemePref(ctx: Context, id: Int, v: String) = p(ctx).edit().putString("theme_$id", v).apply()

    fun clear(ctx: Context, id: Int) {
        p(ctx).edit()
            .remove("ym_$id").remove("alpha_$id").remove("hcolor_$id").remove("fscale_$id")
            .remove("days_$id").remove("sh_$id").remove("eh_$id").remove("theme_$id")
            .apply()
    }

    private fun systemDark(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /** 배경 다크 여부 — 위젯별 테마 설정(자동=시스템 따름). */
    fun isDark(ctx: Context, id: Int): Boolean = when (themePref(ctx, id)) {
        "light" -> false
        "dark" -> true
        else -> systemDark(ctx)
    }
}

/** "2026-06", +1 → "2026-07". */
fun shiftMonth(ym: String, delta: Int): String {
    val parts = ym.split("-")
    val c = Calendar.getInstance()
    c.set(parts[0].toInt(), parts[1].toInt() - 1, 1)
    c.add(Calendar.MONTH, delta)
    return "%04d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
}

/** 색상 테마 옵션 (이름, hex). 설정 화면 + 적용에 공용. */
val THEME_COLORS = listOf(
    "파랑" to "#4772FA",
    "보라" to "#6D4AFA",
    "초록" to "#22A06B",
    "빨강" to "#E5544B",
    "주황" to "#F2802E",
    "청록" to "#0EA5A5",
    "분홍" to "#E0508F",
    "회색" to "#5B626E",
)
