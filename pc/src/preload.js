const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('cc', {
  win: cmd => ipcRenderer.send('win', cmd),
  onWinState: cb => ipcRenderer.on('win-state', (_e, max) => cb(max)),
  getDisplays: () => ipcRenderer.invoke('get-displays'),
  setSource: id => ipcRenderer.send('set-source', id),
  setCastAudio: v => ipcRenderer.send('set-cast-audio', v),
  getNetInfo: () => ipcRenderer.invoke('get-net-info'),
  discovery: on => ipcRenderer.send('discovery', on),
  onDeviceFound: cb => ipcRenderer.on('device-found', (_e, d) => cb(d)),
  controlEnabled: on => ipcRenderer.send('control-enabled', on),
  controlEvent: msg => ipcRenderer.send('control-event', msg)
});
