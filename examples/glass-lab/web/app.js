const defaults = { cornerRadius: 24, blurRadius: 12, refractionHeight: 12, refractionAmount: 24, chromaticAberration: 1 };
const fields = {
  cornerRadius: { name: 'Corner radius', note: '玻璃轮廓的圆角半径', min: 0, max: 80, step: 1, unit: 'dp' },
  blurRadius: { name: 'Blur radius', note: '背景采样的模糊范围', min: 0, max: 64, step: 1, unit: 'dp' },
  refractionHeight: { name: 'Refraction height', note: '透镜边缘的折射厚度', min: 0, max: 80, step: 1, unit: 'dp' },
  refractionAmount: { name: 'Refraction amount', note: '背景光线的位移强度', min: 0, max: 80, step: 1, unit: 'dp' },
  chromaticAberration: { name: 'Chromatic aberration', note: 'RGB 边缘色散开关强度', min: 0, max: 1, step: .01, unit: '' }
};
const presets = {
  soft: { cornerRadius: 32, blurRadius: 22, refractionHeight: 8, refractionAmount: 12, chromaticAberration: .18 },
  crystal: { cornerRadius: 24, blurRadius: 6, refractionHeight: 18, refractionAmount: 36, chromaticAberration: .68 },
  bold: { cornerRadius: 38, blurRadius: 14, refractionHeight: 30, refractionAmount: 64, chromaticAberration: 1 }
};

let config = { ...defaults };
let saveTimer = 0;
let saveVersion = 0;
const pending = new Map();
const root = document.documentElement;
const status = document.querySelector('#status');

window.addEventListener('message', event => {
  let message;
  try { message = JSON.parse(event.data); } catch (_) { return; }
  const request = pending.get(message.id);
  if (!request) return;
  pending.delete(message.id);
  message.ok ? request.resolve(message.response) : request.reject(new Error(message.error || '请求失败'));
});

function requestId() {
  return globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function call(type, payload = {}) {
  if (!window.museHost?.postMessage) return Promise.reject(new Error('当前为独立预览，未连接 Muse 宿主'));
  const id = requestId();
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => { pending.delete(id); reject(new Error('Muse 响应超时')); }, 6000);
    pending.set(id, {
      resolve: value => { clearTimeout(timeout); resolve(value); },
      reject: error => { clearTimeout(timeout); reject(error); }
    });
    window.museHost.postMessage(JSON.stringify({ id, type, payload }));
  });
}

function setStatus(text, state = '') {
  status.textContent = text;
  status.className = state;
}

function paint() {
  root.style.setProperty('--radius', `${config.cornerRadius}px`);
  root.style.setProperty('--blur', `${config.blurRadius}px`);
  root.style.setProperty('--refract-height', `${config.refractionHeight}px`);
  root.style.setProperty('--refract-amount', `${config.refractionAmount}px`);
  root.style.setProperty('--chroma', config.chromaticAberration);
  Object.entries(fields).forEach(([key, field]) => {
    const input = document.querySelector(`#${key}`);
    if (!input) return;
    input.value = config[key];
    input.style.setProperty('--fill', `${(config[key] - field.min) / (field.max - field.min) * 100}%`);
    document.querySelector(`[data-value="${key}"]`).textContent = `${config[key].toFixed(key === 'chromaticAberration' ? 2 : 0)}${field.unit ? ` ${field.unit}` : ''}`;
  });
}

function render() {
  document.querySelector('#controls').innerHTML = Object.entries(fields).map(([key, field]) => `
    <div class="control">
      <div class="control-head">
        <div><span class="control-title">${field.name}</span><span class="control-meta">${field.note}</span></div>
        <output data-value="${key}"></output>
      </div>
      <input id="${key}" aria-label="${field.name}" type="range" min="${field.min}" max="${field.max}" step="${field.step}" value="${config[key]}">
    </div>`).join('');

  Object.keys(fields).forEach(key => {
    document.querySelector(`#${key}`).addEventListener('input', event => {
      config[key] = Number(event.target.value);
      paint();
      scheduleSave();
    });
  });
}

function scheduleSave() {
  clearTimeout(saveTimer);
  setStatus('预览已更新 · 正在应用…');
  const version = ++saveVersion;
  saveTimer = setTimeout(async () => {
    try {
      const response = await call('glass.setConfig', { config });
      if (version !== saveVersion) return;
      if (response?.config) config = { ...config, ...response.config };
      paint();
      setStatus('已应用到 Muse', 'ok');
    } catch (error) {
      if (version === saveVersion) setStatus(error.message, 'error');
    }
  }, 90);
}

async function init() {
  render();
  paint();
  try {
    const response = await call('glass.getConfig');
    if (response?.config) config = { ...config, ...response.config };
    paint();
    setStatus('已连接 · 实时同步', 'ok');
  } catch (error) {
    setStatus(window.museHost ? error.message : '独立预览模式', window.museHost ? 'error' : '');
  }
}

document.querySelector('#reset').addEventListener('click', async () => {
  config = { ...defaults };
  paint();
  setStatus('正在恢复默认值…');
  try {
    const response = await call('glass.resetConfig');
    config = { ...defaults, ...(response?.config || {}) };
    paint();
    setStatus('已恢复默认值', 'ok');
  } catch (error) { setStatus(error.message, 'error'); }
});

document.querySelectorAll('[data-preset]').forEach(button => button.addEventListener('click', () => {
  config = { ...config, ...presets[button.dataset.preset] };
  paint();
  scheduleSave();
}));

init();
