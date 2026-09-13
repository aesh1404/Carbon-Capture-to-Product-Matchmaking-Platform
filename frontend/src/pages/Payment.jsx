import { useEffect, useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { ArrowLeft, CheckCircle2, CreditCard, Landmark, Loader2, Lock, Smartphone } from 'lucide-react'
import { getOrder, payOrder, extractErrorMessage } from '../api/api'
import { formatCurrency } from '../utils/format'

// A simulated checkout step, shown to a buyer between an order being created and its receipt.
//
// NO MONEY MOVES. There is no payment provider, no card data leaves this component, and
// nothing typed into the fields below is read, stored or sent anywhere. The "processing" delay
// is a setTimeout.
//
// It does record ONE fact when that timer finishes: this order is paid. That single flag is
// persisted rather than kept in the browser because the EMITTER depends on it - they cannot
// mark an order delivered until the buyer has paid, and they can't act on a flag that only
// exists in someone else's session.
//
// The order's own status is untouched: it has been CONFIRMED since the emitter accepted the
// match, long before this screen appears.
const PAYMENT_METHODS = [
  { id: 'upi', label: 'UPI', hint: 'Pay via any UPI app', Icon: Smartphone },
  { id: 'netbanking', label: 'Net Banking', hint: 'All major Indian banks', Icon: Landmark },
  { id: 'card', label: 'Credit / Debit Card', hint: 'Visa, Mastercard, RuPay', Icon: CreditCard },
]

const SIMULATED_PROCESSING_MS = 1800

export default function Payment({ orderId, onPaid, onBack }) {
  const [order, setOrder] = useState(null)
  const [loadError, setLoadError] = useState(null)
  const [method, setMethod] = useState('upi')
  const [stage, setStage] = useState('form') // 'form' | 'processing' | 'success'

  useEffect(() => {
    let cancelled = false
    getOrder(orderId)
      .then((fetched) => {
        if (cancelled) return
        setOrder(fetched)
        // Already settled (a reopened receipt, or a historical order) - don't ask twice.
        if (fetched.paymentStatus === 'PAID') onPaid()
      })
      .catch((err) => !cancelled && setLoadError(extractErrorMessage(err)))
    return () => {
      cancelled = true
    }
  }, [orderId, onPaid])

  // The "transaction" is a timer. When it finishes, the one real side effect: flag the order
  // as paid so the emitter's delivery step unlocks.
  useEffect(() => {
    if (stage !== 'processing') return undefined
    const timer = setTimeout(() => {
      payOrder(orderId)
        .then(() => setStage('success'))
        .catch((err) => {
          setLoadError(extractErrorMessage(err))
          setStage('form')
        })
    }, SIMULATED_PROCESSING_MS)
    return () => clearTimeout(timer)
  }, [stage, orderId])

  const amount = order ? formatCurrency(order.totalCost) : '—'
  const orderNumber = order?.orderNumber ?? '—'

  return (
    <main className="page-wrap">
      <div className="mx-auto w-full max-w-lg space-y-5">
        {stage === 'form' && (
          <button
            type="button"
            onClick={onBack}
            className="flex items-center gap-2 text-sm font-medium transition-colors"
            style={{ color: 'var(--muted)' }}
          >
            <ArrowLeft size={16} /> Back to dashboard
          </button>
        )}

        <AnimatePresence mode="wait">
          {stage !== 'success' ? (
            <motion.section
              key="checkout"
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -16 }}
              className="surface-card p-8 space-y-6"
              data-testid="payment-screen"
            >
              <div>
                <h1 className="text-xl font-bold" style={{ color: 'var(--text)' }}>Complete Payment</h1>
                <p className="text-xs mt-1" style={{ color: 'var(--muted)' }}>
                  Settle this order to confirm your CO₂ purchase.
                </p>
              </div>

              {loadError && (
                <p className="text-[var(--danger-text)] text-sm bg-[var(--danger-soft)] border border-[var(--danger-border)] p-3 rounded-xl">{loadError}</p>
              )}

              <div
                className="rounded-xl p-4 border space-y-2"
                style={{ background: 'var(--surface-light)', borderColor: 'var(--border)' }}
              >
                <div className="flex justify-between text-sm">
                  <span style={{ color: 'var(--muted)' }}>Order</span>
                  <span className="font-mono font-semibold" style={{ color: 'var(--text)' }} data-testid="payment-order-number">
                    {orderNumber}
                  </span>
                </div>
                <div className="h-px w-full" style={{ background: 'var(--border)' }} />
                <div className="flex justify-between items-baseline">
                  <span className="text-sm" style={{ color: 'var(--muted)' }}>Amount due</span>
                  <span className="text-2xl font-extrabold text-[var(--accent-text)]" data-testid="payment-amount">{amount}</span>
                </div>
              </div>

              <fieldset disabled={stage === 'processing'} className="space-y-3">
                <legend className="text-xs font-semibold uppercase tracking-wider mb-2" style={{ color: 'var(--muted)' }}>
                  Payment method
                </legend>

                {PAYMENT_METHODS.map(({ id, label, hint, Icon }) => (
                  <label
                    key={id}
                    className={`flex items-center gap-3 rounded-xl border p-3.5 cursor-pointer transition-colors ${
                      method === id ? 'border-emerald-500/60 bg-emerald-500/5' : ''
                    }`}
                    style={method === id ? undefined : { borderColor: 'var(--border)', background: 'var(--surface-light)' }}
                  >
                    <input
                      type="radio"
                      name="paymentMethod"
                      value={id}
                      checked={method === id}
                      onChange={() => setMethod(id)}
                      className="accent-emerald-500"
                    />
                    <Icon size={18} className="text-[var(--accent-text)] shrink-0" />
                    <span className="flex-1">
                      <span className="block text-sm font-semibold" style={{ color: 'var(--text)' }}>{label}</span>
                      <span className="block text-xs" style={{ color: 'var(--muted)' }}>{hint}</span>
                    </span>
                  </label>
                ))}
              </fieldset>

              <button
                type="button"
                onClick={() => setStage('processing')}
                disabled={stage === 'processing' || !order}
                data-testid="pay-now"
                className="w-full flex items-center justify-center gap-2 bg-gradient-to-r from-emerald-500 to-teal-500 text-black font-bold px-4 py-3.5 rounded-xl disabled:opacity-60 transition-all shadow-lg shadow-emerald-500/20"
              >
                {stage === 'processing' ? (
                  <>
                    <Loader2 size={18} className="animate-spin" /> Processing payment...
                  </>
                ) : (
                  <>
                    <Lock size={16} /> Pay {amount}
                  </>
                )}
              </button>

              {/* Small and unobtrusive, but always present - nobody inspecting this should be
                  able to mistake it for a real payment system. */}
              <p className="text-[11px] text-center" style={{ color: 'var(--muted)' }} data-testid="payment-disclaimer">
                This is a demo payment flow — no real transaction is processed.
              </p>
            </motion.section>
          ) : (
            <motion.section
              key="success"
              initial={{ opacity: 0, scale: 0.96 }}
              animate={{ opacity: 1, scale: 1 }}
              className="surface-card p-10 space-y-5 text-center"
              data-testid="payment-success"
            >
              <motion.div
                initial={{ scale: 0.5, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                transition={{ type: 'spring', stiffness: 260, damping: 18 }}
                className="mx-auto w-16 h-16 rounded-full bg-emerald-500/15 border border-[var(--accent-soft-border)] flex items-center justify-center"
              >
                <CheckCircle2 size={34} className="text-[var(--accent-text)]" />
              </motion.div>

              <div>
                <h1 className="text-2xl font-extrabold" style={{ color: 'var(--text)' }}>Payment Successful</h1>
                <p className="text-sm mt-1" style={{ color: 'var(--muted)' }}>Your CO₂ purchase is settled.</p>
              </div>

              <div
                className="rounded-xl p-4 border space-y-2 text-left"
                style={{ background: 'var(--surface-light)', borderColor: 'var(--border)' }}
              >
                <div className="flex justify-between text-sm">
                  <span style={{ color: 'var(--muted)' }}>Order</span>
                  <span className="font-mono font-semibold" style={{ color: 'var(--text)' }}>{orderNumber}</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span style={{ color: 'var(--muted)' }}>Amount paid</span>
                  <span className="font-bold text-[var(--accent-text)]">{amount}</span>
                </div>
              </div>

              <button
                type="button"
                onClick={onPaid}
                data-testid="view-receipt"
                className="w-full bg-gradient-to-r from-emerald-500 to-teal-500 text-black font-bold px-4 py-3.5 rounded-xl transition-all shadow-lg shadow-emerald-500/20"
              >
                View Receipt
              </button>

              <p className="text-[11px]" style={{ color: 'var(--muted)' }}>
                This is a demo payment flow — no real transaction is processed.
              </p>
            </motion.section>
          )}
        </AnimatePresence>
      </div>
    </main>
  )
}
