/**
 * MediaController — Binds media control buttons to WebSocket commands.
 *
 * Actions: PLAY_PAUSE, VOLUME_UP, VOLUME_DOWN, MUTE, NEXT, PREVIOUS
 */
class MediaController {
  constructor(connectionManager) {
    this.conn = connectionManager;
  }

  init() {
    const mediaActions = {
      'btn-play-pause': 'PLAY_PAUSE',
      'btn-vol-up':     'VOLUME_UP',
      'btn-vol-down':   'VOLUME_DOWN',
      'btn-mute':       'MUTE',
      'btn-next':       'NEXT',
      'btn-prev':       'PREVIOUS',
    };

    Object.entries(mediaActions).forEach(([btnId, action]) => {
      const btn = document.getElementById(btnId);
      if (!btn) return;

      btn.addEventListener('touchstart', (e) => {
        e.preventDefault();
        this.conn.send({ type: 'media', action: action });
        btn.classList.add('active');
        if (navigator.vibrate) navigator.vibrate(30);
      });

      btn.addEventListener('touchend', (e) => {
        e.preventDefault();
        btn.classList.remove('active');
      });

      btn.addEventListener('touchcancel', () => {
        btn.classList.remove('active');
      });
    });
  }
}
