package com.example.countryinfoservice.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(Long id) {
        super("Country info with id " + id + " not found");
    }
}
