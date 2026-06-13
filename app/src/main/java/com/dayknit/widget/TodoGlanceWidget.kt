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
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/** 시제품 A: 할일 위젯을 Jetpack Glance(선언형 Kotlin)로 — 유명 앱 방식. */
class TodoGlanceWidget : GlanceAppWidget() {

    private data class Row(val group: String?, val title: String, val color: Long, val time: String?)

    private val rows = listOf(
        Row("오늘", "기상·운동", 0xFF22A06B, "07:00"),
        Row(null, "팀 스탠드업", 0xFF4772FA, "09:30"),
        Row(null, "점심 약속", 0xFFF2802E, "12:00"),
        Row(null, "디자인 리뷰", 0xFFE0508F, "14:00"),
        Row(null, "결혼기념일", 0xFFE5544B, null),
        Row("내일", "치과 예약", 0xFF6D4AFA, "11:00"),
        Row(null, "프로젝트 마감", 0xFFE5544B, null),
        Row("6.13(금)", "주간 회의", 0xFF4772FA, "10:00"),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content() }
    }

    @Composable
    private fun Content() {
        Column(modifier = GlanceModifier.fillMaxSize().background(ColorProvider(Color(0xFFFFFFFF)))) {
            // 헤더
            Row(
                modifier = GlanceModifier.fillMaxWidth()
                    .background(ColorProvider(Color(0xFF4772FA)))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("할 일", style = TextStyle(color = ColorProvider(Color.White), fontSize = 16.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.defaultWeight())
                Text("⚙   +", style = TextStyle(color = ColorProvider(Color.White), fontSize = 15.sp))
            }
            // 리스트
            Column(modifier = GlanceModifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                rows.forEach { r ->
                    if (r.group != null) {
                        Text(
                            r.group,
                            style = TextStyle(color = ColorProvider(Color(0xFF4772FA)), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            modifier = GlanceModifier.padding(top = 9.dp, bottom = 3.dp),
                        )
                    }
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(GlanceModifier.size(13.dp).cornerRadius(7.dp).background(ColorProvider(Color(r.color)))) {}
                        Spacer(GlanceModifier.width(11.dp))
                        Text(
                            r.title,
                            style = TextStyle(color = ColorProvider(Color(0xFF1F2328)), fontSize = 14.sp),
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        if (r.time != null) {
                            Text(r.time, style = TextStyle(color = ColorProvider(Color(0xFF6B7280)), fontSize = 12.sp))
                        }
                    }
                }
            }
        }
    }
}
