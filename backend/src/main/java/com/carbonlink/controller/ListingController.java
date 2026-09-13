package com.carbonlink.controller;

import com.carbonlink.dto.ListingRequestDTO;
import com.carbonlink.dto.ListingResponseDTO;
import com.carbonlink.dto.MatchResponseDTO;
import com.carbonlink.dto.QuickBrowseResultDTO;
import com.carbonlink.dto.QuickRequestDTO;
import com.carbonlink.entity.ListingStatus;
import com.carbonlink.service.ListingService;
import com.carbonlink.service.MatchService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/listings")
public class ListingController {

    private final ListingService listingService;
    private final MatchService matchService;

    public ListingController(ListingService listingService, MatchService matchService) {
        this.listingService = listingService;
        this.matchService = matchService;
    }

    @PostMapping
    public ResponseEntity<ListingResponseDTO> createListing(@Valid @RequestBody ListingRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(listingService.createListing(dto));
    }

    // `emitterId` switches this from the marketplace browse to an emitter's own "My Listings":
    // it returns every status by default and is the ONLY context in which a response carries
    // personalListingNumber.
    @GetMapping
    public List<ListingResponseDTO> getListings(
            @RequestParam(required = false) Double minPurity,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) ListingStatus status,
            @RequestParam(required = false) Long emitterId) {
        return listingService.getListings(minPurity, maxPrice, status, emitterId);
    }

    // Static literal segment - matched ahead of "/{id}" by Spring's path resolution, so this
    // never gets swallowed by getListing(id) below.
    // Every param is optional: buyerCity turns on distance ranking, but a buyer who only
    // cares about purity (or wants everything) shouldn't have to supply an origin.
    @GetMapping("/quick-browse")
    public List<QuickBrowseResultDTO> quickBrowse(
            @RequestParam(required = false) Double minPurity,
            @RequestParam(required = false) String buyerCity,
            @RequestParam(required = false) Double maxDistanceKm) {
        return listingService.quickBrowse(minPurity, buyerCity, maxDistanceKm);
    }

    @GetMapping("/{id}")
    public ListingResponseDTO getListing(@PathVariable Long id) {
        return listingService.getListing(id);
    }

    // "Request This Listing" from Quick Browse - creates a minimal CarbonRequest + an
    // already-REQUESTED Match in one step, bypassing the full request-based matching flow.
    @PostMapping("/{id}/quick-request")
    public ResponseEntity<MatchResponseDTO> quickRequest(@PathVariable Long id,
                                                          @Valid @RequestBody QuickRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(matchService.quickRequest(id, dto));
    }

    // Default (no status param): REQUESTED only ("Incoming Matches" - actionable). status=FINAL:
    // ACCEPTED/REJECTED/CANCELLED/REVERTED ("Order Status" archive). status=<name>: that one status.
    @GetMapping("/{id}/matches")
    public List<MatchResponseDTO> getMatches(@PathVariable Long id, @RequestParam(required = false) String status) {
        return matchService.getMatchesForListing(id, status);
    }
}
