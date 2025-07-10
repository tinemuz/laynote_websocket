package com.laynote.laynote_websocket.hander;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

public class NoteWebSocketHandler extends TextWebSocketHandler {


    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("Server: Connection established with session ID: " + session.getId());
        sessions.add(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println("Server: Received message: [" + payload + "] from session ID: " + session.getId());

        for (WebSocketSession webSocketSession : sessions) {
            if (webSocketSession.isOpen() && !session.getId().equals(webSocketSession.getId())) {
                try {
                    System.out.println("Server: Broadcasting message to session ID: " + webSocketSession.getId());
                    synchronized (webSocketSession) {
                        webSocketSession.sendMessage(new TextMessage(payload));
                    }
                } catch (IOException e) {
                    System.err.println("Error sending message to session " + webSocketSession.getId() + ": " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        System.out.println("Server: Connection closed with session ID: " + session.getId() + " | Status: " + status.getCode());
        sessions.remove(session);
    }
}
