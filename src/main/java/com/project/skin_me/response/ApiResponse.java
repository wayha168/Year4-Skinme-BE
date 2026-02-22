package com.project.skin_me.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ApiResponse {

    private String message;
    private Object data;

    @JsonIgnore
    private Object[] messageArgs;

    /** Plain message (no translation). */
    public ApiResponse(String message, Object data) {
        this.message = message;
        this.data = data;
        this.messageArgs = null;
    }

    public ApiResponse(String message, Object data, Object[] messageArgs) {
        this.message = message;
        this.data = data;
        this.messageArgs = messageArgs;
    }

    /** Use this when message is a message key (e.g. "api.auth.login.success") - ResponseBodyAdvice will translate. */
    public static ApiResponse ofKey(String messageKey, Object data) {
        ApiResponse r = new ApiResponse(messageKey, data, null);
        return r;
    }

    /** Use this for parameterized keys (e.g. api.error.generic with {0}). ResponseBodyAdvice will translate. */
    public static ApiResponse ofKey(String messageKey, Object[] args, Object data) {
        ApiResponse r = new ApiResponse(messageKey, data, args);
        return r;
    }
}
