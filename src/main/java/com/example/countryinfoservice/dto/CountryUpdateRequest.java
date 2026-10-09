package com.example.countryinfoservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CountryUpdateRequest(
        @NotBlank(message = "name is required") @Size(max = 255) String name,
        @Size(max = 255) String capitalCity,
        @Size(max = 255) String phoneCode,
        @Size(max = 255) String continentCode,
        @Size(max = 255) String currencyIsoCode,
        @Size(max = 500) String flagUrl,
        @Valid List<LanguageDto> languages) {
}
