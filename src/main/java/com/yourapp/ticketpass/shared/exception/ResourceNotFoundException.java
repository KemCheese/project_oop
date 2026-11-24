package com.yourapp.ticketpass.shared.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceName, Object id) {
        super("Khong tim thay " + resourceName + " voi id=" + id);
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
