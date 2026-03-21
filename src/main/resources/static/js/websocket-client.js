// Prevent duplicate declaration
(function() {
  if (typeof window.WebSocketClient !== 'undefined') {
    return; // Already defined
  }

  window.WebSocketClient = class WebSocketClient {
    constructor(endpoint) {
      this.endpoint = (typeof endpoint === "string" && endpoint) ? endpoint : (window.SKINME_WS_ENDPOINT || "/ws-endpoint");
      this.stompClient = null;
      this.isConnected = false;
      this.subscriptions = {};
      this.subscriptionCallbacks = {};
      this.reconnectAttempts = 0;
      this.maxReconnectAttempts = 5;
      this.reconnectDelay = 3000;
    }

  _doSubscribe(destination) {
    const callbacks = this.subscriptionCallbacks[destination];
    if (!callbacks || callbacks.length === 0 || !this.stompClient) return;
    const sub = this.stompClient.subscribe(destination, (message) => {
      try {
        const data = typeof message.body === "string" ? JSON.parse(message.body) : message.body;
        callbacks.forEach(function(cb) { try { cb(data); } catch (e) { console.error("WebSocket callback error:", e); } });
      } catch (e) {
        callbacks.forEach(function(cb) { try { cb(message.body); } catch (err) { console.error("WebSocket callback error:", err); } });
      }
    });
    this.subscriptions[destination] = sub;
  }

  _applySubscriptions() {
    const self = this;
    Object.keys(self.subscriptionCallbacks).forEach(function(dest) {
      if (self.subscriptionCallbacks[dest].length > 0 && !self.subscriptions[dest]) {
        self._doSubscribe(dest);
      }
    });
  }

  /**
   * Initialize WebSocket connection (uses same-origin; cookies sent for session auth)
   */
  connect(onConnect, onError) {
    if (typeof SockJS === "undefined" || typeof Stomp === "undefined") {
      const message = "SockJS or StompJS is not loaded. Include their scripts before websocket-client.js.";
      console.error(message);
      if (onError) onError(message);
      return;
    }

    const socket = new SockJS(this.endpoint);
    this.stompClient = Stomp.over(socket);
    this.stompClient.debug = null;

    const self = this;
    this.stompClient.connect(
      {},
      function(frame) {
        self.isConnected = true;
        self.reconnectAttempts = 0;
        self._applySubscriptions();
        if (onConnect) onConnect(frame);
      },
      function(error) {
        console.error("WebSocket Connection Error:", error);
        self.handleConnectionError(onError);
      },
    );
  }

  /**
   * Subscribe to a topic (queued if not connected; applied when connection is ready)
   */
  subscribe(destination, callback) {
    if (!destination || typeof callback !== "function") return;
    if (!this.subscriptionCallbacks[destination]) this.subscriptionCallbacks[destination] = [];
    this.subscriptionCallbacks[destination].push(callback);
    if (this.isConnected && this.stompClient && !this.subscriptions[destination]) {
      this._doSubscribe(destination);
    }
  }

  /**
   * Subscribe to user-specific messages (/user/queue/... or /user/topic/...)
   */
  subscribeToUser(destination, callback) {
    const userDestination = "/user" + (destination.startsWith("/") ? destination : "/" + destination);
    this.subscribe(userDestination, callback);
  }

  /**
   * Send message to server
   */
  sendMessage(destination, message) {
    if (!this.isConnected) {
      console.error("WebSocket not connected");
      return false;
    }

    this.stompClient.send(destination, {}, JSON.stringify(message));
    return true;
  }

  /**
   * Unsubscribe from a topic (removes subscription and callbacks)
   */
  unsubscribe(destination) {
    if (this.subscriptions[destination]) {
      this.subscriptions[destination].unsubscribe();
      delete this.subscriptions[destination];
    }
    delete this.subscriptionCallbacks[destination];
  }

  /**
   * Disconnect from server
   */
  disconnect() {
    if (this.stompClient && this.isConnected) {
      this.stompClient.disconnect(() => {
        console.log("WebSocket Disconnected");
        this.isConnected = false;
      });
    }
  }

  /**
   * Handle connection errors and attempt reconnect (re-subscribes automatically)
   */
  handleConnectionError(onError) {
    this.isConnected = false;
    this.subscriptions = {};

    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      const self = this;
      setTimeout(function() {
        self.connect(null, onError);
      }, this.reconnectDelay);
    } else {
      console.error("Max reconnection attempts reached");
      if (onError) onError("Connection failed after multiple attempts");
    }
  }

  /**
   * Subscribe to chat messages
   */
  subscribeToChat(callback) {
    this.subscribe("/topic/chat", callback);
  }

  /**
   * Subscribe to notifications
   */
  subscribeToNotifications(callback) {
    this.subscribeToUser("/topic/notifications", callback);
  }

  /**
   * Subscribe to order updates
   */
  subscribeToOrders(callback) {
    this.subscribe("/topic/orders", callback);
  }

  /**
   * Subscribe to product updates
   */
  subscribeToProducts(callback) {
    this.subscribe("/topic/products", callback);
  }

  /**
   * Subscribe to inventory updates
   */
  subscribeToInventory(callback) {
    this.subscribe("/topic/inventory", callback);
  }

  /**
   * Subscribe to new product feedback events (admin dashboard).
   */
  subscribeToFeedback(callback) {
    this.subscribe("/topic/feedback", callback);
  }

  /**
   * Send chat message
   */
  sendChatMessage(sender, content, conversationId) {
    const message = {
      sender: sender,
      content: content,
      conversationId: conversationId,
      timestamp: new Date().toISOString(),
    };
    return this.sendMessage("/app/chat/message", message);
  }

  /**
   * Send chat query to AI assistant
   */
  sendChatQuery(sender, content, conversationId) {
    const message = {
      sender: sender,
      content: content,
      conversationId: conversationId,
      timestamp: new Date().toISOString(),
    };
    return this.sendMessage("/app/chat/query", message);
  }

  /**
   * Send order update
   */
  sendOrderUpdate(entityId, action, data) {
    const update = {
      entityId: entityId,
      action: action,
      data: data,
      affectedUsers: "ALL",
    };
    return this.sendMessage("/app/orders/update", update);
  }

  /**
   * Send product update
   */
  sendProductUpdate(entityId, action, data) {
    const update = {
      entityId: entityId,
      action: action,
      data: data,
      affectedUsers: "ALL",
    };
    return this.sendMessage("/app/products/update", update);
  }

  /**
   * Send inventory update
   */
  sendInventoryUpdate(entityId, action, data) {
    const update = {
      entityId: entityId,
      action: action,
      data: data,
      affectedUsers: "ALL",
    };
    return this.sendMessage("/app/inventory/update", update);
  }

  /**
   * Get connection status
   */
  getStatus() {
    return {
      connected: this.isConnected,
      reconnectAttempts: this.reconnectAttempts,
      subscriptions: Object.keys(this.subscriptions),
    };
  }
};
})();

// Create global instance only if it doesn't exist
(function() {
  if (typeof window.webSocketClient !== 'undefined' && window.webSocketClient !== null) {
    return; // Already initialized
  }

  // Initialize on document ready
  function initializeWebSocket() {
    if (typeof window.WebSocketClient === 'undefined') {
      console.error('WebSocketClient class is not defined');
      return;
    }

    window.webSocketClient = new window.WebSocketClient();
    window.webSocketClient.connect(
      () => {
      },
      (error) => {
        console.error("WebSocket initialization failed:", error);
      },
    );
  }

  if (document.readyState === 'loading') {
    document.addEventListener("DOMContentLoaded", initializeWebSocket);
  } else {
    initializeWebSocket();
  }

  // Clean up on page unload
  window.addEventListener("beforeunload", () => {
    if (window.webSocketClient) {
      window.webSocketClient.disconnect();
    }
  });
})();
