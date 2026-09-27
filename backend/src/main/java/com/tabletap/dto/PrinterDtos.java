package com.tabletap.dto;

import com.tabletap.domain.Printer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class PrinterDtos {
    private PrinterDtos() {}

    public record PrinterRequest(@NotBlank @Size(max = 60) String name,
                                 @NotBlank @Size(max = 100) String host,
                                 @Min(9100) @Max(9199) int port,
                                 @Size(max = 30) String station,
                                 Boolean active, Boolean utf8) {}

    public record PrinterView(Long id, String name, String host, int port, String station, boolean active,
                              boolean utf8, Instant lastPrintedAt, String lastError) {
        public static PrinterView of(Printer p) {
            return new PrinterView(p.getId(), p.getName(), p.getHost(), p.getPort(), p.getStation(), p.isActive(),
                p.isUtf8(), p.getLastPrintedAt(), p.getLastError());
        }
    }
}
