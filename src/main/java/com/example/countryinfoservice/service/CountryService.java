package com.example.countryinfoservice.service;

import com.example.countryinfoservice.client.CountryInfoSoapClient;
import com.example.countryinfoservice.client.UpstreamCountry;
import com.example.countryinfoservice.dto.CountryResponse;
import com.example.countryinfoservice.dto.CountryUpdateRequest;
import com.example.countryinfoservice.dto.LanguageDto;
import com.example.countryinfoservice.exception.ExternalServiceException;
import com.example.countryinfoservice.exception.ResourceNotFoundException;
import com.example.countryinfoservice.model.CountryInfo;
import com.example.countryinfoservice.model.Language;
import com.example.countryinfoservice.repository.CountryInfoRepository;
import com.example.countryinfoservice.util.TextUtils;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class CountryService {
    private final CountryInfoSoapClient soapClient;
    private final CountryInfoRepository repository;
    private final TransactionTemplate tx;

    public CountryService(CountryInfoSoapClient soapClient, CountryInfoRepository repository,
                          PlatformTransactionManager txManager) {
        this.soapClient = soapClient;
        this.repository = repository;
        this.tx = new TransactionTemplate(txManager);
    }

    public LookupResult lookupAndStore(String rawName) {
        String name = TextUtils.toSentenceCase(rawName);
        log.info("Country lookup requested name={}", name);

        UpstreamCountry upstream;
        try {
            String iso = soapClient.fetchIsoCode(name);
            upstream = soapClient.fetchFullCountryInfo(iso);
        } catch (ExternalServiceException | CallNotPermittedException e) {
            Optional<CountryResponse> stored = tx.execute(s ->
                    repository.findByNameIgnoreCase(name).map(CountryMapper::toResponse));
            if (stored != null && stored.isPresent()) {
                log.warn("Upstream unavailable, serving stored data for name={} cause={}", name, e.toString());
                return new LookupResult(stored.get(), false, true);
            }
            throw e;
        }

        LookupResult result = tx.execute(s -> {
            CountryInfo entity = repository.findByIsoCode(upstream.isoCode()).orElse(null);
            boolean created = entity == null;
            if (created) entity = new CountryInfo();
            entity.setIsoCode(upstream.isoCode());
            entity.setName(upstream.name());
            entity.setCapitalCity(upstream.capitalCity());
            entity.setPhoneCode(upstream.phoneCode());
            entity.setContinentCode(upstream.continentCode());
            entity.setCurrencyIsoCode(upstream.currencyIsoCode());
            entity.setFlagUrl(upstream.flagUrl());
            entity.replaceLanguages(toLanguages(upstream.languages()));
            CountryInfo saved = repository.save(entity);
            return new LookupResult(CountryMapper.toResponse(saved), created, false);
        });
        log.info("Country stored iso={} created={}", upstream.isoCode(), result.created());
        return result;
    }

    @Transactional(readOnly = true)
    public List<CountryResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(CountryMapper::toResponse).getContent();
    }

    @Transactional(readOnly = true)
    public CountryResponse findById(Long id) {
        return repository.findById(id).map(CountryMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(id));
    }

    @Transactional
    public CountryResponse update(Long id, CountryUpdateRequest req) {
        CountryInfo c = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException(id));
        c.setName(req.name().trim());
        c.setCapitalCity(req.capitalCity());
        c.setPhoneCode(req.phoneCode());
        c.setContinentCode(req.continentCode());
        c.setCurrencyIsoCode(req.currencyIsoCode());
        c.setFlagUrl(req.flagUrl());
        if (req.languages() != null) {
            c.replaceLanguages(toLanguages(req.languages()));
        }
        log.info("Country updated id={}", id);
        return CountryMapper.toResponse(repository.save(c));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new ResourceNotFoundException(id);
        repository.deleteById(id);
        log.info("Country deleted id={}", id);
    }

    private static List<Language> toLanguages(List<LanguageDto> dtos) {
        return dtos.stream().map(d -> new Language(d.isoCode(), d.name())).toList();
    }
}
