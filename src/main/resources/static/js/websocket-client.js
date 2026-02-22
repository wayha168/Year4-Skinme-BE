// Prevent duplicate declaration
(function() {
  if (typeof window.WebSocketClient !== 'undefined') {
    return; // Already defined
  }

  window.WebSocketClient = class WebSocketClient {
    constructor(endpoint = "/ws-endpoint") {
      this.endpoint = endpoint;
      this.stompClient = null;
      this.isConnected = false;
      this.subscriptions = {};
      this.messageHandlers = {};
      this.reconnectAttempts = 0;
      this.maxReconnectAttempts = 5;
      this.reconnectDelay = 3000;
    }

  /**
   * Initialize WebSocket connection
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

    this.stompClient.connect(
      {},
      (frame) => {
        console.log("WebSocket Connected: " + frame.command);
        this.isConnected = true;
        this.reconnectAttempts = 0;
        if (onConnect) onConnect(frame);
      },
      (error) => {
        console.error("WebSocket Connection Error:", error);
        this.handleConnectionError(onError);
      },
    );
  }

  /**
   * Subscribe to a topic
   */
  subscribe(destination, callback) {
    if (!this.isConnected) {
      console.warn("WebSocket not connected. Attempting to reconnect...");
      return;
    }

    const subscription = this.stompClient.subscribe(destination, (message) => {
      try {
        const data = JSON.parse(message.body);
        callback(data);
      } catch (e) {
        console.error("Error parsing message:", e);
        callback(message.body);
      }
    });

    this.subscriptions[destination] = subscription;
    console.log("Subscribed to: " + destination);
  }

  /**
   * Subscribe to user-specific messages
   */
  subscribeToUser(destination, callback) {
    if (!this.isConnected) {
      console.warn("WebSocket not connected.");
      return;
    }

    const userDestination = `/user${destination}`;
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
    console.log("Message sent to: " + destination);
    return true;
  }

  /**
   * Unsubscribe from a topic
   */
  unsubscribe(destination) {
    if (this.subscriptions[destination]) {
      this.subscriptions[destination].unsubscribe();
      delete this.subscriptions[destination];
      console.log("Unsubscribed from: " + destination);
    }
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
   * Handle connection errors and attempt reconnect
   */
  handleConnectionError(onError) {
    this.isConnected = false;

    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      console.log(`Reconnecting... Attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts}`);
      setTimeout(() => {
        this.connect(null, onError);
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
        console.log("WebSocket ready for use");
        // Subscribe to your required topics here
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
