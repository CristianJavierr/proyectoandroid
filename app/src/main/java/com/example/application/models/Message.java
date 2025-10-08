package com.example.application.models;

import java.util.Date;

public class Message {
    private String messageId;
    private String text;
    private String senderId;
    private String senderName;
    private Date timestamp;
    private boolean read;
    private String type; // "text" o "image"
    private String imageUrl; // URL de la imagen si es tipo image

    public Message() {
        // Constructor vacío requerido para Firestore
    }

    public Message(String text, String senderId, String senderName, Date timestamp) {
        this.text = text;
        this.senderId = senderId;
        this.senderName = senderName;
        this.timestamp = timestamp;
        this.read = false;
        this.type = "text";
        this.imageUrl = null;
    }

    // Getters y Setters
    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
