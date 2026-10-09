package com.example.countryinfoservice.client;

import com.example.countryinfoservice.config.SoapProperties;
import com.example.countryinfoservice.dto.LanguageDto;
import com.example.countryinfoservice.exception.CountryNotFoundException;
import com.example.countryinfoservice.exception.ExternalServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.HtmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

@Slf4j
@Component
public class CountryInfoSoapClient {
    static final String NS = "http://www.oorsprong.org/websamples.countryinfo";

    private final RestClient restClient;

    public CountryInfoSoapClient(RestClient.Builder builder, SoapProperties props) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout((int) props.connectTimeout().toMillis());
        rf.setReadTimeout((int) props.readTimeout().toMillis());
        this.restClient = builder
                .baseUrl(props.url())
                .requestFactory(rf)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "text/xml; charset=utf-8")
                .defaultHeader("SOAPAction", "\"\"")
                .build();
    }

    @Cacheable(cacheNames = "isoCodes", key = "#countryName")
    @Retry(name = "countryInfo")
    @CircuitBreaker(name = "countryInfo")
    public String fetchIsoCode(String countryName) {
        long start = System.currentTimeMillis();
        String xml = post("CountryISOCode", "sCountryName", countryName);
        String code = text(parse(xml), "CountryISOCodeResult");
        log.info("SOAP CountryISOCode name={} result={} durationMs={}", countryName, code,
                System.currentTimeMillis() - start);
        if (code == null || code.isBlank() || code.toLowerCase().contains("not found")) {
            throw new CountryNotFoundException(countryName);
        }
        return code.trim();
    }

    @Cacheable(cacheNames = "countryInfo", key = "#isoCode")
    @Retry(name = "countryInfo")
    @CircuitBreaker(name = "countryInfo")
    public UpstreamCountry fetchFullCountryInfo(String isoCode) {
        long start = System.currentTimeMillis();
        String xml = post("FullCountryInfo", "sCountryISOCode", isoCode);
        UpstreamCountry info = parseFullCountryInfo(xml);
        log.info("SOAP FullCountryInfo iso={} durationMs={}", isoCode, System.currentTimeMillis() - start);
        if (info.isoCode() == null || info.isoCode().isBlank() || info.isoCode().toLowerCase().contains("not found")) {
            throw new CountryNotFoundException(isoCode);
        }
        return info;
    }

    private String post(String operation, String param, String value) {
        String envelope = """
                <?xml version="1.0" encoding="utf-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:web="%s">
                  <soap:Body><web:%s><web:%s>%s</web:%s></web:%s></soap:Body>
                </soap:Envelope>""".formatted(NS, operation, param, HtmlUtils.htmlEscape(value, "UTF-8"), param, operation);
        try {
            String body = restClient.post().body(envelope).retrieve().body(String.class);
            if (body == null || body.isBlank()) {
                throw new ExternalServiceException("Empty SOAP response for " + operation, null);
            }
            return body;
        } catch (RestClientException e) {
            throw new ExternalServiceException("SOAP call " + operation + " failed: " + e.getMessage(), e);
        }
    }

    static UpstreamCountry parseFullCountryInfo(String xml) {
        Document doc = parse(xml);
        NodeList results = doc.getElementsByTagNameNS("*", "FullCountryInfoResult");
        if (results.getLength() == 0) {
            throw new ExternalServiceException("FullCountryInfoResult missing in SOAP response", null);
        }
        Element root = (Element) results.item(0);
        List<LanguageDto> languages = new ArrayList<>();
        NodeList langs = root.getElementsByTagNameNS("*", "tLanguage");
        for (int i = 0; i < langs.getLength(); i++) {
            Element l = (Element) langs.item(i);
            languages.add(new LanguageDto(child(l, "sISOCode"), child(l, "sName")));
        }
        return new UpstreamCountry(child(root, "sISOCode"), child(root, "sName"), child(root, "sCapitalCity"),
                child(root, "sPhoneCode"), child(root, "sContinentCode"), child(root, "sCurrencyISOCode"),
                child(root, "sCountryFlag"), languages);
    }

    private static String child(Element parent, String localName) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && localName.equals(e.getLocalName())) {
                return e.getTextContent() == null ? null : e.getTextContent().trim();
            }
        }
        return null;
    }

    private static String text(Document doc, String localName) {
        NodeList nl = doc.getElementsByTagNameNS("*", localName);
        return nl.getLength() == 0 ? null : nl.item(0).getTextContent();
    }

    static Document parse(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new ExternalServiceException("Unparseable SOAP response", e);
        }
    }
}
