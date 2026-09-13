import { useCallback, useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { Factory, Activity, RefreshCw, Inbox, FileText, Clock, Receipt as ReceiptIcon, History } from 'lucide-react'
import ListingForm from '../components/ListingForm'
import MatchCard from '../components/MatchCard'
import { formatCurrency, formatNumber } from '../utils/format'
import {
  createListing,
  getListings,
  getMatchesForListing,
  acceptMatch,
  rejectMatch,
  revertMatch,
  getUnviewedCount,
  getOrdersForUser,
  updateDeliveryStatus,
  getRequestsByIds,
  getUsersByIds,
  extractErrorMessage,
} from '../api/api'

const DECISION_ACTIONS = { accept: acceptMatch, reject: rejectMatch, revert: revertMatch }

const TABS = [
  { key: 'incoming', label: 'Incoming Matches', icon: Clock },
  { key: 'listings', label: 'My Listings', icon: FileText },
  { key: 'orders', label: 'Order Status', icon: History },
]

const containerVariants = {
  hidden: { opacity: 0 },
  show: { opacity: 1, transition: { staggerChildren: 0.1 } },
}

const itemVariants = {
  hidden: { opacity: 0, y: 20 },
  show: { opacity: 1, y: 0, transition: { type: 'spring', stiffness: 300, damping: 24 } },
}

function ListingStatusBadge({ status }) {
  const styles = {
    ACTIVE: 'bg-[var(--accent-soft)] text-[var(--accent-text)] border-[var(--accent-soft-border)]',
    MATCHED: 'bg-[var(--info-soft)] text-[var(--info-text)] border-[var(--info-border)]',
    CLOSED: 'bg-[var(--neutral-soft)] text-[var(--neutral-text)] border-[var(--neutral-border)]',
  }
  return (
    <span className={`px-2.5 py-1 rounded-full text-xs font-bold border ${styles[status] || styles.CLOSED}`}>
      {status}
    </span>
  )
}

export default function EmitterDashboard({ user, onViewReceipt }) {
  const [activeTab, setActiveTab] = useState('incoming')

  const [listings, setListings] = useState([])
  const [loadingListings, setLoadingListings] = useState(true)
  const [listingsError, setListingsError] = useState(null)

  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState(null)

  // --- Tab 1: Incoming Matches - status == REQUESTED only, Accept/Reject act here ---
  const [matches, setMatches] = useState([])
  const [requestsById, setRequestsById] = useState({})
  const [buyersById, setBuyersById] = useState({})
  const [loadingMatches, setLoadingMatches] = useState(false)
  const [matchesError, setMatchesError] = useState(null)
  const [actionState, setActionState] = useState({})

  // --- Tab 3: Order Status - final-state matches only (archive), plus the order ledger ---
  const [finalMatches, setFinalMatches] = useState([])
  const [finalRequestsById, setFinalRequestsById] = useState({})
  const [finalBuyersById, setFinalBuyersById] = useState({})
  const [loadingFinal, setLoadingFinal] = useState(false)
  const [finalError, setFinalError] = useState(null)
  const [finalActionState, setFinalActionState] = useState({})
  const [hasUnviewedFinal, setHasUnviewedFinal] = useState(false)

  const [orders, setOrders] = useState([])
  const [loadingOrders, setLoadingOrders] = useState(false)
  const [ordersError, setOrdersError] = useState(null)
  const [orderCounterpartiesById, setOrderCounterpartiesById] = useState({})
  const [deliveryActionState, setDeliveryActionState] = useState({})

  const ordersById = Object.fromEntries(orders.map((order) => [order.id, order]))

  const refreshUnviewedBadge = useCallback(async () => {
    try {
      const { count } = await getUnviewedCount({ emitterId: user.id })
      setHasUnviewedFinal(count > 0)
    } catch {
      // Best-effort - a failed badge check shouldn't disrupt the dashboard.
    }
  }, [user.id])

  const loadMyListings = useCallback(async () => {
    setLoadingListings(true)
    setListingsError(null)
    try {
      // Owner-scoped: this is the ONLY call that returns personalListingNumber, and it
      // returns every status in one request (previously three calls + a client-side filter).
      const mine = await getListings({ emitterId: user.id })
      setListings(mine)
      return mine
    } catch (err) {
      setListingsError(extractErrorMessage(err))
      return []
    } finally {
      setLoadingListings(false)
    }
  }, [user.id])

  const loadIncomingMatches = useCallback(async (myListings) => {
    if (myListings.length === 0) {
      setMatches([])
      return
    }
    setLoadingMatches(true)
    setMatchesError(null)
    try {
      const perListing = await Promise.all(myListings.map((listing) => getMatchesForListing(listing.id, 'REQUESTED')))
      const flatMatches = perListing.flat()
      const requests = await getRequestsByIds(flatMatches.map((m) => m.requestId))
      const buyers = await getUsersByIds(Object.values(requests).map((r) => r.buyerId))
      setRequestsById(requests)
      setBuyersById(buyers)
      setMatches(flatMatches)
    } catch (err) {
      setMatchesError(extractErrorMessage(err))
    } finally {
      setLoadingMatches(false)
    }
  }, [])

  const loadOrderStatusMatches = useCallback(async (myListings) => {
    if (myListings.length === 0) {
      setFinalMatches([])
      return
    }
    setLoadingFinal(true)
    setFinalError(null)
    try {
      const perListing = await Promise.all(myListings.map((listing) => getMatchesForListing(listing.id, 'FINAL')))
      const flatMatches = perListing.flat()
      const requests = await getRequestsByIds(flatMatches.map((m) => m.requestId))
      const buyers = await getUsersByIds(Object.values(requests).map((r) => r.buyerId))
      setFinalRequestsById(requests)
      setFinalBuyersById(buyers)
      setFinalMatches(flatMatches)
      // This fetch just marked any cancelled matches as viewed server-side.
      refreshUnviewedBadge()
    } catch (err) {
      setFinalError(extractErrorMessage(err))
    } finally {
      setLoadingFinal(false)
    }
  }, [refreshUnviewedBadge])

  const loadOrderHistory = useCallback(async () => {
    setLoadingOrders(true)
    setOrdersError(null)
    try {
      const fetchedOrders = await getOrdersForUser(user.id)
      const counterpartyIds = fetchedOrders.map((o) => (o.emitterId === user.id ? o.buyerId : o.emitterId))
      const counterparties = await getUsersByIds(counterpartyIds)
      setOrderCounterpartiesById(counterparties)
      setOrders(fetchedOrders)
    } catch (err) {
      setOrdersError(extractErrorMessage(err))
    } finally {
      setLoadingOrders(false)
    }
  }, [user.id])

  const refreshAll = useCallback(async () => {
    const mine = await loadMyListings()
    await loadIncomingMatches(mine)
    return mine
  }, [loadMyListings, loadIncomingMatches])

  useEffect(() => {
    // Peek the badge BEFORE any tab's own fetch runs, since those fetches mark cancelled
    // matches as viewed server-side as a side effect - checking after would always report zero.
    refreshUnviewedBadge()
    refreshAll()
  }, [refreshUnviewedBadge, refreshAll])

  // Switching tabs refetches that tab's own data fresh - no full page reload needed.
  useEffect(() => {
    if (activeTab === 'orders') {
      loadOrderStatusMatches(listings)
      loadOrderHistory()
    }
  }, [activeTab, listings, loadOrderStatusMatches, loadOrderHistory])

  async function handleCreateListing(payload) {
    setSubmitting(true)
    setFormError(null)
    try {
      await createListing({ ...payload, emitterId: user.id })
      await refreshAll()
    } catch (err) {
      setFormError(extractErrorMessage(err))
      throw err
    } finally {
      setSubmitting(false)
    }
  }

  async function handleDecision(matchId, action) {
    setActionState((prev) => ({ ...prev, [matchId]: { busy: true, error: null, stale: false } }))
    try {
      await DECISION_ACTIONS[action](matchId)
      // Now ACCEPTED/REJECTED - "Incoming Matches" only shows REQUESTED, so it moves to Order Status.
      setMatches((prev) => prev.filter((m) => m.id !== matchId))
      setActionState((prev) => ({ ...prev, [matchId]: { busy: false, error: null, stale: false } }))
      // Accepting changes this listing's remaining stock/status - refresh the listings table too.
      loadMyListings()
    } catch (err) {
      const isStale = err?.response?.status === 409
      setActionState((prev) => ({
        ...prev,
        [matchId]: { busy: false, error: extractErrorMessage(err), stale: isStale },
      }))
      if (isStale) {
        // The listing/request moved on from under us - reconcile the whole view so this (and
        // any other now-stale match) is filtered out or shown correctly, not just this card.
        await refreshAll()
      }
    }
  }

  async function handleRevert(matchId) {
    setFinalActionState((prev) => ({ ...prev, [matchId]: { busy: true, error: null, stale: false } }))
    try {
      await revertMatch(matchId)
      setFinalActionState((prev) => ({ ...prev, [matchId]: { busy: false, error: null, stale: false } }))
      loadOrderStatusMatches(listings)
      loadMyListings() // reverting restores stock/reopens the listing
    } catch (err) {
      const isStale = err?.response?.status === 409
      setFinalActionState((prev) => ({
        ...prev,
        [matchId]: { busy: false, error: extractErrorMessage(err), stale: isStale },
      }))
      if (isStale) {
        loadOrderStatusMatches(listings)
      }
    }
  }

  async function handleAdvanceDelivery(orderId, nextStatus) {
    setDeliveryActionState((prev) => ({ ...prev, [orderId]: { busy: true, error: null } }))
    try {
      await updateDeliveryStatus(orderId, nextStatus)
      setDeliveryActionState((prev) => ({ ...prev, [orderId]: { busy: false, error: null } }))
      loadOrderHistory() // refreshes the ordersById lookup the progress indicator reads from
    } catch (err) {
      setDeliveryActionState((prev) => ({ ...prev, [orderId]: { busy: false, error: extractErrorMessage(err) } }))
    }
  }

  return (
    <motion.div
      variants={containerVariants}
      initial="hidden"
      animate="show"
      className="dashboard-container space-y-8"
    >
      <motion.div variants={itemVariants} className="flex items-center gap-3">
        <div className="p-3 bg-[var(--accent-soft)] text-[var(--accent-text)] rounded-xl border border-[var(--accent-soft-border)]">
          <Factory size={26} />
        </div>
        <div>
          <h1 className="text-xl font-extrabold" style={{ color: 'var(--text)' }}>Emitter Dashboard</h1>
          <p className="text-sm" style={{ color: 'var(--muted)' }}>{user.companyName}</p>
        </div>
      </motion.div>

      <motion.div variants={itemVariants} className="tab-bar">
        {TABS.map((tab) => {
          const Icon = tab.icon
          return (
            <button
              key={tab.key}
              type="button"
              onClick={() => setActiveTab(tab.key)}
              className={`tab-button ${activeTab === tab.key ? 'active' : ''}`}
            >
              <Icon size={15} />
              {tab.label}
              {tab.key === 'orders' && hasUnviewedFinal && (
                <span data-testid="order-status-new-badge" className="new-badge">NEW</span>
              )}
            </button>
          )
        })}
      </motion.div>

      {activeTab === 'incoming' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="font-bold text-lg flex items-center gap-2" style={{ color: 'var(--text)' }}>
              <Activity className="text-[var(--accent-text)]" size={20} /> Incoming Matches
            </h2>
            <button
              type="button"
              onClick={() => loadIncomingMatches(listings)}
              disabled={loadingMatches}
              className="text-sm disabled:opacity-50 flex items-center gap-1.5"
              style={{ color: 'var(--green)' }}
            >
              <RefreshCw size={14} className={loadingMatches ? 'animate-spin' : ''} /> Refresh
            </button>
          </div>
          {loadingMatches && <p className="text-sm" style={{ color: 'var(--muted)' }}>Loading...</p>}
          {matchesError && <p className="text-[var(--danger-text)] text-sm bg-[var(--danger-soft)] p-3 rounded-lg border border-[var(--danger-border)]">{matchesError}</p>}
          {!loadingMatches && !matchesError && matches.length === 0 && (
            <div className="surface-card flex flex-col items-center justify-center p-10 text-center" style={{ color: 'var(--muted)' }}>
              <Activity size={40} className="mb-3 opacity-50" />
              <p className="text-sm">No pending requests right now.</p>
            </div>
          )}

          <motion.div variants={containerVariants} initial="hidden" animate="show" className="space-y-4">
            {matches.map((match) => {
              const request = requestsById[match.requestId]
              const buyer = request ? buyersById[request.buyerId] : undefined
              const listing = listings.find((l) => l.id === match.listingId)
              const action = actionState[match.id] || {}
              return (
                <motion.div key={match.id} variants={itemVariants}>
                  <MatchCard
                      viewerRole="EMITTER"
                    match={match}
                    listing={listing}
                    counterpartyRoleLabel="Buyer"
                    counterpartyName={buyer?.companyName}
                    intendedUse={request?.intendedUse}
                    onAccept={(id) => handleDecision(id, 'accept')}
                    onReject={(id) => handleDecision(id, 'reject')}
                    busy={action.busy}
                    actionError={action.error}
                    stale={action.stale}
                  />
                </motion.div>
              )
            })}
          </motion.div>
        </div>
      )}

      {/* The table is the wider of the two: it carries six columns and was being squeezed into
          5/12 hard enough to need a horizontal scrollbar, while the form (two stacked field
          columns) had room to spare at 7/12. Swapped. */}
      {activeTab === 'listings' && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
          <div className="lg:col-span-5 space-y-8">
            <motion.div variants={itemVariants}>
              <ListingForm onSubmit={handleCreateListing} submitting={submitting} error={formError} />
            </motion.div>
          </div>

          <div className="lg:col-span-7 space-y-4">
            <div className="flex items-center gap-2 text-lg font-bold" style={{ color: 'var(--text)' }}>
              <FileText className="text-[var(--accent-text)]" size={20} />
              <h2>My Listings</h2>
            </div>

            {loadingListings && (
              <div className="flex justify-center p-8"><RefreshCw className="animate-spin" style={{ color: 'var(--muted)' }} /></div>
            )}
            {listingsError && <p className="text-[var(--danger-text)] text-sm bg-[var(--danger-soft)] p-3 rounded-lg border border-[var(--danger-border)]">{listingsError}</p>}

            {!loadingListings && !listingsError && listings.length === 0 && (
              <div className="surface-card flex flex-col items-center justify-center p-10 text-center" style={{ color: 'var(--muted)' }}>
                <Inbox size={40} className="mb-3 opacity-50" />
                <p className="text-sm">No listings yet. Create one above to get started.</p>
              </div>
            )}

            {listings.length > 0 && (
              <div className="surface-card overflow-x-auto">
                <table className="ledger-table">
                  <thead>
                    <tr>
                      <th>Listing</th>
                      <th>Site</th>
                      <th>Volume</th>
                      <th>Purity</th>
                      <th>Price/ton</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {listings.map((listing) => (
                      <tr key={listing.id}>
                        {/* Two deliberate lines rather than one that wraps: at this column
                            width "#2 (ID: 1)" broke mid-parenthesis. The personal number leads,
                            the global id sits under it as a quiet cross-reference. */}
                        <td className="font-mono whitespace-nowrap">
                          {listing.personalListingNumber != null ? (
                            <>
                              <span className="text-sm font-semibold" style={{ color: 'var(--text)' }}>
                                #{listing.personalListingNumber}
                              </span>
                              <span className="block text-[10px] opacity-50">ID {listing.id}</span>
                            </>
                          ) : (
                            <span className="text-sm" style={{ color: 'var(--text)' }}>#{listing.id}</span>
                          )}
                        </td>
                        {/* Where the supply physically sits, which is what every distance and
                            freight figure downstream is measured from - and the capture method
                            underneath it, since between them they describe what's being sold.
                            Neither was visible anywhere on the emitter's own page. */}
                        <td className="whitespace-nowrap">
                          <span className="text-sm" style={{ color: 'var(--text)' }}>{listing.city || '—'}</span>
                          <span className="block text-[10px] opacity-50">{listing.captureMethod}</span>
                        </td>
                        <td className="font-medium whitespace-nowrap">
                          {formatNumber(listing.remainingVolumeTons)}
                          <span style={{ color: 'var(--muted)' }}> / {formatNumber(listing.totalVolumeTons)} t</span>
                        </td>
                        <td className="whitespace-nowrap">{listing.purityPercent}%</td>
                        <td className="whitespace-nowrap">{formatCurrency(listing.pricePerTon)}</td>
                        <td><ListingStatusBadge status={listing.status} /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}

      {activeTab === 'orders' && (
        <div className="space-y-10">
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="font-bold text-lg flex items-center gap-2" style={{ color: 'var(--text)' }}>
                <History className="text-[var(--accent-text)]" size={20} /> Order Status
              </h2>
              <button
                type="button"
                onClick={() => loadOrderStatusMatches(listings)}
                disabled={loadingFinal}
                className="text-sm disabled:opacity-50 flex items-center gap-1.5"
                style={{ color: 'var(--green)' }}
              >
                <RefreshCw size={14} className={loadingFinal ? 'animate-spin' : ''} /> Refresh
              </button>
            </div>
            {loadingFinal && <p className="text-sm" style={{ color: 'var(--muted)' }}>Loading...</p>}
            {finalError && <p className="text-[var(--danger-text)] text-sm bg-[var(--danger-soft)] p-3 rounded-lg border border-[var(--danger-border)]">{finalError}</p>}
            {!loadingFinal && !finalError && finalMatches.length === 0 && (
              <div className="surface-card flex flex-col items-center justify-center p-10 text-center" style={{ color: 'var(--muted)' }}>
                <Inbox size={40} className="mb-3 opacity-50" />
                <p className="text-sm">No decided matches yet.</p>
              </div>
            )}

            <motion.div variants={containerVariants} initial="hidden" animate="show" className="space-y-4">
              {finalMatches.map((match) => {
                const request = finalRequestsById[match.requestId]
                const buyer = request ? finalBuyersById[request.buyerId] : undefined
                const listing = listings.find((l) => l.id === match.listingId)
                const action = finalActionState[match.id] || {}
                const order = match.orderId != null ? ordersById[match.orderId] : undefined
                const deliveryAction = match.orderId != null ? deliveryActionState[match.orderId] || {} : {}
                return (
                  <motion.div key={match.id} variants={itemVariants}>
                    <MatchCard
                      viewerRole="EMITTER"
                      match={match}
                      listing={listing}
                      counterpartyRoleLabel="Buyer"
                      counterpartyName={buyer?.companyName}
                      intendedUse={request?.intendedUse}
                      onRevert={handleRevert}
                      onViewReceipt={onViewReceipt}
                      deliveryStatus={order?.deliveryStatus}
                      paymentStatus={order?.paymentStatus}
                      onAdvanceDelivery={order?.status === 'CONFIRMED' ? (next) => handleAdvanceDelivery(match.orderId, next) : undefined}
                      deliveryBusy={deliveryAction.busy}
                      deliveryError={deliveryAction.error}
                      busy={action.busy}
                      actionError={action.error}
                      stale={action.stale}
                    />
                  </motion.div>
                )
              })}
            </motion.div>
          </div>

          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="font-bold text-lg flex items-center gap-2" style={{ color: 'var(--text)' }}>
                <ReceiptIcon className="text-[var(--accent-text)]" size={20} /> Order History
              </h2>
              <button
                type="button"
                onClick={loadOrderHistory}
                disabled={loadingOrders}
                className="text-sm disabled:opacity-50 flex items-center gap-1.5"
                style={{ color: 'var(--green)' }}
              >
                <RefreshCw size={14} className={loadingOrders ? 'animate-spin' : ''} /> Refresh
              </button>
            </div>
            {loadingOrders && <p className="text-sm" style={{ color: 'var(--muted)' }}>Loading...</p>}
            {ordersError && <p className="text-[var(--danger-text)] text-sm bg-[var(--danger-soft)] p-3 rounded-lg border border-[var(--danger-border)]">{ordersError}</p>}
            {!loadingOrders && !ordersError && orders.length === 0 && (
              <p className="text-sm" style={{ color: 'var(--muted)' }}>No confirmed orders yet.</p>
            )}
            {orders.length > 0 && (
              <div className="surface-card overflow-x-auto">
                <table className="ledger-table">
                  <thead>
                    <tr>
                      <th>Order #</th>
                      <th>Date</th>
                      <th>Buyer</th>
                      <th>Total Cost</th>
                      <th>Status</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {orders.map((order) => (
                      <tr key={order.id}>
                        <td className="font-mono">{order.orderNumber}</td>
                        <td>{new Date(order.createdAt).toLocaleDateString()}</td>
                        <td>{orderCounterpartiesById[order.buyerId]?.companyName || '...'}</td>
                        <td>{formatCurrency(order.totalCost)}</td>
                        <td>{order.status}</td>
                        <td>
                          <button
                            type="button"
                            onClick={() => onViewReceipt(order.id)}
                            className="text-sm font-semibold"
                            style={{ color: 'var(--green)' }}
                          >
                            View Receipt
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}
    </motion.div>
  )
}
