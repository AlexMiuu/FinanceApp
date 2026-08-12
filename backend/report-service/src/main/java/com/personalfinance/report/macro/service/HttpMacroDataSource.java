package com.personalfinance.report.macro.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.personalfinance.report.macro.entity.MacroReadingKind;

/**
 * Best-effort live ingestion. INS (CPI) and ANRE (energy tariffs) do not
 * publish a single stable, versioned public JSON endpoint the way most
 * commercial data providers do — INS's is a SDMX/tabular data portal (Tempo
 * Online) and ANRE publishes tariff orders as documents, not an API. The URLs
 * below are configuration, not a verified contract: this class has not been
 * exercised against live INS/ANRE traffic in this environment, and should be
 * treated as a starting point a real integration pass would replace or
 * confirm — not as evidence the wire format is correct.
 *
 * <p>This is exactly the shape R4 (docs/roadmap.md) accepts: every failure
 * mode below — timeout, non-2xx, unparseable body — degrades to
 * {@code Optional.empty()}, which {@link MacroService} turns into "keep
 * serving the cache," never a thrown exception or a blocked read.
 */
@Component
public class HttpMacroDataSource implements MacroDataSource {

    private static final Logger log = LoggerFactory.getLogger(HttpMacroDataSource.class);

    private final RestClient insClient;
    private final RestClient anreClient;

    public HttpMacroDataSource(
            @Value("${macro.ins.cpi-url}") String insCpiUrl,
            @Value("${macro.anre.tariff-url}") String anreTariffUrl,
            @Value("${macro.http.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${macro.http.read-timeout-ms:3000}") int readTimeoutMs) {
        ClientHttpRequestFactory requestFactory = timeoutRequestFactory(connectTimeoutMs, readTimeoutMs);
        this.insClient = RestClient.builder().baseUrl(insCpiUrl).requestFactory(requestFactory).build();
        this.anreClient = RestClient.builder().baseUrl(anreTariffUrl).requestFactory(requestFactory).build();
    }

    private static ClientHttpRequestFactory timeoutRequestFactory(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return factory;
    }

    @Override
    public Optional<Reading> fetch(MacroReadingKind kind) {
        try {
            MacroReadingPayload payload = clientFor(kind).get().retrieve().body(MacroReadingPayload.class);
            if (payload == null || payload.value() == null || payload.asOfDate() == null) {
                log.warn("Empty or malformed {} response from {}", kind, kind.source());
                return Optional.empty();
            }
            return Optional.of(new Reading(payload.value(), payload.asOfDate()));
        } catch (Exception e) {
            log.warn("Failed to fetch {} from {}", kind, kind.source(), e);
            return Optional.empty();
        }
    }

    private RestClient clientFor(MacroReadingKind kind) {
        return switch (kind) {
            case CPI -> insClient;
            case ENERGY_TARIFF -> anreClient;
        };
    }

    private record MacroReadingPayload(BigDecimal value, LocalDate asOfDate) {
    }
}
