# Running CarbonLink

Two pieces: a Spring Boot backend (port 8080) and a React/Vite frontend (port 5173). Run both at once.

## Prerequisites

- **Any JDK from 17 to 26.** No `JAVA_HOME` juggling needed — whatever `mvn -v` reports is fine.

  <details>
  <summary>This used to require JDK 17 specifically. What changed.</summary>

  Two separate JDK-version landmines were fixed in `backend/pom.xml`, both of which only bit on newer JDKs:

  1. **Lombok generated nothing on JDK 23+.** Since [JEP 456](https://openjdk.org/jeps/456), javac no longer runs annotation processors it merely finds on the classpath. Lombok therefore produced no getters or builders, and a clean build died with `cannot find symbol: method getId()` on every entity. Fixed by declaring Lombok as an explicit `<annotationProcessorPaths>` entry with `<proc>full</proc>`, and pinning Lombok forward to 1.18.42.
  2. **Mockito couldn't instrument classes on JDK 24+.** The Byte Buddy version Spring Boot 3.3.4 pins (1.14.19) fails with `Could not modify all classes`, which broke 99 of the test suite's tests. Fixed by pinning `byte-buddy` 1.17.6 / `mockito` 5.18.0.

  Verified: `mvn clean package` succeeds with all tests green on JDK **17**, **24** and **26**.
  </details>
- **Maven** (`brew install maven` if you don't have it)
- **Node.js + npm** (for the frontend)
- *(Optional)* An [OpenRouteService](https://openrouteservice.org/) API key — free tier. Without one, distance calculations transparently fall back to a Haversine estimate; everything still works.

## 1. Backend

If you have a real OpenRouteService key, export it (copy `backend/.env.example` to `backend/.env` as a reminder of the variable name — Spring Boot does **not** auto-load `.env` files, you still need to `export` it into the shell):

```bash
export OPENROUTE_API_KEY="your_real_key_here"
```

Then, from `backend/`:

```bash
cd backend
mvn spring-boot:run
```

- Runs on **http://localhost:8080**.
- H2 file database at `backend/data/` (gitignored) — persists between restarts. Delete that folder for a clean slate.
- H2 console: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:file:./data/carbonlink`, user `sa`, no password).
- Watch the startup logs: if you see `OpenRouteService API key not set — DistanceService will use Haversine fallback for all requests`, either you didn't export the key, or it's genuinely running fallback-only (both fine, just know which mode you're in for a demo).

### Demo mode (seeded data)

To boot with realistic pre-populated data (4 emitters, 4 buyers, 8 listings, 5 requests with deliberately varied match scores) instead of an empty database:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

The seeder only runs if the database is currently empty (it self-skips if data already exists), and only when the `demo` profile is active — a plain `mvn spring-boot:run` never seeds anything.

### Run the backend tests

```bash
mvn test
```

## 2. Frontend

In a separate terminal:

```bash
cd frontend
npm install   # first time only
npm run dev
```

- Runs on **http://localhost:5173**.
- The backend's CORS config allows **any loopback origin on any port** — `localhost`, `127.0.0.1` and `[::1]` alike. That matters: a browser treats those three as *different* origins, so an exact-match allow-list would block the app the moment you opened it at `http://127.0.0.1:5173` instead of `http://localhost:5173`, and axios reports that as a bare **"Network Error"** — which looks exactly like the backend being down.

## 3. Using the app

Open http://localhost:5173. Pick a role, then either:

- **Log in as an existing user** — only shown when users of that role already exist (e.g. after running the `demo` profile). Click a company name to log in instantly with their real data already loaded (listings, matches, everything) — no typing required. This is the fast path for a live demo.
- **Create a new user** — the manual registration form (name, company, city — pick from a dropdown of 23 major Indian cities, no coordinates to type), for when you want to add a fresh emitter or buyer instead.

From there:
- **Emitters** create listings and review/accept/reject incoming matches.
- **Buyers** create requests and see ranked matches with a compatibility score, distance, and cost breakdown, and can "Request Match" on ones they like.
- Neither dashboard auto-polls (no login persistence, by design) — use the **Refresh** link near "Matches" / "Incoming Matches" after switching tabs/roles to pick up anything new.

## Troubleshooting

- **`cannot find symbol: method getId()` / `method builder()` on compile** — annotation processing isn't running, so Lombok generated nothing. The `pom.xml` fix above handles this; if you see it, you're on a `pom.xml` from before that change.
- **Port 8080 already in use / `Failed to start bean 'webServerStartStop'`** — an older backend is still running. `pkill -f carbonlink-backend`, then start again. Because H2 runs with `AUTO_SERVER=TRUE`, a stray instance also keeps serving the *old* database, which can make schema changes look like they failed.
- **"Network Error" in the UI / frontend calls fail** — axios shows the same bare "Network Error" for three different causes, so check them in order:
  1. **Is the backend actually up?** `curl http://localhost:8080/api/cities` should return the city list. If not, start it.
  2. **Did you restart it after a backend code change?** `mvn spring-boot:run` does not hot-reload Java changes.
  3. **Is it a CORS block?** Open DevTools → Network; a blocked request shows as failed with no status code, and the Console names CORS explicitly. Loopback origins are all allowed now, so this should only happen if you're reaching the app over a LAN IP or a tunnel — add that origin to `WebConfig`.
- **All distances show "Haversine Fallback" even with a key set** — check the startup log warning; if it's absent the key loaded fine, and OpenRouteService itself may be rate-limited (free tier: 40 requests/min) or briefly unreachable. Note the circuit breaker: after one failure the app stops calling the API for 60 seconds (`openrouteservice.failure-cooldown-seconds`) and answers from Haversine, so a single blip makes the *next minute* of lookups fall back too. That's deliberate — it's what keeps an unreachable API from costing a 3-second timeout on every single route.
- **`WARNING: A restricted method in java.lang.System has been called`** on startup — harmless JDK 24+ noise from Tomcat's native library, not an app problem. Silence it with `java --enable-native-access=ALL-UNNAMED -jar ...` if it bothers you.
- **Empty dashboard / "create a new user" instead of the seeded login list** — the database is empty. Either you deleted `backend/data/`, or you started without the demo profile. The seeder only runs on an **empty** database *and* only under `-Dspring-boot.run.profiles=demo`, so a plain restart after deleting `data/` gives you a working but completely empty app.
- **Want a totally clean slate** — stop both servers, delete `backend/data/`, then restart **with the demo profile** to reseed.

See [`README.md`](./README.md#api-reference) for the full API reference (every endpoint with request/response shapes), and `PROGRESS.md` for the build history and pre-demo checklist.
