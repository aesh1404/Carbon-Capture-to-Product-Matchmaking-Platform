# CarbonLink — Demo Account Credentials

Every seeded account uses the same password so there's nothing to memorise mid-demo.

> **Password for all 17 accounts: `123`**

Usernames are case-insensitive (`Ambuja`, `ambuja` and `AMBUJA` all work).

These accounts only exist when the backend is started with the demo profile:

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

---

## Emitters — 9 accounts

Sign in at the **Emitter Portal** (`http://localhost:5173/#/login/emitter`).

| Username | Password | Company | City | Contact | Listings |
|---|---|---|---|---|---|
| `ambuja` | `123` | Ambuja Cement Works | Mumbai | Rakesh Mehta | 2 |
| `tatasteel` | `123` | Tata Steel Jamshedpur | Jamshedpur | Sunita Rao | 2 |
| `jswenergy` | `123` | JSW Energy | Bangalore | Karthik Iyer | 2 |
| `ultratech` | `123` | UltraTech Cement | Ahmedabad | Priya Shah | 2 |
| `adanipower` | `123` | Adani Power | Surat | Nilesh Trivedi | 1 |
| `vedanta` | `123` | Vedanta Aluminium | Visakhapatnam | Lakshmi Menon | 1 |
| `hindalco` | `123` | Hindalco Industries | Nagpur | Devendra Joshi | 1 |
| `acccement` | `123` | ACC Cement | Chandigarh | Harpreet Gill | 1 |
| `ntpc` | `123` | NTPC Thermal Plant | Kanpur | Ravi Srivastava | 2 |

## Buyers — 8 accounts

Sign in at the **Buyer Portal** (`http://localhost:5173/#/login/buyer`).

| Username | Password | Company | City | Contact |
|---|---|---|---|---|
| `greenfuel` | `123` | GreenFuel Synthetics | Nashik | Anjali Nair |
| `ecobuild` | `123` | EcoBuild Materials | Pune | Vikram Singh |
| `algaegrow` | `123` | AlgaeGrow Farms | Kochi | Meera Pillai |
| `carboncure` | `123` | CarbonCure Concrete | Delhi | Arjun Desai |
| `synfuel` | `123` | SynFuel Technologies | Hyderabad | Sneha Reddy |
| `terraform` | `123` | TerraForm Bioplastics | Coimbatore | Gautam Subramanian |
| `pureair` | `123` | PureAir Carbon Solutions | Jaipur | Ishita Sharma |
| `biocrete` | `123` | BioCrete Industries | Indore | Rohit Kulkarni |

---

## Suggested demo pairing

Pick one from each side so the two dashboards tell one story:

| Role | Account | Why |
|---|---|---|
| Buyer | `greenfuel` (Nashik) | Closest buyer to the Mumbai and Pune supply — matches score high and distances stay believable |
| Emitter | `ambuja` (Mumbai) | Two listings at opposite ends of the range: 800 t at ₹1200 and a boutique 150 t at 99.5% purity / ₹2200 |

Sign in as the buyer in one browser window and the emitter in another (or a private window) to run both sides of a match live.

---

## Notes

- **Buyers start with no requests.** That's deliberate — the demo creates one live so the audience watches a real request get built, scored and matched. All 14 emitter listings are at full stock and untouched.
- **The Impact Dashboard already has history.** ~30 settled orders across the last five months are seeded separately, drawn from their own archive of sold-through listings, so the chart has a real trend from the first second without consuming any of the live demo stock.
- **Sign-up works too.** "Don't have an account? Sign up" creates a new emitter or buyer with your own username and password — useful if you'd rather demo registration than use a seeded account.

## Security note

These are throwaway credentials for a local, disposable H2 database that is recreated from scratch on every demo run. `123` is chosen to be typed quickly on stage, not to protect anything.

Passwords are still stored **BCrypt-hashed**, never in plain text, and the hash is never returned by any API response — a demo shortcut in the *choice of password* shouldn't become a bad pattern in the *code*. A real deployment would need password strength rules, sessions or tokens, and per-endpoint authorization, none of which exist here: sign-in identifies who you are, it does not yet restrict what you can reach.
