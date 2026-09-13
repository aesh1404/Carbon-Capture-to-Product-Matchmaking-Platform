import { useState } from 'react'
import { motion } from 'framer-motion'
import { Wind, Percent, MapPin, IndianRupee, Lightbulb, Search, ChevronDown } from 'lucide-react'
import { blurOnWheel } from '../utils/numberInput'

const INTENDED_USES = ['Fuel Synthesis', 'Building Materials', 'Greenhouse', 'Algae Farming', 'Other']

const initialValues = {
  minVolumeNeeded: '',
  minPurityRequired: '',
  maxDistanceKm: '',
  maxBudgetPerTon: '',
  intendedUse: INTENDED_USES[0],
}

const fieldClass =
  'w-full rounded-xl px-4 py-2.5 outline-none shadow-sm transition-all focus:ring-2 focus:ring-emerald-500'
const fieldStyle = { background: 'var(--surface-light)', border: '1px solid var(--border)', color: 'var(--text)' }

export default function RequestForm({ onSubmit, submitting, error }) {
  const [values, setValues] = useState(initialValues)

  function handleChange(field) {
    return (e) => setValues((prev) => ({ ...prev, [field]: e.target.value }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    try {
      await onSubmit({
        minVolumeNeeded: Number(values.minVolumeNeeded),
        minPurityRequired: Number(values.minPurityRequired),
        maxDistanceKm: Number(values.maxDistanceKm),
        maxBudgetPerTon: Number(values.maxBudgetPerTon),
        intendedUse: values.intendedUse,
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
          <Search size={24} />
        </div>
        <h2 className="font-bold text-xl" style={{ color: 'var(--text)' }}>Create Carbon Request</h2>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Min Volume */}
        <motion.label className="text-sm font-medium block transition-all" style={{ color: 'var(--muted)' }}>
          <span className="flex items-center gap-2 mb-1.5"><Wind size={16} className="text-[var(--accent-text)]" /> Min Volume (tons)</span>
          <input
            type="number"
            onWheel={blurOnWheel}
            step="any"
            min="0.0001"
            required
            value={values.minVolumeNeeded}
            onChange={handleChange('minVolumeNeeded')}
            className={fieldClass}
            style={fieldStyle}
            placeholder="e.g., 100"
          />
        </motion.label>

        {/* Min Purity */}
        <motion.label className="text-sm font-medium block transition-all" style={{ color: 'var(--muted)' }}>
          <span className="flex items-center gap-2 mb-1.5"><Percent size={16} className="text-[var(--accent-text)]" /> Min Purity (%)</span>
          <input
            type="number"
            onWheel={blurOnWheel}
            step="any"
            min="0"
            max="100"
            required
            value={values.minPurityRequired}
            onChange={handleChange('minPurityRequired')}
            className={fieldClass}
            style={fieldStyle}
            placeholder="e.g., 95.0"
          />
        </motion.label>

        {/* Max Distance */}
        <motion.label className="text-sm font-medium block transition-all" style={{ color: 'var(--muted)' }}>
          <span className="flex items-center gap-2 mb-1.5"><MapPin size={16} className="text-[var(--accent-text)]" /> Max Distance (km)</span>
          <input
            type="number"
            onWheel={blurOnWheel}
            step="any"
            min="0.0001"
            required
            value={values.maxDistanceKm}
            onChange={handleChange('maxDistanceKm')}
            className={fieldClass}
            style={fieldStyle}
            placeholder="e.g., 50"
          />
        </motion.label>

        {/* Max Budget */}
        <motion.label className="text-sm font-medium block transition-all" style={{ color: 'var(--muted)' }}>
          <span className="flex items-center gap-2 mb-1.5"><IndianRupee size={16} className="text-[var(--accent-text)]" /> Max Budget (per ton)</span>
          <input
            type="number"
            onWheel={blurOnWheel}
            step="any"
            min="0.0001"
            required
            value={values.maxBudgetPerTon}
            onChange={handleChange('maxBudgetPerTon')}
            className={fieldClass}
            style={fieldStyle}
            placeholder="0.00"
          />
        </motion.label>

        {/* Intended Use */}
        <motion.label className="text-sm font-medium block md:col-span-2 transition-all" style={{ color: 'var(--muted)' }}>
          <span className="flex items-center gap-2 mb-1.5"><Lightbulb size={16} className="text-[var(--accent-text)]" /> Intended Use</span>
          <span className="relative block">
            <select
              value={values.intendedUse}
              onChange={handleChange('intendedUse')}
              className={`${fieldClass} appearance-none pr-9`}
              style={fieldStyle}
            >
              {INTENDED_USES.map((use) => (
                <option key={use} value={use} className="bg-[var(--surface)]">
                  {use}
                </option>
              ))}
            </select>
            <ChevronDown size={16} className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2" style={{ color: 'var(--muted)' }} />
          </span>
        </motion.label>
      </div>

      {error && <p className="text-[var(--danger-text)] text-sm font-medium bg-[var(--danger-soft)] border border-[var(--danger-border)] p-3 rounded-lg">{error}</p>}

      <motion.button
        whileTap={{ scale: 0.98 }}
        type="submit"
        disabled={submitting}
        className="w-full bg-gradient-to-r from-emerald-500 to-teal-500 text-black font-semibold px-4 py-3.5 rounded-xl shadow-lg shadow-emerald-500/20 disabled:opacity-50 transition-all hover:shadow-emerald-500/40 mt-4"
      >
        {submitting ? 'Creating...' : 'Create Request & Find Matches'}
      </motion.button>
    </motion.form>
  )
}
