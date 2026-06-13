package com.dayknit.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.compose
import kotlinx.coroutines.runBlocking
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

    @OptIn(ExperimentalGlanceApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        writeSample()

        // "which": month|todo|timeline|glancetodo|imagetodo 면 그것만 크게, 그 외엔 전부 세로 스택
        val which = intent?.getStringExtra("which") ?: "all"
        val solo = which != "all"
        val mgr = AppWidgetManager.getInstance(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFE9ECF1.toInt())
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

        fun titleTv(title: String) = TextView(this).apply {
            text = title; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(0xFF333333.toInt()); setPadding(0, dp(12), 0, dp(5))
        }
        fun frameFor(w: Int, h: Int) = FrameLayout(this).apply {
            setBackgroundColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(if (w <= 0) LinearLayout.LayoutParams.MATCH_PARENT else dp(w), dp(h))
        }
        fun sectionView(title: String, w: Int, h: Int, v: View) {
            root.addView(titleTv(title)); val f = frameFor(w, h); f.addView(v); root.addView(f)
        }
        fun section(title: String, w: Int, h: Int, rv: RemoteViews) {
            root.addView(titleTv(title)); val f = frameFor(w, h)
            try { f.addView(rv.apply(applicationContext, f)) }
            catch (e: Exception) { f.addView(TextView(this).apply { text = "render 실패: ${e.message}" }) }
            root.addView(f)
        }

        // solo(위젯별)는 화면폭(match_parent=-1)에 꽉 차게 → 실제 홈 위젯처럼, 우측 잘림 없음
        if (which == "all" || which == "month")
            section("월간 (4x5)", if (solo) -1 else 320, if (solo) 460 else 360, MonthWidgetProvider.buildViews(this, 999001))
        if (which == "all" || which == "todo")
            section("할 일", if (solo) -1 else 240, if (solo) 560 else 360, TodoWidgetProvider.buildViews(this, 999002))
        if (which == "all" || which == "timeline")
            section("타임라인 (데이뷰, 3일)", if (solo) -1 else 360, if (solo) 540 else 360, TimelineWidgetProvider.buildViews(this, mgr, 999003))

        // 시제품 A — Jetpack Glance (compose → RemoteViews)
        if (which == "glancetodo") {
            try {
                val rv = runBlocking { TodoGlanceWidget().compose(this@WidgetGalleryActivity, size = DpSize(300.dp, 520.dp)) }
                section("할 일 — 시제품 A (Glance)", -1, 540, rv)
            } catch (e: Exception) {
                sectionView("Glance 렌더 실패", -1, 100, TextView(this).apply { text = "${e.message}" })
            }
        }
        // 시제품 B — 이미지 스냅샷 (Canvas → Bitmap)
        if (which == "imagetodo") {
            val wpx = resources.displayMetrics.widthPixels - dp(20)
            val bmp = TodoImageRenderer.render(this, wpx, dp(540))
            sectionView("할 일 — 시제품 B (이미지)", -1, 540, ImageView(this).apply {
                setImageBitmap(bmp); scaleType = ImageView.ScaleType.FIT_XY
            })
        }

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

            // 빠른추가 리스트 선택용 샘플 lists.json
            val lists = JSONArray()
            fun lst(id: String, label: String, color: String) =
                lists.put(JSONObject().put("id", id).put("label", label).put("color", color))
            lst("inbox", "받은 편지함", "#4772FA")
            lst("work", "업무", "#E5544B")
            lst("personal", "개인", "#22A06B")
            lst("health", "건강", "#0EA5A5")
            lst("study", "공부", "#6D4AFA")
            File(dir, "lists.json").writeText(lists.toString())
        } catch (_: Exception) {}
    }
}
