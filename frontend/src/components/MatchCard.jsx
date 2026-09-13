import { motion } from 'framer-motion'
import {
  Building2,
  MapPin,
  IndianRupee,
  Leaf,
  CheckCircle2,
  XCircle,
  Clock,
  ArrowRight,
  TrendingUp,
  Sparkles,
  Ban,
  RotateCcw,
  Receipt as ReceiptIcon,
  Truck,
  PackageCheck,
  Wallet,
  BadgeCheck,
  AlertTriangle,
} from 'lucide-react'
import DeliveryProgress from './DeliveryProgress'
import { formatCurrency, formatKm, formatNumber } from '../utils/format'

const NEXT_DELIVERY_STATUS = { CONFIRMED: 'IN_TRANSIT', IN_TRANSIT: 'DELIVERED' }
const DELIVERY_ACTION_LABEL = { IN_TRANSIT: 'Mark as In Transit', DELIVERED: 'Mark as Delivered' }

function scoreBarColor(score) {
  if (score > 75) return 'bg-emerald-500'
  if (score >= 50) return 'bg-yellow-500'
  return 'bg-red-500'
}

// Returns a style object rather than Tailwind classes: these badges have to follow the active
// theme, and a "/10 tint + -400 text" pairing that reads well on a dark ground washes out
// completely on white. The tokens carry a separate value per theme.
function statusConfig(status) {
  const pill = (text, soft, border, icon) => ({
    style: { color: `var(--${text})`, background: `var(--${soft})`, borderColor: `var(--${border})` },
    icon,
  })
  switch (status) {
    case 'ACCEPTED':
      return pill('accent-text', 'accent-soft', 'accent-soft-border', CheckCircle2)
    case 'REJECTED':
      return pill('danger-text', 'danger-soft', 'danger-border', XCircle)
    case 'REQUESTED':
      return pill('accent-text', 'accent-soft', 'accent-soft-border', Clock)
    case 'SUGGESTED':
      return pill('info-text', 'info-soft', 'info-border', Sparkles)
    case 'CANCELLED':
      return pill('neutral-text', 'neutral-soft', 'neutral-border', Ban)
    case 'REVERTED':
      return pill('warn-text', 'warn-soft', 'warn-border', RotateCcw)
    // Both display-only, derived from the order rather than stored on the match: paid AND
    // handed over. Each side gets its own half of that event - the emitter delivered it,
    // the buyer received it.
    case 'RECEIVED':
    case 'DELIVERED':
      return pill('accent-text', 'accent-soft', 'accent-soft-border', PackageCheck)
    default:
      return pill('neutral-text', 'neutral-soft', 'neutral-border', Clock)
  }
}

function scoreTextColor(score) {
  if (score > 75) return 'text-[var(--accent-text)]'
  if (score >= 50) return 'text-yellow-400'
  return 'text-[var(--danger-text)]'
}

export default function MatchCard({
  match,
  listing,
  counterpartyRoleLabel,
  counterpartyName,
  intendedUse,
  onRequest,
  onAccept,
  onReject,
  onCancel,
  onRevert,
  onViewReceipt,
  onPay,
  deliveryStatus,
  paymentStatus,
  onAdvanceDelivery,
  deliveryBusy,
  deliveryError,
  busy,
  actionError,
  stale,
  viewerRole,
}) {
  const score = match.compatibilityScore
  const isSuggested = match.status === 'SUGGESTED'
  const isRequested = match.status === 'REQUESTED'
  const isAccepted = match.status === 'ACCEPTED'
  const isActionable = !stale && (isSuggested || isRequested)
  const nextDeliveryStatus = deliveryStatus ? NEXT_DELIVERY_STATUS[deliveryStatus] : null
  // The emitter can dispatch on trust, but can't record handover until the buyer has paid -
  // the backend enforces the same rule, so an enabled button here would only ever earn a 409.
  const deliveryBlockedByPayment = nextDeliveryStatus === 'DELIVERED' && paymentStatus === 'PENDING'
  // Once the shipment is dispatched the CO2 has left the emitter's site, so the backend refuses
  // to restore the listing's stock (409). Drop the button rather than ship one that can only fail.
  const isRevertable = isAccepted && (!deliveryStatus || deliveryStatus === 'CONFIRMED')

  // The volume every rupee on this card is calculated from: the buyer's ask while the match is
  // still open, the committed amount once accepted. The breakdown carries it so this can never
  // drift from the figures beside it; transactedVolumeTons is the fallback for a cached response
  // predating that field.
  const dealVolumeTons = match.costBreakdown?.volumeTons ?? match.transactedVolumeTons ?? null

  // The listing can no longer cover what was asked for. Accepting is refused server-side (409),
  // so both sides are told before they act rather than after: the buyer doesn't send a request
  // that can't be honoured, and the emitter isn't offered a button that can only fail.
  // Only meaningful while the deal is still open - once accepted, the stock has already been
  // deducted and remaining is legitimately below the transacted volume.
  const shortfall =
    (isSuggested || isRequested) &&
    dealVolumeTons != null &&
    listing?.remainingVolumeTons != null &&
    listing.remainingVolumeTons < dealVolumeTons

  // A match spans two parties, so there is no single "correct" numbering for this card.
  // The viewer's OWN entity is labelled with their personal number and carries the global id
  // as a secondary reference; the counterparty's entity is shown by global id only, because
  // that is the one id both sides can quote at each other. The backend only ever sends the
  // viewer's own number, so there is nothing here to accidentally reveal.
  // Match #N stays global for the same reason - it is the shared reference.
  const ownListingNo = match.personalListingNumber != null
    ? `#${match.personalListingNumber} (ID: ${match.listingId})`
    : `#${match.listingId}`
  const ownRequestNo = match.personalRequestNumber != null
    ? `#${match.personalRequestNumber} (ID: ${match.requestId})`
    : `#${match.requestId}`
  const idLine = viewerRole === 'EMITTER'
    ? `Match #${match.id} · Your Listing ${ownListingNo} · Buyer's Request #${match.requestId}`
    : viewerRole === 'BUYER'
      ? `Match #${match.id} · Your Request ${ownRequestNo} · Listing #${match.listingId}`
      : `Match #${match.id} · Listing #${match.listingId} · Request #${match.requestId}`

  // A completed deal reads RECEIVED rather than ACCEPTED. Derived here instead of being a new
  // MatchStatus, because the match lifecycle (what can still be reverted, what counts as
  // decided) is unchanged - only the label the user reads is.
  const isReceived =
    match.status === 'ACCEPTED' && paymentStatus === 'PAID' && deliveryStatus === 'DELIVERED'
  const displayStatus = isReceived
    ? (viewerRole === 'EMITTER' ? 'DELIVERED' : 'RECEIVED')
    : match.status
  // Payment is only owed once there's an order behind the match.
  const paymentDue = match.orderId != null && paymentStatus === 'PENDING'
  const paymentSettled = match.orderId != null && paymentStatus === 'PAID'
  const StatusIcon = statusConfig(displayStatus).icon


  function handleRevertClick() {
    if (window.confirm('Revert this accepted match? The listing’s stock will be restored and the buyer’s request reopened.')) {
      onRevert(match.id)
    }
  }

  return (
    <div className="w-full">
      <motion.div
        style={{ borderColor: 'var(--border)' }}
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ type: 'spring', stiffness: 300, damping: 20 }}
        data-testid="match-card"
        className="surface-card relative p-6 space-y-5 shadow-[var(--shadow-card)]"
      >
        {/* Header Section (Pops out slightly) */}
        <motion.div className="flex justify-between items-start gap-3">
          <div>
            <p className="text-[11px] font-mono" style={{ color: 'var(--muted)' }} data-testid="match-ids">
              {idLine}
            </p>
            <div className="flex items-center gap-2 font-bold text-lg mt-0.5" style={{ color: 'var(--text)' }}>
              <Building2 size={20} style={{ color: 'var(--muted)' }} />
              {counterpartyRoleLabel}: {counterpartyName || 'Loading...'}
            </div>
            {intendedUse && (
              <p className="text-xs mt-1" style={{ color: 'var(--muted)' }}>Intended use: {intendedUse}</p>
            )}
          </div>
          <div className="flex flex-col items-end gap-1.5">
              <span
              className="flex items-center gap-1.5 text-xs font-bold px-3 py-1.5 rounded-full border whitespace-nowrap"
              style={statusConfig(displayStatus).style}
            >
              <StatusIcon size={14} />
              {displayStatus}
            </span>

            {/* Money state, read at a glance next to the deal state. Only meaningful once
                there's an order behind the match - before that there is nothing owed. */}
            {match.orderId != null && (
              <span
                className="flex items-center gap-1.5 text-[11px] font-bold px-2.5 py-1 rounded-full border whitespace-nowrap"
                style={
                  paymentSettled
                    ? { color: 'var(--accent-text)', background: 'var(--accent-soft)', borderColor: 'var(--accent-soft-border)' }
                    : { color: 'var(--danger-text)', background: 'var(--danger-soft)', borderColor: 'var(--danger-border)' }
                }
                data-testid="payment-badge"
              >
                {paymentSettled ? <BadgeCheck size={12} /> : <Wallet size={12} />}
                {paymentSettled ? 'PAID' : 'PAYMENT DUE'}
              </span>
            )}
          </div>
        </motion.div>

        {/* Listing Specs */}
        {listing && (
          <motion.div
            style={{ background: 'var(--surface-light)', borderColor: 'var(--border)' }}
            className="rounded-xl p-4 border space-y-2"
          >
            <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm" style={{ color: 'var(--muted)' }}>
              <span className="flex items-center gap-1.5"><Leaf size={14} style={{ color: 'var(--muted)' }} /> {listing.captureMethod}</span>
              {/* Remaining, not the listed total. The total is the emitter's original supply and
                  says nothing about what can still be bought - a listing that reads "800t" may
                  have 20 left. Since an accept now refuses to under-fill, the available figure is
                  the one that decides whether a deal is even possible. The listed total is only
                  worth showing once it differs, and then as context, not as the headline. */}
              <span className="flex items-center gap-1.5">
                <TrendingUp size={14} style={{ color: 'var(--muted)' }} />
                {formatNumber(listing.remainingVolumeTons)}t available
                {listing.remainingVolumeTons !== listing.totalVolumeTons && (
                  <span style={{ color: 'var(--faint)' }}>of {formatNumber(listing.totalVolumeTons)}t listed</span>
                )}
              </span>
              <span className="flex items-center gap-1.5"><CheckCircle2 size={14} style={{ color: 'var(--muted)' }} /> {listing.purityPercent}% purity</span>
            </div>
            {/* Always state the volume this card's money covers. It used to appear only once a
                match was accepted, so before that the card showed a quote for (say) 100 t beside
                a 2,500 t listing with nothing saying which number the rupees belonged to.
                Sourced from the cost breakdown so the volume and the money can never disagree. */}
            {dealVolumeTons != null && (
              <p
                className="text-sm font-medium flex items-center gap-1.5"
                style={{ color: shortfall ? 'var(--warn-text)' : 'var(--accent-text)' }}
              >
                {shortfall ? <AlertTriangle size={14} /> : <ArrowRight size={14} />}
                {shortfall
                  ? `This listing covers only ${formatNumber(listing.remainingVolumeTons)} of the ${formatNumber(dealVolumeTons)} tons requested`
                  : match.transactedVolumeTons != null
                    ? `Matching ${formatNumber(dealVolumeTons)} tons of this listing's ${formatNumber(listing.totalVolumeTons)}`
                    : `Quoted for ${formatNumber(dealVolumeTons)} tons`}
              </p>
            )}
          </motion.div>
        )}

        {/* Compatibility Score */}
        <motion.div>
          <div className="flex justify-between text-sm font-medium mb-2" style={{ color: 'var(--text)' }}>
            <span>Compatibility Match</span>
            <span className={`font-bold ${scoreTextColor(score)}`}>{score.toFixed(0)}%</span>
          </div>
          <div className="w-full rounded-full h-3 overflow-hidden shadow-inner" style={{ background: 'var(--surface-light)' }}>
            <motion.div
              initial={{ width: 0 }}
              animate={{ width: `${Math.min(100, Math.max(0, score))}%` }}
              transition={{ duration: 1, ease: 'easeOut', delay: 0.2 }}
              className={`h-full rounded-full ${scoreBarColor(score)}`}
            />
          </div>
        </motion.div>

        {/* Logistics & Cost */}
        <motion.div style={{ borderColor: 'var(--border)' }} className="grid grid-cols-2 gap-4 border-t pt-4">
          <div className="space-y-1">
            <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--muted)' }}>
              <MapPin size={12} /> Logistics
            </p>
            <p className="text-sm font-medium" style={{ color: 'var(--text)' }}>
              {match.emitterCity || 'Unknown'} → {match.buyerCity || 'Unknown'}
            </p>
            <p className="text-xs" style={{ color: 'var(--muted)' }}>
              {formatKm(match.distanceKm)} km apart
            </p>
          </div>

          <div className="space-y-1">
            <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--muted)' }}>
              <IndianRupee size={12} /> Financials
              {dealVolumeTons != null && (
                <span className="normal-case tracking-normal font-normal">· for {formatNumber(dealVolumeTons)} t</span>
              )}
            </p>
            <p className="text-xs flex justify-between" style={{ color: 'var(--muted)' }}><span>Base:</span> <span>{formatCurrency(match.costBreakdown.basePrice)}</span></p>
            <p className="text-xs flex justify-between" style={{ color: 'var(--muted)' }}><span>Freight:</span> <span>{formatCurrency(match.costBreakdown.transportCost)}</span></p>
            <div className="h-px w-full my-1" style={{ background: 'var(--border)' }}></div>
            <p className="text-sm font-bold flex justify-between" style={{ color: 'var(--text)' }}><span>Total:</span> <span>{formatCurrency(match.costBreakdown.totalCost)}</span></p>
          </div>
        </motion.div>

        {deliveryStatus && (
          <motion.div
            style={{ background: 'var(--surface-light)', borderColor: 'var(--border)' }}
            className="rounded-xl p-4 border space-y-3"
          >
            <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--muted)' }}>
              <Truck size={12} /> Delivery Status
            </p>
            <DeliveryProgress status={deliveryStatus} paymentStatus={paymentStatus} />
            {deliveryError && (
              <p className="text-[var(--danger-text)] text-xs font-medium bg-[var(--danger-soft)] border border-[var(--danger-border)] p-2 rounded-lg">
                {deliveryError}
              </p>
            )}
            {onAdvanceDelivery && nextDeliveryStatus && deliveryBlockedByPayment && (
              <p
                className="text-[11px] text-center rounded-lg py-2 px-3"
                style={{ background: 'var(--surface)', color: 'var(--muted)' }}
                data-testid="delivery-blocked"
              >
                Waiting on the buyer&apos;s payment before this can be marked delivered.
              </p>
            )}

            {onAdvanceDelivery && nextDeliveryStatus && !deliveryBlockedByPayment && (
              <motion.button
                whileTap={{ scale: 0.98 }}
                type="button"
                onClick={() => onAdvanceDelivery(nextDeliveryStatus)}
                disabled={deliveryBusy}
                className="w-full bg-teal-600 hover:bg-teal-500 text-[var(--text-strong)] font-medium text-sm px-4 py-2 rounded-xl disabled:opacity-50 transition-colors"
              >
                {deliveryBusy ? 'Updating...' : DELIVERY_ACTION_LABEL[nextDeliveryStatus]}
              </motion.button>
            )}
          </motion.div>
        )}

        {match.statusMessage && (
          <motion.p
            style={{ background: 'var(--surface-light)', borderColor: 'var(--border)', color: 'var(--text)' }}
            data-testid="match-status-message"
            className="text-sm italic rounded-lg px-3 py-2 border"
          >
            {match.statusMessage}
          </motion.p>
        )}

        {stale && (
          <motion.p
            style={{ color: 'var(--muted)' }}
            data-testid="match-stale-message"
            className="text-sm italic"
          >
            This match is no longer available.
          </motion.p>
        )}

        {actionError && (
          <motion.p className="text-[var(--danger-text)] text-sm font-medium bg-[var(--danger-soft)] border border-[var(--danger-border)] p-2 rounded-lg">
            {actionError}
          </motion.p>
        )}

        {/* 3D Action Buttons */}
        {!stale && (onRequest || onAccept || onReject || onCancel || onRevert || onViewReceipt || onPay) && (
          <motion.div className="flex flex-wrap gap-3 pt-2">
            {/* Takes the place of Request/Accept rather than sitting beside them: the server
                refuses this deal, so offering a button that can only return a 409 would be
                worse than saying nothing. Reject stays available - an emitter who can't fill a
                request should still be able to decline it. */}
            {shortfall && (onRequest || onAccept) && (
              <p
                className="flex-1 text-[11px] text-center rounded-lg py-2 px-3"
                style={{ background: 'var(--warn-soft)', color: 'var(--warn-text)' }}
                data-testid="shortfall-blocked"
              >
                {onAccept
                  ? `Can't accept — only ${formatNumber(listing?.remainingVolumeTons)} t left of the ${formatNumber(dealVolumeTons)} t requested.`
                  : `Can't request — this listing has only ${formatNumber(listing?.remainingVolumeTons)} t left.`}
              </p>
            )}
            {onRequest && isSuggested && !shortfall && (
              <motion.button
                whileTap={{ scale: 0.95 }}
                type="button"
                onClick={() => onRequest(match.id)}
                disabled={busy}
                className="flex-1 bg-teal-600 hover:bg-teal-500 text-[var(--text-strong)] font-medium text-sm px-4 py-2.5 rounded-xl disabled:opacity-50 transition-colors shadow-lg shadow-teal-600/20"
              >
                {busy ? 'Requesting...' : 'Request Match'}
              </motion.button>
            )}
            {onAccept && isActionable && !shortfall && (
              <motion.button
                whileTap={{ scale: 0.95 }}
                type="button"
                onClick={() => onAccept(match.id)}
                disabled={busy}
                className="flex-1 bg-emerald-600 hover:bg-emerald-500 text-[var(--text-strong)] font-medium text-sm px-4 py-2.5 rounded-xl disabled:opacity-50 transition-colors shadow-lg shadow-emerald-600/20"
              >
                {busy ? 'Working...' : 'Accept'}
              </motion.button>
            )}
            {onReject && isActionable && (
              <motion.button
                whileTap={{ scale: 0.95 }}
                type="button"
                onClick={() => onReject(match.id)}
                disabled={busy}
                style={{ background: 'var(--surface-light)', borderColor: 'var(--border)' }}
                className="flex-1 hover:bg-[var(--danger-soft)] border hover:border-[var(--danger-border)] text-[var(--danger-text)] font-medium text-sm px-4 py-2.5 rounded-xl disabled:opacity-50 transition-colors"
              >
                {busy ? 'Working...' : 'Reject'}
              </motion.button>
            )}
            {onCancel && isRequested && (
              <motion.button
                whileTap={{ scale: 0.95 }}
                type="button"
                onClick={() => onCancel(match.id)}
                disabled={busy}
                className="flex-1 bg-[var(--surface-light)] hover:bg-[var(--surface)] text-[var(--text)] border border-[var(--border)] font-medium text-sm px-4 py-2.5 rounded-xl disabled:opacity-50 transition-colors"
              >
                {busy ? 'Cancelling...' : 'Cancel Request'}
              </motion.button>
            )}
            {onRevert && isRevertable && (
              <motion.button
                whileTap={{ scale: 0.95 }}
                type="button"
                onClick={handleRevertClick}
                disabled={busy}
                className="flex-1 bg-amber-600 hover:bg-amber-500 text-[var(--text-strong)] font-medium text-sm px-4 py-2.5 rounded-xl disabled:opacity-50 transition-colors shadow-lg shadow-amber-600/20"
              >
                {busy ? 'Reverting...' : 'Revert'}
              </motion.button>
            )}
            {/* Paying and reading the receipt are separate buttons: a receipt is a record,
                and opening one shouldn't be treated as an intent to pay. */}
            {onPay && paymentDue && (
              <motion.button
                whileTap={{ scale: 0.95 }}
                type="button"
                onClick={() => onPay(match.orderId)}
                data-testid="pay-order"
                className="flex items-center justify-center gap-1.5 flex-1 bg-gradient-to-r from-emerald-500 to-teal-500 text-black font-semibold text-sm px-4 py-2.5 rounded-xl transition-all shadow-lg shadow-emerald-500/20"
              >
                <Wallet size={14} /> Pay Now
              </motion.button>
            )}

            {onViewReceipt && match.orderId != null && (
              <motion.button
                whileTap={{ scale: 0.95 }}
                type="button"
                onClick={() => onViewReceipt(match.orderId)}
                style={{ background: 'var(--surface-light)', borderColor: 'var(--border)', color: 'var(--text)' }}
                className="flex items-center justify-center gap-1.5 flex-1 border hover:border-[var(--accent-soft-border)] font-medium text-sm px-4 py-2.5 rounded-xl transition-colors"
              >
                <ReceiptIcon size={14} /> View Receipt
              </motion.button>
            )}
          </motion.div>
        )}
      </motion.div>
    </div>
  )
}
