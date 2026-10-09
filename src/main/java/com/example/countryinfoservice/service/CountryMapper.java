package com.example.countryinfoservice.service;

import com.example.countryinfoservice.dto.CountryResponse;
import com.example.countryinfoservice.dto.LanguageDto;
import com.example.countryinfoservice.model.CountryInfo;

final class CountryMapper {
    private CountryMapper() {}

    static CountryResponse toResponse(CountryInfo c) {
        return new CountryResponse(c.getId(), c.getIsoCode(), c.getName(), c.getCapitalCity(), c.getPhoneCode(),
                c.getContinentCode(), c.getCurrencyIsoCode(), c.getFlagUrl(),
                c.getLanguages().stream().map(l -> new LanguageDto(l.getIsoCode(), l.getName())).toList(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
