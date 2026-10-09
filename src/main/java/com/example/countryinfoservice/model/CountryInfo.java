package com.example.countryinfoservice.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "country_info",
        uniqueConstraints = @UniqueConstraint(name = "uk_country_iso", columnNames = "iso_code"))
@Getter
@Setter
@NoArgsConstructor
public class CountryInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "iso_code", nullable = false, length = 10)
    private String isoCode;

    @Column(nullable = false)
    private String name;

    private String capitalCity;
    private String phoneCode;
    private String continentCode;
    private String currencyIsoCode;

    @Column(length = 500)
    private String flagUrl;

    @OneToMany(mappedBy = "country", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("isoCode ASC")
    private List<Language> languages = new ArrayList<>();

    @Column(updatable = false)
    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void replaceLanguages(List<Language> newLanguages) {
        languages.clear();
        for (Language l : newLanguages) {
            l.setCountry(this);
            languages.add(l);
        }
    }
}
