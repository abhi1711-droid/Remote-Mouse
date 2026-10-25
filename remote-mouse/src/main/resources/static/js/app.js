/**
 * App — Main orchestrator for the Remote Mouse client.
 *
 * - Initializes all controllers
 * - Manages screen transitions (connection → control)
 * - Wires up UI event listeners
 * - Prevents default touch behaviours to avoid page bouncing
 */
class App {
  constructor() {
    this.conn = new ConnectionManager();
    this.touchpad = new TouchpadController(this.conn);
    this.keyboard = new KeyboardController(this.conn);
    this.media = new MediaController(this.conn);
    this.settingsOpen = false;
  }

  init() {
    // --- Connection callbacks ---
    this.conn.onConnected = () => this.onConnected();
    this.conn.onDisconnected = () => this.onDisconnected();
    this.conn.onMessage = (data) => this.onMessage(data);
    this.conn.onError = (err) => console.error('[App] WebSocket error:', err);

    // --- Initialize controllers ---
    this.touchpad.init('touchpad');
    this.keyboard.init();
    this.media.init();

    // --- Setup UI ---
    this.setupUI();

    // --- Display connection URL ---
    const urlEl = document.getElementById('connection-url');
    if (urlEl) urlEl.textContent = this.conn.getUrl();

    // --- Connect ---
    document.getElementById('connection-status-text').textContent = 'Connecting...';
    this.conn.connect();
  }

  // ── Connection Callbacks ─────────────────────────────

  onConnected() {
    // Switch screens
    document.getElementById('connection-screen').classList.add('hidden');
    document.getElementById('control-screen').classList.remove('hidden');

    // Status bar
    document.getElementById('status-dot').classList.add('connected');
    document.getElementById('status-text').textContent = 'Connected';

    // Settings panel info
    document.getElementById('settings-conn-status').textContent = 'Connected';
    document.getElementById('settings-conn-status').style.color = 'var(--success)';
    document.getElementById('settings-conn-host').textContent = location.host;
  }

  onDisconnected() {
    // Status bar
    document.getElementById('status-dot').classList.remove('connected');
    document.getElementById('status-text').textContent = 'Disconnected';

    // Switch back to connection screen
    document.getElementById('connection-screen').classList.remove('hidden');
    document.getElementById('control-screen').classList.add('hidden');
    document.getElementById('connection-status-text').textContent = 'Reconnecting...';

    // Restart spinner
    const spinner = document.querySelector('.spinner');
    const checkmark = document.querySelector('.checkmark');
    if (spinner) spinner.classList.remove('hidden');
    if (checkmark) checkmark.classList.add('hidden');

    // Settings info
    document.getElementById('settings-conn-status').textContent = 'Disconnected';
    document.getElementById('settings-conn-status').style.color = 'var(--danger)';
  }

  onMessage(data) {
    if (data.connected !== undefined) {
      console.log('[App] Server status:', data);
    }
    if (data.message) {
      console.log('[App] Server message:', data.message);
    }
  }

  // ── UI Setup ─────────────────────────────────────────

  setupUI() {
    // Keyboard toggle
    document.getElementById('btn-keyboard-toggle').addEventListener('click', () => {
      this.keyboard.toggle();
    });

    // Left click
    const btnLeft = document.getElementById('btn-left-click');
    btnLeft.addEventListener('touchstart', (e) => {
      e.preventDefault();
      this.conn.send({ type: 'click', button: 'LEFT' });
      btnLeft.classList.add('active');
      if (navigator.vibrate) navigator.vibrate(30);
    });
    btnLeft.addEventListener('touchend', (e) => {
      e.preventDefault();
      btnLeft.classList.remove('active');
    });

    // Right click
    const btnRight = document.getElementById('btn-right-click');
    btnRight.addEventListener('touchstart', (e) => {
      e.preventDefault();
      this.conn.send({ type: 'click', button: 'RIGHT' });
      btnRight.classList.add('active');
      if (navigator.vibrate) navigator.vibrate(30);
    });
    btnRight.addEventListener('touchend', (e) => {
      e.preventDefault();
      btnRight.classList.remove('active');
    });

    // Settings toggle
    document.getElementById('btn-settings').addEventListener('click', () => {
      this.toggleSettings();
    });
    document.getElementById('btn-close-settings').addEventListener('click', () => {
      this.closeSettings();
    });

    // Close keyboard
    document.getElementById('btn-close-keyboard').addEventListener('click', () => {
      this.keyboard.hide();
    });

    // Sensitivity slider
    const slider = document.getElementById('sensitivity-slider');
    const display = document.getElementById('sensitivity-value');
    slider.addEventListener('input', (e) => {
      const value = parseFloat(e.target.value);
      display.textContent = value.toFixed(1) + 'x';
      this.touchpad.setSensitivity(value);
    });

    // Scroll mode toggle
    document.getElementById('btn-scroll-mode').addEventListener('click', (e) => {
      e.currentTarget.classList.toggle('active');
    });

    // Prevent page overscroll / pull-to-refresh globally
    document.addEventListener('touchmove', (e) => {
      const target = e.target;
      // Allow scrolling inside text inputs
      if (target.closest('#keyboard-input') || target.closest('#sensitivity-slider')) {
        return;
      }
      // Already handled by touchpad
      if (target.closest('#touchpad')) {
        return;
      }
      e.preventDefault();
    }, { passive: false });

    // Close settings when tapping outside
    document.getElementById('control-screen').addEventListener('click', (e) => {
      if (this.settingsOpen && !e.target.closest('#settings-panel') && !e.target.closest('#btn-settings')) {
        this.closeSettings();
      }
    });
  }

  toggleSettings() {
    const panel = document.getElementById('settings-panel');
    this.settingsOpen = !this.settingsOpen;
    if (this.settingsOpen) {
      panel.classList.remove('hidden');
    } else {
      panel.classList.add('hidden');
    }
  }

  closeSettings() {
    this.settingsOpen = false;
    document.getElementById('settings-panel').classList.add('hidden');
  }
}

// ── Bootstrap ────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  const app = new App();
  app.init();
});
