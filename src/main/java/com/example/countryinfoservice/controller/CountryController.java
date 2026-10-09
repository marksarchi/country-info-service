package com.example.countryinfoservice.controller;

import com.example.countryinfoservice.dto.CountryRequest;
import com.example.countryinfoservice.dto.CountryResponse;
import com.example.countryinfoservice.dto.CountryUpdateRequest;
import com.example.countryinfoservice.service.CountryService;
import com.example.countryinfoservice.service.LookupResult;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/countries")
public class CountryController {
    private final CountryService service;

    public CountryController(CountryService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CountryResponse> lookup(@Valid @RequestBody CountryRequest request) {
        LookupResult r = service.lookupAndStore(request.name());
        ResponseEntity.BodyBuilder b = r.created()
                ? ResponseEntity.created(URI.create("/api/v1/countries/" + r.country().id()))
                : ResponseEntity.ok();
        if (r.fromFallback()) b.header("X-Data-Source", "database-fallback");
        return b.body(r.country());
    }

    @GetMapping
    public List<CountryResponse> all(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return service.findAll(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("name")));
    }

    @GetMapping("/{id}")
    public CountryResponse byId(@PathVariable Long id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    public CountryResponse update(@PathVariable Long id, @Valid @RequestBody CountryUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
