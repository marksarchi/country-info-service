package com.example.countryinfoservice.client;

import com.example.countryinfoservice.dto.LanguageDto;
import java.util.List;

public record UpstreamCountry(String isoCode, String name, String capitalCity, String phoneCode,
                              String continentCode, String currencyIsoCode, String flagUrl,
                              List<LanguageDto> languages) {
}
