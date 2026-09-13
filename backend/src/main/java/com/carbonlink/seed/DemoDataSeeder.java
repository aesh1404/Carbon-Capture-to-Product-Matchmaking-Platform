package com.carbonlink.seed;

import com.carbonlink.constants.CityCoordinates;
import com.carbonlink.entity.Listing;
import com.carbonlink.entity.Role;
import com.carbonlink.entity.User;
import com.carbonlink.repository.ListingRepository;
import com.carbonlink.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// Only active with --spring.profiles.active=demo.
//
// Seeds 9 emitter companies (14 listings between them) and 8 buyer companies, spread across
// 17 different cities so distance genuinely varies from one match to the next rather than
// every route collapsing to the same two or three city pairs.
//
// Deliberately seeds NO carbon requests: the demo creates those live, so the audience watches
// a real request get built, scored and matched instead of being shown pre-cooked results. The
// listing pool below is sized and varied (purity 85-99.5%, price Rs740-2500/t, volume 50-2500 t,
// four capture methods) so a request typed on the day has plenty to match against at every
// threshold - strong matches, marginal ones, and some that correctly fall below the cutoff.
//
// The Impact Dashboard is backed by a separate historical archive (HistoricalOrderSeeder):
// ~30 settled orders over the last five months, drawn from their own already-consumed
// listings so live demo stock is untouched.
@Component
@Profile("demo")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    // Every seeded account shares one throwaway password so a presenter never has to remember
    // sixteen of them. Stored BCrypt-hashed like any other; see DEMO_ACCOUNTS.md for the list.
    // Demo-only credentials on a local, disposable database - not a pattern for anything real.
    private static final String DEMO_PASSWORD = "123";

    private final UserRepository userRepository;
    private final ListingRepository listingRepository;
    private final HistoricalOrderSeeder historicalOrderSeeder;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public DemoDataSeeder(UserRepository userRepository, ListingRepository listingRepository,
                           HistoricalOrderSeeder historicalOrderSeeder) {
        this.userRepository = userRepository;
        this.listingRepository = listingRepository;
        this.historicalOrderSeeder = historicalOrderSeeder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Demo seed skipped - database already has data");
            return;
        }

        log.info("Seeding demo data...");

        // --- Emitters: real Indian heavy industry, one per city ---
        User ambuja = saveUser("Rakesh Mehta", "Ambuja Cement Works", Role.EMITTER, "Mumbai", "ambuja");
        User tataSteel = saveUser("Sunita Rao", "Tata Steel Jamshedpur", Role.EMITTER, "Jamshedpur", "tatasteel");
        User jswEnergy = saveUser("Karthik Iyer", "JSW Energy", Role.EMITTER, "Bangalore", "jswenergy");
        User ultraTech = saveUser("Priya Shah", "UltraTech Cement", Role.EMITTER, "Ahmedabad", "ultratech");
        User adaniPower = saveUser("Nilesh Trivedi", "Adani Power", Role.EMITTER, "Surat", "adanipower");
        User vedanta = saveUser("Lakshmi Menon", "Vedanta Aluminium", Role.EMITTER, "Visakhapatnam", "vedanta");
        User hindalco = saveUser("Devendra Joshi", "Hindalco Industries", Role.EMITTER, "Nagpur", "hindalco");
        User accCement = saveUser("Harpreet Gill", "ACC Cement", Role.EMITTER, "Chandigarh", "acccement");
        User ntpc = saveUser("Ravi Srivastava", "NTPC Thermal Plant", Role.EMITTER, "Kanpur", "ntpc");

        // --- Buyers: varied downstream uses, deliberately no pre-built requests ---
        saveUser("Anjali Nair", "GreenFuel Synthetics", Role.BUYER, "Nashik", "greenfuel");
        saveUser("Vikram Singh", "EcoBuild Materials", Role.BUYER, "Pune", "ecobuild");
        saveUser("Meera Pillai", "AlgaeGrow Farms", Role.BUYER, "Kochi", "algaegrow");
        saveUser("Arjun Desai", "CarbonCure Concrete", Role.BUYER, "Delhi", "carboncure");
        saveUser("Sneha Reddy", "SynFuel Technologies", Role.BUYER, "Hyderabad", "synfuel");
        saveUser("Gautam Subramanian", "TerraForm Bioplastics", Role.BUYER, "Coimbatore", "terraform");
        saveUser("Ishita Sharma", "PureAir Carbon Solutions", Role.BUYER, "Jaipur", "pureair");
        saveUser("Rohit Kulkarni", "BioCrete Industries", Role.BUYER, "Indore", "biocrete");

        // --- Listings: 1-2 per emitter. Spread deliberately so no single filter flattens the
        // pool - there is cheap-but-impure bulk, boutique high-purity DAC, and everything in
        // between, at volumes from a 50 t trial batch to a 2500 t industrial supply. ---
        saveListing(ambuja, 800, 97.0, "Post-combustion", 1200);
        saveListing(ambuja, 150, 99.5, "Direct Air Capture", 2200);
        saveListing(tataSteel, 1500, 91.0, "Pre-combustion", 900);
        saveListing(tataSteel, 600, 94.5, "Oxy-fuel", 1050);
        saveListing(jswEnergy, 2000, 89.0, "Oxy-fuel", 780);
        saveListing(jswEnergy, 350, 96.0, "Post-combustion", 1150);
        saveListing(ultraTech, 600, 99.0, "Direct Air Capture", 1800);
        saveListing(ultraTech, 50, 85.0, "Pre-combustion", 2500);
        saveListing(adaniPower, 2500, 88.0, "Post-combustion", 740);
        saveListing(vedanta, 900, 93.0, "Pre-combustion", 980);
        saveListing(hindalco, 450, 95.5, "Oxy-fuel", 1250);
        saveListing(accCement, 700, 92.0, "Post-combustion", 1020);
        saveListing(ntpc, 1800, 90.5, "Post-combustion", 820);
        saveListing(ntpc, 120, 99.2, "Direct Air Capture", 2350);

        long liveListings = listingRepository.count();

        // Trading history for the Impact Dashboard. Strictly separated from the fresh listings
        // above: it draws on its own archive of already-sold-through supply, so nothing here
        // touches the stock a presenter will click on.
        int historicalOrders = historicalOrderSeeder.seed(
                userRepository.findByRole(Role.EMITTER), userRepository.findByRole(Role.BUYER));

        log.info("Demo seed complete: {} users ({} emitters, {} buyers), {} live listings, "
                        + "0 live requests (created during the demo), {} historical orders",
                userRepository.count(),
                userRepository.findByRole(Role.EMITTER).size(),
                userRepository.findByRole(Role.BUYER).size(),
                liveListings,
                historicalOrders);
        log.info("Demo sign-in: every seeded account uses password '{}' - see DEMO_ACCOUNTS.md", DEMO_PASSWORD);
    }

    // Coordinates are resolved through CityCoordinates rather than written out here, so a
    // seeded city can never be one that /api/cities doesn't offer (which previously left two
    // demo buyers unable to pick their own city in Quick Browse). A typo now fails loudly at
    // startup instead of producing a subtly unusable demo account.
    private static CityCoordinates.LatLng coordinatesOf(String city) {
        return CityCoordinates.lookup(city).orElseThrow(() -> new IllegalStateException(
                "Demo seed uses city '" + city + "', which is not in CityCoordinates"));
    }

    private User saveUser(String name, String companyName, Role role, String city, String username) {
        CityCoordinates.LatLng coordinates = coordinatesOf(city);
        return userRepository.save(User.builder()
                .name(name)
                .companyName(companyName)
                .role(role)
                .city(city)
                .username(username)
                .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                .locationLat(coordinates.lat())
                .locationLng(coordinates.lng())
                .build());
    }

    // A listing sits at its emitter's own city - these companies each have one site in this
    // demo, so there's no separate per-listing location to model.
    private void saveListing(User emitter, double volume, double purity, String captureMethod,
                              double pricePerTon) {
        CityCoordinates.LatLng coordinates = coordinatesOf(emitter.getCity());
        listingRepository.save(Listing.builder()
                .emitterId(emitter.getId())
                .totalVolumeTons(volume)
                .purityPercent(purity)
                .captureMethod(captureMethod)
                .pricePerTon(pricePerTon)
                .city(emitter.getCity())
                .locationLat(coordinates.lat())
                .locationLng(coordinates.lng())
                .build());
    }
}
