import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { Wind, Percent, Settings, IndianRupee, MapPin, Sparkles, Home, ChevronDown } from 'lucide-react'
import { getCities, extractErrorMessage } from '../api/api'
import { blurOnWheel } from '../utils/numberInput'

const CAPTURE_METHODS = ['Post-combustion', 'Direct Air Capture', 'Pre-combustion', 'Oxy-fuel']

const initialValues = {
  totalVolumeTons: '',
  purityPercent: '',
  captureMethod: CAPTURE_METHODS[0],
  pricePerTon: '',
  city: '',
  address: '',
}

const fieldClass =
  'w-full rounded-xl px-4 py-2.5 outline-none shadow-sm transition-all focus:ring-2 focus:ring-emerald-500'
const fieldStyle = { background: 'var(--surface-light)', border: '1px solid var(--border)', color: 'var(--text)' }

export default function ListingForm({ onSubmit, submitting, error }) {
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

  async function handleSubmit(e) {
    e.preventDefault()
    try {
      await onSubmit({
        totalVolumeTons: Number(values.totalVolumeTons),
        purityPercent: Number(values.purityPercent),
        captureMethod: values.captureMethod,
        pricePerTon: Number(values.pricePerTon),
        city: values.city,
        address: values.address.trim() || null,
      })
      setValues(initialValues)
    } catch {
      // parent surfaces the error via the `error` prop
    }
  }

  return (
    <motion.form
      initial={{ opacity: 0, y: 30 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.6, type: 'spring', bounce: 0.4 }}
      onSubmit={handleSubmit}
      className="surface-card space-y-6 p-8"
    >
      <div className="flex items-center gap-3 border-b pb-4" style={{ borderColor: 'var(--border)' }}>
        <div className="p-2 rounded-lg text-[var(--accent-text)] bg-[var(--accent-soft)]">
          <Sparkles size={24} />
        </div>
        <h2 className="font-bold text-xl" style={{ color: 'var(--text)' }}>Create Carbon Listing</h2>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Volume Input */}
        <motion.label className="text-sm font-medium block transition-all" style={{ color: 'var(--muted)' }}>
          <span className="flex items-center gap-2 mb-1.5"><Wind size={16} style={{ color: 'var(--muted)' }} /> Total Volume (tons)</span>
          <input
            type="number"
            onWheel={blurOnWheel}
            step="any"
            min="0.0001"
            required
            value={values.totalVolumeTons}
            onChange={handleChange('totalVolumeTons')}
            className={fieldClass}
            style={fieldStyle}
            placeholder="e.g., 500"
          />
        </motion.label>

        {/* Purity Input */}
        <motion.label className="text-sm font-medium block transition-all" style={{ color: 'var(--muted)' }}>
          <span className="flex items-center gap-2 mb-1.5"><Percent size={16} style={{ color: 'var(--muted)' }} /> Purity (%)</span>
          <input
            type="number"
            onWheel={blurOnWheel}
            step="any"
            min="0"
            max="100"
            required
            value={values.purityPercent}
            onChange={handleChange('purityPercent')}
            className={fieldClass}
            style={fieldStyle}
            placeholder="e.g., 99.9"
          />
        </motion.label>

        {/* Capture Method */}
        <motion.label className="text-sm font-medium block transition-all" style={{ color: 'var(--muted)' }}>
          <span className="flex items-center gap-2 mb-1.5"><Settings size={16} style={{ color: 'var(--muted)' }} /> Capture Method</span>
          <span className="relative block">
            <select
              value={values.captureMethod}
              onChange={handleChange('captureMethod')}
              className={`${fieldClass} appearance-none pr-9`}
              style={fieldStyle}
            >
              {CAPTURE_METHODS.map((method) => (
                <option key={method} value={method} className="bg-[var(--surface)]">{method}</option>
              ))}
            </select>
            <ChevronDown size={16} className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2" style={{ color: 'var(--muted)' }} />
          </span>
        </motion.label>

        {/* Price Input */}
        <motion.label className="text-sm font-medium block transition-all" style={{ color: 'var(--muted)' }}>
          <span className="flex items-center gap-2 mb-1.5"><IndianRupee size={16} style={{ color: 'var(--muted)' }} /> Price per ton</span>
          <input
            type="number"
            onWheel={blurOnWheel}
            step="any"
            min="0.0001"
            required
            value={values.pricePerTon}
            onChange={handleChange('pricePerTon')}
            className={fieldClass}
            style={fieldStyle}
            placeholder="0.00"
          />
        </motion.label>

        {/* City Select */}
        <motion.label className="text-sm font-medium block md:col-span-2 transition-all" style={{ color: 'var(--muted)' }}>
          <span className="flex items-center gap-2 mb-1.5"><MapPin size={16} style={{ color: 'var(--muted)' }} /> Location (City)</span>
          <span className="relative block">
            <select
              required
              value={values.city}
              onChange={handleChange('city')}
              className={`${fieldClass} appearance-none pr-9`}
              style={fieldStyle}
            >
              <option value="" disabled className="bg-[var(--surface)]">Select the listing's city</option>
              {cities.map((c) => (
                <option key={c} value={c} className="bg-[var(--surface)]">{c}</option>
              ))}
            </select>
            <ChevronDown size={16} className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2" style={{ color: 'var(--muted)' }} />
          </span>
        </motion.label>

        {/* Address (optional, display-only) */}
        <motion.label className="text-sm font-medium block md:col-span-2 transition-all" style={{ color: 'var(--muted)' }}>
          <span className="flex items-center gap-2 mb-1.5"><Home size={16} style={{ color: 'var(--muted)' }} /> Address (optional)</span>
          <input
            type="text"
            value={values.address}
            onChange={handleChange('address')}
            className={fieldClass}
            style={fieldStyle}
            placeholder="e.g., Plot 14, MIDC Industrial Area — shown on orders/receipts only"
          />
        </motion.label>
      </div>

      {citiesError && <p className="text-[var(--danger-text)] text-sm font-medium bg-[var(--danger-soft)] border border-[var(--danger-border)] p-3 rounded-lg">{citiesError}</p>}
      {error && <p className="text-[var(--danger-text)] text-sm font-medium bg-[var(--danger-soft)] border border-[var(--danger-border)] p-3 rounded-lg">{error}</p>}

      <motion.button
        whileTap={{ scale: 0.98 }}
        type="submit"
        disabled={submitting}
        className="w-full bg-gradient-to-r from-emerald-500 to-teal-500 text-black font-semibold px-4 py-3.5 rounded-xl shadow-lg shadow-emerald-500/20 disabled:opacity-50 transition-all hover:shadow-emerald-500/40"
      >
        {submitting ? 'Creating Listing...' : 'Create Listing'}
      </motion.button>
    </motion.form>
  )
}
