import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { ArrowLeft, Building2, Leaf, MapPin, IndianRupee, RotateCcw } from 'lucide-react'
import { getOrder, getUser, getListing, extractErrorMessage } from '../api/api'
import { formatCurrency, formatKm } from '../utils/format'

export default function Receipt({ orderId, onBack }) {
  const [order, setOrder] = useState(null)
  const [emitter, setEmitter] = useState(null)
  const [buyer, setBuyer] = useState(null)
  const [listing, setListing] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    getOrder(orderId)
      .then(async (fetchedOrder) => {
        if (cancelled) return
        setOrder(fetchedOrder)
        const [fetchedEmitter, fetchedBuyer, fetchedListing] = await Promise.all([
          getUser(fetchedOrder.emitterId),
          getUser(fetchedOrder.buyerId),
          getListing(fetchedOrder.listingId),
        ])
        if (cancelled) return
        setEmitter(fetchedEmitter)
        setBuyer(fetchedBuyer)
        setListing(fetchedListing)
      })
      .catch((err) => {
        if (!cancelled) setError(extractErrorMessage(err))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [orderId])

  return (
    <div className="dashboard-container max-w-2xl space-y-4">
      <button
        type="button"
        onClick={onBack}
        className="flex items-center gap-2 text-sm font-medium transition-colors"
        style={{ color: 'var(--green)' }}
      >
        <ArrowLeft size={16} /> Back to dashboard
      </button>

      {loading && <p className="text-sm" style={{ color: 'var(--muted)' }}>Loading receipt...</p>}
      {error && <p className="text-[var(--danger-text)] text-sm bg-[var(--danger-soft)] p-3 rounded-lg border border-[var(--danger-border)]">{error}</p>}

      {order && !loading && (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ type: 'spring', stiffness: 300, damping: 24 }}
          className="surface-card p-7 space-y-6"
        >
          <div className="flex justify-between items-start gap-3 border-b pb-5" style={{ borderColor: 'var(--border)' }}>
            <div>
              <p className="text-xs font-mono" style={{ color: 'var(--muted)' }}>Order</p>
              <h1 className="text-2xl font-bold" style={{ color: 'var(--text)' }}>#{order.orderNumber}</h1>
              <p className="text-sm mt-1" style={{ color: 'var(--muted)' }}>
                Confirmed {new Date(order.createdAt).toLocaleString()}
              </p>
            </div>
            <span
              className={`flex items-center gap-1.5 text-xs font-bold px-3 py-1.5 rounded-full border whitespace-nowrap ${
                order.status === 'REVERTED'
                  ? 'bg-[var(--warn-soft)] text-[var(--warn-text)] border-[var(--warn-border)]'
                  : 'bg-[var(--accent-soft)] text-[var(--accent-text)] border-[var(--accent-soft-border)]'
              }`}
            >
              {order.status === 'REVERTED' && <RotateCcw size={14} />}
              {order.status}
            </span>
          </div>

          {order.status === 'REVERTED' && (
            <p className="text-sm px-3 py-2 rounded-lg bg-[var(--warn-soft)] text-[var(--warn-text)] border border-[var(--warn-border)]">
              This order was later reversed.
            </p>
          )}

          <div className="grid grid-cols-2 gap-4">
            <div>
              <p className="flex items-center gap-1.5 text-xs uppercase tracking-wide font-semibold" style={{ color: 'var(--muted)' }}>
                <Building2 size={12} /> Emitter
              </p>
              <p className="font-semibold mt-1" style={{ color: 'var(--text)' }}>{emitter?.companyName || 'Loading...'}</p>
              <p className="text-sm" style={{ color: 'var(--muted)' }}>
                {emitter?.city}
                {(listing?.address || emitter?.address) && ` — ${listing?.address || emitter?.address}`}
              </p>
            </div>
            <div>
              <p className="flex items-center gap-1.5 text-xs uppercase tracking-wide font-semibold" style={{ color: 'var(--muted)' }}>
                <Building2 size={12} /> Buyer
              </p>
              <p className="font-semibold mt-1" style={{ color: 'var(--text)' }}>{buyer?.companyName || 'Loading...'}</p>
              <p className="text-sm" style={{ color: 'var(--muted)' }}>
                {buyer?.city}
                {buyer?.address && ` — ${buyer.address}`}
              </p>
            </div>
          </div>

          <div className="border-t pt-5" style={{ borderColor: 'var(--border)' }}>
            <p className="flex items-center gap-1.5 text-xs uppercase tracking-wide font-semibold mb-1.5" style={{ color: 'var(--muted)' }}>
              <Leaf size={12} /> CO2 Details
            </p>
            <p className="text-sm" style={{ color: 'var(--text)' }}>
              {order.transactedVolumeTons} tons
              {listing && ` · ${listing.purityPercent}% purity · ${listing.captureMethod}`}
            </p>
            <p className="flex items-center gap-1.5 text-sm mt-1.5" style={{ color: 'var(--muted)' }}>
              <MapPin size={13} /> {formatKm(order.distanceKm)} km transport distance
            </p>
          </div>

          <div className="border-t pt-5 space-y-1.5" style={{ borderColor: 'var(--border)' }}>
            <p className="flex items-center gap-1.5 text-xs uppercase tracking-wide font-semibold mb-1" style={{ color: 'var(--muted)' }}>
              <IndianRupee size={12} /> Cost Breakdown
            </p>
            <div className="flex justify-between text-sm" style={{ color: 'var(--text)' }}>
              <span>Base Price</span>
              <span>{formatCurrency(order.basePrice)}</span>
            </div>
            <div className="flex justify-between text-sm" style={{ color: 'var(--text)' }}>
              <span>Transport Cost</span>
              <span>{formatCurrency(order.transportCost)}</span>
            </div>
            <div
              className="flex justify-between text-base font-bold pt-2 mt-1 border-t"
              style={{ color: 'var(--text)', borderColor: 'var(--border)' }}
            >
              <span>Total Cost</span>
              <span>{formatCurrency(order.totalCost)}</span>
            </div>
          </div>

            <p
              className="mt-3 flex items-center justify-center gap-1.5 text-xs font-semibold rounded-lg py-2"
              style={
                order.paymentStatus === 'PAID'
                  ? { background: 'var(--accent-soft)', color: 'var(--accent-text)' }
                  : { background: 'var(--surface-light)', color: 'var(--muted)' }
              }
              data-testid="receipt-payment-status"
            >
              {order.paymentStatus === 'PAID' ? 'Payment received' : 'Payment pending'}
            </p>

          <p className="text-xs border-t pt-4" style={{ color: 'var(--muted)', borderColor: 'var(--border)' }}>
            This is a settlement summary. Actual payment processing would integrate with a payment
            gateway in a production deployment.
          </p>
        </motion.div>
      )}
    </div>
  )
}
