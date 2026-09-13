import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { ChevronDown, MapPin, Percent, Ruler, Zap } from 'lucide-react'
import { getCities, extractErrorMessage } from '../api/api'
import { blurOnWheel } from '../utils/numberInput'

const initialValues = {
  buyerCity: '',
  minPurity: '',
  maxDistanceKm: '',
}

const fieldClass =
  'w-full rounded-xl px-4 py-2.5 outline-none shadow-sm transition-all focus:ring-2 focus:ring-teal-500'

// Each field is a full-height flex column so its control can be pushed to the bottom with
// `mt-auto`. Grid items already stretch to the tallest cell in the row, so this keeps every
// control on the same baseline even when one label wraps to two lines and its neighbour doesn't.
const labelClass = 'text-sm font-medium flex h-full flex-col'
// items-start (not items-center) so the icon sits against the FIRST line of a wrapped label
// rather than floating between the two.
const labelTextClass = 'flex items-start gap-2 mb-1.5'
const fieldStyle = { background: 'var(--surface-light)', border: '1px solid var(--border)', color: 'var(--text)' }

export default function QuickBrowseForm({ onSearch, searching, error }) {
  // Starts with NO city selected. Quick Browse is the one-filter fast path, and the city is
  // genuinely optional - prefilling the buyer's own city quietly imposed a distance ranking
  // they never asked for, and made a deliberate "search everywhere" look like a setting they'd
  // have to undo. Empty means "any city": results come back cheapest-first instead.
  const [values, setValues] = useState(initialValues)
  const [cities, setCities] = useState([])
  const [citiesError, setCitiesError] = useState(null)

  useEffect(() => {
    getCities()
      .then(setCities)
      .catch((err) => setCitiesError(extractErrorMessage(err)))
  }, [])

  function handleChange(field) {
    return (e) => setValues((prev) => ({ ...prev, [field]: e.target.value }))
  }

  function handleSubmit(e) {
    e.preventDefault()
    onSearch({
      // Omitted entirely when blank - the endpoint treats a missing city as "rank by price
      // instead of distance", and an empty string would just be a bad city name.
      buyerCity: values.buyerCity || undefined,
      minPurity: values.minPurity === '' ? undefined : Number(values.minPurity),
      maxDistanceKm:
        values.buyerCity && values.maxDistanceKm !== '' ? Number(values.maxDistanceKm) : undefined,
    })
  }

  return (
    <motion.form
      initial={{ opacity: 0, y: 30 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5 }}
      onSubmit={handleSubmit}
      className="surface-card space-y-6 p-8"
    >
      <div className="flex items-center gap-3 border-b pb-4" style={{ borderColor: 'var(--border)' }}>
        <div className="p-2 rounded-lg text-[var(--accent-text)] bg-[var(--accent-soft)]">
          <Zap size={24} />
        </div>
        <div>
          <h2 className="font-bold text-xl" style={{ color: 'var(--text)' }}>Quick Browse</h2>
          <p className="text-xs" style={{ color: 'var(--muted)' }}>
            Browse active listings closest to you, no request form required.
          </p>
        </div>
      </div>

      {citiesError && <p className="text-[var(--danger-text)] text-sm bg-[var(--danger-soft)] p-3 rounded-lg border border-[var(--danger-border)]">{citiesError}</p>}

      {/* A horizontal filter bar now that this spans the full width of the tab: three fields
          and the button on one row, so the whole thing stays a compact strip above the results
          instead of a tall panel beside them. Collapses to a stack on narrow screens. */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-[1.4fr_1fr_1fr_auto] gap-4 xl:items-end">
        <label className={labelClass} style={{ color: 'var(--muted)' }}>
          <span className={labelTextClass}><MapPin size={16} className="shrink-0 mt-0.5" style={{ color: 'var(--muted)' }} /> Your city</span>
          <span className="relative block mt-auto">
            <select
              value={values.buyerCity}
              onChange={handleChange('buyerCity')}
              className={`${fieldClass} appearance-none pr-9`}
              style={fieldStyle}
            >
              {/* Selectable (not a disabled placeholder) so a buyer who only cares about
                  purity can clear the city back out again after it was prefilled. */}
              <option value="">Any city — don't rank by distance</option>
              {cities.map((c) => (
                <option key={c} value={c} className="bg-[var(--surface)]">{c}</option>
              ))}
            </select>
            <ChevronDown size={16} className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2" style={{ color: 'var(--muted)' }} />
          </span>
        </label>

        <label className={labelClass} style={{ color: 'var(--muted)' }}>
          <span className={labelTextClass}><Percent size={16} className="shrink-0 mt-0.5" style={{ color: 'var(--muted)' }} /> Minimum purity % (optional)</span>
          <input
            type="number"
            onWheel={blurOnWheel}
            step="any"
            min="0"
            max="100"
            value={values.minPurity}
            onChange={handleChange('minPurity')}
            className={`${fieldClass} mt-auto`}
            style={fieldStyle}
            placeholder="e.g., 90"
          />
        </label>

        <label className={labelClass} style={{ color: 'var(--muted)' }}>
          <span className={labelTextClass}>
            <Ruler size={16} className="shrink-0 mt-0.5" style={{ color: 'var(--muted)' }} />
            Max distance km {values.buyerCity ? '(optional)' : '(needs a city)'}
          </span>
          <input
            type="number"
            onWheel={blurOnWheel}
            step="any"
            min="0"
            // There is nothing to measure from without an origin, so this is disabled rather
            // than accepted and then rejected by the server.
            disabled={!values.buyerCity}
            value={values.buyerCity ? values.maxDistanceKm : ''}
            onChange={handleChange('maxDistanceKm')}
            className={`${fieldClass} mt-auto disabled:opacity-40 disabled:cursor-not-allowed`}
            style={fieldStyle}
            placeholder={values.buyerCity ? 'e.g., 300' : '—'}
          />
        </label>

        {/* Sits in the grid as the fourth column on wide screens so it lines up with the
            bottom of the inputs rather than stretching across its own row. */}
        <motion.button
          whileTap={{ scale: 0.98 }}
          type="submit"
          disabled={searching}
          className="w-full xl:w-auto mt-auto bg-gradient-to-r from-teal-500 to-emerald-500 text-black font-semibold px-8 py-2.5 rounded-xl shadow-lg shadow-teal-500/20 disabled:opacity-50 transition-all hover:shadow-teal-500/40 whitespace-nowrap"
        >
          {searching ? 'Searching...' : 'Search Listings'}
        </motion.button>
      </div>

      {error && <p className="text-[var(--danger-text)] text-sm font-medium bg-[var(--danger-soft)] border border-[var(--danger-border)] p-3 rounded-lg">{error}</p>}
    </motion.form>
  )
}
