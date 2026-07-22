package ke.co.sale.cablecast.ui

import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ke.co.sale.cablecast.signaling.Discovery
import ke.co.sale.cablecast.signaling.SignalingServer
import ke.co.sale.cablecast.webrtc.RtcReceiver
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import java.net.Inet4Address
import java.net.NetworkInterface

class TvActivity : ComponentActivity() {

    private val eglBase = EglBase.create()
    private lateinit var server: SignalingServer
    private lateinit var discovery: Discovery
    private var receiver: RtcReceiver? = null

    private val pairingCode = (1000..9999).random().toString()
    private val ip by lazy { localIp() }

    // UI state
    private val phase = mutableStateOf("idle")           // idle | pairing | streaming | reconnecting
    private val peerName = mutableStateOf("")
    private val statsLine = mutableStateOf("")
    private lateinit var renderer: SurfaceViewRenderer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        renderer = SurfaceViewRenderer(this).apply {
            init(eglBase.eglBaseContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        startServer()
        discovery = Discovery(this).also { it.advertise("CableCast TV", 47800) }

        setContent {
            when (phase.value) {
                "streaming" -> StreamingOverlay(renderer, peerName.value, statsLine.value)
                else -> IdleScreen(ip, pairingCode)
            }
        }
    }

    private fun startServer() {
        server = SignalingServer(47800, pairingCode, object : SignalingServer.Callbacks {
            override fun onSenderHello(name: String) { peerName.value = name; phase.value = "pairing" }
            override fun onPaired() {
                receiver = RtcReceiver(
                    this@TvActivity, eglBase, renderer,
                    onLocalIce = { c, mid, idx -> server.sendIce(c, mid, idx) },
                    onConnected = { runOnUiThread { phase.value = "streaming" } },
                    onStats = { br, rtt, w, h ->
                        runOnUiThread { statsLine.value = "${peerName.value} · ${w}×${h} · ${rtt}ms · ${br}Mbps" }
                    }
                )
            }
            override fun onOffer(sdp: String) =
                receiver?.handleOffer(sdp) { answer -> server.sendAnswer(answer) } ?: Unit
            override fun onRemoteIce(json: String) { receiver?.addRemoteIce(json) }
            override fun onSenderGone() {
                runOnUiThread { phase.value = "idle"; statsLine.value = ""; receiver?.close(); receiver = null }
            }
        })
        server.start(0, false)
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { server.stop() }
        discovery.stop(); receiver?.close(); eglBase.release()
    }

    private fun localIp(): String {
        NetworkInterface.getNetworkInterfaces().toList().forEach { nif ->
            nif.inetAddresses.toList().forEach { addr ->
                if (!addr.isLoopbackAddress && addr is Inet4Address) return addr.hostAddress ?: "0.0.0.0"
            }
        }
        return "0.0.0.0"
    }
}

/* ---------------- Composables (match mocks) ---------------- */

@androidx.compose.runtime.Composable
private fun IdleScreen(ip: String, code: String) {
    val infinite = rememberInfiniteTransition(label = "pulse")
    Box(
        Modifier.fillMaxSize().background(CC.tvBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LogoMark(34)
                Text("CableCast TV", color = CC.textPrimary2, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("Ready to receive · connect from your PC or phone",
                color = CC.textSecondary, fontSize = 18.sp)
            Text(ip, color = CC.green, fontSize = 44.sp, fontWeight = FontWeight.Bold,
                fontFamily = CC.mono, letterSpacing = 2.sp)
            Text("pairing code ${code.toCharArray().joinToString(" ")} · port 47800 · ⏚ Ethernet",
                color = CC.muted, fontSize = 15.sp, fontFamily = CC.mono)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvButton("Wait for cast", primary = true)
                TvButton("Settings", primary = false)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun StreamingOverlay(renderer: SurfaceViewRenderer, peer: String, stats: String) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { renderer }, modifier = Modifier.fillMaxSize())

        // top-right status pill
        Row(
            Modifier.align(Alignment.TopEnd).padding(20.dp)
                .clip(RoundedCornerShape(8.dp)).background(Color(0xCC05080B))
                .border(1.dp, CC.b1, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(CC.green))
            Text(if (stats.isNotEmpty()) stats else peer, color = CC.green, fontSize = 13.sp, fontFamily = CC.mono)
        }

        // bottom-center control bar
        Row(
            Modifier.align(Alignment.BottomCenter).padding(24.dp)
                .clip(RoundedCornerShape(10.dp)).background(Color(0xD905080B))
                .border(1.dp, CC.b1, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BarButton("⏸ Pause", primary = true)
            BarButton("🔊 Audio", primary = false)
            BarButton("⌨ Control PC", primary = false)
            BarButton("Disconnect", danger = true)
        }
    }
}

@androidx.compose.runtime.Composable
private fun LogoMark(size: Int) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size / 4).dp))
            .background(CC.green),
        contentAlignment = Alignment.Center
    ) { Text("C", color = CC.onAccent, fontWeight = FontWeight.Bold, fontSize = (size / 2).sp) }
}

@androidx.compose.runtime.Composable
private fun TvButton(label: String, primary: Boolean) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (primary) CC.greenTint else Color.Transparent)
            .border(2.dp, if (primary) CC.green else CC.b2, RoundedCornerShape(8.dp))
            .padding(horizontal = 22.dp, vertical = 12.dp)
    ) { Text(label, color = if (primary) CC.green else CC.textSecondary, fontSize = 17.sp) }
}

@androidx.compose.runtime.Composable
private fun BarButton(label: String, primary: Boolean = false, danger: Boolean = false) {
    val bg = if (primary) CC.green else CC.inset
    val fg = when { primary -> CC.onAccent; danger -> CC.dangerText; else -> Color(0xFFC9D6D0) }
    val border = if (danger) CC.dangerBorder else CC.b2
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(if (primary) bg else Color.Transparent)
            .border(1.dp, if (primary) CC.green else border, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) { Text(label, color = fg, fontSize = 15.sp) }
}
