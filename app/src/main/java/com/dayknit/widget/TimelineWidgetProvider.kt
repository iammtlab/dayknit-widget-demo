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
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/**
 * 주간/일간 타임라인(데이뷰) — 앱 캘린더처럼 시간대별. 정적 그리드(시각라벨+가로선) 위에
 * 요일 컬럼(FrameLayout)을 겹쳐, 각 일정을 시작시각 위치(setViewLayoutMargin TOP, API31+)에
 * 블록으로 배치. 그리드선·블록 모두 같은 ppm(dp/분) 좌표라 정렬이 맞는다.
 * API31 미만은 위치지정 불가 → 안내 메시지 폴백.
 */
class TimelineWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateOne(ctx, mgr, id)
    }
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, o: Bundle?) {
        updateOne(ctx, mgr, id)   // 리사이즈 시 ppm 재계산
    }
    override fun onDeleted(ctx: Context, ids: IntArray) {
        for (id in ids) WidgetConfig.clear(ctx, id)
    }

    private data class Ev(val title: String, val color: Int, val min: Int, val done: Boolean,
                          var lane: Int = 0, var lanes: Int = 1)

    companion object {
        private const val HOURS = 25
        private const val COLS = 7
        private const val SLOTS = 6
        private const val DUR = 50          // 데이터에 지속시간 없음 → 고정 50분 블록(앱과 동일)
        private val WD = arrayOf("일", "월", "화", "수", "목", "금", "토")

        fun updateOne(ctx: Context, mgr: AppWidgetManager, id: Int) {
            mgr.updateAppWidget(id, buildViews(ctx, mgr, id))
        }

        /** RemoteViews 생성(미리보기/갤러리에서 재사용). 본문 크기는 mgr 옵션에서 추정. */
        fun buildViews(ctx: Context, mgr: AppWidgetManager, id: Int): RemoteViews {
            val dark = WidgetConfig.isDark(ctx, id)
            val alpha = WidgetConfig.alpha(ctx, id)
            val header = parseColor(WidgetConfig.headerColor(ctx, id))
            val onHeader = if (luminance(header) > 0.6) 0xFF1B1B1B.toInt() else 0xFFFFFFFF.toInt()
            val fg = if (dark) 0xFFF2F3F5.toInt() else 0xFF1F2328.toInt()
            val dim = if (dark) 0xFFA8ADB6.toInt() else 0xFF6B7280.toInt()
            val faint = if (dark) 0xFF60646B.toInt() else 0xFFB4BAC2.toInt()
            val grid = if (dark) 0x22FFFFFF else 0x14000000
            val sun = if (dark) 0xFFFF6B6B.toInt() else 0xFFE5544B.toInt()
            val sat = if (dark) 0xFF6EA8FF.toInt() else 0xFF3F74E0.toInt()
            val bgRgb = if (dark) 0x1C1C1E else 0xFFFFFF
            val rootArgb = (alpha shl 24) or (bgRgb and 0x00FFFFFF)

            val days = WidgetConfig.days(ctx, id).coerceIn(1, COLS)
            val startH = WidgetConfig.startHour(ctx, id)
            val endH = WidgetConfig.endHour(ctx, id)

            val views = RemoteViews(ctx.packageName, R.layout.widget_timeline)
            views.setInt(R.id.widget_root, "setBackgroundColor", rootArgb)
            views.setInt(R.id.widget_header, "setBackgroundColor", header)
            views.setTextColor(R.id.title, onHeader)
            views.setTextColor(R.id.widget_config, onHeader)
            views.setInt(R.id.widget_add, "setColorFilter", onHeader)

            val cal = Calendar.getInstance()
            val y0 = cal.get(Calendar.YEAR); val m0 = cal.get(Calendar.MONTH) + 1; val d0 = cal.get(Calendar.DAY_OF_MONTH)
            views.setTextViewText(R.id.title, if (days == 1) "${m0}월 ${d0}일" else {
                val last = Calendar.getInstance(); last.add(Calendar.DAY_OF_MONTH, days - 1)
                "${m0}.${d0} ~ ${last.get(Calendar.MONTH) + 1}.${last.get(Calendar.DAY_OF_MONTH)}"
            })

            val imm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            wireHeader(ctx, views, id, imm)

            // ── API31 미만: 위치지정 불가 → 안내 폴백 ──
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                views.setViewVisibility(R.id.tl_cols, View.GONE)
                for (h in 0 until HOURS) {
                    views.setViewVisibility(res(ctx, "ln$h"), View.GONE)
                    views.setViewVisibility(res(ctx, "hl$h"), View.GONE)
                }
                views.setViewVisibility(R.id.tl_msg, View.VISIBLE)
                views.setTextViewText(R.id.tl_msg, "타임라인 위젯은 Android 12 이상에서 표시됩니다.")
                views.setTextColor(R.id.tl_msg, dim)
                return views
            }
            views.setViewVisibility(R.id.tl_msg, View.GONE)
            views.setViewVisibility(R.id.tl_cols, View.VISIBLE)

            // ── 본문 크기 추정 → ppm(dp/분) ──
            val opts = mgr.getAppWidgetOptions(id)
            val minH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            val maxH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            val widgetH = when {
                minH > 0 && maxH > 0 -> (minH + maxH) / 2
                minH > 0 -> minH
                else -> 320
            }
            val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val widgetW = if (minW > 0) minW else 300
            val bodyDp = (widgetH - 36 - 22).coerceAtLeast(140).toFloat()   // 헤더+날짜머리 제외
            val hours = (endH - startH).coerceAtLeast(2)
            val ppm = bodyDp / (hours * 60f)
            val colWidthDp = ((widgetW - 38f) / days).coerceAtLeast(24f)

            // ── 시간 그리드(선+라벨), 같은 ppm 좌표 ──
            for (h in 0 until HOURS) {
                val lnId = res(ctx, "ln$h"); val hlId = res(ctx, "hl$h")
                if (h in startH..endH) {
                    val topDp = (h - startH) * 60 * ppm
                    views.setViewVisibility(lnId, View.VISIBLE)
                    views.setInt(lnId, "setColorFilter", grid)
                    views.setViewLayoutMargin(lnId, RemoteViews.MARGIN_TOP, topDp, TypedValue.COMPLEX_UNIT_DIP)
                    if (h < endH) {
                        views.setViewVisibility(hlId, View.VISIBLE)
                        views.setTextViewText(hlId, hourLabel(h))
                        views.setTextColor(hlId, faint)
                        views.setViewLayoutMargin(hlId, RemoteViews.MARGIN_TOP, (topDp - 1).coerceAtLeast(0f), TypedValue.COMPLEX_UNIT_DIP)
                    } else views.setViewVisibility(hlId, View.GONE)
                } else {
                    views.setViewVisibility(lnId, View.GONE)
                    views.setViewVisibility(hlId, View.GONE)
                }
            }

            // ── 컬럼별 일정 블록 ──
            val root = readEvents(ctx)
            for (c in 0 until COLS) {
                val colId = res(ctx, "col$c")
                if (c >= days) { views.setViewVisibility(colId, View.GONE); continue }
                views.setViewVisibility(colId, View.VISIBLE)

                val cc = Calendar.getInstance(); cc.add(Calendar.DAY_OF_MONTH, c)
                val cy = cc.get(Calendar.YEAR); val cm = cc.get(Calendar.MONTH) + 1; val cd = cc.get(Calendar.DAY_OF_MONTH)
                val ds = "%04d-%02d-%02d".format(cy, cm, cd)
                val dow = cc.get(Calendar.DAY_OF_WEEK) - 1

                // 날짜 머리
                val dhId = res(ctx, "dh$c")
                views.setViewVisibility(dhId, View.VISIBLE)
                views.setTextViewText(dhId, "${WD[dow]} $cd")
                views.setTextColor(dhId, when {
                    c == 0 -> header
                    dow == 0 -> sun
                    dow == 6 -> sat
                    else -> fg
                })
                views.setOnClickPendingIntent(dhId, dayPI(ctx, id, ds, 70 + c, imm))

                val evs = packLanes(eventsFor(root, ds, startH, endH))
                var slot = 0
                for (ev in evs) {
                    if (slot >= SLOTS) break
                    val blkId = res(ctx, "blk${c}_$slot")
                    val bgId = res(ctx, "bkbg${c}_$slot")
                    val txId = res(ctx, "bktx${c}_$slot")
                    val isLast = slot == SLOTS - 1 && evs.size > SLOTS
                    views.setViewVisibility(blkId, View.VISIBLE)
                    val topDp = (ev.min - startH * 60) * ppm
                    val hDp = (DUR * ppm).coerceAtLeast(16f)
                    views.setViewLayoutMargin(blkId, RemoteViews.MARGIN_TOP, topDp.coerceAtLeast(0f), TypedValue.COMPLEX_UNIT_DIP)
                    views.setViewLayoutHeight(blkId, hDp, TypedValue.COMPLEX_UNIT_DIP)
                    if (ev.lanes > 1) {
                        // 겹침: 컬럼 폭 추정으로 좌우 분할(폭 정밀도는 추정이라 약간 어긋날 수 있음)
                        val lw = colWidthDp / ev.lanes
                        views.setViewLayoutMargin(blkId, RemoteViews.MARGIN_START, ev.lane * lw, TypedValue.COMPLEX_UNIT_DIP)
                        views.setViewLayoutWidth(blkId, (lw - 1).coerceAtLeast(10f), TypedValue.COMPLEX_UNIT_DIP)
                    } else {
                        // 단독: 컬럼 전체 폭(match_parent) 유지 — 추정 폭 안 씀
                        views.setViewLayoutMargin(blkId, RemoteViews.MARGIN_START, 0f, TypedValue.COMPLEX_UNIT_DIP)
                    }
                    views.setInt(bgId, "setColorFilter", ev.color)
                    views.setInt(bgId, "setImageAlpha", if (ev.done) 110 else 255)
                    val txt = if (isLast) "+${evs.size - slot}" else (if (days <= 3) timeStr(ev.min) + " " else "") + ev.title
                    views.setTextViewText(txId, txt)
                    views.setTextColor(txId, onColor(ev.color))
                    views.setOnClickPendingIntent(blkId, dayPI(ctx, id, ds, 100 + c * SLOTS + slot, imm))
                    slot++
                }
                // 남은 슬롯 숨김
                for (k in slot until SLOTS) views.setViewVisibility(res(ctx, "blk${c}_$k"), View.GONE)

                // now-line(오늘 = c==0 컬럼만)
                val nowId = res(ctx, "now$c")
                val nowMin = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
                if (c == 0 && nowMin in (startH * 60)..(endH * 60)) {
                    views.setViewVisibility(nowId, View.VISIBLE)
                    views.setInt(nowId, "setColorFilter", 0xFFE5544B.toInt())
                    views.setViewLayoutMargin(nowId, RemoteViews.MARGIN_TOP, (nowMin - startH * 60) * ppm, TypedValue.COMPLEX_UNIT_DIP)
                } else views.setViewVisibility(nowId, View.GONE)
            }
            // 안 쓰는 날짜머리 숨김
            for (c in days until COLS) views.setViewVisibility(res(ctx, "dh$c"), View.GONE)

            return views
        }

        private fun wireHeader(ctx: Context, views: RemoteViews, id: Int, imm: Int) {
            val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            if (open != null) views.setOnClickPendingIntent(R.id.title,
                PendingIntent.getActivity(ctx, id * 100 + 94, open, PendingIntent.FLAG_UPDATE_CURRENT or imm))
            val cfg = Intent(ctx, WidgetConfigActivity::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            views.setOnClickPendingIntent(R.id.widget_config,
                PendingIntent.getActivity(ctx, id * 100 + 90, cfg, PendingIntent.FLAG_UPDATE_CURRENT or imm))
            val add = Intent(ctx, QuickAddActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK; data = Uri.parse("dayknit://add/$id")
            }
            views.setOnClickPendingIntent(R.id.widget_add,
                PendingIntent.getActivity(ctx, id * 100 + 91, add, PendingIntent.FLAG_UPDATE_CURRENT or imm))
        }

        private fun dayPI(ctx: Context, id: Int, ds: String, code: Int, imm: Int): PendingIntent {
            val i = Intent(ctx, DayPopupActivity::class.java).apply {
                putExtra(EXTRA_DATE, ds)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                data = Uri.parse("dayknit://day/$id/$ds/$code")
            }
            return PendingIntent.getActivity(ctx, id * 100 + code, i, PendingIntent.FLAG_UPDATE_CURRENT or imm)
        }

        private fun res(ctx: Context, name: String): Int =
            ctx.resources.getIdentifier(name, "id", ctx.packageName)

        private fun hourLabel(h: Int): String {
            val ampm = if (h < 12 || h == 24) "오전" else "오후"
            var hr = h % 12; if (hr == 0) hr = 12
            return "$ampm $hr"
        }
        private fun timeStr(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

        /** 하루 timed 일정 파싱(범위 내). 시작분 정렬. */
        private fun eventsFor(root: JSONObject, ds: String, startH: Int, endH: Int): List<Ev> {
            val arr = root.optJSONArray(ds) ?: return emptyList()
            val out = ArrayList<Ev>()
            for (j in 0 until arr.length()) {
                val o = arr.optJSONObject(j) ?: continue
                val tm = o.optString("tm", "")
                if (tm.isBlank()) continue                  // 종일 제외(v1)
                val parts = tm.split(":")
                val min = (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
                if (min < startH * 60 || min >= endH * 60) continue
                out.add(Ev(o.optString("t", ""), parseColor(o.optString("c")), min, o.optInt("d", 0) == 1))
            }
            out.sortBy { it.min }
            return out
        }

        /** 겹침 레인 배치(최대 2레인). widget.ts 와 동일 개념, 레인 수 2로 캡. */
        private fun packLanes(evs: List<Ev>): List<Ev> {
            var i = 0
            while (i < evs.size) {
                var j = i; var end = evs[i].min + DUR
                val group = arrayListOf(evs[i])
                while (j + 1 < evs.size && evs[j + 1].min < end) { j++; group.add(evs[j]); end = maxOf(end, evs[j].min + DUR) }
                val laneEnds = ArrayList<Int>()
                for (ev in group) {
                    var placed = false
                    for (k in laneEnds.indices) if (ev.min >= laneEnds[k]) { ev.lane = k; laneEnds[k] = ev.min + DUR; placed = true; break }
                    if (!placed) { ev.lane = laneEnds.size; laneEnds.add(ev.min + DUR) }
                }
                val lanes = minOf(laneEnds.size, 2)
                for (ev in group) { ev.lanes = lanes; ev.lane = minOf(ev.lane, lanes - 1) }
                i = j + 1
            }
            return evs
        }

        private fun luminance(c: Int): Double {
            val r = (c shr 16 and 0xFF) / 255.0; val g = (c shr 8 and 0xFF) / 255.0; val b = (c and 0xFF) / 255.0
            return 0.2126 * r + 0.7152 * g + 0.0722 * b
        }
        private fun onColor(bg: Int): Int = if (luminance(bg) > 0.62) 0xFF1B1B1B.toInt() else 0xFFFFFFFF.toInt()
        private fun parseColor(s: String?): Int = try {
            if (s.isNullOrBlank()) 0xFF4772FA.toInt() else Color.parseColor(s)
        } catch (_: Exception) { 0xFF4772FA.toInt() }

        private fun readEvents(ctx: Context): JSONObject = try {
            val f = File(File(ctx.filesDir, "widgets"), "events.json")
            if (f.exists()) JSONObject(f.readText()) else JSONObject()
        } catch (_: Exception) { JSONObject() }
    }
}
