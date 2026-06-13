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

/** 할일/아젠다 — 오늘부터 다가오는 미완료 일정을 날짜 그룹(오늘/내일/M.D)으로 묶어 보여주는 정적 리스트. */
class TodoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateOne(ctx, mgr, id)
    }
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, o: Bundle?) {
        updateOne(ctx, mgr, id)
    }
    override fun onDeleted(ctx: Context, ids: IntArray) {
        for (id in ids) WidgetConfig.clear(ctx, id)
    }

    private data class Row(val date: String, val title: String, val color: Int,
                           val time: String, val groupStart: Boolean, val groupLabel: String)

    companion object {
        private const val ROWS = 18
        private const val SCAN_DAYS = 28
        private val WD = arrayOf("일", "월", "화", "수", "목", "금", "토")

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
            val sun = if (dark) 0xFFFF6B6B.toInt() else 0xFFE5544B.toInt()
            val bgRgb = if (dark) 0x1C1C1E else 0xFFFFFF
            val rootArgb = (alpha shl 24) or (bgRgb and 0x00FFFFFF)

            val views = RemoteViews(ctx.packageName, R.layout.widget_todo)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg_round)
                views.setColorStateList(R.id.widget_root, "setBackgroundTintList", android.content.res.ColorStateList.valueOf(rootArgb))
            } else {
                views.setInt(R.id.widget_root, "setBackgroundColor", rootArgb)
            }
            views.setInt(R.id.widget_header, "setBackgroundColor", header)
            views.setTextColor(R.id.title, onHeader)
            views.setTextColor(R.id.widget_config, onHeader)
            views.setInt(R.id.widget_add, "setColorFilter", onHeader)

            val imm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

            // 제목 → 앱 열기
            val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            if (open != null) {
                views.setOnClickPendingIntent(R.id.title,
                    PendingIntent.getActivity(ctx, id * 100 + 94, open, PendingIntent.FLAG_UPDATE_CURRENT or imm))
            }
            // 설정
            val cfg = Intent(ctx, WidgetConfigActivity::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            views.setOnClickPendingIntent(R.id.widget_config,
                PendingIntent.getActivity(ctx, id * 100 + 90, cfg, PendingIntent.FLAG_UPDATE_CURRENT or imm))
            // + → 빠른 추가
            val add = Intent(ctx, QuickAddActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                data = Uri.parse("dayknit://add/$id")
            }
            views.setOnClickPendingIntent(R.id.widget_add,
                PendingIntent.getActivity(ctx, id * 100 + 91, add, PendingIntent.FLAG_UPDATE_CURRENT or imm))

            val all = buildRows(ctx)
            val shown = minOf(all.size, ROWS)
            for (i in 0 until ROWS) {
                val rowId = res(ctx, "row$i"); val sepId = res(ctx, "sep$i")
                val ckId = res(ctx, "ck$i"); val ttId = res(ctx, "tt$i"); val tmId = res(ctx, "tm$i")
                if (i >= shown) { views.setViewVisibility(rowId, View.GONE); continue }
                val r = all[i]
                views.setViewVisibility(rowId, View.VISIBLE)
                if (r.groupStart) {
                    views.setViewVisibility(sepId, View.VISIBLE)
                    views.setTextViewText(sepId, r.groupLabel)
                    views.setTextColor(sepId, if (r.groupLabel == "오늘") header else dim)
                } else views.setViewVisibility(sepId, View.GONE)
                views.setInt(ckId, "setColorFilter", r.color)
                views.setTextViewText(ttId, r.title)
                views.setTextColor(ttId, fg)
                if (r.time.isEmpty()) views.setViewVisibility(tmId, View.GONE)
                else {
                    views.setViewVisibility(tmId, View.VISIBLE)
                    views.setTextViewText(tmId, r.time)
                    views.setTextColor(tmId, dim)
                }
                // 행 클릭 → 그날 일정 팝업
                val dayIntent = Intent(ctx, DayPopupActivity::class.java).apply {
                    putExtra(EXTRA_DATE, r.date)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    data = Uri.parse("dayknit://day/$id/${r.date}/$i")
                }
                views.setOnClickPendingIntent(rowId,
                    PendingIntent.getActivity(ctx, id * 100 + i, dayIntent, PendingIntent.FLAG_UPDATE_CURRENT or imm))
            }

            when {
                all.isEmpty() -> {
                    views.setViewVisibility(R.id.todo_more, View.VISIBLE)
                    views.setTextViewText(R.id.todo_more, "다가오는 할 일이 없어요")
                    views.setTextColor(R.id.todo_more, dim)
                }
                all.size > ROWS -> {
                    views.setViewVisibility(R.id.todo_more, View.VISIBLE)
                    views.setTextViewText(R.id.todo_more, "+${all.size - ROWS}개 더")
                    views.setTextColor(R.id.todo_more, dim)
                }
                else -> views.setViewVisibility(R.id.todo_more, View.GONE)
            }

            return views
        }

        private fun res(ctx: Context, name: String): Int =
            ctx.resources.getIdentifier(name, "id", ctx.packageName)

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

        /** 오늘부터 SCAN_DAYS 동안의 미완료 일정을 날짜순으로 평탄화(그룹 시작 표시 포함). */
        private fun buildRows(ctx: Context): List<Row> {
            val root = readEvents(ctx)
            val cal = Calendar.getInstance()
            val out = ArrayList<Row>()
            var lastDate = ""
            for (off in 0 until SCAN_DAYS) {
                val y = cal.get(Calendar.YEAR); val m = cal.get(Calendar.MONTH) + 1; val d = cal.get(Calendar.DAY_OF_MONTH)
                val ds = "%04d-%02d-%02d".format(y, m, d)
                val arr = root.optJSONArray(ds)
                if (arr != null) {
                    for (j in 0 until arr.length()) {
                        val o = arr.optJSONObject(j) ?: continue
                        if (o.optInt("d", 0) == 1) continue          // 완료 제외
                        val groupStart = ds != lastDate
                        out.add(Row(
                            date = ds,
                            title = o.optString("t", ""),
                            color = parseColor(o.optString("c")),
                            time = o.optString("tm", ""),
                            groupStart = groupStart,
                            groupLabel = if (groupStart) label(off, cal) else ""
                        ))
                        lastDate = ds
                    }
                }
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
            return out
        }

        private fun label(off: Int, cal: Calendar): String = when (off) {
            0 -> "오늘"
            1 -> "내일"
            else -> "${cal.get(Calendar.MONTH) + 1}.${cal.get(Calendar.DAY_OF_MONTH)}(${WD[cal.get(Calendar.DAY_OF_WEEK) - 1]})"
        }
    }
}
