# CarbonLink — Carbon Capture-to-Product Matchmaking Platform

## Problem

Industries that capture CO2 (cement plants, steel works, power plants) often have no visibility into who could actually use that captured carbon — so it gets vented or landfilled instead of reused. On the other side, businesses that turn CO2 into fuel, building materials, or food have no reliable way to find a steady, cost-effective supply. Today this matching happens informally, if at all — through personal networks and one-off deals — with no shared marketplace and no way to compare offers on purity, volume, distance, and price at once.

## Solution

CarbonLink is a two-sided marketplace connecting carbon **emitters** (who list captured CO2 for sale) with carbon **buyers** (who request CO2 meeting their volume, purity, distance, and budget needs). It automatically scores every emitter listing against every buyer request using a transparent, rule-based compatibility formula, estimates real transport cost using road-distance logistics, and lets both sides review, request, and accept/reject matches — no manual searching or negotiation required to find the candidates worth talking to.

## Tech Stack

- **Backend**: Java 17 (builds and runs on JDK 17–26), Spring Boot 3.3, Spring Data JPA, H2
- **Frontend**: React, Tailwind CSS
- **Distance/Logistics**: OpenRouteService API (with Haversine geometric fallback for resilience/offline capability)

## Key Features

- Rule-based compatibility matching (purity, volume, distance, price — weighted scoring, no black-box ML)
- Real road-distance logistics cost estimation
- City-based location input (no coordinates needed from users)
- Resilient distance calculation (falls back gracefully if the routing API is unavailable — including a circuit breaker so an unreachable API costs one timeout, not one per route)
- Optimistic locking on listing stock, so two simultaneous accepts can never oversell the same supply
- CORS accepts any loopback origin, so the app works whether you open it at `localhost`, `127.0.0.1` or `[::1]`
- Dark UI built on a neutral slate palette, with green reserved for positive status and primary actions rather than used as the background
- **Light and dark themes**, switchable from anywhere in the app. Dark is the default and the OS preference is deliberately not consulted — light is opt-in, and the choice persists across reloads

## How to Run

Two pieces to run at once — full details, troubleshooting, and demo-mode notes are in [`RUNNING.md`](./RUNNING.md). Quick version:

**Backend** (any JDK from 17 to 26 — no specific version needed):
```bash
export OPENROUTE_API_KEY="your_real_key_here"   # optional — falls back to Haversine if omitted

cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=demo   # -Dspring-boot.run.profiles=demo seeds realistic sample data
```
Runs on **http://localhost:8080**.

**Frontend**:
```bash
cd frontend
npm install
npm run dev
```
Runs on **http://localhost:5173**.

Open the frontend, pick a role, and either log in as one of the seeded users or register a new one.

## Architecture Overview

**Matching (`MatchingService`)** — for a buyer's request, every currently-`ACTIVE` listing is scored on four independent 0–100 sub-scores, then combined into a single weighted compatibility score:

| Sub-score | Weight | Rule |
|---|---|---|
| Purity | 30% | 100 if listing purity ≥ required purity, else scaled proportionally (`purity / required × 100`) |
| Volume | 30% | 100 if listing volume ≥ required volume, else scaled proportionally (`volume / required × 100`) |
| Distance | 20% | 100 if within the buyer's max distance, else `100 − (excess km)`, floored at 0 |
| Price | 20% | 100 if listing price ≤ budget, else `100 − (excess ₹ × 2)`, floored at 0 |

```
compatibilityScore = purity×0.3 + volume×0.3 + distance×0.2 + price×0.2
```

Only listings scoring **≥ 50** are returned, ranked highest-first. This keeps the whole thing auditable — every score on a match card can be traced back to a plain formula, not a trained model.

**Cost estimation (`CostEstimatorService`)** — for a given match, transport cost is distance-and-volume-driven, using a configurable per-km-per-ton rate (default ₹1):

```
transportCost = distanceKm × ratePerKmPerTon × volumeTons
totalCost      = (listingPricePerTon × volumeTons) + transportCost
```

**Distance (`DistanceService`)** — tries the OpenRouteService Matrix API for real road distance first; on any failure, timeout, or missing API key, it transparently falls back to a Haversine straight-line estimate (× 1.2 to approximate road routing — calibrated against ten real Indian highway routes, where the true road/great-circle ratio runs 1.14–1.25) so the app never blocks on a third-party API. Every match records which source was actually used (`OPENROUTESERVICE` or `HAVERSINE_FALLBACK`). Three details that matter in practice:

- **Result cache** keyed on coordinates rounded to 4 decimal places — repeated city pairs cost one lookup, and a pair that resolved via fallback keeps that same number for the rest of the run rather than silently changing distance (and cost) mid-session.
- **Circuit breaker** — one failure skips the API entirely for `openrouteservice.failure-cooldown-seconds` (default 60). Without it, matching a request against listings in *N* different cities would pay the full 3-second timeout *N* times over.
- **10 km floor** — every location is a city centroid, so a buyer and emitter in the same city are exactly 0 km apart. Local haulage still costs something, so any distance under 10 km is floored.

**Why freight often rivals the base price.** CO₂ is a low-value bulk commodity: at ₹1200/ton, moving 400 tons Mumbai→Bangalore costs about as much as the gas itself. That is not a modelling error — the configured ₹1/ton-km is already **2–3× cheaper** than real Indian road freight (a ~22 t full truckload on that route runs ₹50,000–70,000, i.e. ₹2.3–3.3/ton-km), and CO₂ moves in cryogenic tankers, which cost more than dry bulk. Freight dominating the total on long hauls is exactly the economics this marketplace exists to optimise, and it is why distance carries real weight in the match score.

**Theming (`utils/theme.js` + `index.css`)** — two complete palettes defined as CSS custom properties: `:root` for dark and `:root[data-theme="light"]` for light. Components never hardcode a colour, so switching is one attribute on `<html>` and nothing branches per theme. Three details worth knowing:

- **The accent is three tokens, not one.** `--accent` for fills, `--accent-text` for text and icons, `--on-accent` for whatever sits on a filled accent surface. The same green that works as a button fill on white fails contrast as text on it, so light mode darkens `--accent-text` specifically.
- **Status colours are defined per theme.** The dark recipe (a 10%-opacity tint behind a bright text colour) washes out on white, so `--info-*`, `--warn-*`, `--neutral-*` and `--danger-*` each carry separate light values.
- **Dark is the default and `prefers-color-scheme` is deliberately ignored.** Honouring it put anyone on a light-mode machine into light without asking. An explicit past choice wins; otherwise dark.

## API Reference

All 27 endpoints, base URL `http://localhost:8080/api`. Every error response, from every endpoint, has the same shape:

```json
{ "error": "Listing not found with id: 9999", "status": 404 }
```

### Global IDs vs. personal numbers

Every entity has one real, global `id` — that is the only identifier used for matching, linking orders, and every lookup in the system.

On top of that, an owner sees their own rows numbered from 1: an emitter's listings are their #1, #2, #3 regardless of the global ids, and a buyer's requests likewise. These are computed per request (never stored) and are **cosmetic labels only**.

They are scoped on the server, not in the client. `personalListingNumber` is present only in responses explicitly scoped to the owning emitter, and `@JsonInclude(NON_NULL)` means it is *absent from the JSON entirely* everywhere else — a buyer browsing the marketplace never receives it. That's deliberate: the number is only unique within one owner's set, so two emitters both have a "Listing #1", and letting that escape into a shared view would produce ids that look real and collide. On a match, only the viewer's own side is ever sent.

`400` = bad input, `404` = unknown id, `409` = the action conflicts with current state (already decided, stock claimed elsewhere, invalid status transition), `500` = unexpected (message is always the flat string `"Internal server error"` — details go to the server log only, never to the client).

### Reference data

| Method | Path | Purpose | Request | Response |
|---|---|---|---|---|
| GET | `/cities` | The 23 supported city names, alphabetical. Backs every city dropdown. | — | `["Ahmedabad", "Bangalore", ...]` |

### Auth

| Method | Path | Purpose | Request | Response |
|---|---|---|---|---|
| POST | `/auth/login` | Sign in with a username and password. Username is case-insensitive. Returns the same user record every other endpoint identifies a user by. A wrong password and an unknown username return the identical `400` so the response can't be used to enumerate accounts. | `{username, password}` | `UserResponse` |

Demo account credentials are listed in [`DEMO_ACCOUNTS.md`](./DEMO_ACCOUNTS.md) — all 17 seeded accounts use the password `123`.

> Scope: this **authenticates** (proves who you are), it does not **authorize**. There are no sessions or tokens, and every other endpoint remains open exactly as before.

### Users

| Method | Path | Purpose | Request | Response |
|---|---|---|---|---|
| POST | `/users` | Register an emitter or buyer. City resolves to internal coordinates server-side. `username` must be unique; the password is stored BCrypt-hashed. | `{name, companyName, role: "EMITTER"\|"BUYER", city, username?, password?, address?}` | `201` `UserResponse` |
| GET | `/users` | List users; `?role=EMITTER\|BUYER` filters, omitted returns all. Backs the "log in as existing user" list. | — | `[UserResponse]` |
| GET | `/users/{id}` | One user. `404` if unknown. | — | `UserResponse` |

`UserResponse` → `{id, name, companyName, username, role, city, address, createdAt}` — the password hash is never exposed

### Listings (emitter supply)

| Method | Path | Purpose | Request | Response |
|---|---|---|---|---|
| POST | `/listings` | Create a listing. Its `city` is the supply site's own, independent of the emitter's registered city. `400` on a non-EMITTER `emitterId`. | `{emitterId, totalVolumeTons, purityPercent, captureMethod, pricePerTon, city, address?}` | `201` `ListingResponse` |
| GET | `/listings` | Browse the marketplace. `?minPurity` (inclusive), `?maxPrice` (inclusive), `?status` (default `ACTIVE`). Add `?emitterId=` for an emitter's own "My Listings": returns **every** status and is the only listing response carrying `personalListingNumber`. | — | `[ListingResponse]` |
| GET | `/listings/quick-browse` | Quick Browse: plain filter + sort, **no** compatibility scoring and no `≥50` gate. `ACTIVE` listings with stock only. **All params optional.** With `buyerCity`: distances computed, nearest first. Without: `distanceKm`/`distanceSource` are `null` and results are cheapest-first. `maxDistanceKm` without a `buyerCity` is a `400`. | `?buyerCity`, `?minPurity`, `?maxDistanceKm` | `[QuickBrowseResult]` |
| GET | `/listings/{id}` | One listing. `404` if unknown. | — | `ListingResponse` |
| POST | `/listings/{id}/quick-request` | "Request This Listing" — creates a minimal request **and** an already-`REQUESTED` match in one call. `409` if the listing went inactive, or if `minVolumeNeeded` exceeds the listing's `remainingVolumeTons`. | `{buyerId, minVolumeNeeded}` | `201` `MatchResponse` |
| GET | `/listings/{id}/matches` | Emitter's view of matches on their listing. No param = `REQUESTED` only ("Incoming Matches"); `?status=FINAL` = the decided archive; `?status=<NAME>` = one status. Marks unviewed `CANCELLED` rows viewed. | — | `[MatchResponse]` |

`ListingResponse` → `{id, emitterId, totalVolumeTons, remainingVolumeTons, purityPercent, captureMethod, pricePerTon, city, address, status, createdAt}` **+ `personalListingNumber` only when `?emitterId=` was supplied**
`QuickBrowseResult` → the listing fields above (as `listingId`) **+** `{distanceKm, distanceSource}`, no `status`/`createdAt`

> Coordinates are deliberately **not** in either shape — they're an internal derivation of `city`, and every client renders the city name and reads distances pre-computed.

### Requests (buyer demand)

| Method | Path | Purpose | Request | Response |
|---|---|---|---|---|
| POST | `/requests` | Create a request. No location field — distance always comes from the buyer's registered city. | `{buyerId, minVolumeNeeded, minPurityRequired, maxDistanceKm, maxBudgetPerTon, intendedUse}` | `201` `RequestResponse` |
| GET | `/requests` | List requests; `?buyerId` filters, omitted returns all. Only the `?buyerId=` form carries `personalRequestNumber`. | — | `[RequestResponse]` |
| GET | `/requests/{id}` | One request. `404` if unknown. | — | `RequestResponse` |
| GET | `/requests/{id}/matches` | **Find Matches** — fresh scoring for this request, `SUGGESTED` only, `≥50` score, ranked high to low. Returns empty once the request is no longer `OPEN`. No `status` param. | — | `[MatchResponse]` |
| GET | `/requests/{buyerId}/sent-matches` | Everything this buyer has *acted on*, across all their requests (a passive suggestion is excluded). No param = all sent; `?status=REQUESTED` = "Pending Requests"; `?status=FINAL` = "Order Status". | — | `[MatchResponse]` |

`RequestResponse` → `{id, buyerId, minVolumeNeeded, minPurityRequired, maxDistanceKm, maxBudgetPerTon, intendedUse, status, createdAt}` **+ `personalRequestNumber` only when `?buyerId=` was supplied**

> Note the two paths are keyed differently: `/requests/{id}/matches` takes a **request** id, `/requests/{buyerId}/sent-matches` takes a **buyer** id.

### Matches (the lifecycle)

`SUGGESTED → REQUESTED → ACCEPTED → REVERTED`, with `REJECTED` / `CANCELLED` as alternate endings. All take no request body.

| Method | Path | Purpose | Fails with |
|---|---|---|---|
| POST | `/matches/{id}/request` | Buyer expresses interest: `SUGGESTED → REQUESTED`. Touches no stock. | `409` if already decided |
| POST | `/matches/{id}/accept` | Emitter accepts. **The only place stock is ever deducted.** Sets `transactedVolumeTons` to the buyer's full ask, closes the listing if under 1 t remains, sets the request `MATCHED`, and creates the `Order`. **Never fills partially** — a listing that can't cover the ask is refused outright rather than quietly rewriting the deal. | `409` if already decided, if the listing is no longer `ACTIVE`/the request no longer `OPEN`, **if the remaining stock is less than the requested volume**, or if another accept won the race for the same stock |
| POST | `/matches/{id}/reject` | Emitter declines. Stock, listing and request all untouched. | `409` same conditions as accept |
| POST | `/matches/{id}/cancel` | Buyer withdraws a `REQUESTED` match. No stock was ever deducted. Reopens the request **unless** it is already `MATCHED` by a different accepted match. | `409` unless status is exactly `REQUESTED` |
| POST | `/matches/{id}/revert` | Undoes an accept: adds `transactedVolumeTons` back (capped at total), reopens listing and request, flips the `Order` to `REVERTED` (never deletes it). | `409` unless status is exactly `ACCEPTED`, **or if the shipment has already been dispatched** (`deliveryStatus` past `CONFIRMED`) |
| GET | `/matches/unviewed-count` | "NEW" badge peek. Pass exactly **one** of `?buyerId` / `?emitterId`. Does **not** mark anything viewed. | `400` if zero or both given |

`MatchResponse` →
```json
{
  "id": 1, "listingId": 1, "requestId": 1,
  "compatibilityScore": 100.0, "distanceKm": 182.1, "distanceSource": "HAVERSINE_FALLBACK",
  "status": "ACCEPTED",
  "scoreBreakdown": { "purityScore": 100.0, "volumeScore": 100.0, "distanceScore": 100.0, "priceScore": 100.0 },
  "costBreakdown": { "basePrice": 120000.0, "transportCost": 18210.0, "totalCost": 138210.0,
                     "volumeTons": 100.0, "distanceKm": 182.1, "distanceSource": "HAVERSINE_FALLBACK" },
  "transactedVolumeTons": 100.0,
  "createdAt": "2026-09-12T19:36:48.194",
  "statusMessage": "Accepted by Ambuja Cement Plant — 100.0 tons confirmed",
  "viewed": true,
  "emitterCity": "Mumbai", "buyerCity": "Nashik",
  "orderId": 1
}
```
`transactedVolumeTons` is `null` until accepted; `statusMessage` is `null` for `SUGGESTED`/`REQUESTED` and otherwise phrased from the viewpoint of whichever side asked; `orderId` is `null` unless the match is `ACCEPTED` or `REVERTED`. `unviewed-count` returns `{count}`.

### Orders (settlement)

| Method | Path | Purpose | Request | Response |
|---|---|---|---|---|
| GET | `/orders/{id}` | The permanent settlement record. Every price/distance figure is snapshotted at accept time, so it never drifts if the listing's price changes later. | — | `OrderResponse` |
| GET | `/orders/by-user/{userId}` | Every order where this user is emitter **or** buyer, newest first. Empty list for an unknown id (no existence check). | — | `[OrderResponse]` |
| POST | `/orders/{id}/pay` | Records that the buyer settled this order (`paymentStatus` → `PAID`) **and completes the shipment** (`deliveryStatus` → `DELIVERED`) — payment is the last step, so settling up finishes the order rather than leaving it waiting on one more emitter click. Idempotent; `409` on a non-`CONFIRMED` order. Simulated checkout only — no payment provider, no card data, no money movement. | — | `OrderResponse` |
| POST | `/orders/{id}/delivery-status` | Advance shipment exactly one step: `CONFIRMED → IN_TRANSIT → DELIVERED`. Emitter-driven; buyers see it read-only. | `{"status": "IN_TRANSIT"\|"DELIVERED"}` | `OrderResponse` |

`OrderResponse` → `{id, orderNumber, matchId, emitterId, buyerId, listingId, requestId, transactedVolumeTons, pricePerTon, basePrice, transportCost, totalCost, distanceKm, distanceSource, status, deliveryStatus, paymentStatus, createdAt}`

`status` is `CONFIRMED`/`REVERTED` (settlement) and is separate from `deliveryStatus` (physical shipment). Delivery updates `409` on a skip-ahead, a step backward, anything past `DELIVERED`, any order whose `status` isn't `CONFIRMED`, **or a move to `DELIVERED` while `paymentStatus` is still `PENDING`** — handover can't be recorded before the buyer has paid, though dispatch (`IN_TRANSIT`) is allowed unpaid. `orderNumber` is `CL-{year}-{4-digit sequence}`, re-seeded from the database on restart so numbers never collide.

### Analytics

| Method | Path | Purpose | Response |
|---|---|---|---|
| GET | `/analytics/summary` | Platform-wide impact metrics for the Impact Dashboard. Counts `CONFIRMED` orders only — a reverted order drops out of every aggregate. All money and tonnage figures are rounded to 2 dp. | see below |

```json
{
  "totalListingsCount": 8, "totalActiveListings": 8,
  "totalRequestsCount": 5, "totalOpenRequests": 3,
  "totalOrdersConfirmed": 2,
  "totalVolumeTransactedTons": 500.0, "totalValueTransacted": 1057762.0, "totalCo2DivertedTons": 500.0,
  "topCaptureMethod": "Oxy-fuel",
  "ordersByMonth": [ { "month": "2026-09", "count": 2, "volumeTons": 500.0 } ]
}
```

`topCaptureMethod` is `null` when no listings are active; `ordersByMonth` is `[]` when nothing is confirmed.

## A note on the payment step

A buyer opening a receipt passes through a **simulated checkout** (method choice → processing → success). There is no payment provider, no card data and no money movement. The "processing" delay is a timer. It does record one fact — `Order.paymentStatus` → `PAID` — because the **emitter** needs it: an order cannot be marked `DELIVERED` until the buyer has paid, and that can't be driven off a flag living in someone else's browser session. The order's own status is untouched (`CONFIRMED` since acceptance). Both screens carry a visible disclaimer.

## Demo Flow

1. Select a seeded user from the dropdown (e.g. "Ambuja Cement Plant" as Emitter, or "GreenFuel Synthetics" as Buyer)
2. **[Emitter]** View existing listings, or create a new one
3. **[Buyer]** Create a request, view ranked matches with compatibility scores and full cost breakdown
4. Accept or reject a match

## Team

_(placeholder — to be filled in)_
