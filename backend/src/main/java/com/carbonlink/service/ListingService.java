package com.carbonlink.service;

import com.carbonlink.constants.CityCoordinates;
import com.carbonlink.dto.DistanceResult;
import com.carbonlink.dto.ListingRequestDTO;
import com.carbonlink.dto.ListingResponseDTO;
import com.carbonlink.dto.QuickBrowseResultDTO;
import com.carbonlink.entity.Listing;
import com.carbonlink.entity.ListingStatus;
import com.carbonlink.entity.Role;
import com.carbonlink.exception.BadRequestException;
import com.carbonlink.exception.ResourceNotFoundException;
import com.carbonlink.repository.ListingRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class ListingService {

    private final ListingRepository listingRepository;
    private final UserService userService;
    private final DistanceService distanceService;
    private final DisplayIdService displayIdService;

    public ListingService(ListingRepository listingRepository, UserService userService,
                           DistanceService distanceService, DisplayIdService displayIdService) {
        this.listingRepository = listingRepository;
        this.userService = userService;
        this.distanceService = distanceService;
        this.displayIdService = displayIdService;
    }

    public ListingResponseDTO createListing(ListingRequestDTO dto) {
        userService.requireUserWithRole(dto.emitterId(), Role.EMITTER);
        CityCoordinates.LatLng coordinates = CityCoordinates.lookup(dto.city())
                .orElseThrow(() -> new BadRequestException("city must be one of the supported cities"));

        Listing listing = Listing.builder()
                .emitterId(dto.emitterId())
                .totalVolumeTons(dto.totalVolumeTons())
                .purityPercent(dto.purityPercent())
                .captureMethod(dto.captureMethod())
                .pricePerTon(dto.pricePerTon())
                .city(dto.city())
                .address(dto.address())
                .locationLat(coordinates.lat())
                .locationLng(coordinates.lng())
                .build();

        Listing saved = listingRepository.save(listing);
        return toResponseDto(saved, displayIdService.listingDisplayId(saved));
    }

    // Buyer-facing enrichment lookup (match cards, receipts). Deliberately carries NO personal
    // number: the caller is not established as the owner here, and the number is meaningless -
    // even misleading - outside the owning emitter's own set.
    public ListingResponseDTO getListing(Long id) {
        return toResponseDto(requireListing(id), null);
    }

    // Two different views share this endpoint, and only one of them may see personal numbers:
    //
    //   emitterId given  -> "My Listings": that emitter's own listings, WITH their personal
    //                       numbering. Status is optional here and omitting it returns every
    //                       status (active, matched and closed all belong on your own page).
    //   emitterId absent -> marketplace browse: personal numbers are left null, so they are
    //                       absent from the JSON rather than trusted to the client to hide.
    public List<ListingResponseDTO> getListings(Double minPurity, Double maxPrice,
                                                 ListingStatus status, Long emitterId) {
        boolean ownerScoped = emitterId != null;

        List<Listing> source = ownerScoped
                ? listingRepository.findByEmitterId(emitterId)
                : listingRepository.findByStatus(status != null ? status : ListingStatus.ACTIVE);

        List<Listing> matching = source.stream()
                .filter(listing -> !ownerScoped || status == null || listing.getStatus() == status)
                .filter(listing -> minPurity == null || listing.getPurityPercent() >= minPurity)
                .filter(listing -> maxPrice == null || listing.getPricePerTon() <= maxPrice)
                .toList();

        if (!ownerScoped) {
            return matching.stream().map(listing -> toResponseDto(listing, null)).toList();
        }

        // Resolved for the whole page at once rather than per row - one extra query, not one
        // per listing.
        Map<Long, Integer> personalNumbers = displayIdService.listingDisplayIds(matching);

        // Newest first, which for an owner's own page means ordered by their personal number
        // descending. Two reasons: a listing they just created appears at the top where they
        // expect it, and the column reads as an ordered sequence instead of the arbitrary
        // row order the database happens to return (which showed #2, #3, #1).
        return matching.stream()
                .map(listing -> toResponseDto(listing, personalNumbers.get(listing.getId())))
                .sorted(Comparator.comparing(ListingResponseDTO::personalListingNumber,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    // Plain filter + sort for buyers who don't want to build a full request - no compatibility
    // scoring at all, distinct from the request-based matching flow.
    //
    // `buyerCity` is OPTIONAL. Quick Browse exists to answer a one-filter question ("show me
    // anything above 90% purity"), and forcing an origin on someone who only cares about purity
    // made the fast path slower than the thing it was shortcutting. With a city: distances are
    // computed and results come back nearest-first. Without one: distance is genuinely unknown
    // (not zero, not hidden) so it is returned as null and results are ordered cheapest-first,
    // which is the natural ranking once location stops mattering.
    public List<QuickBrowseResultDTO> quickBrowse(Double minPurity, String buyerCity, Double maxDistanceKm) {
        boolean hasCity = buyerCity != null && !buyerCity.isBlank();

        // Filtering by distance without an origin is not a thing we can silently guess at, so
        // it's a clear 400 rather than a filter that quietly does nothing.
        if (!hasCity && maxDistanceKm != null) {
            throw new BadRequestException("maxDistanceKm needs a buyerCity to measure from");
        }

        CityCoordinates.LatLng buyerCoordinates = hasCity
                ? CityCoordinates.lookup(buyerCity)
                        .orElseThrow(() -> new BadRequestException("buyerCity must be one of the supported cities"))
                : null;

        List<QuickBrowseResultDTO> results = listingRepository.findByStatus(ListingStatus.ACTIVE).stream()
                .filter(listing -> listing.getRemainingVolumeTons() > 0)
                .filter(listing -> minPurity == null || listing.getPurityPercent() >= minPurity)
                .map(listing -> toQuickBrowseResult(listing, buyerCoordinates))
                .filter(result -> maxDistanceKm == null || result.distanceKm() <= maxDistanceKm)
                .toList();

        Comparator<QuickBrowseResultDTO> order = buyerCoordinates != null
                ? Comparator.comparingDouble(QuickBrowseResultDTO::distanceKm)
                : Comparator.comparingDouble(QuickBrowseResultDTO::pricePerTon);

        return results.stream().sorted(order).toList();
    }

    // buyerCoordinates == null means the buyer didn't give a city, so there is no origin to
    // measure from: distanceKm/distanceSource come back null rather than being faked.
    private QuickBrowseResultDTO toQuickBrowseResult(Listing listing, CityCoordinates.LatLng buyerCoordinates) {
        DistanceResult distance = buyerCoordinates == null ? null : distanceService.getDistance(
                listing.getLocationLat(), listing.getLocationLng(),
                buyerCoordinates.lat(), buyerCoordinates.lng());

        return new QuickBrowseResultDTO(
                listing.getId(),
                listing.getEmitterId(),
                listing.getTotalVolumeTons(),
                listing.getRemainingVolumeTons(),
                listing.getPurityPercent(),
                listing.getCaptureMethod(),
                listing.getPricePerTon(),
                listing.getCity(),
                listing.getAddress(),
                distance == null ? null : round2(distance.distanceKm()),
                distance == null ? null : distance.source());
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    Listing requireListing(Long id) {
        return listingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found with id: " + id));
    }

    // personalListingNumber is null for every caller that isn't the owning emitter.
    private ListingResponseDTO toResponseDto(Listing listing, Integer personalListingNumber) {
        return new ListingResponseDTO(
                listing.getId(),
                personalListingNumber,
                listing.getEmitterId(),
                listing.getTotalVolumeTons(),
                listing.getRemainingVolumeTons(),
                listing.getPurityPercent(),
                listing.getCaptureMethod(),
                listing.getPricePerTon(),
                listing.getCity(),
                listing.getAddress(),
                listing.getStatus(),
                listing.getCreatedAt());
    }
}
