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
- **PostgreSQL** (local install or Docker). This machine already uses PostgreSQL 17 on port 5432.
- *(Optional)* An [OpenRouteService](https://openrouteservice.org/) API key — free tier. Without one, distance calculations transparently fall back to a Haversine estimate; everything still works.

## 1. Backend

If you have a real OpenRouteService key, export it (copy `backend/.env.example` to `backend/.env` as a reminder of the variable name — Spring Boot does **not** auto-load `.env` files, you still need to `export` it into the shell):

```bash
export OPENROUTE_API_KEY="your_real_key_here"
```

PostgreSQL must be running. Local credentials go in gitignored `backend/src/main/resources/application-secret.properties` (`postgres` user, database `carbonlink`). Then, from `backend/`:

```bash
cd backend
mvn spring-boot:run
```

- Runs on **http://localhost:8080**.
- Data lives in PostgreSQL (`carbonlink`) and survives restarts.
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
- The frontend calls `/api`, and Vite proxies that to `http://localhost:8080`. You do not need a separate API origin locally.
- The backend's CORS config still allows **any loopback origin on any port** — `localhost`, `127.0.0.1` and `[::1]` alike — in case something talks to port 8080 directly. A browser treats those three as *different* origins, so an exact-match allow-list would block the app the moment you opened it at `http://127.0.0.1:5173` instead of `http://localhost:5173`, and axios reports that as a bare **"Network Error"** — which looks exactly like the backend being down.

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
- **Port 8080 already in use / `Failed to start bean 'webServerStartStop'`** — an older backend is still running. `pkill -f carbonlink-backend`, then start again.
- **`Connection to localhost:5432 refused` / `FATAL: password authentication failed`** — PostgreSQL is not running, or `application-secret.properties` does not match your local user/password. Database name is `carbonlink`.
- **"Network Error" in the UI / frontend calls fail** — axios shows the same bare "Network Error" for three different causes, so check them in order:
  1. **Is the backend actually up?** `curl http://localhost:8080/api/cities` should return the city list. If not, start it.
  2. **Did you restart it after a backend code change?** `mvn spring-boot:run` does not hot-reload Java changes.
  3. **Is it a CORS block?** Open DevTools → Network; a blocked request shows as failed with no status code, and the Console names CORS explicitly. Loopback origins are all allowed now, so this should only happen if you're reaching the app over a LAN IP or a tunnel — add that origin to `WebConfig`.
- **All distances show "Haversine Fallback" even with a key set** — check the startup log warning; if it's absent the key loaded fine, and OpenRouteService itself may be rate-limited (free tier: 40 requests/min) or briefly unreachable. Note the circuit breaker: after one failure the app stops calling the API for 60 seconds (`openrouteservice.failure-cooldown-seconds`) and answers from Haversine, so a single blip makes the *next minute* of lookups fall back too. That's deliberate — it's what keeps an unreachable API from costing a 3-second timeout on every single route.
- **`WARNING: A restricted method in java.lang.System has been called`** on startup — harmless JDK 24+ noise from Tomcat's native library, not an app problem. Silence it with `java --enable-native-access=ALL-UNNAMED -jar ...` if it bothers you.
- **Empty dashboard / "create a new user" instead of the seeded login list** — the database is empty. Either this is a fresh Postgres volume, or you started without the demo profile. The seeder only runs on an **empty** database *and* only under `-Dspring-boot.run.profiles=demo`, so a plain restart gives you a working but completely empty app.
- **Want a totally clean slate** — stop the backend, drop and recreate the `carbonlink` database in PostgreSQL, then restart **with the demo profile** to reseed.

## 4. Free deployment (Render)

This is a real public HTTPS URL, which is what LinkedIn's certificate/project link field needs. The free tier is enough for a demo: the site sleeps after ~15 minutes idle and takes about a minute to wake on the next visit.

**Limits to know:** Render's free Postgres expires **30 days** after creation (14-day grace to upgrade). The web URL itself stays. If you need the database longer without paying Render, create a free [Neon](https://neon.tech) or [Supabase](https://supabase.com) Postgres and paste its `DATABASE_URL` into the Render web service env vars instead of using Render's database.

### Deploy from GitHub

1. Push this repo to GitHub (you already have `origin` if you cloned the project).
2. Sign in at [https://dashboard.render.com](https://dashboard.render.com) with GitHub. No credit card is required for free services.
3. **New → Blueprint** and select this repository. Render reads `render.yaml` and creates:
   - a **free Postgres** database named `carbonlink-db`
   - a **free Docker web service** that builds the frontend into the Spring Boot JAR
4. Wait for the first deploy (several minutes). The public URL looks like `https://carbonlink.onrender.com`.
5. Open that URL. First load after idle can take ~30–60 seconds. Demo accounts are seeded (`SPRING_PROFILES_ACTIVE=demo`; passwords in [`DEMO_ACCOUNTS.md`](./DEMO_ACCOUNTS.md)).

Manual alternative: **New → Web Service**, connect the repo, runtime Docker, instance **Free**, then **New → Postgres** (Free) and add env var `DATABASE_URL` from the database's Internal Database URL. Set `SPRING_PROFILES_ACTIVE=demo`.

### LinkedIn — attach the live URL to your certificate

LinkedIn will not let anyone else type this in for you. After Render gives you `https://….onrender.com`:

1. Open [linkedin.com](https://www.linkedin.com) → **Me** → **View Profile**.
2. If the certificate is already on your profile: click the **pencil** on that **Licenses & certifications** entry.
3. If you are adding it: **Add profile section** → **Recommended** → **Licenses & certifications**. Fill in the name, issuing organization, and dates.
4. Tick **This credential does not expire** if that applies.
5. In **Credential URL** (or **Credential ID** plus URL, depending on the form), paste the Render HTTPS link.
6. **Save**.

To also show it as a project (often more visible than a certificate URL):

1. **Add profile section** → **Recommended** → **Projects** (or **Featured** → **Add a link**).
2. Title: CarbonLink — Carbon Capture-to-Product Matchmaking Platform.
3. Paste the same Render URL.
4. Short description: two-sided marketplace matching CO₂ emitters with buyers; Spring Boot, React, PostgreSQL.

See [`README.md`](./README.md#api-reference) for the full API reference (every endpoint with request/response shapes), and `PROGRESS.md` for the build history and pre-demo checklist.
