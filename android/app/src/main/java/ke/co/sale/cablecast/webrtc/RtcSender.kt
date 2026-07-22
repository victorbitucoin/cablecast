package ke.co.sale.cablecast.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import org.json.JSONObject
import org.webrtc.*

/**
 * Phone "Cast this phone" mode: captures the screen with MediaProjection and
 * streams it to the TV receiver over WebRTC. Connects out to ws://<tv-ip>:47800.
 */
class RtcSender(
    private val context: Context,
    private val eglBase: EglBase,
    private val quality: String,
    private val onLocalIce: (String, String, Int) -> Unit,
    private val onOfferReady: (String) -> Unit,
    private val onConnected: () -> Unit
) {
    private lateinit var factory: PeerConnectionFactory
    private var pc: PeerConnection? = null
    private var capturer: VideoCapturer? = null
    private var source: VideoSource? = null

    fun start(projectionData: Intent, projection: MediaProjection?) {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()

        val helper = SurfaceTextureHelper.create("cap", eglBase.eglBaseContext)
        capturer = ScreenCapturerAndroid(projectionData, object : MediaProjection.Callback() {})
        source = factory.createVideoSource(false)
        capturer!!.initialize(helper, context, source!!.capturerObserver)

        val (w, h, fps) = when (quality) {
            "720p" -> Triple(1280, 720, 60)
            "4K30" -> Triple(3840, 2160, 30)
            else -> Triple(1920, 1080, 60)
        }
        capturer!!.startCapture(w, h, fps)

        val track = factory.createVideoTrack("v0", source)
        pc = factory.createPeerConnection(
            PeerConnection.RTCConfiguration(emptyList()).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            },
            object : PeerConnection.Observer {
                override fun onIceCandidate(c: IceCandidate) = onLocalIce(c.sdp, c.sdpMid, c.sdpMLineIndex)
                override fun onConnectionChange(s: PeerConnection.PeerConnectionState) {
                    if (s == PeerConnection.PeerConnectionState.CONNECTED) onConnected()
                }
                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
                override fun onIceConnectionReceivingChange(p0: Boolean) {}
                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
                override fun onTrack(p0: RtpTransceiver?) {}
                override fun onAddStream(p0: MediaStream?) {}
                override fun onRemoveStream(p0: MediaStream?) {}
                override fun onDataChannel(p0: DataChannel?) {}
                override fun onRenegotiationNeeded() {}
                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            })
        val sender = pc!!.addTrack(track)
        applyBitrate(sender)

        pc!!.createOffer(object : SimpleSdp() {
            override fun onCreateSuccess(desc: SessionDescription) {
                pc!!.setLocalDescription(SimpleSdp(), desc)
                onOfferReady(desc.description)
            }
        }, MediaConstraints())
    }

    private fun applyBitrate(sender: RtpSender) {
        val cap = when (quality) { "720p" -> 8_000_000; "4K30" -> 35_000_000; else -> 18_000_000 }
        val p = sender.parameters
        if (p.encodings.isNotEmpty()) { p.encodings[0].maxBitrateBps = cap }
        sender.parameters = p
    }

    fun setAnswer(sdp: String) =
        pc?.setRemoteDescription(SimpleSdp(), SessionDescription(SessionDescription.Type.ANSWER, sdp))

    fun addRemoteIce(json: String) {
        val o = JSONObject(json)
        pc?.addIceCandidate(IceCandidate(o.optString("sdpMid"), o.optInt("sdpMLineIndex"), o.getString("candidate")))
    }

    fun stop() {
        runCatching { capturer?.stopCapture() }
        capturer?.dispose(); source?.dispose(); pc?.close()
        capturer = null; source = null; pc = null
    }
}
