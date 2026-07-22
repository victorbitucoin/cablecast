package ke.co.sale.cablecast.signaling

import fi.iki.elonen.NanoWSD
import org.json.JSONObject
import java.io.IOException

/**
 * Embedded signaling server run by the TV receiver.
 * Sender (PC/phone) connects to ws://<tv-ip>:47800, pairs with the 4-digit
 * code shown on screen, then exchanges SDP offer/answer + ICE candidates.
 */
class SignalingServer(
    port: Int = 47800,
    private val pairingCode: String,
    private val callbacks: Callbacks
) : NanoWSD(port) {

    interface Callbacks {
        fun onSenderHello(name: String)
        fun onPaired()
        fun onOffer(sdp: String)
        fun onRemoteIce(json: String)
        fun onSenderGone()
    }

    @Volatile private var socket: CcSocket? = null

    override fun openWebSocket(handshake: IHTTPSession): WebSocket {
        val s = CcSocket(handshake)
        socket = s
        return s
    }

    fun sendAnswer(sdp: String) = send(JSONObject().put("t", "answer")
        .put("sdp", JSONObject().put("type", "answer").put("sdp", sdp)))

    fun sendIce(candidate: String, sdpMid: String, sdpMLineIndex: Int) = send(
        JSONObject().put("t", "ice").put("candidate", JSONObject()
            .put("candidate", candidate).put("sdpMid", sdpMid).put("sdpMLineIndex", sdpMLineIndex)))

    private fun send(o: JSONObject) { try { socket?.send(o.toString()) } catch (_: IOException) {} }

    inner class CcSocket(h: IHTTPSession) : WebSocket(h) {
        private var paired = false

        override fun onOpen() {}
        override fun onClose(code: WebSocketFrame.CloseCode?, reason: String?, remote: Boolean) {
            if (socket === this) { socket = null; callbacks.onSenderGone() }
        }
        override fun onPong(pong: WebSocketFrame?) {}
        override fun onException(e: IOException?) {}

        override fun onMessage(frame: WebSocketFrame) {
            val msg = try { JSONObject(frame.textPayload) } catch (_: Exception) { return }
            when (msg.optString("t")) {
                "hello" -> {
                    callbacks.onSenderHello(msg.optString("name", "Sender"))
                    send(JSONObject().put("t", "need-pair").toString())
                }
                "pair" -> {
                    if (msg.optString("code") == pairingCode) {
                        paired = true
                        send(JSONObject().put("t", "paired").toString())
                        callbacks.onPaired()
                    } else {
                        send(JSONObject().put("t", "pair-bad").toString())
                    }
                }
                "offer" -> if (paired) callbacks.onOffer(msg.getJSONObject("sdp").getString("sdp"))
                "ice" -> if (paired && msg.has("candidate")) callbacks.onRemoteIce(msg.getJSONObject("candidate").toString())
            }
        }
    }
}
