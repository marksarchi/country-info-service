package com.example.countryinfoservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CountryRequest(@NotBlank(message = "name is required") @Size(max = 100) String name) {
}
