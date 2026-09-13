# Session Changes

Files created or modified in this session (stale-match fixes, ID visibility, revert/cancel lifecycle, location/city visibility, transport cost investigation + same-city distance floor, and the Order/Receipt feature).

## New files

### Backend
- `backend/src/main/java/com/carbonlink/entity/Order.java` — settlement record entity
- `backend/src/main/java/com/carbonlink/entity/OrderStatus.java` — `CONFIRMED` / `REVERTED`
- `backend/src/main/java/com/carbonlink/repository/OrderRepository.java`
- `backend/src/main/java/com/carbonlink/dto/OrderResponseDTO.java`
- `backend/src/main/java/com/carbonlink/dto/UnviewedCountDTO.java` — "NEW" badge peek response
- `backend/src/main/java/com/carbonlink/service/OrderService.java`
- `backend/src/main/java/com/carbonlink/service/OrderNumberGenerator.java` — `CL-{year}-{seq}` numbering
- `backend/src/main/java/com/carbonlink/controller/OrderController.java`
- `backend/src/test/java/com/carbonlink/service/OrderServiceTest.java`
- `backend/src/test/java/com/carbonlink/service/OrderNumberGeneratorTest.java`

### Frontend
- `frontend/src/pages/Receipt.jsx` — order settlement summary view

## Modified files

### Backend
- `backend/src/main/java/com/carbonlink/entity/Match.java` — added `viewed` boolean
- `backend/src/main/java/com/carbonlink/entity/MatchStatus.java` — added `CANCELLED`, `REVERTED`
- `backend/src/main/java/com/carbonlink/entity/User.java` — added `city`
- `backend/src/main/java/com/carbonlink/entity/Listing.java` — added `city`
- `backend/src/main/java/com/carbonlink/dto/MatchResponseDTO.java` — added `statusMessage`, `viewed`, `emitterCity`, `buyerCity`, `orderId`
- `backend/src/main/java/com/carbonlink/dto/UserResponseDTO.java` — added `city`
- `backend/src/main/java/com/carbonlink/dto/ListingResponseDTO.java` — added `city`
- `backend/src/main/java/com/carbonlink/repository/MatchRepository.java` — added `findByListingIdIn`, `findByRequestIdIn`
- `backend/src/main/java/com/carbonlink/service/MatchService.java` — stale-match filtering, `cancel()`/`revert()`, status messages, viewed/unviewed-count, city fields, Order creation on accept + reversion on revert
- `backend/src/main/java/com/carbonlink/service/UserService.java` — persist/return `city`
- `backend/src/main/java/com/carbonlink/service/ListingService.java` — persist/return `city`
- `backend/src/main/java/com/carbonlink/service/DistanceService.java` — 10 km minimum-distance floor (same-city matches)
- `backend/src/main/java/com/carbonlink/seed/DemoDataSeeder.java` — pass city names for seeded users/listings
- `backend/src/main/java/com/carbonlink/controller/MatchController.java` — `cancel`, `revert`, `unviewed-count` endpoints
- `backend/src/main/java/com/carbonlink/controller/CarbonRequestController.java` — `sent-matches` endpoint
- `backend/src/main/resources/application.properties` — transport rate lowered 8.0 → 3.0
- `backend/src/test/java/com/carbonlink/service/MatchServiceTest.java` — extensive new coverage (stale matches, cancel/revert, status messages, viewed badges, orders)
- `backend/src/test/java/com/carbonlink/service/DistanceServiceTest.java` — minimum-distance floor tests
- `backend/src/test/java/com/carbonlink/service/CostEstimatorServiceTest.java` — exact-numbers regression test (150t/182.1km/₹8 → ₹218,520)

### Frontend
- `frontend/src/App.jsx` — `receiptOrderId` state, navigation into/out of `Receipt`
- `frontend/src/api/api.js` — new calls for cancel/revert/sent-matches/unviewed-count/orders
- `frontend/src/components/MatchCard.jsx` — IDs, city line, statusMessage box, stale handling, Cancel/Revert/View Receipt buttons
- `frontend/src/pages/EmitterDashboard.jsx` — listing IDs, Revert button, "NEW" badge, Order History section
- `frontend/src/pages/BuyerDashboard.jsx` — "My Sent Requests" section, "NEW" badge, Order History section

### Docs
- `PROGRESS.md` — full write-up of every change above, updated API reference, updated Pre-Demo Checklist
- `TESTING.md` — updated test counts and walkthrough notes
