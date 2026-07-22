const { app, BrowserWindow, ipcMain, desktopCapturer, session } = require('electron');
const os = require('os');
const path = require('path');
const { spawn } = require('child_process');

// Fixes the classic all-black window on many Windows GPU/driver combos.
app.disableHardwareAcceleration();
app.commandLine.appendSwitch('disable-gpu-compositing');

let win = null;
let selectedSourceId = null;   // display chosen in the renderer
let castAudio = true;          // "Cast system audio" toggle
let helper = null;             // powershell input-injection helper
let bonjourInstance = null;
let browser = null;

function createWindow() {
  win = new BrowserWindow({
    width: 860,
    height: 600,
    minWidth: 780,
    minHeight: 540,
    frame: false,
    show: false,
    backgroundColor: '#0c0f12',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));
  win.once('ready-to-show', () => win.show());
  // Fallback: never leave a hidden/black window if ready-to-show is missed.
  setTimeout(() => { if (win && !win.isVisible()) win.show(); }, 1500);
  win.webContents.on('did-fail-load', (_e, code, desc) => {
    console.error('Renderer failed to load:', code, desc);
    if (win) win.show();
  });
  win.on('maximize', () => win.webContents.send('win-state', true));
  win.on('unmaximize', () => win.webContents.send('win-state', false));
}

app.whenReady().then(() => {
  // WASAPI system-audio loopback + per-monitor selection for getDisplayMedia
  session.defaultSession.setDisplayMediaRequestHandler(async (request, callback) => {
    const sources = await desktopCapturer.getSources({ types: ['screen'] });
    const source = sources.find(s => s.id === selectedSourceId) || sources[0];
    callback({ video: source, audio: castAudio ? 'loopback' : undefined });
  });
  createWindow();
});

app.on('window-all-closed', () => { stopHelper(); app.quit(); });

/* ---------- window chrome ---------- */
ipcMain.on('win', (_e, cmd) => {
  if (!win) return;
  if (cmd === 'min') win.minimize();
  else if (cmd === 'max') win.isMaximized() ? win.unmaximize() : win.maximize();
  else if (cmd === 'close') win.close();
});

/* ---------- displays + network info ---------- */
ipcMain.handle('get-displays', async () => {
  const sources = await desktopCapturer.getSources({ types: ['screen'], thumbnailSize: { width: 320, height: 180 } });
  return sources.map((s, i) => ({ id: s.id, name: s.name || `Display ${i + 1}`, thumb: s.thumbnail.toDataURL() }));
});
ipcMain.on('set-source', (_e, id) => { selectedSourceId = id; });
ipcMain.on('set-cast-audio', (_e, v) => { castAudio = !!v; });

ipcMain.handle('get-net-info', () => {
  const ifs = os.networkInterfaces();
  for (const [name, addrs] of Object.entries(ifs)) {
    for (const a of addrs || []) {
      if (a.family === 'IPv4' && !a.internal) {
        const wired = /^(eth|en|Ethernet)/i.test(name) && !/wi-?fi|wlan|wireless/i.test(name);
        return { ip: a.address, iface: name, wired };
      }
    }
  }
  return { ip: '0.0.0.0', iface: 'none', wired: false };
});

/* ---------- mDNS discovery (_cablecast._tcp) — Wi-Fi mode ---------- */
ipcMain.on('discovery', (_e, on) => {
  try {
    if (on) {
      if (!bonjourInstance) {
        const { Bonjour } = require('bonjour-service');
        bonjourInstance = new Bonjour();
      }
      if (browser) browser.stop();
      browser = bonjourInstance.find({ type: 'cablecast' }, svc => {
        const ip = (svc.addresses || []).find(a => /^\d+\.\d+\.\d+\.\d+$/.test(a));
        if (ip && win) win.webContents.send('device-found', { name: svc.name, ip, port: svc.port || 47800 });
      });
    } else if (browser) { browser.stop(); browser = null; }
  } catch (err) { /* discovery is best-effort */ }
});

/* ---------- remote-control injection (Windows, no native modules) ---------- */
function helperPath() {
  return app.isPackaged
    ? path.join(process.resourcesPath, 'control-helper.ps1')
    : path.join(__dirname, '..', 'resources', 'control-helper.ps1');
}
function startHelper() {
  if (helper || process.platform !== 'win32') return;
  helper = spawn('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', helperPath()], { windowsHide: true });
  helper.on('exit', () => { helper = null; });
}
function stopHelper() {
  if (helper) { try { helper.stdin.end(); helper.kill(); } catch (_) {} helper = null; }
}
ipcMain.on('control-enabled', (_e, on) => { on ? startHelper() : stopHelper(); });
ipcMain.on('control-event', (_e, msg) => {
  if (helper && helper.stdin.writable) helper.stdin.write(JSON.stringify(msg) + '\n');
});
