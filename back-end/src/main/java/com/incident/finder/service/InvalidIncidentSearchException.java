package com.incident.finder.service;

public class InvalidIncidentSearchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidIncidentSearchException(String message) {
        super(message);
    }
}
