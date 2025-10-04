package org.example.controller.request;

public class MessageRequest {
    private String message;

    // Пустий конструктор необхідний для Spring
    public MessageRequest() {}

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
