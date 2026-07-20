const pending = new Map();
const devicesNode = document.querySelector('#transferDevices');
const toastNode = document.querySelector('#toast');
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

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[character]);
}

function toast(text) {
  toastNode.textContent = text;
  toastNode.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toastNode.classList.remove('show'), 2600);
}

async function loadPairedDevices() {
  try {
    const result = await call('lan.devices');
    const devices = result.devices || [];
    if (!devices.length) {
      devicesNode.innerHTML = '<p class="empty">暂无已配对设备，请先在插件管理页完成配对。</p>';
      return;
    }
    devicesNode.innerHTML = devices.map(device => `
      <button class="transfer-device" data-transfer="${escapeHtml(device.id)}">
        <span class="device-icon">♫</span>
        <span class="device-copy"><strong>${escapeHtml(device.name)}</strong><small>已安全配对</small></span>
        <span class="device-arrow">›</span>
      </button>
    `).join('');
    document.querySelectorAll('[data-transfer]').forEach(button => {
      button.onclick = async () => {
        if (button.disabled) return;
        document.querySelectorAll('[data-transfer]').forEach(item => { item.disabled = true; });
        button.classList.add('transferring');
        button.querySelector('small').textContent = '正在流转歌曲…';
        try {
          await call('lan.transferPlayback', { deviceId: button.dataset.transfer });
          button.classList.remove('transferring');
          button.classList.add('success');
          button.querySelector('small').textContent = '流转成功';
          toast('音乐已流转');
        } catch (error) {
          button.classList.remove('transferring');
          button.querySelector('small').textContent = error.message || '流转失败';
          document.querySelectorAll('[data-transfer]').forEach(item => { item.disabled = false; });
          toast(error.message || '流转失败');
        }
      };
    });
  } catch (error) {
    devicesNode.innerHTML = `<p class="empty">${escapeHtml(error.message || '读取设备失败')}</p>`;
  }
}

loadPairedDevices();
