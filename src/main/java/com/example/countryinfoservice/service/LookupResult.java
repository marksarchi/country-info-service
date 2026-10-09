package com.example.countryinfoservice.service;

import com.example.countryinfoservice.dto.CountryResponse;

public record LookupResult(CountryResponse country, boolean created, boolean fromFallback) {
}
