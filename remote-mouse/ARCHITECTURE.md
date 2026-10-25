# Remote Mouse — Architecture

A Spring Boot server on macOS that turns any phone browser on the same Wi-Fi
into a virtual trackpad + keyboard + media remote. Client uses raw WebSocket
(no STOMP) for low-latency input streaming; server uses `java.awt.Robot` to
drive the OS.

## Component Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                          MOBILE BROWSER (Phone)                      │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  index.html  ── CSS/style.css                                  │ │
│  │  ┌──────────────────────────────────────────────────────────┐  │ │
│  │  │  app.js  (App orchestrator)                              │  │ │
│  │  │    ├─ TouchpadController  (touchpad.js)                  │  │ │
│  │  │    │    • 1-finger drag → move                           │  │ │
│  │  │    │    • tap → click, double-tap → dblclick             │  │ │
│  │  │    │    • long-press → drag start/end                    │  │ │
│  │  │    │    • 2-finger vertical → scroll                     │  │ │
│  │  │    │    • 2-finger tap → right-click                     │  │ │
│  │  │    ├─ KeyboardController  (keyboard.js)                  │  │ │
│  │  │    │    • text input, modifiers, special keys            │  │ │
│  │  │    ├─ MediaController     (media.js)                     │  │ │
│  │  │    │    • play/pause, vol±, mute, next, prev             │  │ │
│  │  │    └─ ConnectionManager   (connection.js)                │  │ │
│  │  │         • WebSocket + exponential-backoff reconnect      │  │ │
│  │  └──────────────────────────────────────────────────────────┘  │ │
│  └────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                    │                          ▲
    HTTP GET /,     │  WebSocket /ws           │ StatusMessage JSON
    /api/*          │  InputMessage JSON       │ (connected, screen size)
                    ▼                          │
                 ══════════════ Wi-Fi LAN ══════════════
                    │                          │
┌─────────────────────────────────────────────────────────────────────┐
│                    SPRING BOOT SERVER (Mac, port 8080)               │
│                          Undertow embedded                           │
│                                                                      │
│  ┌───────────────────────┐        ┌──────────────────────────────┐  │
│  │ ConnectionController  │        │   WebSocketConfig            │  │
│  │  GET /api/connection- │        │   registers /ws endpoint     │  │
│  │      info             │        └──────────────┬───────────────┘  │
│  │  GET /api/qr-code     │                       │                  │
│  │  (ZXing PNG)          │                       ▼                  │
│  └───────────┬───────────┘        ┌──────────────────────────────┐  │
│              │                    │   InputWebSocketHandler      │  │
│              │                    │   • single-client guard      │  │
│              │                    │   • JSON → InputMessage      │  │
│              │                    │   • routes by type field     │  │
│              │                    └──────────────┬───────────────┘  │
│              ▼                                   ▼                  │
│  ┌───────────────────────┐        ┌──────────────────────────────┐  │
│  │   NetworkService      │        │   RobotService               │  │
│  │  • enumerates NICs    │        │  • java.awt.Robot            │  │
│  │  • prefers RFC1918    │        │  • synchronized on robot     │  │
│  │  • skips CGNAT/utun   │        │  • KEY_MAP (name→VK_*)       │  │
│  └───────────────────────┘        │  • sensitivity clamp [.5,5]  │  │
│                                   └──────────────┬───────────────┘  │
└──────────────────────────────────────────────────┼──────────────────┘
                                                   ▼
                                    ┌──────────────────────────────┐
                                    │      macOS Host              │
                                    │  Mouse • Keyboard • Screen   │
                                    │  (Accessibility permission)  │
                                    └──────────────────────────────┘
```

## Message Flow

```
                        USER TOUCHES PHONE SCREEN
                                   │
                                   ▼
                    ┌──────────────────────────────┐
                    │ Browser dispatches touch     │
                    │ event to TouchpadController  │
                    └──────────────┬───────────────┘
                                   ▼
                    ┌──────────────────────────────┐
                    │ Classify gesture:            │
                    │  tap? drag? scroll? key?     │
                    └──────────────┬───────────────┘
                                   ▼
                    ┌──────────────────────────────┐
                    │ Build JSON InputMessage      │
                    │ { type, dx, dy, button, … }  │
                    └──────────────┬───────────────┘
                                   ▼
                    ┌──────────────────────────────┐
                    │ ConnectionManager.send()     │
                    │  throttled via rAF for moves │
                    └──────────────┬───────────────┘
                                   ▼
                       ══ WebSocket frame ══
                                   ▼
                    ┌──────────────────────────────┐
                    │ InputWebSocketHandler        │
                    │  .handleTextMessage()        │
                    └──────────────┬───────────────┘
                                   ▼
                    ┌──────────────────────────────┐
                    │ Jackson: JSON → InputMessage │
                    └──────────────┬───────────────┘
                                   ▼
                    ┌──────────────────────────────┐
                    │ switch(input.type) →         │
                    │  handleMove / Click / Drag / │
                    │  Type / Key / Media / …      │
                    └──────────────┬───────────────┘
                                   ▼
                    ┌──────────────────────────────┐
                    │ RobotService.<method>()      │
                    │  synchronized(robot) { … }   │
                    └──────────────┬───────────────┘
                                   ▼
                    ┌──────────────────────────────┐
                    │ java.awt.Robot fires OS event│
                    │  mouseMove/Press/keyPress/…  │
                    └──────────────┬───────────────┘
                                   ▼
                       macOS cursor / keystroke
```

## Connection Lifecycle

```
Phone loads http://<lan-ip>:8080/
        │
        ▼
GET / ──► Undertow serves static/index.html + JS bundle
        │
        ▼
JS boots → ConnectionManager.connect() → new WebSocket('/ws')
        │
        ▼
Server: afterConnectionEstablished
        │
        ├─ activeSession==null?  ── no ──► send error, close(POLICY_VIOLATION)
        │
        └─ yes → store session, send StatusMessage{connected:true, w, h}
                │
                ▼
        Phone: onConnected() → hide connection screen, show controls
                │
                ▼
        ────── steady state: JSON frames both ways ──────
                │
        On close/error → onclose fires → tryReconnect()
                │
                ▼
        Exponential backoff: 1s × 1.5^n (cap 10s), max 10 attempts
```

## WebSocket Message Protocol

All frames are JSON `InputMessage` records. `type` is required; other fields
are populated per message kind.

| type          | fields                             | server action                          |
|---------------|------------------------------------|----------------------------------------|
| `move`        | `dx`, `dy`                         | `Robot.mouseMove` with sensitivity     |
| `click`       | `button` (LEFT/RIGHT/MIDDLE)       | press + release                        |
| `doubleclick` | —                                  | two BUTTON1 clicks, 50 ms apart        |
| `scroll`      | `delta`                            | `Robot.mouseWheel(delta)`              |
| `drag`        | `action` (start/move/end), dx, dy  | press → move* → release                |
| `type`        | `text`                             | char-by-char via `getExtendedKeyCode`  |
| `key`         | `key`, `modifiers[]`               | modifiers down, key press+release, up  |
| `media`       | `action` (PLAY_PAUSE, VOLUME_UP…)  | mapped to fallback VK_* combos         |
| `sensitivity` | `value`                            | clamped to [0.5, 5.0]                  |

Server → client `StatusMessage`: `{ connected, screenWidth, screenHeight, message }`.

## Key Design Choices

- **Single-client lock** in `InputWebSocketHandler` — avoids concurrent
  gesture contention on the shared `Robot`. Second connection is rejected
  with `POLICY_VIOLATION`.
- **Raw WebSocket over STOMP** — minimises framing overhead for high-rate
  move events.
- **`requestAnimationFrame` throttling** in the client — coalesces move
  deltas to ~60 Hz max regardless of `touchmove` firing rate.
- **Undertow instead of Tomcat** — avoids a macOS `setSoLinger` SocketException.
- **LAN IP resolution** — enumerates interfaces, prefers RFC1918 addresses,
  skips loopback, `utun*`, `awdl0`, `bridge*`, and CGNAT (100.64.0.0/10)
  so Tailscale/VPN addresses aren't advertised to phones.
- **KEY_MAP built once in static block** — string key names → `KeyEvent.VK_*`
  lookup is O(1) at input time.
