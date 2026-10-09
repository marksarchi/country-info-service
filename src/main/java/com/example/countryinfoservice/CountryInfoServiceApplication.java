package com.example.countryinfoservice;

import com.example.countryinfoservice.config.SoapProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SoapProperties.class)
public class CountryInfoServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CountryInfoServiceApplication.class, args);
    }

}
