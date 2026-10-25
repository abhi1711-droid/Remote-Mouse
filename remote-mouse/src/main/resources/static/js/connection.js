/**
 * ConnectionManager — WebSocket connection lifecycle handler
 * Manages connecting, reconnecting, and message passing.
 */
class ConnectionManager {
  constructor() {
    this.ws = null;
    this.connected = false;
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 10;
    this.reconnectDelay = 1000; // base delay in ms
    this.reconnectTimer = null;

    // Callbacks
    this.onConnected = null;
    this.onDisconnected = null;
    this.onMessage = null;
    this.onError = null;
  }

  /**
   * Initiate a WebSocket connection to the server.
   */
  connect() {
    // Clean up any existing connection
    if (this.ws) {
      this.ws.onclose = null; // prevent triggering reconnect
      this.ws.close();
    }

    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${location.host}/ws`;

    try {
      this.ws = new WebSocket(wsUrl);
    } catch (err) {
      console.error('WebSocket creation failed:', err);
      this.tryReconnect();
      return;
    }

    this.ws.onopen = () => {
      this.connected = true;
      this.reconnectAttempts = 0;
      console.log('[Connection] Connected to', wsUrl);
      if (this.onConnected) this.onConnected();
    };

    this.ws.onclose = (event) => {
      const wasConnected = this.connected;
      this.connected = false;
      console.log('[Connection] Closed', event.code, event.reason);
      if (this.onDisconnected) this.onDisconnected();
      this.tryReconnect();
    };

    this.ws.onerror = (err) => {
      console.error('[Connection] Error:', err);
      if (this.onError) this.onError(err);
    };

    this.ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        if (this.onMessage) this.onMessage(data);
      } catch (err) {
        console.warn('[Connection] Non-JSON message:', event.data);
      }
    };
  }

  /**
   * Send a JSON message over the WebSocket.
   * @param {Object} message - The message to send.
   */
  send(message) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(message));
    }
  }

  /**
   * Attempt to reconnect with exponential backoff.
   */
  tryReconnect() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
    }

    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      const delay = Math.min(
        this.reconnectDelay * Math.pow(1.5, this.reconnectAttempts - 1),
        10000
      );
      console.log(`[Connection] Reconnecting in ${Math.round(delay)}ms (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})`);
      this.reconnectTimer = setTimeout(() => this.connect(), delay);
    } else {
      console.warn('[Connection] Max reconnect attempts reached.');
    }
  }

  /**
   * Cleanly close the connection without triggering reconnect.
   */
  disconnect() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.maxReconnectAttempts = 0; // prevent further reconnects
    if (this.ws) {
      this.ws.close();
    }
  }

  /**
   * Get the WebSocket URL for display purposes.
   */
  getUrl() {
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${location.host}/ws`;
  }
}
