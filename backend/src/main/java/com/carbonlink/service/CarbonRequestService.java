package com.carbonlink.service;

import com.carbonlink.dto.CarbonRequestDTO;
import com.carbonlink.dto.CarbonRequestResponseDTO;
import com.carbonlink.entity.CarbonRequest;
import com.carbonlink.entity.Role;
import com.carbonlink.exception.ResourceNotFoundException;
import com.carbonlink.repository.CarbonRequestRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CarbonRequestService {

    private final CarbonRequestRepository carbonRequestRepository;
    private final UserService userService;
    private final DisplayIdService displayIdService;

    public CarbonRequestService(CarbonRequestRepository carbonRequestRepository, UserService userService,
                                 DisplayIdService displayIdService) {
        this.carbonRequestRepository = carbonRequestRepository;
        this.userService = userService;
        this.displayIdService = displayIdService;
    }

    public CarbonRequestResponseDTO createRequest(CarbonRequestDTO dto) {
        userService.requireUserWithRole(dto.buyerId(), Role.BUYER);

        CarbonRequest request = CarbonRequest.builder()
                .buyerId(dto.buyerId())
                .minVolumeNeeded(dto.minVolumeNeeded())
                .minPurityRequired(dto.minPurityRequired())
                .maxDistanceKm(dto.maxDistanceKm())
                .maxBudgetPerTon(dto.maxBudgetPerTon())
                .intendedUse(dto.intendedUse())
                .build();

        CarbonRequest saved = carbonRequestRepository.save(request);
        return toResponseDto(saved, displayIdService.requestDisplayId(saved));
    }

    // Emitter-facing enrichment lookup (incoming match cards). No personal number: an emitter
    // must only ever see a buyer's request by its global id.
    public CarbonRequestResponseDTO getRequest(Long id) {
        return toResponseDto(requireRequest(id), null);
    }

    public List<CarbonRequestResponseDTO> getRequests(Long buyerId) {
        List<CarbonRequest> requests = buyerId != null
                ? carbonRequestRepository.findByBuyerId(buyerId)
                : carbonRequestRepository.findAll();
        // Only a buyer asking for their OWN requests gets personal numbering; the unscoped
        // listing is an admin/debug view and carries global ids only.
        if (buyerId == null) {
            return requests.stream().map(request -> toResponseDto(request, null)).toList();
        }
        Map<Long, Integer> personalNumbers = displayIdService.requestDisplayIds(requests);
        return requests.stream()
                .map(request -> toResponseDto(request, personalNumbers.get(request.getId())))
                .toList();
    }

    CarbonRequest requireRequest(Long id) {
        return carbonRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found with id: " + id));
    }

    private CarbonRequestResponseDTO toResponseDto(CarbonRequest request, Integer personalRequestNumber) {
        return new CarbonRequestResponseDTO(
                request.getId(),
                personalRequestNumber,
                request.getBuyerId(),
                request.getMinVolumeNeeded(),
                request.getMinPurityRequired(),
                request.getMaxDistanceKm(),
                request.getMaxBudgetPerTon(),
                request.getIntendedUse(),
                request.getStatus(),
                request.getCreatedAt());
    }
}
