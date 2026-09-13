package com.carbonlink.service;

import com.carbonlink.dto.DistanceResult;
import com.carbonlink.dto.ListingRequestDTO;
import com.carbonlink.dto.ListingResponseDTO;
import com.carbonlink.dto.QuickBrowseResultDTO;
import com.carbonlink.entity.DistanceSource;
import com.carbonlink.entity.Listing;
import com.carbonlink.entity.ListingStatus;
import com.carbonlink.entity.Role;
import com.carbonlink.exception.BadRequestException;
import com.carbonlink.repository.ListingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingServiceTest {

    @Mock
    private ListingRepository listingRepository;
    @Mock
    private UserService userService;
    @Mock
    private DistanceService distanceService;
    @Mock
    private DisplayIdService displayIdService;

    private ListingService listingService;

    @BeforeEach
    void setUp() {
        listingService = new ListingService(listingRepository, userService, distanceService, displayIdService);
    }

    @Test
    void quickBrowse_sortsByDistanceAscending() {
        Listing near = listing(2L, 90.0, 500.0);
        Listing far = listing(3L, 95.0, 500.0);
        when(listingRepository.findByStatus(ListingStatus.ACTIVE)).thenReturn(List.of(far, near));
        // far's coordinates (id 3L) resolve to a bigger distance than near's (id 2L) - keyed by
        // listing id via the fake lat value below so each listing gets a distinct result.
        when(distanceService.getDistance(eq(3.0), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new DistanceResult(300.0, DistanceSource.HAVERSINE_FALLBACK));
        when(distanceService.getDistance(eq(2.0), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new DistanceResult(50.0, DistanceSource.OPENROUTESERVICE));

        List<QuickBrowseResultDTO> results = listingService.quickBrowse(null, "Mumbai", null);

        assertThat(results).extracting(QuickBrowseResultDTO::listingId).containsExactly(2L, 3L);
        assertThat(results.get(0).distanceKm()).isEqualTo(50.0);
        assertThat(results.get(0).distanceSource()).isEqualTo(DistanceSource.OPENROUTESERVICE);
    }

    @Test
    void quickBrowse_filtersOutListingsBelowMinPurity() {
        Listing lowPurity = listing(2L, 80.0, 500.0);
        Listing highPurity = listing(3L, 96.0, 500.0);
        when(listingRepository.findByStatus(ListingStatus.ACTIVE)).thenReturn(List.of(lowPurity, highPurity));
        when(distanceService.getDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new DistanceResult(100.0, DistanceSource.HAVERSINE_FALLBACK));

        List<QuickBrowseResultDTO> results = listingService.quickBrowse(90.0, "Mumbai", null);

        assertThat(results).extracting(QuickBrowseResultDTO::listingId).containsExactly(3L);
    }

    @Test
    void quickBrowse_filtersOutListingsBeyondMaxDistanceKm() {
        Listing close = listing(2L, 90.0, 500.0);
        Listing distant = listing(3L, 90.0, 500.0);
        when(listingRepository.findByStatus(ListingStatus.ACTIVE)).thenReturn(List.of(close, distant));
        when(distanceService.getDistance(eq(2.0), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new DistanceResult(50.0, DistanceSource.HAVERSINE_FALLBACK));
        when(distanceService.getDistance(eq(3.0), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new DistanceResult(5000.0, DistanceSource.HAVERSINE_FALLBACK));

        List<QuickBrowseResultDTO> results = listingService.quickBrowse(null, "Mumbai", 100.0);

        assertThat(results).extracting(QuickBrowseResultDTO::listingId).containsExactly(2L);
    }

    @Test
    void quickBrowse_400sOnUnsupportedBuyerCity() {
        assertThatThrownBy(() -> listingService.quickBrowse(null, "Atlantis", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("buyerCity");
    }

    // --- getListings: the marketplace browse filter. Its boundaries are inclusive on both
    // sides (purity >= minPurity, price <= maxPrice), which nothing pinned before. ---

    @Test
    void getListings_noFilters_defaultsToActiveListingsOnly() {
        Listing active = listing(1L, 90.0, 500.0);
        when(listingRepository.findByStatus(ListingStatus.ACTIVE)).thenReturn(List.of(active));

        List<ListingResponseDTO> results = listingService.getListings(null, null, null, null);

        assertThat(results).extracting(ListingResponseDTO::id).containsExactly(1L);
    }

    @Test
    void getListings_explicitStatus_overridesTheActiveDefault() {
        Listing closed = listing(9L, 90.0, 0.0);
        closed.setStatus(ListingStatus.CLOSED);
        when(listingRepository.findByStatus(ListingStatus.CLOSED)).thenReturn(List.of(closed));

        List<ListingResponseDTO> results = listingService.getListings(null, null, ListingStatus.CLOSED, null);

        assertThat(results).extracting(ListingResponseDTO::id).containsExactly(9L);
    }

    @Test
    void getListings_minPurityBoundaryIsInclusive_soAnExactMatchIsKept() {
        Listing exactlyAtThreshold = listing(1L, 90.0, 500.0);
        Listing justUnder = listing(2L, 89.99, 500.0);
        when(listingRepository.findByStatus(ListingStatus.ACTIVE))
                .thenReturn(List.of(exactlyAtThreshold, justUnder));

        List<ListingResponseDTO> results = listingService.getListings(90.0, null, null, null);

        assertThat(results).extracting(ListingResponseDTO::id).containsExactly(1L);
    }

    @Test
    void getListings_maxPriceBoundaryIsInclusive_soAnExactMatchIsKept() {
        Listing exactlyAtBudget = listing(1L, 95.0, 500.0);
        exactlyAtBudget.setPricePerTon(1000.0);
        Listing justOver = listing(2L, 95.0, 500.0);
        justOver.setPricePerTon(1000.01);
        when(listingRepository.findByStatus(ListingStatus.ACTIVE)).thenReturn(List.of(exactlyAtBudget, justOver));

        List<ListingResponseDTO> results = listingService.getListings(null, 1000.0, null, null);

        assertThat(results).extracting(ListingResponseDTO::id).containsExactly(1L);
    }

    @Test
    void getListings_bothFiltersApplyTogether() {
        Listing passesBoth = listing(1L, 95.0, 500.0);
        passesBoth.setPricePerTon(800.0);
        Listing failsPurityOnly = listing(2L, 80.0, 500.0);
        failsPurityOnly.setPricePerTon(800.0);
        Listing failsPriceOnly = listing(3L, 95.0, 500.0);
        failsPriceOnly.setPricePerTon(5000.0);
        when(listingRepository.findByStatus(ListingStatus.ACTIVE))
                .thenReturn(List.of(passesBoth, failsPurityOnly, failsPriceOnly));

        List<ListingResponseDTO> results = listingService.getListings(90.0, 1000.0, null, null);

        assertThat(results).extracting(ListingResponseDTO::id).containsExactly(1L);
    }

    @Test
    void getListings_neverExposesRawCoordinates_onlyTheCityName() {
        // locationLat/locationLng are an internal derivation of `city` - the client renders the
        // name and reads distances pre-computed, so coordinates must not leave the server.
        Listing active = listing(1L, 90.0, 500.0);
        when(listingRepository.findByStatus(ListingStatus.ACTIVE)).thenReturn(List.of(active));

        ListingResponseDTO dto = listingService.getListings(null, null, null, null).get(0);

        assertThat(dto.city()).isEqualTo("Pune");
        assertThat(ListingResponseDTO.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("locationLat", "locationLng");
    }

    // --- Dual ID: the personal number must never reach a buyer-facing response ---
    //
    // The rule is enforced on the SERVER, not by asking the client to not render it. A number
    // that only means something within one emitter's set would, out in the marketplace, look
    // exactly like a real id and collide with every other emitter's - so it is left null and
    // (via @JsonInclude NON_NULL) absent from the JSON entirely.

    @Test
    void getListings_scopedToAnEmitter_carriesTheirPersonalNumbering() {
        Listing first = listing(7L, 95.0, 500.0);
        Listing second = listing(9L, 95.0, 500.0);
        when(listingRepository.findByEmitterId(10L)).thenReturn(List.of(first, second));
        when(displayIdService.listingDisplayIds(List.of(first, second)))
                .thenReturn(Map.of(7L, 1, 9L, 2));

        List<ListingResponseDTO> results = listingService.getListings(null, null, null, 10L);

        // Global ids stay 7 and 9; the emitter sees them as their 1st and 2nd. Returned
        // newest-first, so a listing they just created lands at the top of their page and the
        // column reads as an ordered run rather than whatever order the rows came back in.
        assertThat(results).extracting(ListingResponseDTO::personalListingNumber).containsExactly(2, 1);
        assertThat(results).extracting(ListingResponseDTO::id).containsExactly(9L, 7L);
    }

    @Test
    void getListings_marketplaceBrowse_neverCarriesAPersonalNumber() {
        Listing a = listing(7L, 95.0, 500.0);
        Listing b = listing(9L, 95.0, 500.0);
        when(listingRepository.findByStatus(ListingStatus.ACTIVE)).thenReturn(List.of(a, b));

        List<ListingResponseDTO> results = listingService.getListings(null, null, null, null);

        assertThat(results).extracting(ListingResponseDTO::personalListingNumber)
                .containsOnlyNulls();
        // Not merely null-and-unused: the numbering is never even computed for this path.
        verifyNoInteractions(displayIdService);
    }

    @Test
    void getListing_byId_neverCarriesAPersonalNumber() {
        // This is the buyer-facing enrichment lookup behind match cards and receipts.
        Listing listing = listing(9L, 95.0, 500.0);
        when(listingRepository.findById(9L)).thenReturn(java.util.Optional.of(listing));

        assertThat(listingService.getListing(9L).personalListingNumber()).isNull();
        verifyNoInteractions(displayIdService);
    }

    @Test
    void getListings_scopedToAnEmitter_returnsEveryStatusWhenNoStatusIsGiven() {
        // An emitter's own page should show active, matched and closed listings together.
        Listing active = listing(1L, 95.0, 500.0);
        Listing closed = listing(2L, 95.0, 0.0);
        closed.setStatus(ListingStatus.CLOSED);
        when(listingRepository.findByEmitterId(10L)).thenReturn(List.of(active, closed));

        assertThat(listingService.getListings(null, null, null, 10L))
                .extracting(ListingResponseDTO::id).containsExactly(1L, 2L);
    }

    @Test
    void getListings_scopedToAnEmitter_stillHonoursAnExplicitStatusFilter() {
        Listing active = listing(1L, 95.0, 500.0);
        Listing closed = listing(2L, 95.0, 0.0);
        closed.setStatus(ListingStatus.CLOSED);
        when(listingRepository.findByEmitterId(10L)).thenReturn(List.of(active, closed));

        assertThat(listingService.getListings(null, null, ListingStatus.CLOSED, 10L))
                .extracting(ListingResponseDTO::id).containsExactly(2L);
    }

    // --- Quick Browse without a city: the one-filter fast path ---

    @Test
    void quickBrowse_withoutACity_returnsResultsCheapestFirst_withNoDistance() {
        // Quick Browse exists to answer "show me anything above X% purity". Demanding an origin
        // from someone who only cares about purity made the fast path slower than the flow it
        // was meant to shortcut. With no city there is no distance to rank by, so price is.
        Listing pricey = listing(2L, 95.0, 500.0);
        pricey.setPricePerTon(2200.0);
        Listing cheap = listing(3L, 95.0, 500.0);
        cheap.setPricePerTon(740.0);
        Listing middle = listing(4L, 95.0, 500.0);
        middle.setPricePerTon(1200.0);
        when(listingRepository.findByStatus(ListingStatus.ACTIVE)).thenReturn(List.of(pricey, cheap, middle));

        List<QuickBrowseResultDTO> results = listingService.quickBrowse(90.0, null, null);

        assertThat(results).extracting(QuickBrowseResultDTO::listingId).containsExactly(3L, 4L, 2L);
        // Distance is genuinely unknown, not zero and not silently hidden.
        assertThat(results).allSatisfy(r -> {
            assertThat(r.distanceKm()).isNull();
            assertThat(r.distanceSource()).isNull();
        });
        verifyNoInteractions(distanceService);
    }

    @Test
    void quickBrowse_blankCityIsTreatedAsNoCity_notAsABadCityName() {
        Listing only = listing(2L, 95.0, 500.0);
        when(listingRepository.findByStatus(ListingStatus.ACTIVE)).thenReturn(List.of(only));

        assertThat(listingService.quickBrowse(null, "   ", null)).hasSize(1);
    }

    @Test
    void quickBrowse_stillFiltersByPurityWithoutACity() {
        Listing tooImpure = listing(2L, 80.0, 500.0);
        Listing pure = listing(3L, 96.0, 500.0);
        when(listingRepository.findByStatus(ListingStatus.ACTIVE)).thenReturn(List.of(tooImpure, pure));

        assertThat(listingService.quickBrowse(90.0, null, null))
                .extracting(QuickBrowseResultDTO::listingId).containsExactly(3L);
    }

    @Test
    void quickBrowse_maxDistanceWithoutACity_is400_ratherThanAFilterThatQuietlyDoesNothing() {
        assertThatThrownBy(() -> listingService.quickBrowse(null, null, 300.0))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("maxDistanceKm needs a buyerCity");
    }

    @Test
    void quickBrowse_withACity_stillRanksByDistance() {
        // The city path is unchanged by making the param optional.
        Listing near = listing(2L, 90.0, 500.0);
        Listing far = listing(3L, 90.0, 500.0);
        when(listingRepository.findByStatus(ListingStatus.ACTIVE)).thenReturn(List.of(far, near));
        when(distanceService.getDistance(eq(2.0), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new DistanceResult(50.0, DistanceSource.OPENROUTESERVICE));
        when(distanceService.getDistance(eq(3.0), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new DistanceResult(900.0, DistanceSource.HAVERSINE_FALLBACK));

        List<QuickBrowseResultDTO> results = listingService.quickBrowse(null, "Mumbai", null);

        assertThat(results).extracting(QuickBrowseResultDTO::listingId).containsExactly(2L, 3L);
        assertThat(results.get(0).distanceKm()).isEqualTo(50.0);
    }

    // --- Price fidelity: what the user typed is what gets stored and returned ---

    // A reported "3000 entered, 2989 stored" turned out to be the browser stepping a focused
    // <input type="number"> on mouse-wheel scroll, not anything server-side (fixed in the
    // frontend with a wheel guard). These pin the backend half of that pipeline anyway, so a
    // future rounding/precision change here can't reintroduce the same symptom from the other
    // end. Values chosen to cover the reported pair plus whole, fractional and large amounts.
    @ParameterizedTest
    @ValueSource(doubles = {800.0, 1500.0, 2989.0, 3000.0, 12345.50, 999.99, 0.01, 1234567.89})
    void createListing_storesAndReturnsPricePerTonExactly_withNoDrift(double price) {
        when(listingRepository.save(any())).thenAnswer(inv -> {
            Listing saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        ListingResponseDTO response = listingService.createListing(new ListingRequestDTO(
                10L, 500.0, 95.0, "Direct Air Capture", price, "Mumbai", null));

        ArgumentCaptor<Listing> captor = ArgumentCaptor.forClass(Listing.class);
        verify(listingRepository).save(captor.capture());
        // isEqualTo on Double is an exact bit comparison - no delta, deliberately.
        assertThat(captor.getValue().getPricePerTon()).isEqualTo(price);
        assertThat(response.pricePerTon()).isEqualTo(price);
    }

    @ParameterizedTest
    @ValueSource(doubles = {800.0, 1500.0, 2989.0, 3000.0, 12345.50})
    void createListing_storesVolumeAndPurityExactlyToo(double value) {
        // The wheel bug hit every numeric field, not just price - a silently decremented
        // volume or purity would be just as wrong and far harder to spot.
        when(listingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        listingService.createListing(new ListingRequestDTO(
                10L, value, 95.0, "Direct Air Capture", 900.0, "Mumbai", null));

        ArgumentCaptor<Listing> captor = ArgumentCaptor.forClass(Listing.class);
        verify(listingRepository).save(captor.capture());
        assertThat(captor.getValue().getTotalVolumeTons()).isEqualTo(value);
    }

    // --- createListing ---

    @Test
    void createListing_resolvesCoordinatesFromTheCityName() {
        when(listingRepository.save(any())).thenAnswer(inv -> {
            Listing saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        listingService.createListing(new ListingRequestDTO(
                10L, 500.0, 95.0, "Direct Air Capture", 900.0, "Mumbai", "Plot 14"));

        ArgumentCaptor<Listing> captor = ArgumentCaptor.forClass(Listing.class);
        verify(listingRepository).save(captor.capture());
        Listing saved = captor.getValue();
        assertThat(saved.getCity()).isEqualTo("Mumbai");
        assertThat(saved.getLocationLat()).isEqualTo(19.076);
        assertThat(saved.getLocationLng()).isEqualTo(72.877);
        // remainingVolumeTons is seeded from totalVolumeTons by @PrePersist, not set here.
        assertThat(saved.getTotalVolumeTons()).isEqualTo(500.0);
    }

    @Test
    void createListing_400sOnUnsupportedCity() {
        assertThatThrownBy(() -> listingService.createListing(new ListingRequestDTO(
                10L, 500.0, 95.0, "Direct Air Capture", 900.0, "Atlantis", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("city");
        verify(listingRepository, never()).save(any());
    }

    @Test
    void createListing_rejectsAnEmitterIdThatIsNotAnEmitter() {
        doThrow(new BadRequestException("User 10 has role BUYER, expected EMITTER"))
                .when(userService).requireUserWithRole(10L, Role.EMITTER);

        assertThatThrownBy(() -> listingService.createListing(new ListingRequestDTO(
                10L, 500.0, 95.0, "Direct Air Capture", 900.0, "Mumbai", null)))
                .isInstanceOf(BadRequestException.class);
        verify(listingRepository, never()).save(any());
    }

    @Test
    void quickBrowse_excludesListingsWithNoRemainingVolume() {
        Listing depleted = listing(2L, 90.0, 0.0);
        when(listingRepository.findByStatus(ListingStatus.ACTIVE)).thenReturn(List.of(depleted));

        List<QuickBrowseResultDTO> results = listingService.quickBrowse(null, "Mumbai", null);

        assertThat(results).isEmpty();
    }

    private Listing listing(Long id, double purityPercent, double remainingVolumeTons) {
        return Listing.builder()
                .id(id)
                .emitterId(10L)
                .totalVolumeTons(1000.0)
                .remainingVolumeTons(remainingVolumeTons)
                .purityPercent(purityPercent)
                .captureMethod("Direct Air Capture")
                .pricePerTon(900.0)
                .city("Pune")
                .locationLat(id.doubleValue())
                .locationLng(id.doubleValue())
                .status(ListingStatus.ACTIVE)
                .build();
    }
}
