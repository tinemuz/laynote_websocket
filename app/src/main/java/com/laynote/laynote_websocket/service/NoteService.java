package com.laynote.laynote_websocket.service;

import com.laynote.laynote_websocket.dto.NoteDto;
import com.laynote.laynote_websocket.dto.UserDto;
import com.laynote.laynote_websocket.model.Note;
import com.laynote.laynote_websocket.model.User;
import com.laynote.laynote_websocket.repository.NoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoteService {
    private final NoteRepository noteRepository;

    @Autowired
    public NoteService(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    @Transactional(readOnly = true)
    public NoteDto findNoteById(String noteId) {
        return noteRepository.findById(noteId)
                .map(this::toDto)
                .orElse(null);
    }

    private NoteDto toDto(Note note) {
        User user = note.getUser();
        UserDto userDto = user != null ? new UserDto(user.getId() != null ? user.getId().toString() : null) : null;
        return new NoteDto(
                note.getId() != null ? note.getId().toString() : null,
                note.getTitle(),
                note.getContent(),
                userDto
        );
    }
} 