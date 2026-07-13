package com.personalfinance.report.report;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.personalfinance.report.domain.ExpenseProjectionEntity;
import com.personalfinance.report.domain.ReportEntity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    public record ReportDto(UUID id, String name, Map<String, Object> filters, Instant lastRunAt,
            Map<String, Object> cachedResult) {

        static ReportDto of(ReportEntity r) {
            return new ReportDto(r.getId(), r.getName(), r.getFilters(), r.getLastRunAt(), r.getCachedResult());
        }
    }

    public record SaveRequest(@NotBlank @Size(max = 100) String name, @NotNull Map<String, Object> filters) {
    }

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReportDto> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(userId(jwt)).stream().map(ReportDto::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportDto create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SaveRequest request) {
        return ReportDto.of(service.create(userId(jwt), request.name(), request.filters()));
    }

    @GetMapping("/{id}")
    public ReportDto get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ReportDto.of(service.get(id, userId(jwt)));
    }

    @PutMapping("/{id}")
    public ReportDto update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody SaveRequest request) {
        return ReportDto.of(service.update(id, userId(jwt), request.name(), request.filters()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(id, userId(jwt));
    }

    @PostMapping("/{id}/run")
    public Map<String, Object> run(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.run(id, userId(jwt));
    }

    /** CSV of the rows behind the report — the explicit export required by FR-4. */
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        ReportEntity report = service.get(id, userId(jwt));
        List<ExpenseProjectionEntity> rows = service.rowsFor(id, userId(jwt));

        StringBuilder csv = new StringBuilder("Date,Category,Note,Mandatory,Amount (RON)\r\n");
        for (ExpenseProjectionEntity row : rows) {
            csv.append(row.getExpenseDate()).append(',')
                    .append(escape(row.getCategoryPath())).append(',')
                    .append(escape(row.getNote())).append(',')
                    .append(row.isMandatory() ? "yes" : "no").append(',')
                    .append(String.format(java.util.Locale.ROOT, "%d.%02d", row.getAmount() / 100,
                            row.getAmount() % 100))
                    .append("\r\n");
        }

        String filename = report.getName().replaceAll("[^\\p{L}\\p{N} _-]", "").trim();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + (filename.isEmpty() ? "report" : filename) + ".csv\"")
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
