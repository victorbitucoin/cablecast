// CastSession is provided globally by signaling.js (classic script, no ES modules
// because Chromium blocks module scripts over file://).

const state = {
  mode: 'ethernet',
  quality: '1080p60',
  castAudio: true,
  allowControl: false,
  streaming: false,
  sourceId: null,
  displayName: 'Display 1'
};

let session = null;
let pendingStream = null;

const $ = id => document.getElementById(id);

/* ---------- net info + displays ---------- */
(async () => {
  try {
    const net = await cc.getNetInfo();
    $('helper').textContent = `This PC: ${net.ip} · ${net.wired ? 'wired' : 'Wi-Fi'} (${net.iface})`;
  } catch (e) {
    $('helper').textContent = 'This PC: network info unavailable';
  }

  try {
    const displays = await cc.getDisplays();
    if (displays.length) {
    state.sourceId = displays[0].id;
    state.displayName = displays[0].name;
    cc.setSource(state.sourceId);
    $('corner').textContent = `${displays[0].name.toUpperCase()} · —`;
    const box = $('displays');
    box.innerHTML = '';
    displays.forEach((d, i) => {
      const el = document.createElement('div');
      el.className = 'chip' + (i === 0 ? ' on' : '');
      el.textContent = d.name.length > 12 ? `Disp ${i + 1}` : d.name;
      el.title = d.name;
      el.onclick = () => {
        [...box.children].forEach(c => c.classList.remove('on'));
        el.classList.add('on');
        state.sourceId = d.id; state.displayName = d.name;
        cc.setSource(d.id);
        $('corner').textContent = `${d.name.toUpperCase()} · —`;
      };
      box.appendChild(el);
    });
    }
  } catch (e) { /* display enumeration unavailable */ }
})();

cc.onWinState(() => {});
cc.onDeviceFound(d => addDevice(d));

/* ---------- mode toggle ---------- */
window.setMode = m => {
  state.mode = m;
  $('m-eth').classList.toggle('on', m === 'ethernet');
  $('m-wifi').classList.toggle('on', m === 'wifi');
  const empty = $('dev-empty');
  if (m === 'wifi') {
    cc.discovery(true);
    if (empty) empty.textContent = 'Scanning for CableCast TVs…';
  } else {
    cc.discovery(false);
    clearDevices();
  }
};

function clearDevices() {
  $('devices').innerHTML = '<div class="empty" id="dev-empty">Switch to Wi-Fi to auto-discover, or type an IP.</div>';
}
const seen = new Set();
function addDevice(d) {
  if (seen.has(d.ip)) return;
  seen.add(d.ip);
  const empty = $('dev-empty'); if (empty) empty.remove();
  const el = document.createElement('div');
  el.className = 'dev';
  el.innerHTML = `<div><div class="name">${d.name}</div><div class="ip mono">${d.ip} · Wi-Fi</div></div><div class="dot"></div>`;
  el.onclick = () => { $('ip').value = d.ip; };
  $('devices').appendChild(el);
}

/* ---------- quality / toggles ---------- */
window.setQuality = q => {
  state.quality = q;
  document.querySelectorAll('[data-q]').forEach(c => c.classList.toggle('on', c.dataset.q === q));
};
window.toggleAudio = () => {
  state.castAudio = !state.castAudio;
  $('t-audio').classList.toggle('on', state.castAudio);
  cc.setCastAudio(state.castAudio);
};
window.toggleControl = () => {
  state.allowControl = !state.allowControl;
  $('t-ctrl').classList.toggle('on', state.allowControl);
  cc.controlEnabled(state.allowControl);
};

/* ---------- casting ---------- */
window.toggleCast = async () => {
  if (state.streaming) return stopCast();
  const ip = $('ip').value.trim();
  if (!/^\d{1,3}(\.\d{1,3}){3}$/.test(ip)) return showErr('Enter a valid IPv4 address.');
  hideErr();

  let stream;
  try {
    stream = await navigator.mediaDevices.getDisplayMedia({
      video: { frameRate: state.quality === '4K30' ? 30 : 60 },
      audio: state.castAudio
    });
  } catch (e) { return showErr('Screen capture was cancelled.'); }
  pendingStream = stream;

  // local preview
  const v = $('vid'); v.srcObject = stream; v.style.display = 'block';

  session = new CastSession({
    onState: onSessionState,
    onStats: onStats,
    onPairingNeeded: bad => openPairing(bad),
    onError: msg => { showErr(msg); },
    onControl: msg => { if (state.allowControl) cc.controlEvent(msg); }
  });
  session.start(ip, stream, { quality: state.quality });
  setCastBtn(true);
  $('status').textContent = 'connecting…';
};

function stopCast() {
  if (session) session.stop();
  session = null;
  state.streaming = false;
  setCastBtn(false);
  $('vid').style.display = 'none';
  $('pv-idle').style.display = 'block';
  $('pv-live').style.display = 'none';
  $('status').textContent = 'idle · ready';
}

function onSessionState(s) {
  if (s === 'streaming') {
    state.streaming = true;
    $('pv-idle').style.display = 'none';
    $('pv-live').style.display = 'flex';
    $('pv-live-t').textContent = `LIVE — casting ${state.displayName}`;
    $('status').textContent = 'streaming · connected';
  } else if (s === 'connecting') {
    $('status').textContent = 'connecting…';
  } else if (s === 'reconnecting') {
    $('status').textContent = 'reconnecting…';
    $('pv-live-t').textContent = 'reconnecting…';
  } else if (s === 'idle') {
    $('status').textContent = 'idle · ready';
  }
}

function onStats(st) {
  const res = st.w ? `${st.w}×${st.h}` : state.quality;
  $('corner').textContent = `${state.displayName.toUpperCase()} · ${res}`;
  $('pv-stats').textContent = `${state.quality} · ${st.bitrate || '—'} Mbps · ${st.rtt || '—'} ms · ${st.codec}`;
}

/* ---------- pairing ---------- */
window.submitPair = () => {
  const code = $('code').value.trim();
  if (code.length !== 4) { shake(); return; }
  session && session.submitPairing(code);
  closePairing();
};
window.cancelPair = () => { closePairing(); stopCast(); };
function openPairing(bad) {
  $('modal').classList.add('show');
  $('code').value = ''; $('code').focus();
  if (bad) shake();
}
function closePairing() { $('modal').classList.remove('show'); }
function shake() { const c = $('code'); c.classList.add('shake'); setTimeout(() => c.classList.remove('shake'), 400); }

/* ---------- ui helpers ---------- */
function setCastBtn(on) {
  const b = $('cast');
  b.classList.toggle('on', on);
  b.textContent = on ? '■ Stop casting' : '▶ Start casting';
}
function showErr(m) { const e = $('ip-err'); e.textContent = m; e.style.display = 'block'; }
function hideErr() { $('ip-err').style.display = 'none'; }

$('code').addEventListener('keydown', e => { if (e.key === 'Enter') submitPair(); });
