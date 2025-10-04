package org.example.controller.response;

public class MessageResponse {
    private final String reply;

    public MessageResponse(String reply) {
        this.reply = reply;
    }

    public String getReply() {
        return reply;
    }
}
