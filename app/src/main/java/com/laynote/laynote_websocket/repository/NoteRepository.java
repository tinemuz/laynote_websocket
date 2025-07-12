package com.laynote.laynote_websocket.repository;

import com.laynote.laynote_websocket.model.Note;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteRepository extends JpaRepository<Note, String> {
}