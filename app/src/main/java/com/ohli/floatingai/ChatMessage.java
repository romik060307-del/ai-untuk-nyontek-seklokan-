package com.ohli.floatingai;

final class ChatMessage {
    final String role;
    final String text;

    ChatMessage(String role, String text) {
        this.role = role;
        this.text = text;
    }
}
