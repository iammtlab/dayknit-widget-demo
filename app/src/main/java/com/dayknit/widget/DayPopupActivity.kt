package com.dayknit.widget

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/** 달력 셀 클릭 → 그날 일정 목록 팝업(다이얼로그). 앱 본체(WebView)는 열지 않는다. */
class DayPopupActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(true)
        render(intent)
    }

    // singleTask 인스턴스 재사용 시 새 날짜로 다시 그림
    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        render(intent)
    }

    private fun render(intent: android.content.Intent?) {
        val date = intent?.getStringExtra(EXTRA_DATE) ?: run { finish(); return }
        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(18), px(20), px(18))
        }

        root.addView(TextView(this).apply {
            text = prettyDate(date)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, px(10))
        })

        val events = readDay(date)
        if (events.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "일정이 없어요"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, px(8), 0, px(8))
            })
        } else {
            for (ev in events) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, px(7), 0, px(7))
                }
                row.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(px(8), px(8)).also { it.rightMargin = px(10) }
                    setBackgroundColor(parseColor(ev.color))
                })
                ev.time?.let {
                    row.addView(TextView(this).apply {
                        text = it
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        width = px(56)
                    })
                }
                row.addView(TextView(this).apply {
                    text = ev.title
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    if (ev.done) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                root.addView(row)
            }
        }

        setContentView(ScrollView(this).apply {
            addView(root)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
    }

    private data class Ev(val title: String, val color: String?, val time: String?, val done: Boolean)

    private fun readDay(date: String): List<Ev> {
        return try {
            val f = File(File(filesDir, "widgets"), "events.json")
            if (!f.exists()) return emptyList()
            val arr = JSONObject(f.readText()).optJSONArray(date) ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Ev(o.optString("t"), o.optString("c").ifBlank { null },
                   o.optString("tm").ifBlank { null }, o.optInt("d", 0) == 1)
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun parseColor(s: String?): Int = try {
        if (s.isNullOrBlank()) 0xFF4772FA.toInt() else Color.parseColor(s)
    } catch (_: Exception) { 0xFF4772FA.toInt() }

    private fun prettyDate(date: String): String {
        return try {
            val p = date.split("-")
            val c = Calendar.getInstance()
            c.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
            val wd = arrayOf("일", "월", "화", "수", "목", "금", "토")[c.get(Calendar.DAY_OF_WEEK) - 1]
            "${p[1].toInt()}월 ${p[2].toInt()}일 ($wd)"
        } catch (_: Exception) { date }
    }
}
