package com.carbonlink.controller;

import com.carbonlink.dto.MatchResponseDTO;
import com.carbonlink.dto.UnviewedCountDTO;
import com.carbonlink.service.MatchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @PostMapping("/{id}/accept")
    public MatchResponseDTO accept(@PathVariable Long id) {
        return matchService.accept(id);
    }

    @PostMapping("/{id}/reject")
    public MatchResponseDTO reject(@PathVariable Long id) {
        return matchService.reject(id);
    }

    @PostMapping("/{id}/cancel")
    public MatchResponseDTO cancel(@PathVariable Long id) {
        return matchService.cancel(id);
    }

    @PostMapping("/{id}/revert")
    public MatchResponseDTO revert(@PathVariable Long id) {
        return matchService.revert(id);
    }

    @PostMapping("/{id}/request")
    public MatchResponseDTO request(@PathVariable Long id) {
        return matchService.request(id);
    }

    @GetMapping("/unviewed-count")
    public UnviewedCountDTO unviewedCount(@RequestParam(required = false) Long buyerId,
                                           @RequestParam(required = false) Long emitterId) {
        return matchService.getUnviewedCount(buyerId, emitterId);
    }
}
