package com.istory.storyengine.error;

public record ApiError(
        String errorId,
        String requestId,
        int status,
        String error,
        String message,
        String path,
        String timestamp
) {}