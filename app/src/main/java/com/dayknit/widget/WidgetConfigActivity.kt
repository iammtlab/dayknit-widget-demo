package com.dayknit.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView

/**
 * 위젯별 설정 — HTML 미리보기 설정과 동일 구성:
 * 상단바 색상 · (타임라인)데이뷰 일수 · (타임라인)시간 범위 · 글자 크기 · 배경 투명도 · 폰 배경(테마).
 * 색 스와치는 GradientDrawable 로 칠해 버튼 틴트에 안 덮이게 한다.
 */
class WidgetConfigActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var providerClass = ""
    private var selectedColor = "#4772FA"
    private val swatchBg = HashMap<String, GradientDrawable>()
    private val swatchTv = HashMap<String, TextView>()

    private lateinit var daysGroup: RadioGroup
    private lateinit var fontGroup: RadioGroup
    private lateinit var themeGroup: RadioGroup
    private lateinit var startSpin: Spinner
    private lateinit var endSpin: Spinner
    private lateinit var alphaBar: SeekBar

    private val DAYS = listOf(1, 3, 4, 5, 7)
    private val FONTS = listOf("small", "normal", "large")
    private val THEMES = listOf("auto", "light", "dark")
    private val dp get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        providerClass = AppWidgetManager.getInstance(this)
            .getAppWidgetInfo(appWidgetId)?.provider?.className ?: ""
        // 데모 렌더용: 실제 위젯 없이도 타임라인 옵션(일수·시간범위)까지 보이게
        val isTimeline = providerClass.endsWith("TimelineWidgetProvider") ||
            intent?.getBooleanExtra("demoTimeline", false) == true

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        selectedColor = prefs.getString("hcolor_$appWidgetId", "#4772FA") ?: "#4772FA"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad(20), pad(20), pad(20), pad(20))
        }

        root.addView(TextView(this).apply {
            text = "위젯 설정"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, pad(14))
        })

        // ── 상단바 색상 ──
        root.addView(label("상단바 색상"))
        root.addView(buildSwatches())

        // ── (타임라인) 데이뷰 일수 ──
        if (isTimeline) {
            root.addView(label("데이뷰 일수"))
            val cur = prefs.getInt("days_$appWidgetId", 3)
            daysGroup = segment(DAYS.map { "${it}일" }, DAYS.indexOf(cur).coerceAtLeast(0))
            root.addView(daysGroup)

            root.addView(label("보이는 시간 범위"))
            val sh = prefs.getInt("sh_$appWidgetId", 8)
            val eh = prefs.getInt("eh_$appWidgetId", 22)
            val rangeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            startSpin = Spinner(this).apply { adapter = hourAdapter(0, 23); setSelection(sh.coerceIn(0, 23)) }
            endSpin = Spinner(this).apply { adapter = hourAdapter(1, 24); setSelection((eh - 1).coerceIn(0, 23)) }
            rangeRow.addView(startSpin, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            rangeRow.addView(TextView(this).apply { text = "  ~  "; gravity = Gravity.CENTER })
            rangeRow.addView(endSpin, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            root.addView(rangeRow)
        }

        // ── 글자 크기 ──
        root.addView(label("글자 크기"))
        val curFont = prefs.getString("fscale_$appWidgetId", "normal") ?: "normal"
        fontGroup = segment(listOf("작게", "보통", "크게"), FONTS.indexOf(curFont).coerceAtLeast(1))
        root.addView(fontGroup)

        // ── 배경 투명도 ──
        root.addView(label("배경 투명도"))
        alphaBar = SeekBar(this).apply { max = 255; progress = prefs.getInt("alpha_$appWidgetId", 255) }
        root.addView(alphaBar)

        // ── 폰 배경(테마) ──
        root.addView(label("폰 배경(테마)"))
        val curTheme = prefs.getString("theme_$appWidgetId", "auto") ?: "auto"
        themeGroup = segment(listOf("자동", "라이트", "다크"), THEMES.indexOf(curTheme).coerceAtLeast(0))
        root.addView(themeGroup)

        // ── 버튼 ──
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END
            setPadding(0, pad(20), 0, 0)
        }
        btnRow.addView(Button(this).apply { text = "취소"; setOnClickListener { finish() } })
        btnRow.addView(Button(this).apply { text = "저장"; setOnClickListener { save() } })
        root.addView(btnRow)

        setContentView(ScrollView(this).apply {
            addView(root)
            setPadding(0, pad(4), 0, 0)
        })
    }

    private fun save() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ed = prefs.edit()
        ed.putString("hcolor_$appWidgetId", selectedColor)
        ed.putString("fscale_$appWidgetId", FONTS[sel(fontGroup, FONTS.size, 1)])
        ed.putInt("alpha_$appWidgetId", alphaBar.progress.coerceIn(40, 255))
        ed.putString("theme_$appWidgetId", THEMES[sel(themeGroup, THEMES.size, 0)])
        if (providerClass.endsWith("TimelineWidgetProvider")) {
            ed.putInt("days_$appWidgetId", DAYS[sel(daysGroup, DAYS.size, 1)])
            val sh = startSpin.selectedItemPosition.coerceIn(0, 23)
            var eh = endSpin.selectedItemPosition + 1
            if (eh < sh + 2) eh = (sh + 2).coerceAtMost(24)
            ed.putInt("sh_$appWidgetId", sh)
            ed.putInt("eh_$appWidgetId", eh)
        }
        ed.apply()

        val mgr = AppWidgetManager.getInstance(this)
        when {
            providerClass.endsWith("TimelineWidgetProvider") -> TimelineWidgetProvider.updateOne(this, mgr, appWidgetId)
            providerClass.endsWith("TodoWidgetProvider") -> TodoWidgetProvider.updateOne(this, mgr, appWidgetId)
            providerClass.endsWith("MonthWidgetProvider") -> MonthWidgetProvider.updateOne(this, mgr, appWidgetId)
            else -> refreshAll(mgr)
        }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }

    private fun refreshAll(mgr: AppWidgetManager) {
        for (id in mgr.getAppWidgetIds(ComponentName(this, MonthWidgetProvider::class.java))) MonthWidgetProvider.updateOne(this, mgr, id)
        for (id in mgr.getAppWidgetIds(ComponentName(this, TodoWidgetProvider::class.java))) TodoWidgetProvider.updateOne(this, mgr, id)
        for (id in mgr.getAppWidgetIds(ComponentName(this, TimelineWidgetProvider::class.java))) TimelineWidgetProvider.updateOne(this, mgr, id)
    }

    // ── UI 헬퍼 ──
    private fun pad(v: Int) = (v * dp).toInt()

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, pad(16), 0, pad(7))
    }

    private fun buildSwatches(): View {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var row = newRow(); wrap.addView(row)
        THEME_COLORS.forEachIndexed { i, (_, hex) ->
            if (i == 4) { row = newRow(); wrap.addView(row) }
            val gd = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10 * dp
                setColor(Color.parseColor(hex))
            }
            val tv = TextView(this).apply {
                val lp = LinearLayout.LayoutParams(pad(52), pad(44)).also { it.setMargins(pad(3), 0, pad(3), 0) }
                layoutParams = lp
                gravity = Gravity.CENTER
                background = gd
                setTextColor(if (lum(hex) > 0.6) Color.BLACK else Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setOnClickListener { selectedColor = hex; refreshSwatches() }
            }
            swatchBg[hex] = gd; swatchTv[hex] = tv
            row.addView(tv)
        }
        refreshSwatches()
        return wrap
    }
    private fun newRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, pad(3), 0, pad(3))
    }
    private fun refreshSwatches() {
        for ((hex, gd) in swatchBg) {
            val on = hex.equals(selectedColor, true)
            gd.setStroke(if (on) (3 * dp).toInt() else 0, if (lum(hex) > 0.6) Color.DKGRAY else Color.WHITE)
            swatchTv[hex]?.text = if (on) "✓" else ""
        }
    }

    private fun segment(labels: List<String>, selectedIdx: Int): RadioGroup {
        val g = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        labels.forEachIndexed { i, t ->
            val rb = RadioButton(this).apply {
                text = t
                setPadding(pad(2), 0, pad(10), 0)
            }
            g.addView(rb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            if (i == selectedIdx) rb.isChecked = true
        }
        return g
    }
    /** RadioGroup 선택 인덱스(없으면 def). */
    private fun sel(g: RadioGroup, size: Int, def: Int): Int {
        val v = g.findViewById<View>(g.checkedRadioButtonId) ?: return def
        return g.indexOfChild(v).coerceIn(0, size - 1)
    }

    private fun hourAdapter(from: Int, to: Int): ArrayAdapter<String> {
        val items = (from..to).map { "${it}시" }
        return ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
    }

    private fun lum(hex: String): Double = try {
        val c = Color.parseColor(hex)
        0.2126 * (c shr 16 and 0xFF) / 255.0 + 0.7152 * (c shr 8 and 0xFF) / 255.0 + 0.0722 * (c and 0xFF) / 255.0
    } catch (_: Exception) { 0.0 }
}
