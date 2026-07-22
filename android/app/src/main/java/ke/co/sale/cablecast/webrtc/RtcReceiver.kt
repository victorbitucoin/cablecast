package ke.co.sale.cablecast.webrtc

import android.content.Context
import org.json.JSONObject
import org.webrtc.*

/**
 * Receives the incoming desktop/phone stream on the TV.
 * Pure LAN: no STUN/TURN. Renders video into the provided SurfaceViewRenderer.
 */
class RtcReceiver(
    context: Context,
    private val eglBase: EglBase,
    private val sink: VideoSink,
    private val onLocalIce: (candidate: String, mid: String, idx: Int) -> Unit,
    private val onConnected: () -> Unit,
    private val onStats: (bitrateMbps: Double, rttMs: Int, w: Int, h: Int) -> Unit
) {
    private val factory: PeerConnectionFactory
    private var pc: PeerConnection? = null

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
        )
        val encoder = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoder = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoder)
            .setVideoDecoderFactory(decoder)
            .createPeerConnectionFactory()
    }

    fun handleOffer(sdp: String, sendAnswer: (String) -> Unit) {
        val cfg = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        pc = factory.createPeerConnection(cfg, object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate) = onLocalIce(c.sdp, c.sdpMid, c.sdpMLineIndex)
            override fun onTrack(transceiver: RtpTransceiver) {
                val track = transceiver.receiver.track()
                if (track is VideoTrack) track.addSink(sink)
            }
            override fun onConnectionChange(s: PeerConnection.PeerConnectionState) {
                if (s == PeerConnection.PeerConnectionState.CONNECTED) { onConnected(); pollStats() }
            }
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
        })

        val offer = SessionDescription(SessionDescription.Type.OFFER, sdp)
        pc?.setRemoteDescription(SimpleSdp(), offer)
        pc?.createAnswer(object : SimpleSdp() {
            override fun onCreateSuccess(desc: SessionDescription) {
                pc?.setLocalDescription(SimpleSdp(), desc)
                sendAnswer(desc.description)
            }
        }, MediaConstraints())
    }

    fun addRemoteIce(json: String) {
        val o = JSONObject(json)
        pc?.addIceCandidate(IceCandidate(o.optString("sdpMid"), o.optInt("sdpMLineIndex"), o.getString("candidate")))
    }

    private var lastBytes = 0L
    private var lastTs = 0L
    private fun pollStats() {
        pc?.getStats { report ->
            var bitrate = 0.0; var rtt = 0; var w = 0; var h = 0
            for (s in report.statsMap.values) {
                if (s.type == "inbound-rtp" && s.members["kind"] == "video") {
                    val bytes = (s.members["bytesReceived"] as? Number)?.toLong() ?: 0L
                    val now = s.timestampUs
                    if (lastTs != 0L) bitrate = (bytes - lastBytes) * 8.0 / ((now - lastTs) / 1_000_000.0) / 1e6
                    lastBytes = bytes; lastTs = now
                    w = (s.members["frameWidth"] as? Number)?.toInt() ?: w
                    h = (s.members["frameHeight"] as? Number)?.toInt() ?: h
                }
                if (s.type == "candidate-pair" && s.members["nominated"] == true) {
                    rtt = ((s.members["currentRoundTripTime"] as? Number)?.toDouble()?.times(1000))?.toInt() ?: rtt
                }
            }
            onStats(String.format("%.1f", bitrate).toDouble(), rtt, w, h)
        }
    }

    fun close() { pc?.close(); pc = null }
}

/** No-op SdpObserver base to cut boilerplate. */
open class SimpleSdp : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}
