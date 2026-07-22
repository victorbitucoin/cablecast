package ke.co.sale.cablecast.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** CableCast design tokens — mirror of the handoff Design Tokens section. */
object CC {
    val bgPage = Color(0xFF0C0F12)
    val tvBg = Color(0xFF05080B)
    val panel = Color(0xFF12171C)
    val inset = Color(0xFF0C1014)
    val inset2 = Color(0xFF0A0E12)
    val card = Color(0xFF151B21)

    val b1 = Color(0xFF232C33)
    val b2 = Color(0xFF2A343C)
    val b3 = Color(0xFF1C242B)

    val textPrimary = Color(0xFFE8F3EE)
    val textPrimary2 = Color(0xFFF2F6F4)
    val textSecondary = Color(0xFF8B9A93)
    val muted = Color(0xFF5A6A63)
    val muted2 = Color(0xFF4A5952)
    val label = Color(0xFF6B7A74)

    val green = Color(0xFF39E58C)
    val greenTint = Color(0x1F39E58C)   // rgba(57,229,140,.12)
    val onAccent = Color(0xFF08110C)

    val dangerText = Color(0xFFE08A82)
    val dangerBg = Color(0xFF241416)
    val dangerBorder = Color(0xFF52302E)

    // Mono font falls back to system monospace; swap for bundled JetBrains Mono if desired.
    val mono = FontFamily.Monospace
}
