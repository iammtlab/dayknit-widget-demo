package com.dayknit.widget

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/**
 * 위젯 '+' → 앱처럼 일정 추가(앱 본체 안 열림): 제목 + 리스트 선택 + 날짜 + 시간(또는 종일).
 * - 리스트 목록은 앱이 내보낸 filesDir/widgets/lists.json 에서 읽음.
 * - pending_adds.json 에 {t,date,list,tm} 큐잉(앱이 다음 실행 시 실제 task 생성+서버 동기화).
 * - events.json 에 낙관적 반영(위젯 즉시 표시) + 모든 위젯 갱신.
 */
class QuickAddActivity : Activity() {

    private val listIds = ArrayList<String>()
    private val listLabels = ArrayList<String>()
    private val listColors = ArrayList<String>()
    private val cal = Calendar.getInstance()
    private var timeStr: String? = null
    private lateinit var input: EditText
    private lateinit var listSpinner: Spinner
    private lateinit var dateBtn: Button
    private lateinit var timeBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build()
    }
    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent); setIntent(intent); build()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun build() {
        loadLists()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(14))
        }
        root.addView(TextView(this).apply {
            text = "새 일정"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(6))
        })
        input = EditText(this).apply { hint = "할 일을 입력하세요"; setSingleLine() }
        root.addView(input)

        root.addView(label("리스트"))
        listSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@QuickAddActivity, android.R.layout.simple_spinner_dropdown_item, listLabels)
        }
        root.addView(listSpinner)

        root.addView(label("날짜 / 시간"))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        dateBtn = Button(this).apply { setOnClickListener { pickDate() } }
        timeBtn = Button(this).apply { setOnClickListener { pickTime() } }
        val alldayBtn = Button(this).apply { text = "종일"; setOnClickListener { timeStr = null; refreshBtns() } }
        row.addView(dateBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f))
        row.addView(timeBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(alldayBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(row)
        refreshBtns()

        val btns = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END; setPadding(0, dp(10), 0, 0)
        }
        btns.addView(Button(this).apply { text = "취소"; setOnClickListener { finish() } })
        btns.addView(Button(this).apply { text = "추가"; setOnClickListener { submit() } })
        root.addView(btns)

        setContentView(root)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        input.requestFocus()
    }

    private fun label(t: String) = TextView(this).apply {
        text = t; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setPadding(0, dp(12), 0, dp(2))
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
                    listIds.add(id)
                    listLabels.add(o.optString("label").ifBlank { id })
                    listColors.add(o.optString("color", "#4772fa"))
                }
            }
        } catch (_: Exception) {}
        if (listIds.isEmpty()) { listIds.add("inbox"); listLabels.add("받은 편지함"); listColors.add("#4772fa") }
    }

    private fun refreshBtns() {
        val m = cal.get(Calendar.MONTH) + 1; val d = cal.get(Calendar.DAY_OF_MONTH)
        val today = Calendar.getInstance()
        val isToday = cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        dateBtn.text = if (isToday) "오늘 (${m}/${d})" else "${m}/${d}"
        timeBtn.text = timeStr ?: "시간 없음"
    }
    private fun pickDate() {
        DatePickerDialog(this, { _, y, mo, da -> cal.set(Calendar.YEAR, y); cal.set(Calendar.MONTH, mo); cal.set(Calendar.DAY_OF_MONTH, da); refreshBtns() },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }
    private fun pickTime() {
        val now = Calendar.getInstance()
        TimePickerDialog(this, { _, h, mi -> timeStr = "%02d:%02d".format(h, mi); refreshBtns() },
            now.get(Calendar.HOUR_OF_DAY), 0, true).show()
    }

    private fun submit() {
        val text = input.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) { finish(); return }
        val pos = listSpinner.selectedItemPosition.coerceIn(0, listIds.size - 1)
        val date = "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        save(text, date, listIds[pos], listColors[pos], timeStr ?: "")
        finish()
    }

    private fun save(text: String, date: String, listId: String, color: String, tm: String) {
        val ctx = applicationContext
        val dir = File(ctx.filesDir, "widgets").apply { mkdirs() }
        // 1) 대기열(pending_adds.json) — 앱이 실제 task 로 생성
        try {
            val pf = File(dir, "pending_adds.json")
            val arr = if (pf.exists()) JSONArray(pf.readText()) else JSONArray()
            arr.put(JSONObject().put("t", text).put("date", date).put("list", listId).put("tm", tm))
            writeAtomic(File(dir, "pending_adds.json.tmp"), pf, arr.toString())
        } catch (_: Exception) {}
        // 2) events.json 낙관적 반영(위젯 즉시 표시)
        try {
            val ef = File(dir, "events.json")
            val root = if (ef.exists()) JSONObject(ef.readText()) else JSONObject()
            val day = root.optJSONArray(date) ?: JSONArray()
            day.put(JSONObject().put("t", text).put("c", color).put("tm", tm).put("d", 0))
            root.put(date, day)
            writeAtomic(File(dir, "events.json.tmp"), ef, root.toString())
        } catch (_: Exception) {}
        // 3) 모든 위젯 갱신
        val mgr = AppWidgetManager.getInstance(ctx)
        for (id in mgr.getAppWidgetIds(ComponentName(ctx, MonthWidgetProvider::class.java))) MonthWidgetProvider.updateOne(ctx, mgr, id)
        for (id in mgr.getAppWidgetIds(ComponentName(ctx, TodoWidgetProvider::class.java))) TodoWidgetProvider.updateOne(ctx, mgr, id)
        for (id in mgr.getAppWidgetIds(ComponentName(ctx, TimelineWidgetProvider::class.java))) TimelineWidgetProvider.updateOne(ctx, mgr, id)
    }

    private fun writeAtomic(tmp: File, dst: File, content: String) {
        tmp.writeText(content); tmp.renameTo(dst)
    }
}
