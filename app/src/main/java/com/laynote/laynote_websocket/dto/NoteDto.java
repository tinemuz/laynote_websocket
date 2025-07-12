package com.laynote.laynote_websocket.dto;

public class NoteDto {
    private String id;
    private String title;
    private String content;
    private UserDto user;

    public NoteDto() {}

    public NoteDto(String id, String title, String content, UserDto user) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.user = user;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public UserDto getUser() { return user; }
    public void setUser(UserDto user) { this.user = user; }
} 