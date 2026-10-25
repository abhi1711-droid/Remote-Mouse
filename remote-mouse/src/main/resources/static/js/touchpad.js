/**
 * TouchpadController — Handles touch gestures on the virtual trackpad.
 *
 * Supported gestures:
 *  - Single finger drag: mouse move
 *  - Single tap: left click
 *  - Double tap: double click
 *  - Long press + drag: click-drag
 *  - Two-finger vertical: scroll
 *  - Two-finger tap: right click
 */
class TouchpadController {
  constructor(connectionManager) {
    this.conn = connectionManager;
    this.element = null;
    this.sensitivity = 1.5;

    // Single touch state
    this.lastTouchX = 0;
    this.lastTouchY = 0;
    this.touchStartTime = 0;
    this.touchStartX = 0;
    this.touchStartY = 0;
    this.isTouching = false;
    this.moveThreshold = 10; // px — below this is a tap
    this.hasMoved = false;

    // Double tap
    this.lastTapTime = 0;
    this.doubleTapDelay = 300; // ms

    // Long press / drag
    this.longPressTimer = null;
    this.longPressDelay = 500; // ms
    this.isLongPress = false;
    this.isDragging = false;

    // Two-finger
    this.isTwoFinger = false;
    this.lastTwoFingerY = 0;
    this.twoFingerStartTime = 0;
    this.twoFingerMoved = false;

    // Three-finger swipe (macOS Mission Control / space switch)
    this.isThreeFinger = false;
    this.threeFingerStartX = 0;
    this.threeFingerStartY = 0;
    this.threeFingerFired = false;
    this.threeFingerSwipeThreshold = 50; // px

    // Throttle via rAF
    this.pendingMove = null;
    this.rafId = null;

    // Ghost text element
    this.ghostText = null;
    this.ghostTimeout = null;
  }

  /**
   * Bind the controller to a DOM element.
   * @param {string} elementId
   */
  init(elementId) {
    this.element = document.getElementById(elementId);
    this.ghostText = this.element.querySelector('.touchpad-ghost');

    const opts = { passive: false };
    this.element.addEventListener('touchstart', (e) => this.onTouchStart(e), opts);
    this.element.addEventListener('touchmove', (e) => this.onTouchMove(e), opts);
    this.element.addEventListener('touchend', (e) => this.onTouchEnd(e), opts);
    this.element.addEventListener('touchcancel', (e) => this.onTouchEnd(e), opts);
    this.element.addEventListener('contextmenu', (e) => e.preventDefault());
  }

  // ── Touch Start ──────────────────────────────────────
  onTouchStart(e) {
    e.preventDefault();
    this.hideGhostText();

    if (e.touches.length >= 3) {
      this.isThreeFinger = true;
      this.isTwoFinger = false;
      this.isTouching = false;
      this.threeFingerFired = false;
      const cx = (e.touches[0].clientX + e.touches[1].clientX + e.touches[2].clientX) / 3;
      const cy = (e.touches[0].clientY + e.touches[1].clientY + e.touches[2].clientY) / 3;
      this.threeFingerStartX = cx;
      this.threeFingerStartY = cy;
      this.clearLongPress();
      return;
    }

    if (e.touches.length === 2) {
      this.isTwoFinger = true;
      this.twoFingerMoved = false;
      this.twoFingerStartTime = Date.now();
      this.lastTwoFingerY = (e.touches[0].clientY + e.touches[1].clientY) / 2;
      this.clearLongPress();
      return;
    }

    if (e.touches.length === 1) {
      const touch = e.touches[0];
      this.lastTouchX = touch.clientX;
      this.lastTouchY = touch.clientY;
      this.touchStartX = touch.clientX;
      this.touchStartY = touch.clientY;
      this.touchStartTime = Date.now();
      this.isTouching = true;
      this.hasMoved = false;
      this.isTwoFinger = false;

      // Long press timer
      this.longPressTimer = setTimeout(() => {
        if (!this.hasMoved) {
          this.isLongPress = true;
          this.isDragging = true;
          this.conn.send({ type: 'drag', action: 'START', dx: 0, dy: 0 });
          if (navigator.vibrate) navigator.vibrate(50);
          this.element.classList.add('dragging');
        }
      }, this.longPressDelay);

      // Ripple
      this.createRipple(touch.clientX, touch.clientY);
    }
  }

  // ── Touch Move ───────────────────────────────────────
  onTouchMove(e) {
    e.preventDefault();

    // Three-finger swipe — fire once per gesture
    if (this.isThreeFinger && e.touches.length >= 3) {
      if (this.threeFingerFired) return;
      const cx = (e.touches[0].clientX + e.touches[1].clientX + e.touches[2].clientX) / 3;
      const cy = (e.touches[0].clientY + e.touches[1].clientY + e.touches[2].clientY) / 3;
      const dx = cx - this.threeFingerStartX;
      const dy = cy - this.threeFingerStartY;
      const absX = Math.abs(dx);
      const absY = Math.abs(dy);
      if (absX < this.threeFingerSwipeThreshold && absY < this.threeFingerSwipeThreshold) return;

      if (absX > absY) {
        // Horizontal swipe → switch macOS spaces
        const key = dx > 0 ? 'RIGHT' : 'LEFT';
        this.conn.send({ type: 'key', key: key, modifiers: ['CTRL'] });
      } else if (dy < 0) {
        // Swipe up → Mission Control
        this.conn.send({ type: 'key', key: 'UP', modifiers: ['CTRL'] });
      } else {
        // Swipe down → also Mission Control (macOS has no down variant we can reliably use)
        this.conn.send({ type: 'key', key: 'UP', modifiers: ['CTRL'] });
      }
      this.threeFingerFired = true;
      if (navigator.vibrate) navigator.vibrate(40);
      return;
    }

    // Two-finger scroll
    if (this.isTwoFinger && e.touches.length === 2) {
      const currentY = (e.touches[0].clientY + e.touches[1].clientY) / 2;
      const deltaY = this.lastTwoFingerY - currentY;
      this.lastTwoFingerY = currentY;

      if (Math.abs(deltaY) > 2) {
        this.twoFingerMoved = true;
        const scrollDelta = Math.round(deltaY / 10);
        if (scrollDelta !== 0) {
          this.conn.send({ type: 'scroll', delta: scrollDelta });
        }
      }
      return;
    }

    // Single-finger move
    if (e.touches.length === 1 && this.isTouching) {
      const touch = e.touches[0];
      const dx = (touch.clientX - this.lastTouchX) * this.sensitivity;
      const dy = (touch.clientY - this.lastTouchY) * this.sensitivity;

      this.lastTouchX = touch.clientX;
      this.lastTouchY = touch.clientY;

      // Movement detection
      const totalDx = touch.clientX - this.touchStartX;
      const totalDy = touch.clientY - this.touchStartY;
      if (Math.abs(totalDx) > this.moveThreshold || Math.abs(totalDy) > this.moveThreshold) {
        this.hasMoved = true;
        this.clearLongPress();
      }

      // Throttle via rAF
      if (this.isDragging) {
        this.pendingMove = { type: 'drag', action: 'MOVE', dx: Math.round(dx), dy: Math.round(dy) };
      } else {
        this.pendingMove = { type: 'move', dx: Math.round(dx), dy: Math.round(dy) };
      }

      if (!this.rafId) {
        this.rafId = requestAnimationFrame(() => {
          if (this.pendingMove) {
            this.conn.send(this.pendingMove);
            this.pendingMove = null;
          }
          this.rafId = null;
        });
      }
    }
  }

  // ── Touch End ────────────────────────────────────────
  onTouchEnd(e) {
    e.preventDefault();

    // Three-finger release — swallow until all fingers lift, no click on end
    if (this.isThreeFinger) {
      if (e.touches.length === 0) {
        this.isThreeFinger = false;
        this.threeFingerFired = false;
        this.showGhostTextDelayed();
      }
      return;
    }

    // Two-finger release
    if (this.isTwoFinger && e.touches.length === 0) {
      const duration = Date.now() - this.twoFingerStartTime;
      if (duration < 300 && !this.twoFingerMoved) {
        this.conn.send({ type: 'click', button: 'RIGHT' });
      }
      this.isTwoFinger = false;
      this.isTouching = false;
      this.showGhostTextDelayed();
      return;
    }

    // End drag
    if (this.isDragging) {
      this.conn.send({ type: 'drag', action: 'END', dx: 0, dy: 0 });
      this.isDragging = false;
      this.isLongPress = false;
      this.element.classList.remove('dragging');
    }

    this.clearLongPress();

    // Tap detection
    if (!this.hasMoved && !this.isLongPress && e.touches.length === 0) {
      const duration = Date.now() - this.touchStartTime;
      if (duration < 300) {
        const now = Date.now();
        if (now - this.lastTapTime < this.doubleTapDelay) {
          // Double tap
          this.conn.send({ type: 'doubleclick' });
          this.lastTapTime = 0;
        } else {
          this.lastTapTime = now;
          // Wait to see if double tap follows
          setTimeout(() => {
            if (this.lastTapTime === now) {
              this.conn.send({ type: 'click', button: 'LEFT' });
            }
          }, this.doubleTapDelay);
        }
      }
    }

    this.isTouching = false;
    this.hasMoved = false;
    this.isLongPress = false;
    this.showGhostTextDelayed();
  }

  // ── Helpers ──────────────────────────────────────────

  clearLongPress() {
    if (this.longPressTimer) {
      clearTimeout(this.longPressTimer);
      this.longPressTimer = null;
    }
  }

  createRipple(x, y) {
    const ripple = document.createElement('div');
    ripple.className = 'touch-ripple';
    const rect = this.element.getBoundingClientRect();
    ripple.style.left = (x - rect.left) + 'px';
    ripple.style.top = (y - rect.top) + 'px';
    this.element.appendChild(ripple);
    ripple.addEventListener('animationend', () => ripple.remove());
  }

  hideGhostText() {
    if (this.ghostText) {
      this.ghostText.style.opacity = '0';
    }
    if (this.ghostTimeout) {
      clearTimeout(this.ghostTimeout);
      this.ghostTimeout = null;
    }
  }

  showGhostTextDelayed() {
    if (this.ghostTimeout) clearTimeout(this.ghostTimeout);
    this.ghostTimeout = setTimeout(() => {
      if (this.ghostText) {
        this.ghostText.style.opacity = '';
      }
    }, 3000);
  }

  /**
   * Update mouse sensitivity multiplier.
   * @param {number} value
   */
  setSensitivity(value) {
    this.sensitivity = value;
    this.conn.send({ type: 'sensitivity', value: value });
  }
}
