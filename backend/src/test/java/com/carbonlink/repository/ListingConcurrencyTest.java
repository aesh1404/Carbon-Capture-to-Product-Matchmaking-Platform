package com.carbonlink.repository;

import com.carbonlink.entity.Listing;
import com.carbonlink.entity.ListingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.OptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Proves the @Version guard on Listing actually stops a lost update to remainingVolumeTons.
// This has to be a real JPA slice test - a Mockito-based service test can never catch it,
// because the lost update happens in the database, not in the service logic.
@DataJpaTest
class ListingConcurrencyTest {

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void twoConcurrentStockDeductions_theSecondOneFailsInsteadOfSilentlyOverselling() {
        Long listingId = persistListingWith(1000.0);

        // Two accept() calls that both read the listing before either of them writes - exactly
        // what two emitters clicking Accept at the same moment produces.
        Listing firstReader = loadDetached(listingId);
        Listing secondReader = loadDetached(listingId);

        // First accept commits: 1000 - 300 = 700.
        firstReader.setRemainingVolumeTons(700.0);
        listingRepository.saveAndFlush(firstReader);
        entityManager.clear();

        // Second accept tries to commit its own 1000 - 400 = 600, computed from the stale read.
        // Without @Version this would land and 300 tons of deduction would silently vanish.
        secondReader.setRemainingVolumeTons(600.0);
        assertThatThrownBy(() -> listingRepository.saveAndFlush(secondReader))
                .isInstanceOf(OptimisticLockingFailureException.class);

        entityManager.clear();
        assertThat(listingRepository.findById(listingId).orElseThrow().getRemainingVolumeTons())
                .isEqualTo(700.0);
    }

    @Test
    void versionIncrementsOnEveryUpdate_startingFromZeroOnInsert() {
        Long listingId = persistListingWith(500.0);

        assertThat(loadDetached(listingId).getVersion()).isZero();

        Listing listing = listingRepository.findById(listingId).orElseThrow();
        listing.setRemainingVolumeTons(400.0);
        listingRepository.saveAndFlush(listing);
        entityManager.clear();

        assertThat(loadDetached(listingId).getVersion()).isEqualTo(1L);
    }

    @Test
    void sequentialDeductionsAgainstAFreshReadEachTime_allApplyNormally() {
        // The ordinary (non-concurrent) path must be completely unaffected by the version column.
        Long listingId = persistListingWith(1000.0);

        for (double remaining : new double[] {800.0, 600.0, 400.0}) {
            Listing listing = listingRepository.findById(listingId).orElseThrow();
            listing.setRemainingVolumeTons(remaining);
            listingRepository.saveAndFlush(listing);
            entityManager.clear();
        }

        assertThat(listingRepository.findById(listingId).orElseThrow().getRemainingVolumeTons())
                .isEqualTo(400.0);
    }

    private Long persistListingWith(double volumeTons) {
        Listing listing = listingRepository.saveAndFlush(Listing.builder()
                .emitterId(1L)
                .totalVolumeTons(volumeTons)
                .remainingVolumeTons(volumeTons)
                .purityPercent(95.0)
                .captureMethod("Direct Air Capture")
                .pricePerTon(900.0)
                .city("Mumbai")
                .locationLat(19.076).locationLng(72.877)
                .status(ListingStatus.ACTIVE)
                .build());
        entityManager.clear();
        return listing.getId();
    }

    private Listing loadDetached(Long id) {
        Listing listing = listingRepository.findById(id).orElseThrow();
        entityManager.detach(listing);
        return listing;
    }
}
