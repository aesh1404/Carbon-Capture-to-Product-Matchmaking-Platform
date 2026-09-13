package com.carbonlink.constants;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CityCoordinates {

    public record LatLng(double lat, double lng) {
    }

    private static final Map<String, LatLng> CITIES = Map.ofEntries(
            Map.entry("Mumbai", new LatLng(19.076, 72.877)),
            Map.entry("Delhi", new LatLng(28.613, 77.209)),
            Map.entry("Bangalore", new LatLng(12.972, 77.594)),
            Map.entry("Chennai", new LatLng(13.083, 80.270)),
            Map.entry("Kolkata", new LatLng(22.573, 88.364)),
            Map.entry("Hyderabad", new LatLng(17.385, 78.487)),
            Map.entry("Pune", new LatLng(18.520, 73.856)),
            Map.entry("Ahmedabad", new LatLng(23.023, 72.571)),
            Map.entry("Surat", new LatLng(21.170, 72.831)),
            Map.entry("Jaipur", new LatLng(26.912, 75.787)),
            Map.entry("Lucknow", new LatLng(26.847, 80.946)),
            Map.entry("Kanpur", new LatLng(26.449, 80.332)),
            Map.entry("Nagpur", new LatLng(21.146, 79.089)),
            Map.entry("Indore", new LatLng(22.719, 75.858)),
            Map.entry("Bhopal", new LatLng(23.259, 77.412)),
            Map.entry("Visakhapatnam", new LatLng(17.687, 83.219)),
            Map.entry("Vadodara", new LatLng(22.307, 73.181)),
            Map.entry("Coimbatore", new LatLng(11.017, 76.956)),
            Map.entry("Chandigarh", new LatLng(30.734, 76.779)),
            Map.entry("Kochi", new LatLng(9.932, 76.267)),
            // Both of these are the home cities of seeded demo buyers. They were previously
            // written straight into the seed data without existing here, which meant those
            // buyers held a city that /api/cities never returned - so Quick Browse (a dropdown
            // fed by that endpoint) could not offer them their own location. Coordinates match
            // the seed values exactly, so no match score or distance changes.
            Map.entry("Nashik", new LatLng(19.997, 73.789)),
            Map.entry("Guwahati", new LatLng(26.144, 91.736)),
            // Home of Tata Steel's main works, and a genuine heavy-industry hub in its own
            // right - the seeded "Tata Steel Jamshedpur" account was previously parked in
            // Kolkata (~240 km away) purely because this entry didn't exist.
            Map.entry("Jamshedpur", new LatLng(22.804, 86.203))
    );

    private CityCoordinates() {
    }

    public static List<String> cityNames() {
        return CITIES.keySet().stream().sorted().toList();
    }

    public static Optional<LatLng> lookup(String cityName) {
        return Optional.ofNullable(CITIES.get(cityName));
    }
}
