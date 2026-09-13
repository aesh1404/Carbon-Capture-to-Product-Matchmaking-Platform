import { useEffect, useState } from 'react'
import ThemeToggle from '../components/ThemeToggle'
import { motion, AnimatePresence } from 'framer-motion'
import { Factory, Leaf, ArrowLeft, User, Building2, MapPin, Home, KeyRound, AtSign } from 'lucide-react'
import { createUser, getCities, login, extractErrorMessage } from '../api/api'

// One component, two portals. The sign-in mechanics are identical for both sides - that was
// never the thing worth duplicating - but each side gets its own accent, icon and wording so
// it reads as a separate entrance rather than one form with a variable in it.
//
// This is a real credential prompt: it asks who you are, it does not list everyone who has an
// account. The earlier one-click roster was convenient for a rehearsal, but it published the
// entire user directory to anyone who opened the page - not something a marketplace does.
//
// Tailwind class strings are written out in full here rather than assembled from fragments:
// the scanner only sees literals in the source, so `text-${accent}-400` would silently
// produce no CSS at all.
const THEME = {
  EMITTER: {
    label: 'Emitter',
    portalName: 'Emitter Portal',
    tagline: 'Supply side · publish and sell captured CO₂',
    Icon: Factory,
    glow: 'bg-[var(--accent-soft)]',
    iconWrap: 'bg-[var(--accent-soft)] border border-[var(--accent-soft-border)] text-[var(--accent-text)]',
    card: 'bg-[var(--surface)] border border-[var(--accent-soft-border)]',
    rule: 'border-[var(--border)]',
    accentText: 'text-[var(--accent-text)]',
    accentTextHover: 'hover:text-[var(--accent-text)]',
    fieldIcon: 'text-[var(--accent-text)]',
    field: 'w-full border border-[var(--accent-soft-border)] bg-[var(--surface-light)] text-[var(--text-strong)] rounded-xl px-4 py-2.5 focus:ring-2 focus:ring-emerald-500 outline-none transition-all placeholder:text-[var(--faint)] text-sm',
    option: 'bg-[var(--background)]',
    submit: 'w-full bg-gradient-to-r from-emerald-500 to-teal-500 text-black font-bold px-4 py-3 rounded-xl disabled:opacity-50 transition-all shadow-lg shadow-emerald-500/20 mt-2 text-sm',
    companyHint: 'e.g. EcoCapture Ltd.',
  },
  BUYER: {
    label: 'Buyer',
    portalName: 'Buyer Portal',
    tagline: 'Demand side · source verified CO₂ supply',
    Icon: Leaf,
    glow: 'bg-[var(--accent-soft)]',
    iconWrap: 'bg-[var(--accent-soft)] border border-[var(--accent-soft-border)] text-[var(--accent-text)]',
    card: 'bg-[var(--surface)] border border-[var(--accent-soft-border)]',
    rule: 'border-[var(--border)]',
    accentText: 'text-[var(--accent-text)]',
    accentTextHover: 'hover:text-teal-200',
    fieldIcon: 'text-[var(--accent-text)]',
    field: 'w-full border border-[var(--accent-soft-border)] bg-[var(--surface-light)] text-[var(--text-strong)] rounded-xl px-4 py-2.5 focus:ring-2 focus:ring-teal-500 outline-none transition-all placeholder:text-[var(--faint)] text-sm',
    option: 'bg-[var(--background)]',
    submit: 'w-full bg-gradient-to-r from-teal-400 to-emerald-500 text-black font-bold px-4 py-3 rounded-xl disabled:opacity-50 transition-all shadow-lg shadow-teal-500/20 mt-2 text-sm',
    companyHint: 'e.g. GreenFuel Synthetics',
  },
}

export default function LoginPortal({ role, onRegistered, onBack, appTheme, onToggleTheme }) {
  const theme = THEME[role]
  const { Icon } = theme

  const [mode, setMode] = useState('signin') // 'signin' | 'signup'

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')

  const [name, setName] = useState('')
  const [companyName, setCompanyName] = useState('')
  const [city, setCity] = useState('')
  const [address, setAddress] = useState('')

  const [cities, setCities] = useState([])
  const [citiesError, setCitiesError] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    getCities()
      .then(setCities)
      .catch((err) => setCitiesError(extractErrorMessage(err)))
  }, [])

  // Switching tabs shouldn't carry a stale failure across with it.
  function switchMode(next) {
    setMode(next)
    setError(null)
  }

  async function handleSignIn(e) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      onRegistered(await login({ username, password }))
    } catch (err) {
      setError(extractErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  // Credentials are required at sign-up, not optional: an account created without them could
  // never be signed into again, which is a dead end rather than a shortcut.
  async function handleSignUp(e) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const user = await createUser({
        name,
        companyName,
        role,
        city,
        address: address.trim() || null,
        username,
        password,
      })
      onRegistered(user)
    } catch (err) {
      setError(extractErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="min-h-screen bg-[var(--background)] text-[var(--text)] flex flex-col items-center justify-center p-6 relative overflow-hidden">
      {onToggleTheme && <ThemeToggle theme={appTheme} onToggle={onToggleTheme} floating />}

      <div className={`absolute top-0 left-1/2 -translate-x-1/2 w-[600px] h-[300px] rounded-full blur-3xl pointer-events-none ${theme.glow}`} />

      <motion.div
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        className="w-full max-w-md mx-auto z-10"
      >
        <button
          type="button"
          onClick={onBack}
          className="flex items-center gap-2 text-sm font-medium text-[var(--muted)] hover:text-[var(--text-strong)] mb-6 transition-colors"
        >
          <ArrowLeft size={16} /> Back to marketplace
        </button>

        <div className={`${theme.card} p-8 rounded-3xl shadow-2xl backdrop-blur-xl`}>
          <div className="flex items-center gap-3 mb-6">
            <div className={`p-2.5 rounded-xl ${theme.iconWrap}`}>
              <Icon size={22} />
            </div>
            <div>
              <h1 className="text-xl font-bold text-[var(--text-strong)] leading-tight">{theme.portalName}</h1>
              <p className="text-[11px] text-[var(--muted)]">{theme.tagline}</p>
            </div>
          </div>

          <AnimatePresence mode="wait">
            {mode === 'signin' && (
              <motion.form
                key="signin"
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                onSubmit={handleSignIn}
                className="space-y-4"
                data-testid="signin-form"
              >
                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--muted)]">
                  <span className="flex items-center gap-2 mb-1.5"><AtSign size={14} className={theme.fieldIcon} /> Username</span>
                  <input
                    type="text"
                    required
                    autoComplete="username"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    placeholder="Enter username"
                    className={theme.field}
                  />
                </label>

                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--muted)]">
                  <span className="flex items-center gap-2 mb-1.5"><KeyRound size={14} className={theme.fieldIcon} /> Password</span>
                  <input
                    type="password"
                    required
                    autoComplete="current-password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Enter password"
                    className={theme.field}
                  />
                </label>

                {error && <p className="text-[var(--danger-text)] text-sm bg-[var(--danger-soft)] p-3 rounded-xl border border-[var(--danger-border)]">{error}</p>}

                <motion.button whileTap={{ scale: 0.98 }} type="submit" disabled={busy} className={theme.submit}>
                  {busy ? 'Signing in...' : `Sign in as ${theme.label}`}
                </motion.button>

                <div className={`pt-3 border-t text-center ${theme.rule}`}>
                  <button
                    type="button"
                    onClick={() => switchMode('signup')}
                    className={`text-sm font-semibold transition-colors ${theme.accentText} ${theme.accentTextHover}`}
                  >
                    Don&apos;t have an account? Sign up
                  </button>
                </div>
              </motion.form>
            )}

            {mode === 'signup' && (
              <motion.form
                key="signup"
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                onSubmit={handleSignUp}
                className="space-y-4"
                data-testid="signup-form"
              >
                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--muted)]">
                  <span className="flex items-center gap-2 mb-1.5"><User size={14} className={theme.fieldIcon} /> Full Name</span>
                  <input
                    type="text"
                    required
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="e.g. John Doe"
                    className={theme.field}
                  />
                </label>

                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--muted)]">
                  <span className="flex items-center gap-2 mb-1.5"><Building2 size={14} className={theme.fieldIcon} /> Company Name</span>
                  <input
                    type="text"
                    required
                    value={companyName}
                    onChange={(e) => setCompanyName(e.target.value)}
                    placeholder={theme.companyHint}
                    className={theme.field}
                  />
                </label>

                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--muted)]">
                  <span className="flex items-center gap-2 mb-1.5"><AtSign size={14} className={theme.fieldIcon} /> Username</span>
                  <input
                    type="text"
                    required
                    autoComplete="username"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    placeholder="Used to sign in"
                    className={theme.field}
                  />
                </label>

                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--muted)]">
                  <span className="flex items-center gap-2 mb-1.5"><KeyRound size={14} className={theme.fieldIcon} /> Password</span>
                  <input
                    type="password"
                    required
                    autoComplete="new-password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Choose a password"
                    className={theme.field}
                  />
                </label>

                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--muted)]">
                  <span className="flex items-center gap-2 mb-1.5"><MapPin size={14} className={theme.fieldIcon} /> Headquarters City</span>
                  <select
                    required
                    value={city}
                    onChange={(e) => setCity(e.target.value)}
                    className={theme.field}
                  >
                    <option value="" disabled className={theme.option}>Select your city</option>
                    {cities.map((c) => (
                      <option key={c} value={c} className={theme.option}>{c}</option>
                    ))}
                  </select>
                </label>
                {citiesError && <p className="text-[var(--danger-text)] text-xs">{citiesError}</p>}

                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--muted)]">
                  <span className="flex items-center gap-2 mb-1.5"><Home size={14} className={theme.fieldIcon} /> Address (optional)</span>
                  <input
                    type="text"
                    value={address}
                    onChange={(e) => setAddress(e.target.value)}
                    placeholder="For display on orders/receipts only"
                    className={theme.field}
                  />
                </label>

                {error && <p className="text-[var(--danger-text)] text-sm bg-[var(--danger-soft)] p-3 rounded-xl border border-[var(--danger-border)]">{error}</p>}

                <motion.button whileTap={{ scale: 0.98 }} type="submit" disabled={busy} className={theme.submit}>
                  {busy ? 'Creating account...' : `Create ${theme.label} account`}
                </motion.button>

                <div className={`pt-3 border-t text-center ${theme.rule}`}>
                  <button
                    type="button"
                    onClick={() => switchMode('signin')}
                    className="text-xs font-semibold text-[var(--muted)] hover:text-[var(--text-strong)] transition-colors"
                  >
                    Already have an account? Sign in
                  </button>
                </div>
              </motion.form>
            )}
          </AnimatePresence>
        </div>
      </motion.div>
    </div>
  )
}
