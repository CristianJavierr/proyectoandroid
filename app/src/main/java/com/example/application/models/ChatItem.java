package com.example.application.models;

import java.util.Date;

public class ChatItem {
    public static final int TYPE_MESSAGE = 0;
    public static final int TYPE_DATE_SEPARATOR = 1;

    private int type;
    private Message message;
    private String dateText;
    private Date date;

    // Constructor para mensajes
    public ChatItem(Message message) {
        this.type = TYPE_MESSAGE;
        this.message = message;
    }

    // Constructor para separadores de fecha
    public ChatItem(String dateText, Date date) {
        this.type = TYPE_DATE_SEPARATOR;
        this.dateText = dateText;
        this.date = date;
    }

    public int getType() {
        return type;
    }

    public Message getMessage() {
        return message;
    }

    public String getDateText() {
        return dateText;
    }

    public Date getDate() {
        return date;
    }
}
