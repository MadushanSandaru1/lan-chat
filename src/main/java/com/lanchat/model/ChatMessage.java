package com.lanchat.model;
public record ChatMessage(String messageId, String senderId, String receiverId, String content,
                          long timestamp, MessageStatus status) {}
