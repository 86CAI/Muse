const pending = new Map();
const $ = selector => document.querySelector(selector);
let discovering = false;
let toastTimer;

window.addEventListener('message', event => {
  let message;
  try { message = JSON.parse(event.data); } catch (_) { return; }
  const request = pending.get(message.id);
  if (!request) return;
  pending.delete(message.id);
  message.ok ? request.resolve(message.response) : request.reject(new Error(message.error || '请求失败'));
});

function call(type, payload = {}) {
  const id = crypto.randomUUID();
  return new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject });
    window.museHost.postMessage(JSON.stringify({ id, type, payload }));
  });
}
function toast(text) {
  const node = $('#toast'); node.textContent = text; node.classList.add('show');
  clearTimeout(toastTimer); toastTimer = setTimeout(() => node.classList.remove('show'), 2600);
}
function escapeHtml(value) { return String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }

async function refreshLocal() {
  const state = await call('lan.localState');
  $('#hosting').checked = state.hosting;
  $('#hostStatus').textContent = state.hosting ? `服务运行中 · 端口 ${state.port}` : '当前不接受远程控制';
  const validCode = state.pairingCode && state.pairingExpiresAt > Date.now();
  $('#pairingBox').classList.toggle('hidden', !validCode);
  if (validCode) {
    $('#pairingCode').textContent = state.pairingCode;
    $('#pairingExpiry').textContent = `约 ${Math.max(1, Math.ceil((state.pairingExpiresAt - Date.now()) / 60000))} 分钟后失效`;
  }
  $('#clients').innerHTML = state.clients.length ? '<p>已授权控制端</p>' + state.clients.map(client => `<div class="device"><div class="device-head"><div><strong>${escapeHtml(client.name)}</strong><small>${escapeHtml(client.id.slice(0, 12))}</small></div><button class="danger" data-revoke="${escapeHtml(client.id)}">撤销</button></div></div>`).join('') : '';
  document.querySelectorAll('[data-revoke]').forEach(button => button.onclick = async () => { await call('lan.revokeClient', { clientId: button.dataset.revoke }); toast('授权已撤销'); refreshLocal(); });
}

async function refreshDiscovered(start = false) {
  const activeCode = document.activeElement?.matches?.('[data-code]') ? document.activeElement : null;
  if (!start && activeCode) return;
  const result = await call(start ? 'lan.discover' : 'lan.discover');
  discovering = true; $('#discover').textContent = '停止发现';
  $('#discovered').innerHTML = result.devices.length ? result.devices.map(device => `<div class="device"><div class="device-head"><div><strong>${escapeHtml(device.name)}</strong><small>${device.paired ? '已配对' : '等待配对'}</small></div></div>${device.paired ? '' : `<div class="pair-row"><input inputmode="numeric" maxlength="6" placeholder="6 位配对码" data-code="${escapeHtml(device.id)}"><button data-pair="${escapeHtml(device.id)}">配对</button></div>`}</div>`).join('') : '<p class="empty">尚未发现设备，请稍候刷新</p>';
  document.querySelectorAll('[data-pair]').forEach(button => button.onclick = async () => { const input = document.querySelector(`[data-code="${CSS.escape(button.dataset.pair)}"]`); if (input.value.trim().length !== 6) { input.focus(); toast('请输入 6 位配对码'); return; } await call('lan.pair', { deviceId: button.dataset.pair, code: input.value.trim() }); toast('配对成功'); await refreshAll(); });
}

async function refreshPaired() {
  const result = await call('lan.devices');
  $('#paired').innerHTML = result.devices.length ? result.devices.map(device => `<div class="device"><div class="device-head"><div><strong>${escapeHtml(device.name)}</strong><small>已安全配对</small></div><button class="danger" data-forget="${escapeHtml(device.id)}">忘记</button></div><div class="now" data-now="${escapeHtml(device.id)}">点击刷新获取状态</div><div class="controls"><button class="control" data-command="previous" data-device="${escapeHtml(device.id)}">上一首</button><button class="control" data-command="play" data-device="${escapeHtml(device.id)}">播放</button><button class="control" data-command="pause" data-device="${escapeHtml(device.id)}">暂停</button><button class="control" data-command="next" data-device="${escapeHtml(device.id)}">下一首</button><button class="control" data-state="${escapeHtml(device.id)}">状态</button><button class="primary" data-transfer="${escapeHtml(device.id)}">流转当前音乐</button></div></div>`).join('') : '<p class="empty">暂无已配对设备</p>';
  document.querySelectorAll('[data-command]').forEach(button => button.onclick = async () => { await call('lan.command', { deviceId: button.dataset.device, command: button.dataset.command, payload: {} }); toast('命令已发送'); });
  document.querySelectorAll('[data-state]').forEach(button => button.onclick = () => refreshRemoteState(button.dataset.state));
  document.querySelectorAll('[data-transfer]').forEach(button => button.onclick = async () => {
    button.disabled = true;
    try { await call('lan.transferPlayback', { deviceId: button.dataset.transfer }); toast('音乐已流转'); }
    catch (error) { showError(error); }
    finally { button.disabled = false; }
  });
  document.querySelectorAll('[data-forget]').forEach(button => button.onclick = async () => { await call('lan.forgetDevice', { deviceId: button.dataset.forget }); toast('设备已忘记'); refreshPaired(); });
}
async function refreshRemoteState(deviceId) {
  const state = await call('lan.getState', { deviceId });
  const song = state.currentSong ? `${state.currentSong.title} · ${state.currentSong.artist}` : '当前没有歌曲';
  document.querySelector(`[data-now="${CSS.escape(deviceId)}"]`).textContent = `${state.isPlaying ? '播放中' : '已暂停'} · ${song}`;
}
async function refreshAll() { await Promise.all([refreshLocal(), refreshPaired()]); }

$('#hosting').onchange = async event => { try { await call('lan.setHosting', { enabled: event.target.checked }); setTimeout(() => refreshLocal().catch(showError), 350); } catch (error) { event.target.checked = !event.target.checked; showError(error); } };
$('#generateCode').onclick = async () => { try { if (!$('#hosting').checked) { await call('lan.setHosting', { enabled: true }); await new Promise(resolve => setTimeout(resolve, 500)); } await call('lan.generatePairingCode'); await refreshLocal(); } catch (error) { showError(error); } };
$('#discover').onclick = async () => { try { if (discovering) { await call('lan.stopDiscovery'); discovering = false; $('#discover').textContent = '开始发现'; } else await refreshDiscovered(true); } catch (error) { showError(error); } };
$('#refresh').onclick = () => refreshAll().catch(showError);
function showError(error) { toast(error.message || String(error)); }

refreshAll().catch(showError);
setInterval(() => { refreshLocal().catch(() => {}); if (discovering) refreshDiscovered().catch(() => {}); }, 4000);
