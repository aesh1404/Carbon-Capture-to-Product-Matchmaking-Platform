package com.carbonlink.repository;

import com.carbonlink.entity.Listing;
import com.carbonlink.entity.ListingStatus;
import com.carbonlink.entity.Order;
import com.carbonlink.entity.OrderStatus;
import com.carbonlink.entity.Role;
import com.carbonlink.entity.User;
import com.carbonlink.seed.HistoricalOrderSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// The Impact Dashboard is the one screen whose credibility rests entirely on its data looking
// like a real business. These pin the two things that would quietly ruin it: a chart that
// flattens into a straight line, and history that eats the stock the live demo needs.
@DataJpaTest
@Import(HistoricalOrderSeeder.class)
class HistoricalOrderSeederTest {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    @Autowired
    private HistoricalOrderSeeder seeder;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ListingRepository listingRepository;
    @Autowired
    private OrderRepository orderRepository;

    private List<Listing> liveListings;

    @BeforeEach
    void seedWorld() {
        List<User> emitters = new ArrayList<>();
        List<User> buyers = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            emitters.add(saveUser("Emitter " + i, Role.EMITTER));
        }
        for (int i = 0; i < 8; i++) {
            buyers.add(saveUser("Buyer " + i, Role.BUYER));
        }
        // Stand-ins for the fresh listings a presenter clicks on during the demo.
        liveListings = emitters.stream().map(this::saveLiveListing).toList();

        seeder.seed(emitters, buyers);
    }

    @Test
    void seedsRoughlyThirtyConfirmedOrdersAcrossFiveMonths() {
        List<Order> orders = orderRepository.findAll();

        assertThat(orders).hasSizeBetween(25, 30);
        assertThat(orders).allSatisfy(o -> assertThat(o.getStatus()).isEqualTo(OrderStatus.CONFIRMED));
        assertThat(ordersByMonth()).hasSize(5);
    }

    @Test
    void monthsVisiblyRiseAndFall_ratherThanTrendingInAStraightLine() {
        List<Double> volumes = new ArrayList<>(ordersByMonth().values());

        // The intended shape: strong, quiet, spike, dip, strongest.
        assertThat(volumes.get(1)).isLessThan(volumes.get(0));     // down
        assertThat(volumes.get(2)).isGreaterThan(volumes.get(1));  // up
        assertThat(volumes.get(3)).isLessThan(volumes.get(2));     // down
        assertThat(volumes.get(4)).isGreaterThan(volumes.get(3));  // up

        // ...and still materially up overall, so the period reads as growth.
        assertThat(volumes.get(4)).isGreaterThan(volumes.get(0));

        // The quiet month must stay clearly visible against the peak - if it renders as a
        // sliver the chart looks broken rather than varied.
        double ratio = volumes.stream().min(Double::compare).orElseThrow()
                / volumes.stream().max(Double::compare).orElseThrow();
        assertThat(ratio).isBetween(0.1, 0.6);
    }

    @Test
    void neverTouchesTheStockOfListingsReservedForTheLiveDemo() {
        // The whole point of the separate archive: a presenter must find full shelves.
        for (Listing live : liveListings) {
            Listing reloaded = listingRepository.findById(live.getId()).orElseThrow();
            assertThat(reloaded.getRemainingVolumeTons()).isEqualTo(reloaded.getTotalVolumeTons());
            assertThat(reloaded.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        }
    }

    @Test
    void historicalListingsAreClosed_soTheyNeverSurfaceInBrowseOrMatching() {
        List<Listing> archived = listingRepository.findByStatus(ListingStatus.CLOSED);
        List<Long> liveIds = liveListings.stream().map(Listing::getId).toList();

        assertThat(archived).isNotEmpty();
        assertThat(archived).noneMatch(l -> liveIds.contains(l.getId()));
        // And they were actually drawn down, not just closed while still full.
        assertThat(archived).anySatisfy(l ->
                assertThat(l.getRemainingVolumeTons()).isLessThan(l.getTotalVolumeTons()));
    }

    @Test
    void orderNumbersAreUniqueAndSequential_soLiveOrdersCanCarryOnFromThem() {
        List<String> numbers = orderRepository.findAll().stream()
                .map(Order::getOrderNumber).sorted().toList();

        assertThat(numbers).doesNotHaveDuplicates();
        assertThat(numbers.get(0)).endsWith("-0001");
        assertThat(numbers).allMatch(n -> n.matches("CL-\\d{4}-\\d{4}"));
    }

    @Test
    void pairsAndPricesVary_soNoMonthIsTheSameDealRepeated() {
        List<Order> orders = orderRepository.findAll();

        long distinctPairs = orders.stream()
                .map(o -> o.getEmitterId() + "->" + o.getBuyerId()).distinct().count();
        // Comfortably more than a handful of repeated pairings.
        assertThat(distinctPairs).isGreaterThan(orders.size() / 2);
        assertThat(orders.stream().map(Order::getPricePerTon).distinct().count()).isGreaterThan(1);
    }

    @Test
    void everyOrderIsBackdatedAndNoneLandInTheFuture() {
        LocalDate today = LocalDate.now();
        assertThat(orderRepository.findAll()).allSatisfy(o -> {
            assertThat(o.getCreatedAt()).isNotNull();
            assertThat(o.getCreatedAt().toLocalDate()).isBeforeOrEqualTo(today);
            assertThat(o.getCreatedAt().toLocalDate()).isAfter(today.minusMonths(6));
        });
    }

    @Test
    void costFieldsAreInternallyConsistent() {
        assertThat(orderRepository.findAll()).allSatisfy(o -> {
            assertThat(o.getBasePrice()).isCloseTo(o.getPricePerTon() * o.getTransactedVolumeTons(),
                    org.assertj.core.data.Offset.offset(0.5));
            assertThat(o.getTotalCost()).isCloseTo(o.getBasePrice() + o.getTransportCost(),
                    org.assertj.core.data.Offset.offset(0.5));
            assertThat(o.getTransactedVolumeTons()).isPositive();
        });
    }

    private Map<String, Double> ordersByMonth() {
        return orderRepository.findAll().stream().collect(Collectors.groupingBy(
                o -> o.getCreatedAt().format(MONTH),
                TreeMap::new,
                Collectors.summingDouble(Order::getTransactedVolumeTons)));
    }

    private User saveUser(String company, Role role) {
        return userRepository.save(User.builder()
                .name("Rep").companyName(company).role(role)
                .city("Mumbai").locationLat(19.076).locationLng(72.877)
                .build());
    }

    private Listing saveLiveListing(User emitter) {
        return listingRepository.save(Listing.builder()
                .emitterId(emitter.getId())
                .totalVolumeTons(800.0).remainingVolumeTons(800.0)
                .purityPercent(95.0).captureMethod("Post-combustion").pricePerTon(1200.0)
                .city("Mumbai").locationLat(19.076).locationLng(72.877)
                .status(ListingStatus.ACTIVE)
                .build());
    }
}
