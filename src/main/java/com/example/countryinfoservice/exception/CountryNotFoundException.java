package com.example.countryinfoservice.exception;

public class CountryNotFoundException extends RuntimeException {
    public CountryNotFoundException(String what) {
        super("Country not found: " + what);
    }
}
