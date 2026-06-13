package com.dayknit.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject
import java.io.File
import java.util.Calendar

const val ACTION_NAV = "com.dayknit.widget.NAV"
const val EXTRA_DATE = "date"          // yyyy-MM-dd

/** 월간 캘린더 — 정적 42칸 그리드(plain RemoteViews). 선명·셀 클릭. 상단바 색·글자크기 설정. */
class MonthWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateOne(ctx, mgr, id)
    }
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, o: Bundle?) {
        updateOne(ctx, mgr, id)
    }
    override fun onDeleted(ctx: Context, ids: IntArray) {
        for (id in ids) WidgetConfig.clear(ctx, id)
    }
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == ACTION_NAV) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            val ym = intent.getStringExtra("ym")
            val delta = intent.getIntExtra("delta", 0)
            if (id != -1 && ym != null) {
                WidgetConfig.setYearMonth(ctx, id, shiftMonth(ym, delta))
                updateOne(ctx, AppWidgetManager.getInstance(ctx), id)
            }
        }
        super.onReceive(ctx, intent)
    }

    private data class Ev(val title: String, val color: Int)
    private data class Cell(val date: String, val day: Int, val inMonth: Boolean,
                            val isToday: Boolean, val dow: Int, val events: List<Ev>)

    companion object {
        fun updateOne(ctx: Context, mgr: AppWidgetManager, id: Int) {
            mgr.updateAppWidget(id, buildViews(ctx, id))
        }

        /** RemoteViews 생성(미리보기/갤러리에서 재사용). */
        fun buildViews(ctx: Context, id: Int): RemoteViews {
            val dark = WidgetConfig.isDark(ctx, id)
            val alpha = WidgetConfig.alpha(ctx, id)
            val header = parseColor(WidgetConfig.headerColor(ctx, id))
            val onHeader = if (luminance(header) > 0.6) 0xFF1B1B1B.toInt() else 0xFFFFFFFF.toInt()
            val fg = if (dark) 0xFFF2F3F5.toInt() else 0xFF1F2328.toInt()
            val dim = if (dark) 0xFFA8ADB6.toInt() else 0xFF6B7280.toInt()
            val faint = if (dark) 0xFF60646B.toInt() else 0xFFB4BAC2.toInt()
            val sun = if (dark) 0xFFFF6B6B.toInt() else 0xFFE5544B.toInt()
            val sat = if (dark) 0xFF6EA8FF.toInt() else 0xFF3F74E0.toInt()
            val bgRgb = if (dark) 0x1C1C1E else 0xFFFFFF
            val rootArgb = (alpha shl 24) or (bgRgb and 0x00FFFFFF)

            val layoutRes = when (WidgetConfig.fontScale(ctx, id)) {
                "small" -> R.layout.widget_calendar_small
                "large" -> R.layout.widget_calendar_large
                else -> R.layout.widget_calendar_normal
            }
            val views = RemoteViews(ctx.packageName, layoutRes)
            // 둥근 위젯 배경 — 흰 라운드 드로어블 + 틴트(rootArgb: 색·투명도). 미만은 사각 색배경.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg_round)
                views.setColorStateList(R.id.widget_root, "setBackgroundTintList", android.content.res.ColorStateList.valueOf(rootArgb))
            } else {
                views.setInt(R.id.widget_root, "setBackgroundColor", rootArgb)
            }
            views.setInt(R.id.widget_header, "setBackgroundColor", header)

            val ym = WidgetConfig.currentYearMonth(ctx, id)
            val parts = ym.split("-")
            views.setTextViewText(R.id.title, "${parts[0]}년 ${parts[1].toInt()}월")
            views.setTextColor(R.id.title, onHeader)
            views.setTextColor(R.id.btn_prev, onHeader)
            views.setTextColor(R.id.btn_next, onHeader)
            views.setTextColor(R.id.widget_config, onHeader)
            views.setInt(R.id.widget_add, "setColorFilter", onHeader) // + 아이콘을 헤더 글자색으로

            val imm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

            // 년/월(타이틀) → 앱 열기
            val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            if (open != null) {
                views.setOnClickPendingIntent(R.id.title,
                    PendingIntent.getActivity(ctx, id * 100 + 94, open, PendingIntent.FLAG_UPDATE_CURRENT or imm))
            }
            // 이전/다음 달
            views.setOnClickPendingIntent(R.id.btn_prev, navPI(ctx, id, ym, -1, imm))
            views.setOnClickPendingIntent(R.id.btn_next, navPI(ctx, id, ym, +1, imm))
            // 설정
            val cfg = Intent(ctx, WidgetConfigActivity::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            views.setOnClickPendingIntent(R.id.widget_config,
                PendingIntent.getActivity(ctx, id * 100 + 90, cfg, PendingIntent.FLAG_UPDATE_CURRENT or imm))
            // '+' → 위젯 내 빠른 추가
            val add = Intent(ctx, QuickAddActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                data = Uri.parse("dayknit://add/$id")
            }
            views.setOnClickPendingIntent(R.id.widget_add,
                PendingIntent.getActivity(ctx, id * 100 + 91, add, PendingIntent.FLAG_UPDATE_CURRENT or imm))

            // 35칸 (5주)
            val cells = buildCells(ym, readEvents(ctx))
            val res = ctx.resources; val pkg = ctx.packageName
            for (i in 0 until 35) {
                val c = cells[i]
                val dId = res.getIdentifier("d$i", "id", pkg)
                val cellId = res.getIdentifier("cell$i", "id", pkg)
                // 오늘 셀 은은한 강조 — 테마색 저알파 둥근 배경(API31+)
                if (c.isToday && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    views.setInt(cellId, "setBackgroundResource", R.drawable.today_cell_bg)
                    views.setColorStateList(cellId, "setBackgroundTintList",
                        android.content.res.ColorStateList.valueOf((0x26 shl 24) or (header and 0x00FFFFFF)))
                } else {
                    views.setInt(cellId, "setBackgroundColor", Color.TRANSPARENT)
                }
                views.setTextViewText(dId, c.day.toString())
                views.setTextColor(dId, when {
                    c.isToday -> header
                    !c.inMonth -> faint
                    c.dow == Calendar.SUNDAY -> if (c.inMonth) sun else faint
                    c.dow == Calendar.SATURDAY -> if (c.inMonth) sat else faint
                    else -> fg
                })
                val n = c.events.size
                for (k in 0 until 4) {
                    val vId = res.getIdentifier("ev${i}_$k", "id", pkg)
                    if (n > 4 && k == 3) {
                        views.setViewVisibility(vId, View.VISIBLE)
                        views.setTextViewText(vId, "+${n - 3}")
                        views.setInt(vId, "setBackgroundColor", Color.TRANSPARENT)
                        views.setTextColor(vId, dim)
                    } else {
                        val ev = c.events.getOrNull(k)
                        if (ev == null) { views.setViewVisibility(vId, View.GONE) }
                        else {
                            views.setViewVisibility(vId, View.VISIBLE)
                            views.setTextViewText(vId, ev.title)
                            // 둥근 칩: 흰 라운드 드로어블 + tint(API31+), 미만은 사각 색배경
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                views.setInt(vId, "setBackgroundResource", R.drawable.chip_bg)
                                views.setColorStateList(vId, "setBackgroundTintList",
                                    android.content.res.ColorStateList.valueOf(ev.color))
                            } else {
                                views.setInt(vId, "setBackgroundColor", ev.color)
                            }
                            views.setTextColor(vId, 0xFFFFFFFF.toInt())
                        }
                    }
                }
                // 날짜 칸 클릭 → 그날 일정 팝업
                val dayIntent = Intent(ctx, DayPopupActivity::class.java).apply {
                    putExtra(EXTRA_DATE, c.date)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    data = Uri.parse("dayknit://day/$id/${c.date}")
                }
                views.setOnClickPendingIntent(cellId,
                    PendingIntent.getActivity(ctx, id * 100 + i, dayIntent, PendingIntent.FLAG_UPDATE_CURRENT or imm))
            }

            return views
        }

        private fun luminance(c: Int): Double {
            val r = (c shr 16 and 0xFF) / 255.0
            val g = (c shr 8 and 0xFF) / 255.0
            val b = (c and 0xFF) / 255.0
            return 0.2126 * r + 0.7152 * g + 0.0722 * b
        }
        private fun parseColor(s: String?): Int = try {
            if (s.isNullOrBlank()) 0xFF4772FA.toInt() else Color.parseColor(s)
        } catch (_: Exception) { 0xFF4772FA.toInt() }

        private fun readEvents(ctx: Context): JSONObject = try {
            val f = File(File(ctx.filesDir, "widgets"), "events.json")
            if (f.exists()) JSONObject(f.readText()) else JSONObject()
        } catch (_: Exception) { JSONObject() }

        private fun buildCells(ym: String, root: JSONObject): List<Cell> {
            val parts = ym.split("-")
            val year = parts[0].toInt(); val month = parts[1].toInt()
            val cal = Calendar.getInstance(); cal.clear(); cal.set(year, month - 1, 1)
            val lead = cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
            cal.add(Calendar.DAY_OF_MONTH, -lead)
            val t = Calendar.getInstance()
            val todayStr = "%04d-%02d-%02d".format(t.get(Calendar.YEAR), t.get(Calendar.MONTH) + 1, t.get(Calendar.DAY_OF_MONTH))
            val out = ArrayList<Cell>(35)
            for (i in 0 until 35) {
                val y = cal.get(Calendar.YEAR); val m = cal.get(Calendar.MONTH) + 1; val d = cal.get(Calendar.DAY_OF_MONTH)
                val ds = "%04d-%02d-%02d".format(y, m, d)
                val evs = ArrayList<Ev>()
                root.optJSONArray(ds)?.let { arr ->
                    for (j in 0 until arr.length()) {
                        val o = arr.optJSONObject(j) ?: continue
                        evs.add(Ev(o.optString("t", ""), parseColor(o.optString("c"))))
                    }
                }
                out.add(Cell(ds, d, m == month, ds == todayStr, cal.get(Calendar.DAY_OF_WEEK), evs))
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
            return out
        }

        private fun navPI(ctx: Context, id: Int, ym: String, delta: Int, imm: Int): PendingIntent {
            val i = Intent(ctx, MonthWidgetProvider::class.java).apply {
                action = ACTION_NAV
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra("ym", ym)
                putExtra("delta", delta)
                data = Uri.parse("dayknit://nav/$id/$delta")
            }
            return PendingIntent.getBroadcast(ctx, id * 100 + (if (delta < 0) 92 else 93), i,
                PendingIntent.FLAG_UPDATE_CURRENT or imm)
        }
    }
}
