package com.carbonlink.service;

import com.carbonlink.dto.DistanceResult;
import com.carbonlink.entity.DistanceSource;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DistanceService {

    private static final Logger log = LoggerFactory.getLogger(DistanceService.class);

    // Straight-line distance scaled up to approximate an actual road route. Calibrated against
    // ten real Indian highway routes (Mumbai-Pune 150 km, Mumbai-Bangalore 980, Delhi-Kolkata
    // 1500, Mumbai-Delhi 1420, Chennai-Bangalore 350, ...): the true road/great-circle ratio
    // runs 1.14-1.25, median 1.21. The earlier 1.3 overshot every one of them by ~9% - it put
    // Mumbai-Bangalore at 1099 km against a real 980 - which inflated transport cost on exactly
    // the long hauls where transport cost dominates the total.
    static final double HAVERSINE_ROAD_FACTOR = 1.2;
    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final int CACHE_KEY_DECIMAL_PLACES = 4;

    // City-level registration means a buyer and emitter in the same city resolve to the exact
    // same lat/lng (one fixed coordinate per city, not a real street address), so the raw
    // distance comes out to exactly 0. A truck still costs something for local intra-city
    // haulage, so floor at a standard minimum instead of pricing that leg as free.
    static final double MIN_DISTANCE_KM = 10.0;

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String apiUrl;
    private final long failureCooldownMillis;

    private final Map<String, DistanceResult> cache = new ConcurrentHashMap<>();

    // Circuit breaker. "Find Matches" resolves one distance per candidate listing, so with an
    // unreachable OpenRouteService every uncached pair would burn the full connect+read timeout
    // before falling back - 4 distinct emitter cities x 3s = a 12-second frozen button on the
    // very first click. After one failure, every other lookup goes straight to Haversine for a
    // cooldown window, so a network problem costs one timeout in total rather than one per pair.
    private volatile long skipRemoteUntilMillis = 0L;

    public DistanceService(RestTemplate restTemplate,
                            @Value("${openrouteservice.api.key}") String apiKey,
                            @Value("${openrouteservice.api.url}") String apiUrl,
                            @Value("${openrouteservice.failure-cooldown-seconds:60}") long failureCooldownSeconds) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.failureCooldownMillis = failureCooldownSeconds * 1000L;
    }

    @PostConstruct
    void warnIfApiKeyMissing() {
        if (!hasApiKey()) {
            log.warn("OpenRouteService API key not set — DistanceService will use Haversine fallback for all requests");
        }
    }

    public DistanceResult getDistance(double lat1, double lng1, double lat2, double lng2) {
        String cacheKey = buildCacheKey(lat1, lng1, lat2, lng2);
        DistanceResult cached = cache.get(cacheKey);
        if (cached != null) {
            log.debug("Distance cache HIT for {}: {} km via {}", cacheKey, cached.distanceKm(), cached.source());
            return cached;
        }
        log.debug("Distance cache MISS for {} — resolving", cacheKey);

        DistanceResult result = shouldTryRemote()
                ? fetchFromOpenRouteService(lat1, lng1, lat2, lng2)
                        .orElseGet(() -> haversineFallback(lat1, lng1, lat2, lng2))
                : haversineFallback(lat1, lng1, lat2, lng2);

        DistanceResult floored = result.distanceKm() < MIN_DISTANCE_KM
                ? new DistanceResult(MIN_DISTANCE_KM, result.source())
                : result;

        // The fallback result is cached just like a real one, deliberately: a pair that resolved
        // via Haversine keeps that same number for the rest of the run instead of silently
        // changing distance (and therefore cost) mid-demo if the API comes back.
        cache.put(cacheKey, floored);
        return floored;
    }

    private boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    // With no key configured the call can only ever 403, so skip the round trip entirely rather
    // than paying network latency to be told what we already know.
    private boolean shouldTryRemote() {
        if (!hasApiKey()) {
            return false;
        }
        return System.currentTimeMillis() >= skipRemoteUntilMillis;
    }

    private void openCircuit() {
        skipRemoteUntilMillis = System.currentTimeMillis() + failureCooldownMillis;
        log.warn("OpenRouteService marked unavailable for the next {}s — using Haversine for all lookups until then",
                failureCooldownMillis / 1000);
    }

    private Optional<DistanceResult> fetchFromOpenRouteService(double lat1, double lng1, double lat2, double lng2) {
        try {
            Map<String, Object> body = Map.of(
                    "locations", List.of(List.of(lng1, lat1), List.of(lng2, lat2)),
                    "metrics", List.of("distance")
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<OrsMatrixResponse> response =
                    restTemplate.postForEntity(apiUrl, entity, OrsMatrixResponse.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("OpenRouteService returned non-success status: {}", response.getStatusCode());
                openCircuit();
                return Optional.empty();
            }

            double distanceMeters = response.getBody().distances().get(0).get(1);
            double distanceKm = distanceMeters / 1000.0;

            log.info("Distance resolved via OPENROUTESERVICE: {} km (({}, {}) -> ({}, {}))",
                    String.format(Locale.ROOT, "%.2f", distanceKm), lat1, lng1, lat2, lng2);

            return Optional.of(new DistanceResult(distanceKm, DistanceSource.OPENROUTESERVICE));
        } catch (Exception e) {
            // Deliberately broad: a timeout, a DNS failure, a rate-limit body we can't parse and
            // a malformed distances matrix all mean the same thing here - fall back, never fail
            // the user's request.
            log.warn("OpenRouteService call failed, falling back to Haversine: {}", e.getMessage());
            openCircuit();
            return Optional.empty();
        }
    }

    private DistanceResult haversineFallback(double lat1, double lng1, double lat2, double lng2) {
        double straightLineKm = haversine(lat1, lng1, lat2, lng2);
        double roadDistanceKm = straightLineKm * HAVERSINE_ROAD_FACTOR;

        log.info("Distance resolved via HAVERSINE_FALLBACK: {} km (({}, {}) -> ({}, {}))",
                String.format(Locale.ROOT, "%.2f", roadDistanceKm), lat1, lng1, lat2, lng2);

        return new DistanceResult(roadDistanceKm, DistanceSource.HAVERSINE_FALLBACK);
    }

    double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    private String buildCacheKey(double lat1, double lng1, double lat2, double lng2) {
        return round(lat1) + "," + round(lng1) + ":" + round(lat2) + "," + round(lng2);
    }

    private double round(double value) {
        double scale = Math.pow(10, CACHE_KEY_DECIMAL_PLACES);
        return Math.round(value * scale) / scale;
    }

    // Only the fields of the ORS Matrix API response we actually use.
    // Package-private (not private) so tests can construct one directly.
    record OrsMatrixResponse(List<List<Double>> distances) {
    }
}
