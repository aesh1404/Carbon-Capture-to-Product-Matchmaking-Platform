package com.carbonlink.controller;

import com.carbonlink.dto.CarbonRequestDTO;
import com.carbonlink.dto.CarbonRequestResponseDTO;
import com.carbonlink.dto.MatchResponseDTO;
import com.carbonlink.service.CarbonRequestService;
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
@RequestMapping("/api/requests")
public class CarbonRequestController {

    private final CarbonRequestService carbonRequestService;
    private final MatchService matchService;

    public CarbonRequestController(CarbonRequestService carbonRequestService, MatchService matchService) {
        this.carbonRequestService = carbonRequestService;
        this.matchService = matchService;
    }

    @PostMapping
    public ResponseEntity<CarbonRequestResponseDTO> createRequest(@Valid @RequestBody CarbonRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(carbonRequestService.createRequest(dto));
    }

    @GetMapping
    public List<CarbonRequestResponseDTO> getRequests(@RequestParam(required = false) Long buyerId) {
        return carbonRequestService.getRequests(buyerId);
    }

    @GetMapping("/{id}")
    public CarbonRequestResponseDTO getRequest(@PathVariable Long id) {
        return carbonRequestService.getRequest(id);
    }

    // "Find Matches": fresh, actionable, unrequested (SUGGESTED) recommendations for this request only.
    @GetMapping("/{id}/matches")
    public List<MatchResponseDTO> getMatches(@PathVariable Long id) {
        return matchService.getRankedMatchesWithCost(id);
    }

    // Every match across all of this buyer's requests that they've actually acted on. Default
    // (no status param): everything sent (REQUESTED/ACCEPTED/REJECTED/CANCELLED/REVERTED).
    // status=REQUESTED: "Pending Requests". status=FINAL: "Order Status" archive.
    @GetMapping("/{buyerId}/sent-matches")
    public List<MatchResponseDTO> getSentMatches(@PathVariable Long buyerId,
                                                  @RequestParam(required = false) String status) {
        return matchService.getSentMatchesForBuyer(buyerId, status);
    }
}
