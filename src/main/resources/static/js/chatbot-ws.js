(function (global) {
  function ChatbotWebSocket(options) {
    this.sessionId = options.sessionId;
    this.wsUrl = options.wsUrl;
    this.onMessage = options.onMessage || function () {};
    this.onPresence = options.onPresence || function () {};
    this.onOpen = options.onOpen || function () {};
    this.onClose = options.onClose || function () {};
    this.onError = options.onError || function () {};
    this.onExhausted = options.onExhausted || function () {};
    this.ws = null;
    this.reconnectTimer = null;
    this.closedByUser = false;
    // Reconnection policy
    this.maxRetries = typeof options.maxRetries === "number" ? options.maxRetries : 5;
    this.retryBackoffMs = typeof options.retryBackoffMs === "number" ? options.retryBackoffMs : 4000;
    this.retryCount = 0;
    this.exhausted = false;
  }

  ChatbotWebSocket.prototype.connect = function () {
    var self = this;
    if (!self.wsUrl || !self.sessionId) {
      return;
    }
    self.closedByUser = false;
    if (self.ws) {
      try {
        self.ws.close();
      } catch (e) {
        /* ignore */
      }
    }
    var connectUrl = self.wsUrl;
    if (!connectUrl.startsWith("ws://") && !connectUrl.startsWith("wss://")) {
      connectUrl = "wss://" + connectUrl;
    }
    try {
      self.ws = new WebSocket(connectUrl);
    } catch (e) {
      if (
        connectUrl.startsWith("wss://") &&
        typeof window !== "undefined" &&
        window.location.protocol === "http:"
      ) {
        var fallbackUrl = "ws://" + connectUrl.substring("wss://".length);
        try {
          self.ws = new WebSocket(fallbackUrl);
          self.wsUrl = fallbackUrl;
        } catch (fallbackError) {
          self.onError(fallbackError);
          return;
        }
      } else {
        self.onError(e);
        return;
      }
    }
    self.ws.onopen = function () {
      // Reset retry policy on successful open
      self.retryCount = 0;
      self.exhausted = false;
      self.onOpen();
    };
    self.ws.onmessage = function (event) {
      self.handlePayload(event.data);
    };
    self.ws.onerror = function (err) {
      self.onError(err);
    };
    self.ws.onclose = function () {
      self.onClose();
      if (!self.closedByUser) {
        self.scheduleReconnect();
      }
    };
  };

  ChatbotWebSocket.prototype.scheduleReconnect = function () {
    var self = this;
    if (self.reconnectTimer) {
      clearTimeout(self.reconnectTimer);
    }
    // If we've exhausted retries, notify and stop attempting
    if (self.maxRetries > 0 && self.retryCount >= self.maxRetries) {
      self.exhausted = true;
      try {
        if (typeof self.onExhausted === "function") self.onExhausted();
      } catch (e) {
        console.error("onExhausted handler failed", e);
      }
      return;
    }

    // Increment retry counter and schedule with exponential backoff
    self.retryCount = Math.max(0, self.retryCount) + 1;
    var backoff = Math.min(60000, self.retryBackoffMs * Math.pow(2, self.retryCount - 1));
    self.reconnectTimer = setTimeout(function () {
      self.connect();
    }, backoff);
  };

  ChatbotWebSocket.prototype.disconnect = function () {
    this.closedByUser = true;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  };

  ChatbotWebSocket.prototype.changeSession = function (sessionId, wsUrl) {
    this.sessionId = sessionId;
    this.wsUrl = wsUrl;
    this.disconnect();
    this.closedByUser = false;
    if (sessionId && wsUrl) {
      this.connect();
    }
  };

  ChatbotWebSocket.prototype.handlePayload = function (raw) {
    var data;
    try {
      data = typeof raw === "string" ? JSON.parse(raw) : raw;
    } catch (e) {
      data = { type: "message", content: String(raw), role: "assistant" };
    }
    var type = (data.type || data.event || "").toLowerCase();
    if (
      type === "presence" ||
      type === "user_online" ||
      type === "user_offline" ||
      data.online !== undefined
    ) {
      this.onPresence({
        online: data.online !== undefined ? !!data.online : type === "user_online",
        sessionId: data.session_id || data.sessionId || this.sessionId,
        userId: data.user_id || data.userId,
        userEmail: data.user_email || data.userEmail,
      });
      return;
    }
    this.onMessage(normalizeMessage(data));
  };

  function normalizeMessage(data) {
    var role = data.role || data.type || "assistant";
    if (role === "human") role = "user";
    return {
      role: role,
      type: role,
      content: data.content || data.message || data.text || "",
      sender: data.sender || data.user_email || data.user_name || role,
      timestamp: data.created_at || data.timestamp || new Date().toISOString(),
      isAiResponse: data.is_ai_response === true || role === "assistant",
    };
  }

  global.ChatbotWebSocket = ChatbotWebSocket;
})(typeof window !== "undefined" ? window : this);
