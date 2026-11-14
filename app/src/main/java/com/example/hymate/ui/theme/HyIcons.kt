package com.example.hymate.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** 프로토타입의 24×24 라인 아이콘 세트 (stroke 2, round cap/join). */
object HyIcons {
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

    private fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"

    /** parts: path data → filled? */
    private fun icon(name: String, vararg parts: Pair<String, Boolean>): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            parts.forEach { (d, filled) ->
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    fill = if (filled) SolidColor(Color.Black) else null,
                    stroke = if (filled) null else SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Gear = icon(
        "gear", circle(12f, 12f, 3f) to false,
        "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z" to false,
    )
    val Mic = icon("mic", rrect(9f, 2f, 6f, 12f, 3f) to false, "M19 10v1a7 7 0 0 1-14 0v-1M12 18v4" to false)
    val Speaker = icon("spk", "M11 5L6 9H2v6h4l5 4z" to false, "M15.5 8.5a5 5 0 0 1 0 7M19 5a10 10 0 0 1 0 14" to false)
    val Lock = icon("lock", rrect(5f, 11f, 14f, 10f, 2f) to false, "M8 11V7a4 4 0 0 1 8 0v4" to false)
    val Check = icon("check", "M20 6L9 17l-5-5" to false)
    val Play = icon("play", "M7 4l13 8-13 8z" to true)
    val ChevL = icon("chevL", "M15 18l-6-6 6-6" to false)
    val ChevR = icon("chevR", "M9 18l6-6-6-6" to false)
    val ChevD = icon("chevD", "M6 9l6 6 6-6" to false)
    val TurnL = icon("turnL", "M9 5L4 10l5 5" to false, "M4 10h9a5 5 0 0 1 5 5v6" to false)
    val TurnR = icon("turnR", "M15 5l5 5-5 5" to false, "M20 10h-9a5 5 0 0 0-5 5v6" to false)
    val Merge = icon("merge", "M8 21v-6l8-8V3" to false, "M12 3h4v4" to false, "M8 3v4" to false)
    val Straight = icon("straight", "M12 21V4M6 10l6-6 6 6" to false)
    val Flag = icon("flag", "M5 21V4h11l-2 4 2 4H5" to false)
    val Rain = icon("rain", "M7 15a5 5 0 1 1 1-9.9A6 6 0 0 1 20 8a4 4 0 0 1-1 7" to false, "M8 18l-1 3M12 18l-1 3M16 18l-1 3" to false)
    val Moon = icon("moon", "M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" to false)
    val Sun = icon(
        "sun", circle(12f, 12f, 4f) to false,
        "M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" to false,
    )
    val User = icon("user", "M20 21a8 8 0 0 0-16 0" to false, circle(12f, 8f, 5f) to false)
    val Dot = icon("dot", circle(12f, 12f, 5f) to true)
    val Shield = icon("shield", "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z" to false, "M9 12l2 2 4-4" to false)
    val Clock = icon("clock", circle(12f, 12f, 9f) to false, "M12 7v5l3 2" to false)
    val Star = icon("star", "M12 2l2.6 7.4L22 12l-7.4 2.6L12 22l-2.6-7.4L2 12l7.4-2.6z" to true)
}
