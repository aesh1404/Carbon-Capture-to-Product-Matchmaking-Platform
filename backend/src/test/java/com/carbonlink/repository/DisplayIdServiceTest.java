package com.carbonlink.repository;

import com.carbonlink.entity.CarbonRequest;
import com.carbonlink.entity.Listing;
import com.carbonlink.entity.ListingStatus;
import com.carbonlink.service.DisplayIdService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// A real JPA slice rather than mocks: the whole point of display numbering is the ORDER rows
// come back in, which only the database can actually answer.
@DataJpaTest
@Import(DisplayIdService.class)
class DisplayIdServiceTest {

    @Autowired
    private DisplayIdService displayIdService;
    @Autowired
    private ListingRepository listingRepository;
    @Autowired
    private CarbonRequestRepository carbonRequestRepository;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    void listingsAreNumberedPerEmitter_eachEmitterStartingAtOne() {
        // The bug this replaces: an emitter with three listings saw them as #4, #9 and #17
        // because those were their rows in a table shared with every other emitter.
        Listing emitterAFirst = persistListing(100L, 1);
        Listing emitterBFirst = persistListing(200L, 2);
        Listing emitterASecond = persistListing(100L, 3);
        Listing emitterAThird = persistListing(100L, 4);
        Listing emitterBSecond = persistListing(200L, 5);

        Map<Long, Integer> ids = displayIdService.listingDisplayIds(listingRepository.findAll());

        assertThat(ids.get(emitterAFirst.getId())).isEqualTo(1);
        assertThat(ids.get(emitterASecond.getId())).isEqualTo(2);
        assertThat(ids.get(emitterAThird.getId())).isEqualTo(3);
        // Emitter B has their own sequence that also starts at 1 - display numbers are only
        // meaningful within one owner's set, never globally unique.
        assertThat(ids.get(emitterBFirst.getId())).isEqualTo(1);
        assertThat(ids.get(emitterBSecond.getId())).isEqualTo(2);
    }

    @Test
    void numberingFollowsCreationOrder_notInsertionOrIdOrder() {
        // Persisted newest-first so row id order and creation order disagree.
        Listing newest = persistListing(100L, 3);
        Listing oldest = persistListing(100L, 1);
        Listing middle = persistListing(100L, 2);

        Map<Long, Integer> ids = displayIdService.listingDisplayIds(List.of(newest, oldest, middle));

        assertThat(ids.get(oldest.getId())).isEqualTo(1);
        assertThat(ids.get(middle.getId())).isEqualTo(2);
        assertThat(ids.get(newest.getId())).isEqualTo(3);
    }

    @Test
    void aListingKeepsItsNumberWhenAnotherEmitterAddsListings() {
        // Someone else's activity must never renumber your listings.
        Listing mine = persistListing(100L, 1);
        assertThat(displayIdService.listingDisplayId(mine)).isEqualTo(1);

        persistListing(999L, 2);
        persistListing(999L, 3);

        assertThat(displayIdService.listingDisplayId(mine)).isEqualTo(1);
    }

    @Test
    void numberingIsNotAffectedByListingStatus_soClosedListingsDontLeaveGaps() {
        Listing first = persistListing(100L, 1);
        first.setStatus(ListingStatus.CLOSED);
        listingRepository.saveAndFlush(first);
        Listing second = persistListing(100L, 2);

        Map<Long, Integer> ids = displayIdService.listingDisplayIds(List.of(first, second));

        assertThat(ids.get(first.getId())).isEqualTo(1);
        assertThat(ids.get(second.getId())).isEqualTo(2);
    }

    @Test
    void requestsAreNumberedPerBuyer() {
        CarbonRequest buyerAFirst = persistRequest(100L, 1);
        CarbonRequest buyerBFirst = persistRequest(200L, 2);
        CarbonRequest buyerASecond = persistRequest(100L, 3);

        Map<Long, Integer> ids = displayIdService.requestDisplayIds(
                carbonRequestRepository.findAll());

        assertThat(ids.get(buyerAFirst.getId())).isEqualTo(1);
        assertThat(ids.get(buyerASecond.getId())).isEqualTo(2);
        assertThat(ids.get(buyerBFirst.getId())).isEqualTo(1);
    }

    @Test
    void emptyInputReturnsAnEmptyMapWithoutQuerying() {
        assertThat(displayIdService.listingDisplayIds(List.of())).isEmpty();
        assertThat(displayIdService.requestDisplayIds(List.of())).isEmpty();
    }

    private Listing persistListing(Long emitterId, int minutesOld) {
        Listing listing = listingRepository.saveAndFlush(Listing.builder()
                .emitterId(emitterId)
                .totalVolumeTons(500.0).remainingVolumeTons(500.0)
                .purityPercent(95.0).captureMethod("Direct Air Capture").pricePerTon(900.0)
                .city("Mumbai").locationLat(19.076).locationLng(72.877)
                .status(ListingStatus.ACTIVE)
                .build());
        // @PrePersist stamps createdAt with now(). It's mapped updatable=false (correctly - an
        // audit stamp shouldn't drift), so a setter + save is silently ignored and the rows
        // would just keep their insertion order. A JPQL update is the way to force a specific
        // timestamp, which is what makes the ordering under test explicit rather than dependent
        // on how fast the machine inserts rows.
        backdate("Listing", listing.getId(), minutesOld);
        return listing;
    }

    private CarbonRequest persistRequest(Long buyerId, int minutesOld) {
        CarbonRequest request = carbonRequestRepository.saveAndFlush(CarbonRequest.builder()
                .buyerId(buyerId)
                .minVolumeNeeded(100.0).minPurityRequired(90.0)
                .maxDistanceKm(500.0).maxBudgetPerTon(1500.0)
                .intendedUse("Fuel Synthesis")
                .build());
        backdate("CarbonRequest", request.getId(), minutesOld);
        return request;
    }

    private void backdate(String entity, Long id, int minutesOld) {
        entityManager.getEntityManager()
                .createQuery("UPDATE " + entity + " e SET e.createdAt = :t WHERE e.id = :id")
                .setParameter("t", LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(minutesOld))
                .setParameter("id", id)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }
}
