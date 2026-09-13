package com.carbonlink.service;

import com.carbonlink.dto.DistanceResult;
import com.carbonlink.entity.DistanceSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DistanceServiceTest {

    private static final String API_URL = "https://api.openrouteservice.org/v2/matrix/driving-car";
    private static final String API_KEY = "test-key";
    // Long enough that a tripped circuit stays open for the whole of a single test.
    private static final long COOLDOWN_SECONDS = 300;

    // Bengaluru -> Chennai, ~290km straight-line
    private static final double LAT1 = 12.9716;
    private static final double LNG1 = 77.5946;
    private static final double LAT2 = 13.0827;
    private static final double LNG2 = 80.2707;

    @Mock
    private RestTemplate restTemplate;

    private DistanceService distanceService;

    @BeforeEach
    void setUp() {
        distanceService = new DistanceService(restTemplate, API_KEY, API_URL, COOLDOWN_SECONDS);
    }

    @Test
    void getDistance_whenApiSucceeds_returnsOpenRouteServiceResult() {
        DistanceService.OrsMatrixResponse body = new DistanceService.OrsMatrixResponse(
                List.of(List.of(0.0, 15000.0), List.of(15000.0, 0.0)));
        when(restTemplate.postForEntity(eq(API_URL), any(HttpEntity.class), eq(DistanceService.OrsMatrixResponse.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        DistanceResult result = distanceService.getDistance(LAT1, LNG1, LAT2, LNG2);

        assertThat(result.source()).isEqualTo(DistanceSource.OPENROUTESERVICE);
        assertThat(result.distanceKm()).isEqualTo(15.0);
    }

    @Test
    void getDistance_whenApiThrowsException_fallsBackToHaversine() {
        when(restTemplate.postForEntity(eq(API_URL), any(HttpEntity.class), eq(DistanceService.OrsMatrixResponse.class)))
                .thenThrow(new RestClientException("simulated timeout"));

        DistanceResult result = distanceService.getDistance(LAT1, LNG1, LAT2, LNG2);

        double expectedKm = distanceService.haversine(LAT1, LNG1, LAT2, LNG2)
                * DistanceService.HAVERSINE_ROAD_FACTOR;
        assertThat(result.source()).isEqualTo(DistanceSource.HAVERSINE_FALLBACK);
        assertThat(result.distanceKm()).isCloseTo(expectedKm, within(0.001));
    }

    @Test
    void getDistance_whenApiReturnsNon2xxStatus_fallsBackToHaversine() {
        when(restTemplate.postForEntity(eq(API_URL), any(HttpEntity.class), eq(DistanceService.OrsMatrixResponse.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));

        DistanceResult result = distanceService.getDistance(LAT1, LNG1, LAT2, LNG2);

        assertThat(result.source()).isEqualTo(DistanceSource.HAVERSINE_FALLBACK);
    }

    @Test
    void getDistance_repeatedCoordinatePairs_hitCacheAndCallApiOnce() {
        DistanceService.OrsMatrixResponse body = new DistanceService.OrsMatrixResponse(
                List.of(List.of(0.0, 10000.0), List.of(10000.0, 0.0)));
        when(restTemplate.postForEntity(eq(API_URL), any(HttpEntity.class), eq(DistanceService.OrsMatrixResponse.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        distanceService.getDistance(LAT1, LNG1, LAT2, LNG2);
        distanceService.getDistance(LAT1, LNG1, LAT2, LNG2);
        // Rounds to the same 4-decimal-place key as LAT1/LNG1/LAT2/LNG2 -> should still hit cache
        distanceService.getDistance(12.97160001, 77.59460001, 13.08270001, 80.27070001);

        verify(restTemplate, times(1))
                .postForEntity(eq(API_URL), any(HttpEntity.class), eq(DistanceService.OrsMatrixResponse.class));
    }

    @Test
    void getDistance_whenNoApiKeyConfigured_skipsTheHttpCallEntirely() {
        // With no key the call could only ever come back 403, so paying the network round trip
        // to be told that is wasted latency on every uncached pair.
        DistanceService keyless = new DistanceService(restTemplate, "", API_URL, COOLDOWN_SECONDS);

        DistanceResult result = keyless.getDistance(LAT1, LNG1, LAT2, LNG2);

        assertThat(result.source()).isEqualTo(DistanceSource.HAVERSINE_FALLBACK);
        verify(restTemplate, never())
                .postForEntity(eq(API_URL), any(HttpEntity.class), eq(DistanceService.OrsMatrixResponse.class));
    }

    @Test
    void getDistance_whenNoApiKeyConfigured_stillAppliesTheMinimumDistanceFloor() {
        DistanceService keyless = new DistanceService(restTemplate, null, API_URL, COOLDOWN_SECONDS);

        DistanceResult result = keyless.getDistance(LAT1, LNG1, LAT1, LNG1);

        assertThat(result.distanceKm()).isEqualTo(DistanceService.MIN_DISTANCE_KM);
    }

    @Test
    void getDistance_afterOneFailure_stopsCallingTheApiForEveryOtherPair() {
        // "Find Matches" resolves one distance per candidate listing. Without the circuit
        // breaker an unreachable API costs the full timeout on every distinct pair, which is
        // what turns a slow network into a visibly frozen button.
        when(restTemplate.postForEntity(eq(API_URL), any(HttpEntity.class), eq(DistanceService.OrsMatrixResponse.class)))
                .thenThrow(new RestClientException("simulated timeout"));

        DistanceResult first = distanceService.getDistance(LAT1, LNG1, LAT2, LNG2);
        // Three further *distinct* coordinate pairs - none of them can hit the cache.
        DistanceResult second = distanceService.getDistance(20.0, 70.0, LAT2, LNG2);
        DistanceResult third = distanceService.getDistance(21.0, 71.0, LAT2, LNG2);
        DistanceResult fourth = distanceService.getDistance(22.0, 72.0, LAT2, LNG2);

        assertThat(List.of(first, second, third, fourth))
                .allMatch(r -> r.source() == DistanceSource.HAVERSINE_FALLBACK);
        // Only the first pair paid the timeout; the rest short-circuited.
        verify(restTemplate, times(1))
                .postForEntity(eq(API_URL), any(HttpEntity.class), eq(DistanceService.OrsMatrixResponse.class));
    }

    @Test
    void getDistance_afterTheCooldownElapses_triesTheApiAgain() {
        DistanceService shortCooldown = new DistanceService(restTemplate, API_KEY, API_URL, 0);
        when(restTemplate.postForEntity(eq(API_URL), any(HttpEntity.class), eq(DistanceService.OrsMatrixResponse.class)))
                .thenThrow(new RestClientException("simulated timeout"));

        shortCooldown.getDistance(LAT1, LNG1, LAT2, LNG2);
        shortCooldown.getDistance(20.0, 70.0, LAT2, LNG2); // distinct pair, cooldown already expired

        // A zero-length cooldown means the breaker never latches - the outage isn't permanent.
        verify(restTemplate, times(2))
                .postForEntity(eq(API_URL), any(HttpEntity.class), eq(DistanceService.OrsMatrixResponse.class));
    }

    @Test
    void haversineRoadFactor_estimatesRealIndianHighwayDistancesWithin8Percent() {
        // The factor scales a straight line up to approximate a real road route, and it feeds
        // transport cost directly - so an over-estimate here inflates freight on exactly the
        // long hauls where freight already dominates the total. Pinned against real highway
        // distances rather than left as a round number.
        //
        // The tolerance is what gives this test teeth: at 1.2 the worst route is 5.1% off, at
        // the previous 1.3 it was 13.8% off (Mumbai->Hyderabad 808 km vs a real 710). An 8%
        // bound passes the former and fails the latter, with ~3 points of headroom.
        assertRoadEstimateNear("Mumbai->Pune", 19.076, 72.877, 18.520, 73.856, 150);
        assertRoadEstimateNear("Mumbai->Ahmedabad", 19.076, 72.877, 23.023, 72.571, 525);
        assertRoadEstimateNear("Chennai->Bangalore", 13.083, 80.270, 12.972, 77.594, 350);
        assertRoadEstimateNear("Mumbai->Hyderabad", 19.076, 72.877, 17.385, 78.487, 710);
        assertRoadEstimateNear("Mumbai->Bangalore", 19.076, 72.877, 12.972, 77.594, 980);
        assertRoadEstimateNear("Delhi->Kolkata", 28.613, 77.209, 22.573, 88.364, 1500);
        assertRoadEstimateNear("Mumbai->Delhi", 19.076, 72.877, 28.613, 77.209, 1420);
        assertRoadEstimateNear("Ahmedabad->Delhi", 23.023, 72.571, 28.613, 77.209, 950);
    }

    // A single global multiplier can never match every route exactly, so some slack is
    // necessary - but it has to stay tight enough to catch a factor that is systematically
    // wrong in one direction, which is the failure mode that actually costs money here.
    private static final double ROAD_ESTIMATE_TOLERANCE = 0.08;

    private void assertRoadEstimateNear(String route, double lat1, double lng1,
                                         double lat2, double lng2, double realRoadKm) {
        double estimated = distanceService.haversine(lat1, lng1, lat2, lng2)
                * DistanceService.HAVERSINE_ROAD_FACTOR;
        assertThat(estimated)
                .as("%s: estimated %.0f km vs real road %.0f km", route, estimated, realRoadKm)
                .isBetween(realRoadKm * (1 - ROAD_ESTIMATE_TOLERANCE),
                           realRoadKm * (1 + ROAD_ESTIMATE_TOLERANCE));
    }

    @Test
    void haversine_knownRoute_isReasonablyAccurate() {
        double distanceKm = distanceService.haversine(LAT1, LNG1, LAT2, LNG2);
        assertThat(distanceKm).isBetween(280.0, 300.0);
    }

    @Test
    void getDistance_sameCityIdenticalCoordinates_flooredToMinimumInsteadOfZero() {
        // A buyer and emitter registered in the same city resolve to the exact same lat/lng
        // (one fixed coordinate per city), so the raw Haversine distance is exactly 0.
        when(restTemplate.postForEntity(eq(API_URL), any(HttpEntity.class), eq(DistanceService.OrsMatrixResponse.class)))
                .thenThrow(new RestClientException("simulated timeout"));

        DistanceResult result = distanceService.getDistance(LAT1, LNG1, LAT1, LNG1);

        assertThat(distanceService.haversine(LAT1, LNG1, LAT1, LNG1)).isEqualTo(0.0);
        assertThat(result.distanceKm()).isEqualTo(DistanceService.MIN_DISTANCE_KM);
        assertThat(result.source()).isEqualTo(DistanceSource.HAVERSINE_FALLBACK);
    }

    @Test
    void getDistance_openRouteServiceReturnsBelowMinimum_alsoFlooredToMinimum() {
        DistanceService.OrsMatrixResponse body = new DistanceService.OrsMatrixResponse(
                List.of(List.of(0.0, 2000.0), List.of(2000.0, 0.0))); // 2km real route, still floored
        when(restTemplate.postForEntity(eq(API_URL), any(HttpEntity.class), eq(DistanceService.OrsMatrixResponse.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        DistanceResult result = distanceService.getDistance(LAT1, LNG1, LAT2, LNG2);

        assertThat(result.distanceKm()).isEqualTo(DistanceService.MIN_DISTANCE_KM);
        assertThat(result.source()).isEqualTo(DistanceSource.OPENROUTESERVICE);
    }

    @Test
    void getDistance_atOrAboveMinimum_isNotAltered() {
        DistanceService.OrsMatrixResponse body = new DistanceService.OrsMatrixResponse(
                List.of(List.of(0.0, 10000.0), List.of(10000.0, 0.0))); // exactly 10km == the floor
        when(restTemplate.postForEntity(eq(API_URL), any(HttpEntity.class), eq(DistanceService.OrsMatrixResponse.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        DistanceResult result = distanceService.getDistance(LAT1, LNG1, LAT2, LNG2);

        assertThat(result.distanceKm()).isEqualTo(10.0);
    }
}
