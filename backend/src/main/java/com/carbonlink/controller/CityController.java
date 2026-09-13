package com.carbonlink.controller;

import com.carbonlink.constants.CityCoordinates;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cities")
public class CityController {

    @GetMapping
    public List<String> getCities() {
        return CityCoordinates.cityNames();
    }
}
