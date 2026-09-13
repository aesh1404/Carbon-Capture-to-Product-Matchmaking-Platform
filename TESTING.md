# Testing CarbonLink

A practical checklist to confirm everything built so far actually works. Two layers:
1. **Automated** — the backend's unit test suite (fast, run this first).
2. **Manual** — a step-by-step walkthrough of the API (curl) and the UI (browser), organized so you can tick through it and see the real behavior with your own eyes.

Nothing here is destructive — feel free to restart the backend with a clean `backend/data/` between sections if you want a fresh slate (see `RUNNING.md`).

---

## 1. Automated tests (do this first)

```bash
cd backend
mvn test
```

**Expect:** `Tests run: 207, Failures: 0, Errors: 0` and `BUILD SUCCESS`. (Any JDK from 17 to 26 works — no `JAVA_HOME` export needed. If you instead see ~99 errors reading `Could not modify all classes`, or a compile failure saying `cannot find symbol: method getId()`, you're on a `pom.xml` from before the final audit pass — see `RUNNING.md`.) This covers the matching formula (boundary cases), cost estimation (including an exact-numbers regression test at 150t/182.1km/₹8 → ₹218,520), distance fallback logic (including the same-city 10km minimum-distance floor), the full accept-flow volume-tracking behavior (stock deduction, auto-close, partial fulfillment), the stale-match guard (409 on accept/reject once the underlying request/listing has moved on), the revert/cancel lifecycle + status messages + viewed/unviewed-count badge logic, Order creation/reversion/numbering, the Find Matches / Pending Requests / Order Status / Incoming Matches status-filtering split, and — added in the final audit pass — optimistic locking against concurrent stock deduction (a real `@DataJpaTest`, not a mock), the `cancel()` guard against reopening an already-`MATCHED` request, the `revert()` guard against reversing a dispatched order, analytics rounding, the marketplace `getListings` filter boundaries, `UserService` role/city validation, and the OpenRouteService key short-circuit + failure circuit breaker — all without needing the app running.

---

## 2. Start everything

```bash
# Terminal 1 — backend (demo profile seeds realistic data so you're not typing everything by hand)
export OPENROUTE_API_KEY="your_key_here"   # optional
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

```bash
# Terminal 2 — frontend
cd frontend
npm install   # first time only
npm run dev
```

**Expect:** backend log shows `Demo seed complete: 8 users, 8 listings, 5 requests` and no `OpenRouteService API key not set` warning (if you set a real key). Frontend serves at http://localhost:5173.

---

## 3. Manual API testing (curl) — happy path

Run these in order; each step's output feeds the next.

```bash
BASE=http://localhost:8080/api

# 3a. Cities — should list ~20 names, alphabetical
curl -s "$BASE/cities" | jq .

# 3b. List seeded users by role
curl -s "$BASE/users?role=EMITTER" | jq -c '.[] | {id, companyName}'
curl -s "$BASE/users?role=BUYER" | jq -c '.[] | {id, companyName}'

# 3c. Register a new emitter via city (not raw lat/lng)
EMITTER=$(curl -s -X POST "$BASE/users" -H "Content-Type: application/json" -d '{
  "name": "Test Emitter", "companyName": "Test Cement Co", "role": "EMITTER", "city": "Pune"
}')
echo "$EMITTER" | jq .
EMITTER_ID=$(echo "$EMITTER" | jq -r .id)
# Expect: "city": "Pune" in the response. Coordinates are resolved server-side (18.52, 73.856) but are
# deliberately NOT returned - they're internal, and every client renders the city name instead.

# 3d. Create a listing for that emitter
LISTING=$(curl -s -X POST "$BASE/listings" -H "Content-Type: application/json" -d "{
  \"emitterId\": $EMITTER_ID, \"totalVolumeTons\": 500, \"purityPercent\": 96,
  \"captureMethod\": \"Post-combustion\", \"pricePerTon\": 1000, \"city\": \"Pune\"
}")
echo "$LISTING" | jq .
LISTING_ID=$(echo "$LISTING" | jq -r .id)
# Expect: totalVolumeTons=500, remainingVolumeTons=500 (equal — freshly created), status=ACTIVE

# 3e. Register a buyer and create a request
BUYER=$(curl -s -X POST "$BASE/users" -H "Content-Type: application/json" -d '{
  "name": "Test Buyer", "companyName": "Test Fuels Co", "role": "BUYER", "city": "Mumbai"
}')
BUYER_ID=$(echo "$BUYER" | jq -r .id)

REQUEST=$(curl -s -X POST "$BASE/requests" -H "Content-Type: application/json" -d "{
  \"buyerId\": $BUYER_ID, \"minVolumeNeeded\": 200, \"minPurityRequired\": 90,
  \"maxDistanceKm\": 500, \"maxBudgetPerTon\": 1500, \"intendedUse\": \"Fuel Synthesis\"
}")
REQUEST_ID=$(echo "$REQUEST" | jq -r .id)

# 3f. Get ranked matches for the request
curl -s "$BASE/requests/$REQUEST_ID/matches" | jq .
# Expect: a list including your listing, with compatibilityScore, scoreBreakdown, distanceKm +
# distanceSource, costBreakdown, and transactedVolumeTons: null (not yet decided)
MATCH_ID=$(curl -s "$BASE/requests/$REQUEST_ID/matches" | jq -r ".[] | select(.listingId==$LISTING_ID) | .id")

# 3g. Buyer expresses interest
curl -s -X POST "$BASE/matches/$MATCH_ID/request" | jq -c '{status}'
# Expect: status: "REQUESTED"

# 3h. Emitter sees it on their own listing's incoming matches
curl -s "$BASE/listings/$LISTING_ID/matches" | jq -c '.[] | {id, status}'
# Expect: the same match, status REQUESTED

# 3i. Emitter accepts
curl -s -X POST "$BASE/matches/$MATCH_ID/accept" | jq .
# Expect: status ACCEPTED, transactedVolumeTons: 200 (the smaller of request need vs. remaining),
# costBreakdown recalculated for 200 tons (not the listing's full 500)

# 3j. Confirm stock was deducted, listing still ACTIVE (500-200=300 left)
curl -s "$BASE/listings/$LISTING_ID" | jq -c '{totalVolumeTons, remainingVolumeTons, status}'
# Expect: remainingVolumeTons: 300, status: ACTIVE (not fully depleted, still matchable)

# 3k. Confirm the request is now MATCHED
curl -s "$BASE/requests/$REQUEST_ID" | jq -c '{status}'
```

---

## 4. Manual API testing — validation & error cases

```bash
BASE=http://localhost:8080/api

# Invalid role on user creation -> 400
curl -s -w " [%{http_code}]\n" -X POST "$BASE/users" -H "Content-Type: application/json" \
  -d '{"name":"X","companyName":"Y","role":"MANAGER","city":"Mumbai"}'

# Invalid city -> 400
curl -s -w " [%{http_code}]\n" -X POST "$BASE/users" -H "Content-Type: application/json" \
  -d '{"name":"X","companyName":"Y","role":"EMITTER","city":"Atlantis"}'

# Missing required field -> 400
curl -s -w " [%{http_code}]\n" -X POST "$BASE/users" -H "Content-Type: application/json" \
  -d '{"name":"X","role":"EMITTER","city":"Mumbai"}'

# Negative volume on listing -> 400
curl -s -w " [%{http_code}]\n" -X POST "$BASE/listings" -H "Content-Type: application/json" \
  -d '{"emitterId":1,"totalVolumeTons":-5,"purityPercent":95,"captureMethod":"DAC","pricePerTon":900,"city":"Mumbai"}'

# Purity out of range -> 400
curl -s -w " [%{http_code}]\n" -X POST "$BASE/listings" -H "Content-Type: application/json" \
  -d '{"emitterId":1,"totalVolumeTons":10,"purityPercent":150,"captureMethod":"DAC","pricePerTon":900,"city":"Mumbai"}'

# Listing with emitterId pointing at a BUYER user -> 400 (role mismatch)
# (swap in a real BUYER id from step 3b above)
curl -s -w " [%{http_code}]\n" -X POST "$BASE/listings" -H "Content-Type: application/json" \
  -d '{"emitterId":5,"totalVolumeTons":10,"purityPercent":95,"captureMethod":"DAC","pricePerTon":900,"city":"Mumbai"}'

# Nonexistent user id -> 404
curl -s -w " [%{http_code}]\n" "$BASE/users/99999"
curl -s -w " [%{http_code}]\n" "$BASE/listings/99999"
curl -s -w " [%{http_code}]\n" "$BASE/requests/99999"
curl -s -w " [%{http_code}]\n" -X POST "$BASE/matches/99999/accept"

# Double-accept the same match -> 409 (use MATCH_ID from section 3)
curl -s -w " [%{http_code}]\n" -X POST "$BASE/matches/$MATCH_ID/accept"
# Expect: {"error":"Match X has already been accepted","status":409}

# Malformed JSON -> 400
curl -s -w " [%{http_code}]\n" -X POST "$BASE/users" -H "Content-Type: application/json" -d '{not valid json'
```

All error bodies should look like `{ "error": "<message>", "status": <code> }`.

---

## 5. Manual frontend testing — Role Selector & city dropdown

Open http://localhost:5173.

1. Landing page shows "CarbonLink" + "I'm an Emitter" / "I'm a Buyer" buttons.
2. Click **I'm an Emitter** → since demo data exists, you should land directly on a **login list** ("Log in as an existing emitter") showing all 4 seeded emitters (Ambuja Cement Plant, Tata Steel Works, JSW Power Plant, UltraTech Cement).
3. Click **"or create a new user"** → form appears with Name, Company name, and a **City dropdown** (no lat/lng fields). Confirm the dropdown lists ~20 cities with a "Select your city" placeholder.
4. Fill in the form and submit → should land on the Emitter Dashboard with a fresh user (no listings yet).
5. Click **Switch User** (top right) → back to role selector. Repeat for Buyer — confirm the 4 seeded buyers show (GreenFuel Synthetics, EcoBuild Materials, AlgaeGrow Farms, CarbonCure Concrete).

---

## 6. Manual frontend testing — Emitter flow

1. Log in as **Ambuja Cement Plant** (existing user, one click).
2. **My Listings** table shows their real seeded listings — check the column is **"Remaining / Total (tons)"**, e.g. `800 / 800`.
3. Fill in **Create Listing** (pick a city from the dropdown, not coordinates) and submit → new row appears in the table immediately with `remaining = total` and status `ACTIVE`.
4. **Incoming Matches** section: click **Refresh**. If empty, that's expected until a buyer has actually queried matches against this emitter's listings (matches are only computed/persisted when a buyer views them — see step 7 below, then come back and refresh here).
5. Once a match appears: confirm you see counterpart company name, compatibility bar (colored — green >75%, yellow 50–75%, red <50%, though only ≥50 ever appears), distance + source label, cost breakdown, and **Accept**/**Reject** buttons.
6. Click **Accept** → card updates in place to `ACCEPTED` status, shows **"Matching X tons of this listing's Y total"**, and the listing's row in "My Listings" table updates its remaining/total accordingly (no page reload needed).

---

## 7. Manual frontend testing — Buyer flow

1. **Switch User** → log in as **GreenFuel Synthetics** (has 2 seeded requests) — matches for the most recent request should load **immediately**, no typing required. Confirm the request selector dropdown (top of "Matches") lets you switch between their two requests.
2. Fill in **Create Request** and submit → ranked match cards appear below, sorted by compatibility score descending.
3. On a card with status `SUGGESTED`, click **Request Match** → status flips to `REQUESTED` in place, button disappears.
4. Switch to the emitter that owns that listing (see section 6) and confirm the same match now shows up as `REQUESTED` with Accept/Reject available.
5. Accept it from the emitter side, then switch back to the buyer and click **Refresh** — the card should now show `ACCEPTED` and the transacted-volume line, not have vanished.

---

## 8. Stock depletion scenario (the volume-tracking feature)

This is the most important one to confirm end-to-end — it shows a listing surviving partial sales and correctly closing once exhausted.

1. As an emitter, create a listing with **totalVolumeTons = 1000**.
2. As Buyer A, create a request needing **300** tons (generous purity/distance/budget so it matches easily) and accept the resulting match.
   - **Expect:** listing shows `700 / 1000`, status still `ACTIVE`.
3. As Buyer B, create a *new* request needing **700** tons against the same listing, and confirm it still shows up as a match (scored against the 700 remaining, not the original 1000) — **not filtered out** just because it's not "full stock" anymore.
4. Accept Buyer B's match.
   - **Expect:** listing now shows `0 / 1000`, status **`CLOSED`**.
5. As Buyer C, create a third request that would otherwise match this listing easily (same city, generous terms).
   - **Expect:** this listing does **not** appear in Buyer C's results at all — it's closed and no longer matchable.
6. On the emitter's dashboard, both of the accepted match cards (Buyer A's and Buyer B's) should independently show the correct **"Matching 300 tons..."** / **"Matching 700 tons..."** lines and their own correctly-scaled cost breakdowns.

---

## 9. Distance fallback (resilience)

1. With the backend running and a real `OPENROUTE_API_KEY` set, get matches for any request — `distanceSource` should be `OPENROUTESERVICE` and the UI should show **"OpenRouteService"**.
2. Turn off wifi (or unset/invalidate the API key and restart the backend), then get matches for a **new** (previously unseen) coordinate pair.
   - **Expect:** no crash, no error shown to the user — `distanceSource` falls back to `HAVERSINE_FALLBACK`, and the UI shows **"Haversine Fallback"** with the layout unchanged.
3. Check the backend startup log: if the key was never set, you should see `OpenRouteService API key not set — DistanceService will use Haversine fallback for all requests` at boot.

Note: `DistanceService` caches results per coordinate pair for the life of the running backend — if you already looked up a pair successfully via the real API earlier in the session, re-querying the *same* pair will keep returning the cached `OPENROUTESERVICE` result even with wifi off. Use a fresh city pair you haven't queried yet to see the fallback trigger live.

---

## 10. Stale match scenario (two buyers, one supply)

Reproduces a real bug found via live testing: a match card kept showing live Accept/Reject buttons after its underlying request/listing had already moved on.

1. As an emitter, create a listing with **totalVolumeTons = 300**.
2. As Buyer A, create a request needing **300** tons (generous purity/distance/budget) and note the match id shown on the card (small gray "Match #N · Listing #N · Request #N" line).
3. As Buyer B, create a *second, separate* request that also matches the same listing (before accepting A's match) — confirm Buyer B's dashboard shows the match with live "Request Match" available.
4. Accept Buyer A's match.
   - **Expect:** listing shows `0 / 300`, status **`CLOSED`**; Buyer A's request is now `MATCHED`.
5. Refresh Buyer B's matches (`GET /api/requests/{buyerBRequestId}/matches`, or the "Refresh" link in the UI).
   - **Expect:** the stale match against the now-closed listing is **gone entirely** from the response/UI — not shown with live buttons.
6. On the emitter's "Incoming Matches", refresh.
   - **Expect:** if Buyer B's match row was ever fetched before the accept, it either disappears on refresh or (if somehow still cached client-side before a refresh completes) shows "This match is no longer available" with Accept/Reject disabled — never a live, clickable pair of buttons.
7. `curl -X POST $BASE/api/matches/{buyerBsMatchId}/accept` directly (bypassing the UI) against the stale match id from step 3.
   - **Expect:** `409 Conflict` with `{"error": "Listing N is no longer active", "status": 409}` — never a `200` and never a `500`/stack trace.

---

## Quick reference — what "working" looks like

| Area | Confirms |
|---|---|
| `mvn test` → 207/207 | Scoring formulas, cost math (incl. the exact 150t/182.1km/₹8 regression case), distance fallback (incl. the same-city floor), accept-flow volume tracking, the stale-match guard, revert/cancel/statusMessage/viewed-badge logic, Order creation/reversion/numbering, and the tab-filtering status logic (Find Matches/Pending Requests/Order Status/Incoming Matches) are all correct in isolation |
| Section 3 (curl happy path) | The full backend flow works end-to-end, real data |
| Section 4 (curl errors) | Validation, 404s, 409s all return clean `{error, status}` bodies |
| Section 5–7 (browser) | The actual UI a judge would click through works, not just the API |
| Section 8 (depletion) | Partial fulfillment, auto-close, re-matchability |
| Section 9 (distance fallback) | Haversine fallback triggers cleanly when OpenRouteService is unavailable |
| Section 10 (stale match) | Two buyers on one listing: the loser's match disappears/becomes non-actionable, and Accept on it 409s instead of silently succeeding |
| Section 9 (fallback) | The app survives OpenRouteService being down/rate-limited/unset |

See `RUNNING.md` for setup/troubleshooting and `PROGRESS.md` for the full build history and API reference.
