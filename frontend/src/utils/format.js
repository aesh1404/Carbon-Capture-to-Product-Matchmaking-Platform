// Single source of truth for how money and tonnage render.
//
// Two problems this fixes:
//  1. Bare `.toLocaleString()` uses the *viewer's* locale, so the same order read "₹1,86,000"
//     on an en-IN machine and "₹186,000" on an en-US one. A demo shouldn't look different on
//     someone else's laptop, so the locale is pinned.
//  2. Some call sites interpolated the raw number (`₹{listing.pricePerTon}` -> "₹1200") while
//     others formatted it, so the same rupee value appeared two different ways on one screen.
//
// maximumFractionDigits also means a float artifact that survived the backend's rounding can
// never reach the screen as "₹218520.00000001".

const CURRENCY_FORMAT = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 })
// Headline aggregates round to whole rupees. Paise are meaningful on a single transaction -
// that's the amount someone actually pays - but on a crore-scale roll-up like "total value
// transacted" they're noise, and "₹1,47,16,752.45" reads as clutter rather than precision.
const WHOLE_CURRENCY_FORMAT = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 })
const NUMBER_FORMAT = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 })

function isRenderableNumber(value) {
  return typeof value === 'number' && Number.isFinite(value)
}

// Rupee amounts: "₹2,18,520", "₹1,234.57". Renders an em dash for a missing value rather than
// "₹undefined" or "₹NaN".
export function formatCurrency(value) {
  return isRenderableNumber(value) ? `₹${CURRENCY_FORMAT.format(value)}` : '—'
}

// Rupee aggregates on dashboards and stat cards: "₹2,18,520". Use formatCurrency instead for
// anything that represents one real transaction.
export function formatCurrencyWhole(value) {
  return isRenderableNumber(value) ? `₹${WHOLE_CURRENCY_FORMAT.format(value)}` : '—'
}

// Tonnage and other plain quantities: "1,200", "0.9".
export function formatNumber(value) {
  return isRenderableNumber(value) ? NUMBER_FORMAT.format(value) : '—'
}

// Distances are always shown to one decimal place.
export function formatKm(value) {
  return isRenderableNumber(value) ? value.toFixed(1) : '—'
}
