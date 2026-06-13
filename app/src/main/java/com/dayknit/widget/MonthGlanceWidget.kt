package com.dayknit.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.util.Calendar

/** 월간 캘린더 위젯 — Jetpack Glance. 7열 정적 격자(Row 7칸 weight) × 6주. 둥근 색칩. */
class MonthGlanceWidget : GlanceAppWidget() {

    private data class Cell(val day: Int, val inMonth: Boolean, val isToday: Boolean, val dow: Int, val events: List<Long>)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val weeks = buildMonth()
        provideContent { Content(weeks) }
    }

    private fun buildMonth(): List<List<Cell>> {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR); val month = cal.get(Calendar.MONTH)
        val today = cal.get(Calendar.DAY_OF_MONTH)
        // 샘플 이벤트 색(오늘/내일/모레/주말)
        val sample = mapOf(
            today to listOf(0xFF22A06B, 0xFF4772FA),
            (today + 1) to listOf(0xFF6D4AFA),
            (today + 2) to listOf(0xFFF2802E, 0xFFE0508F),
            (today + 5) to listOf(0xFF0EA5A5),
        )
        val first = Calendar.getInstance().apply { set(year, month, 1) }
        val lead = first.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val start = Calendar.getInstance().apply { set(year, month, 1); add(Calendar.DAY_OF_MONTH, -lead) }
        val weeks = ArrayList<List<Cell>>()
        for (w in 0 until 6) {
            val row = ArrayList<Cell>(7)
            for (d in 0 until 7) {
                val y = start.get(Calendar.YEAR); val m = start.get(Calendar.MONTH); val day = start.get(Calendar.DAY_OF_MONTH)
                val inM = (m == month)
                row.add(Cell(day, inM, inM && day == today, start.get(Calendar.DAY_OF_WEEK),
                    if (inM) sample[day] ?: emptyList() else emptyList()))
                start.add(Calendar.DAY_OF_MONTH, 1)
            }
            weeks.add(row)
        }
        return weeks
    }

    @Composable
    private fun Content(weeks: List<List<Cell>>) {
        val cal = Calendar.getInstance()
        val title = "${cal.get(Calendar.YEAR)}년 ${cal.get(Calendar.MONTH) + 1}월"
        val wd = listOf("일", "월", "화", "수", "목", "금", "토")
        Column(GlanceModifier.fillMaxSize().background(ColorProvider(Color(0xFFFFFFFF)))) {
            // 헤더
            Row(
                GlanceModifier.fillMaxWidth().background(ColorProvider(Color(0xFF4772FA))).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = TextStyle(color = ColorProvider(Color.White), fontSize = 16.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.defaultWeight())
                Text("‹    ›    ⚙    +", style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp))
            }
            // 요일 헤더
            Row(GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                for (i in 0 until 7) {
                    val col = when (i) { 0 -> 0xFFE5544B; 6 -> 0xFF3F74E0; else -> 0xFF8A9099 }
                    Text(wd[i], modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(color = ColorProvider(Color(col)), fontSize = 11.sp, textAlign = TextAlign.Center))
                }
            }
            // 6주 격자
            for (week in weeks) {
                Row(GlanceModifier.fillMaxWidth().defaultWeight().padding(horizontal = 3.dp)) {
                    // defaultWeight() 는 Row 스코프에서만 가능 → 여기서 modifier 로 만들어 넘김
                    for (c in week) DayCell(c, GlanceModifier.defaultWeight().fillMaxHeight())
                }
            }
        }
    }

    @Composable
    private fun DayCell(c: Cell, modifier: GlanceModifier) {
        val numColor = when {
            !c.inMonth -> 0xFFC4C9D0
            c.isToday -> 0xFF4772FA
            c.dow == Calendar.SUNDAY -> 0xFFE5544B
            c.dow == Calendar.SATURDAY -> 0xFF3F74E0
            else -> 0xFF1F2328
        }
        Column(modifier.padding(2.dp)) {
            Text(
                c.day.toString(),
                style = TextStyle(color = ColorProvider(Color(numColor)), fontSize = 11.sp,
                    fontWeight = if (c.isToday) FontWeight.Bold else FontWeight.Normal),
            )
            for (color in c.events.take(2)) {
                Text(
                    " ",
                    maxLines = 1,
                    modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp)
                        .background(ColorProvider(Color(color))).cornerRadius(3.dp),
                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 8.sp),
                )
            }
        }
    }
}
