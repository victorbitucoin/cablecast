// CableCast signaling + WebRTC (browser context, runs in renderer).
// Connects to the TV receiver's WebSocket at ws://<ip>:47800, does the
// 4-digit pairing handshake, then negotiates a WebRTC sender connection.

const PORT = 47800;

class CastSession {
  constructor({ onState, onStats, onPairingNeeded, onError, onControl }) {
    this.onState = onState || (() => {});
    this.onStats = onStats || (() => {});
    this.onPairingNeeded = onPairingNeeded || (() => {});
    this.onError = onError || (() => {});
    this.onControl = onControl || (() => {});
    this.ws = null;
    this.pc = null;
    this.stream = null;
    this.ctrlChannel = null;
    this.statsTimer = null;
    this.backoff = 500;
    this.wantConnected = false;
    this.ip = null;
  }

  async start(ip, stream, { quality }) {
    this.wantConnected = true;
    this.ip = ip;
    this.stream = stream;
    this.quality = quality;
    this._connect();
  }

  _connect() {
    if (!this.wantConnected) return;
    this.onState('connecting');
    let ws;
    try { ws = new WebSocket(`ws://${this.ip}:${PORT}`); }
    catch (e) { return this._retry(); }
    this.ws = ws;

    ws.onopen = () => { this.backoff = 500; ws.send(JSON.stringify({ t: 'hello', role: 'sender', name: hostName() })); };
    ws.onerror = () => { this.onError('Could not reach ' + this.ip); };
    ws.onclose = () => { if (this.wantConnected) this._retry(); };
    ws.onmessage = ev => this._onSignal(JSON.parse(ev.data));
  }

  _retry() {
    this.onState('reconnecting');
    clearTimeout(this._t);
    this._t = setTimeout(() => this._connect(), this.backoff);
    this.backoff = Math.min(this.backoff * 2, 8000);
  }

  submitPairing(code) { this._send({ t: 'pair', code: String(code) }); }

  async _onSignal(msg) {
    switch (msg.t) {
      case 'need-pair': this.onPairingNeeded(); break;
      case 'pair-bad': this.onError('Pairing code did not match'); this.onPairingNeeded(true); break;
      case 'paired': await this._negotiate(); break;
      case 'answer': await this.pc.setRemoteDescription(msg.sdp); break;
      case 'ice': if (msg.candidate) { try { await this.pc.addIceCandidate(msg.candidate); } catch (_) {} } break;
    }
  }

  async _negotiate() {
    this.pc = new RTCPeerConnection({ iceServers: [] }); // LAN only, no STUN/TURN
    for (const track of this.stream.getTracks()) {
      const sender = this.pc.addTrack(track, this.stream);
      if (track.kind === 'video') this._applyEncoding(sender);
    }
    this.ctrlChannel = this.pc.createDataChannel('control');
    this.ctrlChannel.onmessage = e => { try { this.onControl(JSON.parse(e.data)); } catch (_) {} };

    this.pc.onicecandidate = e => { if (e.candidate) this._send({ t: 'ice', candidate: e.candidate }); };
    this.pc.onconnectionstatechange = () => {
      const s = this.pc.connectionState;
      if (s === 'connected') { this.onState('streaming'); this._startStats(); }
      else if (s === 'failed' || s === 'disconnected') { if (this.wantConnected) this._retry(); }
    };

    const offer = await this.pc.createOffer();
    await this.pc.setLocalDescription(offer);
    this._send({ t: 'offer', sdp: this.pc.localDescription });
  }

  _applyEncoding(sender) {
    const map = { '720p': 8e6, '1080p60': 18e6, '4K30': 35e6 };
    const p = sender.getParameters();
    if (!p.encodings || !p.encodings.length) p.encodings = [{}];
    p.encodings[0].maxBitrate = map[this.quality] || 18e6;
    p.encodings[0].maxFramerate = this.quality === '4K30' ? 30 : 60;
    try { sender.setParameters(p); } catch (_) {}
  }

  _startStats() {
    clearInterval(this.statsTimer);
    let lastBytes = 0, lastTs = 0;
    this.statsTimer = setInterval(async () => {
      if (!this.pc) return;
      const report = await this.pc.getStats();
      let fps = 0, w = 0, h = 0, rtt = 0, bitrate = 0, codec = 'H.264';
      report.forEach(r => {
        if (r.type === 'outbound-rtp' && r.kind === 'video') {
          fps = Math.round(r.framesPerSecond || 0);
          if (lastTs) bitrate = Math.round((r.bytesSent - lastBytes) * 8 / ((r.timestamp - lastTs) / 1000) / 1e6 * 10) / 10;
          lastBytes = r.bytesSent; lastTs = r.timestamp;
        }
        if (r.type === 'track' && r.frameWidth) { w = r.frameWidth; h = r.frameHeight; }
        if (r.type === 'candidate-pair' && r.nominated && r.currentRoundTripTime != null) rtt = Math.round(r.currentRoundTripTime * 1000);
      });
      this.onStats({ fps, w, h, rtt, bitrate, codec });
    }, 1000);
  }

  _send(o) { if (this.ws && this.ws.readyState === 1) this.ws.send(JSON.stringify(o)); }

  stop() {
    this.wantConnected = false;
    clearTimeout(this._t);
    clearInterval(this.statsTimer);
    try { this.ws && this.ws.close(); } catch (_) {}
    try { this.pc && this.pc.close(); } catch (_) {}
    if (this.stream) this.stream.getTracks().forEach(t => t.stop());
    this.pc = null; this.ws = null; this.stream = null;
    this.onState('idle');
  }
}

function hostName() {
  return (navigator.userAgentData && navigator.userAgentData.platform) ? 'DESKTOP-PC' : 'DESKTOP-PC';
}
