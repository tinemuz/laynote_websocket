package com.laynote.laynote_websocket.dto;

public class UserDto {
    private String id;

    public UserDto() {}

    public UserDto(String id) {
        this.id = id;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
} 