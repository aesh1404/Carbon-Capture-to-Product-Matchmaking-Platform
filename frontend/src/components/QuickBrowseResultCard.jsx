import { useState } from 'react'
import { motion } from 'framer-motion'
import { Building2, Leaf, IndianRupee, Layers, MapPin, CheckCircle2, Zap } from 'lucide-react'
import { formatCurrency, formatKm } from '../utils/format'
import { blurOnWheel } from '../utils/numberInput'

export default function QuickBrowseResultCard({ result, emitterName, onRequest, busy, error, requested }) {
  const [volume, setVolume] = useState('')
  const numericVolume = Number(volume)
  const canRequest = volume !== '' && numericVolume > 0 && numericVolume <= result.remainingVolumeTons

  function handleRequest() {
    onRequest(result.listingId, numericVolume)
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 15 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ type: 'spring', stiffness: 300, damping: 24 }}
      className="surface-card p-5 space-y-4"
      data-testid="quick-browse-result"
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-[11px] font-mono" style={{ color: 'var(--muted)' }}>Listing #{result.listingId}</p>
          <div className="flex items-center gap-2 font-bold text-base mt-0.5" style={{ color: 'var(--text)' }}>
            <Building2 size={18} style={{ color: 'var(--muted)' }} />
            {emitterName || 'Loading...'}
          </div>
        </div>
        <span className="flex items-center gap-1.5 text-xs font-bold px-3 py-1.5 rounded-full border whitespace-nowrap bg-[var(--accent-soft)] text-[var(--accent-text)] border-[var(--accent-soft-border)]">
          <MapPin size={12} /> {result.distanceKm == null ? 'Distance n/a' : `${formatKm(result.distanceKm)} km`}
        </span>
      </div>

      <div
        className="rounded-xl p-4 border grid grid-cols-2 gap-x-4 gap-y-2.5 text-sm"
        style={{ background: 'var(--surface-light)', borderColor: 'var(--border)' }}
      >
        <span className="flex items-center gap-1.5 whitespace-nowrap" style={{ color: 'var(--muted)' }}>
          <Leaf size={14} style={{ color: 'var(--muted)' }} /> {result.captureMethod}
        </span>
        <span className="flex items-center gap-1.5 whitespace-nowrap" style={{ color: 'var(--muted)' }}>
          <CheckCircle2 size={14} style={{ color: 'var(--muted)' }} /> {result.purityPercent}% purity
        </span>
        <span className="flex items-center gap-1.5 whitespace-nowrap" style={{ color: 'var(--muted)' }}>
          <Layers size={14} style={{ color: 'var(--muted)' }} /> {result.remainingVolumeTons}t available
        </span>
        <span className="flex items-center gap-1.5 whitespace-nowrap" style={{ color: 'var(--muted)' }}>
          <IndianRupee size={14} style={{ color: 'var(--muted)' }} /> {formatCurrency(result.pricePerTon)}/ton
        </span>
      </div>

      <p className="text-xs" style={{ color: 'var(--muted)' }}>
        {result.city}
        {result.address && ` — ${result.address}`}{result.distanceKm != null && ` · ${formatKm(result.distanceKm)} km away`}
      </p>

      {error && (
        <p className="text-[var(--danger-text)] text-xs font-medium bg-[var(--danger-soft)] border border-[var(--danger-border)] p-2 rounded-lg">{error}</p>
      )}

      {requested ? (
        <p className="flex items-center gap-1.5 text-sm font-semibold text-[var(--accent-text)]">
          <CheckCircle2 size={16} /> Requested — see it under "Pending Requests"
        </p>
      ) : (
        <div className="flex flex-wrap items-center gap-3">
          <input
            type="number"
            onWheel={blurOnWheel}
            step="any"
            min="0.0001"
            max={result.remainingVolumeTons}
            value={volume}
            onChange={(e) => setVolume(e.target.value)}
            placeholder="Volume needed (tons)"
            className="flex-1 min-w-[160px] rounded-xl px-3 py-2 text-sm outline-none shadow-sm transition-all focus:ring-2 focus:ring-teal-500"
            style={{ background: 'var(--background)', border: '1px solid var(--border)', color: 'var(--text)' }}
          />
          <motion.button
            whileTap={{ scale: 0.97 }}
            type="button"
            onClick={handleRequest}
            disabled={!canRequest || busy}
            className="flex items-center gap-1.5 bg-teal-600 hover:bg-teal-500 text-[var(--text-strong)] font-medium text-sm px-4 py-2 rounded-xl disabled:opacity-50 transition-colors shadow-lg shadow-teal-600/20"
          >
            <Zap size={14} /> {busy ? 'Requesting...' : 'Request This Listing'}
          </motion.button>
        </div>
      )}
    </motion.div>
  )
}
