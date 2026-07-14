package com.ezcloudstore.domain.model;

public class FileTooLargeException extends DomainException {

    public FileTooLargeException(String message) {
        super(message);
    }
}
