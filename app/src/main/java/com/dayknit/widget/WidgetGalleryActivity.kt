package com.dayknit.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.util.TypedValue
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/**
 * 디버그 전용: 각 위젯의 실제 RemoteViews 를 그대로 렌더해 한 화면에 모아 보여준다.
 * CI 에뮬레이터에서 `am start` 로 띄워 screencap → 진짜 위젯 모습을 폰/설치 없이 확인.
 * 샘플 events.json 을 먼저 써서 내용이 차도록 한다.
 */
class WidgetGalleryActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        writeSample()

        // "which": month|todo|timeline 면 그 위젯만 크게(스크린샷 1장에 꽉 차게), 그 외엔 전부 세로 스택
        val which = intent?.getStringExtra("which") ?: "all"
        val solo = which != "all"
        val mgr = AppWidgetManager.getInstance(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFE9ECF1.toInt())
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

        fun section(title: String, w: Int, h: Int, rv: RemoteViews) {
            root.addView(TextView(this).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(0xFF333333.toInt())
                setPadding(0, dp(12), 0, dp(5))
            })
            val fw = if (w <= 0) LinearLayout.LayoutParams.MATCH_PARENT else dp(w)
            val frame = FrameLayout(this).apply {
                setBackgroundColor(0xFFFFFFFF.toInt())
                layoutParams = LinearLayout.LayoutParams(fw, dp(h))
            }
            try {
                frame.addView(rv.apply(applicationContext, frame))
            } catch (e: Exception) {
                frame.addView(TextView(this@WidgetGalleryActivity).apply { text = "render 실패: ${e.message}" })
            }
            root.addView(frame)
        }

        // solo(위젯별)는 화면폭(match_parent=-1)에 꽉 차게 → 실제 홈 위젯처럼, 우측 잘림 없음
        if (which == "all" || which == "month")
            section("월간 (4x5)", if (solo) -1 else 320, if (solo) 460 else 360, MonthWidgetProvider.buildViews(this, 999001))
        if (which == "all" || which == "todo")
            section("할 일", if (solo) -1 else 240, if (solo) 560 else 360, TodoWidgetProvider.buildViews(this, 999002))
        if (which == "all" || which == "timeline")
            section("타임라인 (데이뷰, 3일)", if (solo) -1 else 360, if (solo) 540 else 360, TimelineWidgetProvider.buildViews(this, mgr, 999003))

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** 위젯이 비지 않도록 샘플 일정 데이터를 events.json 에 기록. */
    private fun writeSample() {
        try {
            val dir = File(filesDir, "widgets").apply { mkdirs() }
            val root = JSONObject()
            fun day(off: Int, vararg evs: Triple<String, String, String>) {
                val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_MONTH, off)
                val ds = "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
                val arr = JSONArray()
                for ((t, color, tm) in evs)
                    arr.put(JSONObject().put("t", t).put("c", color).put("tm", tm).put("d", 0))
                root.put(ds, arr)
            }
            day(0,
                Triple("기상·운동", "#22A06B", "07:00"),
                Triple("팀 스탠드업", "#4772FA", "09:30"),
                Triple("점심 약속", "#F2802E", "12:00"),
                Triple("디자인 리뷰", "#E0508F", "14:00"),
                Triple("1:1 미팅", "#6D4AFA", "14:30"),
                Triple("저녁 운동", "#0EA5A5", "19:00"),
                Triple("결혼기념일", "#E5544B", ""))
            day(1,
                Triple("치과 예약", "#6D4AFA", "11:00"),
                Triple("프로젝트 마감", "#E5544B", ""),
                Triple("저녁 모임", "#F2802E", "18:30"))
            day(2,
                Triple("주간 회의", "#4772FA", "10:00"),
                Triple("영화 관람", "#E0508F", "20:00"))
            day(3, Triple("출장", "#0EA5A5", "09:00"))
            day(5, Triple("세미나", "#22A06B", "13:00"))
            File(dir, "events.json").writeText(root.toString())
        } catch (_: Exception) {}
    }
}
