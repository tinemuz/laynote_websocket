package com.laynote.laynote_websocket.hander;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laynote.laynote_websocket.dto.WebSocketMessage;
import com.laynote.laynote_websocket.dto.NoteDto;
import com.laynote.laynote_websocket.model.Note;
import com.laynote.laynote_websocket.model.User;
import com.laynote.laynote_websocket.repository.NoteRepository;
import com.laynote.laynote_websocket.service.NoteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class NoteWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(NoteWebSocketHandler.class);
    private final NoteRepository noteRepository;
    private final NoteService noteService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<WebSocketSession>> sessionsByNote = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<WebSocketSession, String> noteBySession = new ConcurrentHashMap<>();

    @Autowired
    public NoteWebSocketHandler(NoteRepository noteRepository, NoteService noteService) {
        this.noteRepository = noteRepository;
        this.noteService = noteService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("Connection established with session ID: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            WebSocketMessage wsMessage = objectMapper.readValue(payload, WebSocketMessage.class);
            String action = wsMessage.getAction();

            if (action == null) {
                logger.warn("Received message with missing action. Session ID: {}", session.getId());
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
                    logger.warn("Unknown action '{}' received. Session ID: {}", action, session.getId());
            }
        } catch (JsonProcessingException e) {
            logger.error("Error parsing JSON from session {}. Payload: {} | Error: {}", session.getId(), message.getPayload(), e.getMessage());
        } catch (Exception e) {
            logger.error("An unexpected error occurred for session {}: {}", session.getId(), e.getMessage(), e);
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
        logger.info("Created new note with ID: {}", savedNote.getId());

        WebSocketMessage response = new WebSocketMessage();
        response.setAction("NOTE_CREATED");
        response.setNoteId(String.valueOf(savedNote.getId()));

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    private void handleLoadNote(WebSocketSession session, String noteId) {
        if (noteId == null) return;
        sessionsByNote.computeIfAbsent(noteId, k -> new CopyOnWriteArrayList<>()).add(session);
        noteBySession.put(session, noteId);
        logger.info("Session ID {} subscribed to note ID: {}", session.getId(), noteId);

        try {
            NoteDto noteDto = noteService.findNoteById(noteId);
            if (noteDto != null) {
                String notePayload = objectMapper.writeValueAsString(noteDto);
                session.sendMessage(new TextMessage(notePayload));
            }
        } catch (IOException e) {
            logger.error("Error sending note data to session {}: {}", session.getId(), e.getMessage());
        }
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

        logger.info("Broadcasting to {} sessions for note ID: {}", sessions.size(), noteId);
        for (WebSocketSession webSocketSession : sessions) {
            try {
                if (webSocketSession.isOpen()) {
                    synchronized (webSocketSession) {
                        webSocketSession.sendMessage(new TextMessage(message));
                    }
                }
            } catch (IOException e) {
                logger.error("Error broadcasting to session {}: {}", webSocketSession.getId(), e.getMessage());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        logger.info("Connection closed with session ID: {} | Status: {}", session.getId(), status.getCode());
        String noteId = noteBySession.remove(session);
        if (noteId != null) {
            CopyOnWriteArrayList<WebSocketSession> sessions = sessionsByNote.get(noteId);
            if (sessions != null) {
                sessions.remove(session);
                logger.info("Session removed from note ID: {}", noteId);
            }
        }
    }
}