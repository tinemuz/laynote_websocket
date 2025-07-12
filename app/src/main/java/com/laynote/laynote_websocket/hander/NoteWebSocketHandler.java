package com.laynote.laynote_websocket.hander;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laynote.laynote_websocket.dto.WebSocketMessage;
import com.laynote.laynote_websocket.model.Note;
import com.laynote.laynote_websocket.model.User;
import com.laynote.laynote_websocket.repository.NoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class NoteWebSocketHandler extends TextWebSocketHandler {

    private final NoteRepository noteRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<WebSocketSession>> sessionsByNote = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<WebSocketSession, String> noteBySession = new ConcurrentHashMap<>();

    @Autowired
    public NoteWebSocketHandler(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("Server: Connection established with session ID: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            WebSocketMessage wsMessage = objectMapper.readValue(payload, WebSocketMessage.class);
            String action = wsMessage.getAction();

            if (action == null) {
                System.err.println("Server: Received message with missing action. Session ID: " + session.getId());
                return;
            }

            switch (action) {
                case "CREATE_NOTE":
                    handleCreateNote(session, wsMessage);
                    break;
                case "LOAD_NOTE":
                    handleLoadNote(session, wsMessage.getNoteId());
                    break;
                case "UPDATE_CONTENT":
                    handleUpdateContent(wsMessage.getNoteId(), wsMessage.getContent(), payload);
                    break;
                case "UPDATE_TITLE":
                    handleUpdateTitle(wsMessage.getNoteId(), wsMessage.getTitle(), payload);
                    break;
                default:
                    System.err.println("Server: Unknown action '" + action + "' received. Session ID: " + session.getId());
            }
        } catch (JsonProcessingException e) {
            System.err.println("Server: Error parsing JSON from session " + session.getId() + ". Payload: " + message.getPayload() + " | Error: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Server: An unexpected error occurred for session " + session.getId() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleCreateNote(WebSocketSession session, WebSocketMessage wsMessage) throws IOException {
        User user = new User();
        user.setId(wsMessage.getUserId());

        Note newNote = new Note();
        newNote.setUser(user);
        newNote.setTitle(wsMessage.getTitle() != null ? wsMessage.getTitle() : "Untitled Note");
        newNote.setContent("");

        Note savedNote = noteRepository.save(newNote);
        System.out.println("Server: Created new note with ID: " + savedNote.getId());

        WebSocketMessage response = new WebSocketMessage();
        response.setAction("NOTE_CREATED");
        response.setNoteId(String.valueOf(savedNote.getId()));

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    private void handleLoadNote(WebSocketSession session, String noteId) {
        if (noteId == null) return;
        sessionsByNote.computeIfAbsent(noteId, k -> new CopyOnWriteArrayList<>()).add(session);
        noteBySession.put(session, noteId);
        System.out.println("Server: Session ID " + session.getId() + " subscribed to note ID: " + noteId);

        noteRepository.findById(noteId).ifPresent(note -> {
            try {
                String notePayload = objectMapper.writeValueAsString(note);
                session.sendMessage(new TextMessage(notePayload));
            } catch (IOException e) {
                System.err.println("Server: Error sending note data to session " + session.getId() + ": " + e.getMessage());
            }
        });
    }

    private void handleUpdateContent(String noteId, String content, String originalPayload) {
        if (noteId == null) return;
        noteRepository.findById(noteId).ifPresent(note -> {
            note.setContent(content);
            noteRepository.save(note);
            broadcast(noteId, originalPayload);
        });
    }

    private void handleUpdateTitle(String noteId, String title, String originalPayload) {
        if (noteId == null) return;
        noteRepository.findById(noteId).ifPresent(note -> {
            note.setTitle(title);
            noteRepository.save(note);
            broadcast(noteId, originalPayload);
        });
    }

    private void broadcast(String noteId, String message) {
        CopyOnWriteArrayList<WebSocketSession> sessions = sessionsByNote.get(noteId);
        if (sessions == null) return;

        System.out.println("Server: Broadcasting to " + sessions.size() + " sessions for note ID: " + noteId);
        for (WebSocketSession webSocketSession : sessions) {
            try {
                if (webSocketSession.isOpen()) {
                    synchronized (webSocketSession) {
                        webSocketSession.sendMessage(new TextMessage(message));
                    }
                }
            } catch (IOException e) {
                System.err.println("Server: Error broadcasting to session " + webSocketSession.getId() + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        System.out.println("Server: Connection closed with session ID: " + session.getId() + " | Status: " + status.getCode());
        String noteId = noteBySession.remove(session);
        if (noteId != null) {
            CopyOnWriteArrayList<WebSocketSession> sessions = sessionsByNote.get(noteId);
            if (sessions != null) {
                sessions.remove(session);
                System.out.println("Server: Session removed from note ID: " + noteId);
            }
        }
    }
}