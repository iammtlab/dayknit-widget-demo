package com.dayknit.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/**
 * 위젯 '+' → 작은 입력창으로 오늘 일정 추가(앱 본체 안 열림).
 * - pending_adds.json 에 큐잉(앱이 다음 실행 시 실제 task 생성+서버 동기화)
 * - events.json 에 낙관적 반영(위젯에 즉시 표시) + 월간 위젯 갱신
 */
class QuickAddActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUi()
    }

    // singleTask 인스턴스 재사용 시 입력창 초기화
    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        setupUi()
    }

    private fun setupUi() {
        setContentView(R.layout.widget_quickadd)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        val input = findViewById<EditText>(R.id.qa_text)
        input.requestFocus()

        findViewById<Button>(R.id.qa_cancel).setOnClickListener { finish() }
        findViewById<Button>(R.id.qa_save).setOnClickListener {
            val text = input.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) save(text)
            finish()
        }
        input.setOnEditorActionListener { _, _, _ ->
            val text = input.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) save(text)
            finish(); true
        }
    }

    private fun save(text: String) {
        val ctx = applicationContext
        val cal = Calendar.getInstance()
        val today = "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        val dir = File(ctx.filesDir, "widgets").apply { mkdirs() }

        // 1) 대기열(pending_adds.json) append
        try {
            val pf = File(dir, "pending_adds.json")
            val arr = if (pf.exists()) JSONArray(pf.readText()) else JSONArray()
            arr.put(JSONObject().put("t", text).put("date", today))
            writeAtomic(File(dir, "pending_adds.json.tmp"), pf, arr.toString())
        } catch (_: Exception) {}

        // 2) events.json 낙관적 반영(위젯 즉시 표시)
        try {
            val ef = File(dir, "events.json")
            val root = if (ef.exists()) JSONObject(ef.readText()) else JSONObject()
            val day = root.optJSONArray(today) ?: JSONArray()
            day.put(JSONObject().put("t", text).put("c", "#4772fa").put("tm", "").put("d", 0))
            root.put(today, day)
            writeAtomic(File(dir, "events.json.tmp"), ef, root.toString())
        } catch (_: Exception) {}

        // 3) 모든 위젯 갱신(월간·할일·타임라인)
        val mgr = AppWidgetManager.getInstance(ctx)
        for (id in mgr.getAppWidgetIds(ComponentName(ctx, MonthWidgetProvider::class.java)))
            MonthWidgetProvider.updateOne(ctx, mgr, id)
        for (id in mgr.getAppWidgetIds(ComponentName(ctx, TodoWidgetProvider::class.java)))
            TodoWidgetProvider.updateOne(ctx, mgr, id)
        for (id in mgr.getAppWidgetIds(ComponentName(ctx, TimelineWidgetProvider::class.java)))
            TimelineWidgetProvider.updateOne(ctx, mgr, id)
    }

    private fun writeAtomic(tmp: File, dst: File, content: String) {
        tmp.writeText(content)
        tmp.renameTo(dst)
    }
}
