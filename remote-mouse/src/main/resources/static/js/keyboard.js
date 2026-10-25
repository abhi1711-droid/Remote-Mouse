/**
 * KeyboardController — Virtual keyboard panel manager.
 *
 * Handles:
 *  - Text input (character-by-character via input event)
 *  - Modifier toggles (Ctrl, Alt, Shift, Meta) with one-shot behaviour
 *  - Special keys (Esc, Tab, arrows, Enter, Backspace, etc.)
 */
class KeyboardController {
  constructor(connectionManager) {
    this.conn = connectionManager;
    this.isVisible = false;
    this.activeModifiers = new Set();
    this.panel = null;
    this.textInput = null;
  }

  init() {
    this.panel = document.getElementById('keyboard-panel');
    this.textInput = document.getElementById('keyboard-input');

    // Character input
    this.textInput.addEventListener('input', (e) => {
      const text = e.data;
      if (text) {
        this.conn.send({ type: 'type', text: text });
        // Clear the input so each character is fresh
        requestAnimationFrame(() => {
          this.textInput.value = '';
        });
      }
    });

    // Handle backspace / enter on text field
    this.textInput.addEventListener('keydown', (e) => {
      if (e.key === 'Backspace') {
        e.preventDefault();
        this.sendKey('BACKSPACE');
      } else if (e.key === 'Enter') {
        e.preventDefault();
        this.sendKey('ENTER');
      } else if (e.key === 'Tab') {
        e.preventDefault();
        this.sendKey('TAB');
      }
    });

    this.setupKeyButtons();
  }

  /**
   * Attach touch handlers to every button with [data-key].
   */
  setupKeyButtons() {
    document.querySelectorAll('[data-key]').forEach((btn) => {
      btn.addEventListener('touchstart', (e) => {
        e.preventDefault();
        const key = btn.dataset.key;

        if (['CTRL', 'ALT', 'SHIFT', 'META'].includes(key)) {
          this.toggleModifier(key, btn);
        } else {
          this.sendKey(key);
        }

        // Haptic
        if (navigator.vibrate) navigator.vibrate(20);

        // Visual press
        btn.classList.add('active');
        setTimeout(() => btn.classList.remove('active'), 150);
      });
    });
  }

  /**
   * Toggle a modifier key on/off.
   */
  toggleModifier(modifier, btn) {
    if (this.activeModifiers.has(modifier)) {
      this.activeModifiers.delete(modifier);
      btn.classList.remove('modifier-active');
    } else {
      this.activeModifiers.add(modifier);
      btn.classList.add('modifier-active');
    }
  }

  /**
   * Send a key press with any active modifiers, then clear modifiers.
   */
  sendKey(key) {
    const modifiers = Array.from(this.activeModifiers);
    this.conn.send({ type: 'key', key: key, modifiers: modifiers });
    this.clearModifiers();
  }

  /**
   * Clear all active modifiers and remove visual highlights.
   */
  clearModifiers() {
    this.activeModifiers.clear();
    document.querySelectorAll('.modifier-active').forEach((btn) => {
      btn.classList.remove('modifier-active');
    });
  }

  /**
   * Toggle keyboard panel visibility with slide animation.
   */
  toggle() {
    this.isVisible = !this.isVisible;
    if (this.isVisible) {
      this.panel.classList.remove('hidden');
      // Force reflow so the transition triggers
      void this.panel.offsetHeight;
      this.panel.classList.add('slide-up');
    } else {
      this.panel.classList.remove('slide-up');
      // Wait for transition to finish, then hide
      const onDone = () => {
        this.panel.classList.add('hidden');
        this.panel.removeEventListener('transitionend', onDone);
      };
      this.panel.addEventListener('transitionend', onDone);
      this.clearModifiers();
    }
  }

  /**
   * Hide the keyboard panel if it's visible.
   */
  hide() {
    if (this.isVisible) {
      this.toggle();
    }
  }
}
