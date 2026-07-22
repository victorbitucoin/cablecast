package ke.co.sale.cablecast.ui

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Phone companion. Two modes: "Cast this phone" (MediaProjection -> WebRTC sender)
 * and "Remote for PC" (touchpad + keys over DataChannel). UI matches mock #4.
 */
class PhoneActivity : ComponentActivity() {

    private lateinit var projectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        setContent { PhoneScreen(onStartCast = { requestProjection() }) }
    }

    private fun requestProjection() {
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                startForegroundService(Intent(this, ke.co.sale.cablecast.webrtc.ProjectionService::class.java))
                // RtcSender(...).start(result.data!!, projection) wired to the connected TV IP here.
            }
        }
}

@Composable
private fun PhoneScreen(onStartCast: () -> Unit) {
    var mode by remember { mutableStateOf("cast") } // cast | remote
    Column(
        Modifier.fillMaxSize().background(CC.inset).padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Connection card
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CC.panel)
                .border(1.dp, CC.b1, RoundedCornerShape(12.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Connected to", color = CC.textSecondary, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(CC.green))
                    Text("LIVE", color = CC.green, fontSize = 12.sp, fontFamily = CC.mono)
                }
            }
            Text("CableCast TV", color = CC.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("192.168.1.42 · Wi-Fi · 1080p60", color = CC.muted, fontSize = 12.sp, fontFamily = CC.mono)
        }

        // Action grid 2x1
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionCard("📱 Cast this phone", active = mode == "cast", Modifier.weight(1f)) {
                mode = "cast"; onStartCast()
            }
            ActionCard("🖥 Remote for PC", active = mode == "remote", Modifier.weight(1f)) { mode = "remote" }
        }

        // Touchpad
        Box(
            Modifier.fillMaxWidth().weight(1f).heightIn(min = 220.dp)
                .clip(RoundedCornerShape(12.dp)).background(CC.inset2)
                .border(1.dp, CC.b2, RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectDragGestures { _, drag ->
                        // Send {type:'mouse', dx, dy} over DataChannel to the PC.
                        val dx = drag.x.toInt(); val dy = drag.y.toInt()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text("touchpad — drag to move the PC cursor", color = CC.muted2, fontSize = 12.sp, fontFamily = CC.mono)
        }

        // Bottom row: keyboard, vol-, vol+, End
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BottomBtn("⌨", Modifier.weight(1f))
            BottomBtn("🔉", Modifier.weight(1f))
            BottomBtn("🔊", Modifier.weight(1f))
            BottomBtn("End", Modifier.weight(1f), danger = true)
        }
    }
}

@Composable
private fun ActionCard(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(84.dp).clip(RoundedCornerShape(12.dp))
            .background(if (active) CC.greenTint else CC.card)
            .border(1.dp, if (active) CC.green else CC.b1, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) { Text(label, color = if (active) CC.green else CC.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun BottomBtn(label: String, modifier: Modifier, danger: Boolean = false) {
    Box(
        modifier.height(52.dp).clip(RoundedCornerShape(10.dp))
            .background(if (danger) CC.dangerBg else CC.card)
            .border(1.dp, if (danger) CC.dangerBorder else CC.b1, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) { Text(label, color = if (danger) CC.dangerText else CC.textPrimary, fontSize = 16.sp,
        fontWeight = if (danger) FontWeight.SemiBold else FontWeight.Normal) }
}
