package io.voltweave.intelligence.optimization.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SimulatedTariffSchedule {
    private final BigDecimal offPeakPrice;
    private final BigDecimal peakPrice;
    private final int peakStartHourUtc;
    private final int peakEndHourUtc;

    public SimulatedTariffSchedule(
            @Value("${voltweave.tariff.off-peak-price:0.10}") BigDecimal offPeakPrice,
            @Value("${voltweave.tariff.peak-price:0.30}") BigDecimal peakPrice,
            @Value("${voltweave.tariff.peak-start-hour-utc:17}") int peakStartHourUtc,
            @Value("${voltweave.tariff.peak-end-hour-utc:21}") int peakEndHourUtc
    ) {
        if (offPeakPrice.signum() < 0 || peakPrice.signum() < 0) {
            throw new IllegalArgumentException("tariff prices cannot be negative");
        }
        if (peakStartHourUtc < 0 || peakEndHourUtc > 24
                || peakStartHourUtc >= peakEndHourUtc) {
            throw new IllegalArgumentException("tariff peak hours are invalid");
        }
        this.offPeakPrice = offPeakPrice;
        this.peakPrice = peakPrice;
        this.peakStartHourUtc = peakStartHourUtc;
        this.peakEndHourUtc = peakEndHourUtc;
    }

    public BigDecimal priceAt(Instant instant) {
        int hour = instant.atZone(ZoneOffset.UTC).getHour();
        return hour >= peakStartHourUtc && hour < peakEndHourUtc
                ? peakPrice : offPeakPrice;
    }
}
