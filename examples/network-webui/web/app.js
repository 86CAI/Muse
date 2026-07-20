const result = document.querySelector('#result');
const pending = new Map();

window.addEventListener('message', event => {
  const message = JSON.parse(event.data);
  const resolve = pending.get(message.id);
  if (!resolve) return;
  pending.delete(message.id);
  resolve(message);
});

function hostRequest(payload) {
  return hostCall('network.request', payload);
}

function hostCall(type, payload = {}) {
  const id = crypto.randomUUID();
  return new Promise(resolve => {
    pending.set(id, resolve);
    window.museHost.postMessage(JSON.stringify({ id, type, payload }));
  });
}

async function call(type, payload = {}) {
  const message = await hostCall(type, payload);
  if (!message.ok) throw new Error(message.error);
  return message.response;
}

document.querySelectorAll('[data-call]').forEach(button => button.addEventListener('click', async () => {
  try { await call(button.dataset.call); result.textContent = `已执行 ${button.textContent}`; }
  catch (error) { result.textContent = error.message; }
}));

document.querySelector('#state').addEventListener('click', async () => {
  try {
    const state = await call('player.getState');
    result.textContent = state.currentSong ? `${state.currentSong.title}\n${state.currentSong.artist}\n${state.isPlaying ? '播放中' : '已暂停'}` : '当前没有歌曲';
  } catch (error) { result.textContent = error.message; }
});

document.querySelector('#applyTheme').addEventListener('click', async () => {
  try {
    await call('theme.apply', { accent: document.querySelector('#accent').value, isLight: document.querySelector('#light').checked });
    result.textContent = 'Muse 主题已更新';
  } catch (error) { result.textContent = error.message; }
});

document.querySelector('#resetTheme').addEventListener('click', async () => {
  try { await call('theme.reset'); result.textContent = '已重置强调色'; }
  catch (error) { result.textContent = error.message; }
});

document.querySelector('#saveConfig').addEventListener('click', async () => {
  try {
    await call('config.set', { config: { compact: document.querySelector('#compact').checked } });
    result.textContent = '插件配置已保存';
  } catch (error) { result.textContent = error.message; }
});

call('config.get').then(response => { document.querySelector('#compact').checked = !!response.config.compact; }).catch(() => {});
call('theme.get').then(theme => { document.querySelector('#light').checked = theme.isLight; }).catch(() => {});

document.querySelector('#load').addEventListener('click', async () => {
  result.textContent = '加载中…';
  const message = await hostRequest({
    method: 'GET',
    url: 'https://api.github.com/repos/square/okhttp',
    headers: { Accept: 'application/vnd.github+json' }
  });
  if (!message.ok) {
    result.textContent = message.error;
    return;
  }
  const data = JSON.parse(message.response.body);
  result.textContent = `${data.full_name}\n★ ${data.stargazers_count}\n${data.description}`;
});
