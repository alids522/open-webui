/**
 * AdoetzGPT Flash — Main JavaScript Entry Point
 *
 * Flow:
 * 1. Check if backendUrl is stored in localStorage
 * 2. If not → show setup screen
 * 3. If yes → show loading screen, then redirect WebView to backendUrl
 *
 * On Android: Capacitor WebView will handle the redirect to the Open WebUI backend.
 * On web dev: Works normally in browser for testing.
 */

// ============================================================
// Constants
// ============================================================
const STORAGE_KEY_BACKEND_URL = 'adoetzgpt_flash_backend_url';
const STORAGE_KEY_RECENT_URLS = 'adoetzgpt_flash_recent_urls';
const MAX_RECENT = 5;
const PING_TIMEOUT_MS = 8000;

// ============================================================
// State
// ============================================================
let currentBackendUrl = null;

// ============================================================
// DOM Helpers
// ============================================================
function showScreen(id) {
  document.querySelectorAll('.screen').forEach(s => s.classList.add('hidden'));
  const el = document.getElementById(id);
  if (el) {
    el.classList.remove('hidden');
  }
}

function setLoadingStatus(text) {
  const el = document.getElementById('loading-status');
  if (el) el.textContent = text;
}

function showSetupError(msg) {
  const el = document.getElementById('setup-error');
  if (el) {
    el.textContent = msg;
    el.classList.remove('hidden');
  }
}

function hideSetupError() {
  const el = document.getElementById('setup-error');
  if (el) el.classList.add('hidden');
}

function setConnectBtnLoading(loading) {
  const btn = document.getElementById('connect-btn');
  if (!btn) return;
  btn.disabled = loading;
  btn.querySelector('.btn-text').textContent = loading ? 'Connecting…' : 'Connect';
}

// ============================================================
// Storage Helpers
// ============================================================
function getSavedBackendUrl() {
  try {
    return localStorage.getItem(STORAGE_KEY_BACKEND_URL) || null;
  } catch {
    return null;
  }
}

function saveBackendUrl(url) {
  try {
    localStorage.setItem(STORAGE_KEY_BACKEND_URL, url);
    addToRecent(url);
  } catch (e) {
    console.warn('Storage write failed:', e);
  }
}

function clearBackendUrl() {
  try {
    localStorage.removeItem(STORAGE_KEY_BACKEND_URL);
  } catch {}
}

function getRecentUrls() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY_RECENT_URLS) || '[]');
  } catch {
    return [];
  }
}

function addToRecent(url) {
  try {
    let recents = getRecentUrls().filter(u => u !== url);
    recents.unshift(url);
    recents = recents.slice(0, MAX_RECENT);
    localStorage.setItem(STORAGE_KEY_RECENT_URLS, JSON.stringify(recents));
  } catch {}
}

// ============================================================
// URL Normalization
// ============================================================
function normalizeUrl(raw) {
  let url = raw.trim().replace(/\/+$/, '');
  if (!url.startsWith('http://') && !url.startsWith('https://')) {
    url = 'http://' + url;
  }
  try {
    const u = new URL(url);
    return u.origin; // scheme + host + port, no trailing slash
  } catch {
    return null;
  }
}

// ============================================================
// Backend Ping (best-effort, works around CORS issues)
// ============================================================
async function pingBackend(baseUrl) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), PING_TIMEOUT_MS);

  // Try fetching /health or /api/version as a lightweight check.
  // We use 'no-cors' mode so CORS won't block us — we just need to know
  // if the server is reachable (a network error means it's not).
  try {
    const res = await fetch(`${baseUrl}/health`, {
      method: 'GET',
      signal: controller.signal,
      mode: 'no-cors',
      cache: 'no-store',
    });
    clearTimeout(timer);
    return true; // any response (even opaque) means server is up
  } catch (err) {
    clearTimeout(timer);
    if (err.name === 'AbortError') {
      throw new Error('Connection timed out. Is the server running?');
    }
    throw new Error('Server unreachable. Check the URL and your network.');
  }
}

// ============================================================
// Navigation — redirect WebView to backend
// ============================================================
function navigateToBackend(url) {
  setLoadingStatus('Opening Open WebUI…');
  // Small delay for the loading animation to play
  setTimeout(() => {
    window.location.href = url;
  }, 600);
}

// ============================================================
// Connect Handler (called from HTML onclick)
// ============================================================
window.handleConnect = async function() {
  hideSetupError();

  const input = document.getElementById('backend-url-input');
  const raw = input ? input.value.trim() : '';

  if (!raw) {
    showSetupError('Please enter a backend URL.');
    input?.focus();
    return;
  }

  const normalized = normalizeUrl(raw);
  if (!normalized) {
    showSetupError('Invalid URL format. Example: https://chat.example.com');
    return;
  }

  setConnectBtnLoading(true);
  showScreen('loading-screen');
  setLoadingStatus(`Connecting to ${normalized}…`);

  try {
    await pingBackend(normalized);
    saveBackendUrl(normalized);
    currentBackendUrl = normalized;
    navigateToBackend(normalized);
  } catch (err) {
    // Go back to setup screen and show error
    showScreen('setup-screen');
    showSetupError(err.message || 'Could not connect to backend.');
    setConnectBtnLoading(false);
  }
};

// ============================================================
// Retry from error screen
// ============================================================
window.retryConnection = async function() {
  if (!currentBackendUrl) {
    showSetup();
    return;
  }
  showScreen('loading-screen');
  setLoadingStatus(`Retrying ${currentBackendUrl}…`);
  try {
    await pingBackend(currentBackendUrl);
    navigateToBackend(currentBackendUrl);
  } catch (err) {
    showScreen('error-screen');
    const detail = document.getElementById('error-detail');
    if (detail) detail.textContent = err.message;
  }
};

// ============================================================
// Show Setup Screen
// ============================================================
window.showSetup = function() {
  clearBackendUrl();
  currentBackendUrl = null;
  showScreen('setup-screen');
  renderRecentList();
};

// ============================================================
// Render recent connections list
// ============================================================
function renderRecentList() {
  const recents = getRecentUrls();
  const listEl = document.getElementById('recent-list');
  const sectionEl = document.getElementById('recent-section');
  if (!listEl || !sectionEl) return;

  if (recents.length === 0) {
    sectionEl.style.display = 'none';
    return;
  }

  sectionEl.style.display = '';
  listEl.innerHTML = '';

  recents.forEach(url => {
    const item = document.createElement('div');
    item.className = 'recent-item';
    item.innerHTML = `
      <span class="recent-item-url">${url}</span>
      <span class="recent-item-arrow">→</span>
    `;
    item.addEventListener('click', async () => {
      const input = document.getElementById('backend-url-input');
      if (input) input.value = url;
      await window.handleConnect();
    });
    listEl.appendChild(item);
  });
}

// ============================================================
// Keyboard / soft keyboard handling (Android)
// ============================================================
function setupKeyboardHandling() {
  // On Android, when soft keyboard appears, scrollIntoView the focused input
  if (window.visualViewport) {
    window.visualViewport.addEventListener('resize', () => {
      const focused = document.activeElement;
      if (focused && (focused.tagName === 'INPUT' || focused.tagName === 'TEXTAREA')) {
        setTimeout(() => {
          focused.scrollIntoView({ block: 'center', behavior: 'smooth' });
        }, 100);
      }
    });
  }
}

// ============================================================
// Enter key submit
// ============================================================
function setupEnterKey() {
  const input = document.getElementById('backend-url-input');
  if (input) {
    input.addEventListener('keydown', e => {
      if (e.key === 'Enter') {
        e.preventDefault();
        window.handleConnect();
      }
    });
  }
}

// ============================================================
// Init
// ============================================================
async function init() {
  setupKeyboardHandling();
  setupEnterKey();

  const saved = getSavedBackendUrl();
  if (saved) {
    currentBackendUrl = saved;
    showScreen('loading-screen');
    setLoadingStatus(`Connecting to ${saved}…`);

    try {
      await pingBackend(saved);
      navigateToBackend(saved);
    } catch (err) {
      // Backend unreachable - show error screen
      showScreen('error-screen');
      const detail = document.getElementById('error-detail');
      if (detail) {
        detail.textContent = `Could not reach ${saved}. ${err.message}`;
      }
    }
  } else {
    showScreen('setup-screen');
    renderRecentList();
  }
}

// Run after DOM is ready
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', init);
} else {
  init();
}
