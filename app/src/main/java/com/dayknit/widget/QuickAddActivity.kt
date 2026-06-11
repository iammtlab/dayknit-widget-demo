package com.dayknit.widget

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/**
 * 위젯 '+' → 앱처럼 일정 추가(앱 본체 안 열림): 제목 + 리스트 선택 + 날짜 + 시간(또는 종일).
 * 카드형 라이트 UI(둥근 입력칸·색점 리스트·알약 칩·accent 추가버튼).
 */
class QuickAddActivity : Activity() {

    private val ACCENT = 0xFF4772FA.toInt()
    private val FG = 0xFF1F2328.toInt()
    private val DIM = 0xFF6B7280.toInt()
    private val FIELD = 0xFFF1F3F5.toInt()
    private val CHIP = 0xFFEAF0FF.toInt()

    private val listIds = ArrayList<String>()
    private val listLabels = ArrayList<String>()
    private val listColors = ArrayList<String>()
    private var selList = 0
    private val cal = Calendar.getInstance()
    private var timeStr: String? = null

    private lateinit var input: EditText
    private lateinit var listDot: View
    private lateinit var listLabel: TextView
    private lateinit var dateChip: TextView
    private lateinit var timeChip: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build()
    }
    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent); setIntent(intent); build()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun fdp(v: Float) = (v * resources.displayMetrics.density)

    private fun rounded(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = fdp(radius); setColor(color) }

    private fun build() {
        loadLists()
        val pad = dp(22)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(20), pad, dp(16))
        }

        root.addView(TextView(this).apply {
            text = "새 일정"; setTextColor(FG)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(12))
        })

        // 제목 입력 — 둥근 필드
        input = EditText(this).apply {
            hint = "할 일을 입력하세요"
            setHintTextColor(0xFF9AA0A8.toInt()); setTextColor(FG)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            background = rounded(FIELD, 11f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setSingleLine()
        }
        root.addView(input)

        // 리스트 선택 — 색점 + 라벨 + ▾
        root.addView(sectionLabel("리스트"))
        val listRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = rounded(FIELD, 11f); setPadding(dp(14), dp(12), dp(14), dp(12))
            isClickable = true; setOnClickListener { pickList() }
        }
        listDot = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)) }
        listLabel = TextView(this).apply {
            setTextColor(FG); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = dp(10) }
        }
        listRow.addView(listDot); listRow.addView(listLabel)
        listRow.addView(TextView(this).apply { text = "▾"; setTextColor(DIM); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f) })
        root.addView(listRow)
        refreshList()

        // 날짜 · 시간 — 알약 칩
        root.addView(sectionLabel("날짜 · 시간"))
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        dateChip = chip { pickDate() }
        timeChip = chip { pickTime() }
        chips.addView(dateChip)
        chips.addView(timeChip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.marginStart = dp(8) })
        root.addView(chips)
        refreshChips()

        // 취소 / 추가
        val btns = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END
            setPadding(0, dp(20), 0, 0); gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        btns.addView(TextView(this).apply {
            text = "취소"; setTextColor(DIM); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(14), dp(10), dp(18), dp(10)); isClickable = true; setOnClickListener { finish() }
        })
        btns.addView(TextView(this).apply {
            text = "추가"; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = rounded(ACCENT, 10f); setPadding(dp(22), dp(11), dp(22), dp(11))
            isClickable = true; setOnClickListener { submit() }
        })
        root.addView(btns)

        setContentView(ScrollView(this).apply { addView(root) })
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        input.requestFocus()
    }

    private fun sectionLabel(t: String) = TextView(this).apply {
        text = t; setTextColor(DIM); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(dp(2), dp(14), 0, dp(6))
    }
    private fun chip(onClick: () -> Unit) = TextView(this).apply {
        setTextColor(ACCENT); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        background = rounded(CHIP, 16f); setPadding(dp(16), dp(8), dp(16), dp(8))
        gravity = Gravity.CENTER; isClickable = true; setOnClickListener { onClick() }
    }

    private fun loadLists() {
        listIds.clear(); listLabels.clear(); listColors.clear()
        try {
            val f = File(File(filesDir, "widgets"), "lists.json")
            if (f.exists()) {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id"); if (id.isBlank()) continue
                    listIds.add(id); listLabels.add(o.optString("label").ifBlank { id })
                    listColors.add(o.optString("color", "#4772fa"))
                }
            }
        } catch (_: Exception) {}
        if (listIds.isEmpty()) { listIds.add("inbox"); listLabels.add("받은 편지함"); listColors.add("#4772fa") }
        selList = selList.coerceIn(0, listIds.size - 1)
    }

    private fun refreshList() {
        listLabel.text = listLabels[selList]
        listDot.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(parseColor(listColors[selList])) }
    }
    private fun pickList() {
        AlertDialog.Builder(this)
            .setTitle("리스트")
            .setItems(listLabels.toTypedArray()) { _, i -> selList = i; refreshList() }
            .show()
    }

    private fun refreshChips() {
        val m = cal.get(Calendar.MONTH) + 1; val d = cal.get(Calendar.DAY_OF_MONTH)
        val today = Calendar.getInstance()
        val isToday = cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        dateChip.text = if (isToday) "오늘 · ${m}/${d}" else "${m}/${d}"
        timeChip.text = timeStr?.let { friendly(it) } ?: "종일"
    }
    private fun friendly(hhmm: String): String = try {
        val (h, mi) = hhmm.split(":").map { it.toInt() }
        val ampm = if (h < 12) "오전" else "오후"; var hr = h % 12; if (hr == 0) hr = 12
        "$ampm $hr:%02d".format(mi)
    } catch (_: Exception) { hhmm }

    private fun pickDate() {
        DatePickerDialog(this, { _, y, mo, da ->
            cal.set(Calendar.YEAR, y); cal.set(Calendar.MONTH, mo); cal.set(Calendar.DAY_OF_MONTH, da); refreshChips()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }
    private fun pickTime() {
        // 칩 탭 → 종일 / 시간 지정 선택
        AlertDialog.Builder(this).setItems(arrayOf("종일", "시간 지정…")) { _, i ->
            if (i == 0) { timeStr = null; refreshChips() }
            else {
                val now = Calendar.getInstance()
                TimePickerDialog(this, { _, h, mi -> timeStr = "%02d:%02d".format(h, mi); refreshChips() },
                    now.get(Calendar.HOUR_OF_DAY), 0, true).show()
            }
        }.show()
    }

    private fun submit() {
        val text = input.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) { finish(); return }
        val date = "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        save(text, date, listIds[selList], listColors[selList], timeStr ?: "")
        finish()
    }

    private fun parseColor(s: String?): Int = try {
        if (s.isNullOrBlank()) ACCENT else Color.parseColor(s)
    } catch (_: Exception) { ACCENT }

    private fun save(text: String, date: String, listId: String, color: String, tm: String) {
        val ctx = applicationContext
        val dir = File(ctx.filesDir, "widgets").apply { mkdirs() }
        try {
            val pf = File(dir, "pending_adds.json")
            val arr = if (pf.exists()) JSONArray(pf.readText()) else JSONArray()
            arr.put(JSONObject().put("t", text).put("date", date).put("list", listId).put("tm", tm))
            writeAtomic(File(dir, "pending_adds.json.tmp"), pf, arr.toString())
        } catch (_: Exception) {}
        try {
            val ef = File(dir, "events.json")
            val root = if (ef.exists()) JSONObject(ef.readText()) else JSONObject()
            val day = root.optJSONArray(date) ?: JSONArray()
            day.put(JSONObject().put("t", text).put("c", color).put("tm", tm).put("d", 0))
            root.put(date, day)
            writeAtomic(File(dir, "events.json.tmp"), ef, root.toString())
        } catch (_: Exception) {}
        val mgr = AppWidgetManager.getInstance(ctx)
        for (id in mgr.getAppWidgetIds(ComponentName(ctx, MonthWidgetProvider::class.java))) MonthWidgetProvider.updateOne(ctx, mgr, id)
        for (id in mgr.getAppWidgetIds(ComponentName(ctx, TodoWidgetProvider::class.java))) TodoWidgetProvider.updateOne(ctx, mgr, id)
        for (id in mgr.getAppWidgetIds(ComponentName(ctx, TimelineWidgetProvider::class.java))) TimelineWidgetProvider.updateOne(ctx, mgr, id)
    }

    private fun writeAtomic(tmp: File, dst: File, content: String) { tmp.writeText(content); tmp.renameTo(dst) }
}
