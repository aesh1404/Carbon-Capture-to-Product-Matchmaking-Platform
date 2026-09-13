import { useCallback, useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { Leaf, Activity, RefreshCw, Search, Inbox, Filter, Clock, Receipt as ReceiptIcon, History, Zap, ChevronDown } from 'lucide-react'
import RequestForm from '../components/RequestForm'
import MatchCard from '../components/MatchCard'
import QuickBrowseForm from '../components/QuickBrowseForm'
import QuickBrowseResultCard from '../components/QuickBrowseResultCard'
import { formatCurrency } from '../utils/format'
import {
  createRequest,
  getRequestsByBuyerId,
  getMatchesForRequest,
  getSentMatchesForBuyer,
  requestMatch,
  cancelMatch,
  getUnviewedCount,
  getOrdersForUser,
  getListingsByIds,
  getUsersByIds,
  quickBrowseListings,
  quickRequestListing,
  extractErrorMessage,
} from '../api/api'

const TABS = [
  { key: 'find', label: 'Find Matches', icon: Search },
  { key: 'quickBrowse', label: 'Quick Browse', icon: Zap },
  { key: 'pending', label: 'Pending Requests', icon: Clock },
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

export default function BuyerDashboard({ user, onViewReceipt, onPayOrder }) {
  const [activeTab, setActiveTab] = useState('find')

  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState(null)

  // --- Tab 1: Find Matches - fresh, actionable, unrequested recommendations only ---
  const [myRequests, setMyRequests] = useState([])
  const [loadingMatches, setLoadingMatches] = useState(false)
  const [matchesError, setMatchesError] = useState(null)
  const [matches, setMatches] = useState([])
  const [hasSearched, setHasSearched] = useState(false)
  const [activeRequestId, setActiveRequestId] = useState(null)
  const [listingsById, setListingsById] = useState({})
  const [emittersById, setEmittersById] = useState({})
  const [actionState, setActionState] = useState({})

  // --- Tab 1b: Quick Browse - plain distance filter+sort, separate from the request-based flow ---
  const [quickBrowseResults, setQuickBrowseResults] = useState([])
  const [quickBrowseEmittersById, setQuickBrowseEmittersById] = useState({})
  const [searchingQuickBrowse, setSearchingQuickBrowse] = useState(false)
  const [quickBrowseError, setQuickBrowseError] = useState(null)
  const [hasSearchedQuickBrowse, setHasSearchedQuickBrowse] = useState(false)
  const [quickRequestState, setQuickRequestState] = useState({})

  // --- Tab 2: Pending Requests - status == REQUESTED only, Cancel is the only action ---
  const [pendingMatches, setPendingMatches] = useState([])
  const [loadingPending, setLoadingPending] = useState(false)
  const [pendingError, setPendingError] = useState(null)
  const [pendingListingsById, setPendingListingsById] = useState({})
  const [pendingEmittersById, setPendingEmittersById] = useState({})
  const [pendingActionState, setPendingActionState] = useState({})

  // --- Tab 3: Order Status - final-state matches only (archive), plus the order ledger ---
  const [finalMatches, setFinalMatches] = useState([])
  const [loadingFinal, setLoadingFinal] = useState(false)
  const [finalError, setFinalError] = useState(null)
  const [finalListingsById, setFinalListingsById] = useState({})
  const [finalEmittersById, setFinalEmittersById] = useState({})
  const [hasUnviewedFinal, setHasUnviewedFinal] = useState(false)

  const [orders, setOrders] = useState([])
  const [loadingOrders, setLoadingOrders] = useState(false)
  const [ordersError, setOrdersError] = useState(null)
  const [orderCounterpartiesById, setOrderCounterpartiesById] = useState({})

  const ordersById = Object.fromEntries(orders.map((order) => [order.id, order]))

  const refreshUnviewedBadge = useCallback(async () => {
    try {
      const { count } = await getUnviewedCount({ buyerId: user.id })
      setHasUnviewedFinal(count > 0)
    } catch {
      // Best-effort - a failed badge check shouldn't disrupt the dashboard.
    }
  }, [user.id])

  async function loadMatches(requestId) {
    setLoadingMatches(true)
    setMatchesError(null)
    try {
      const rawMatches = await getMatchesForRequest(requestId)
      const listings = await getListingsByIds(rawMatches.map((m) => m.listingId))
      const emitters = await getUsersByIds(Object.values(listings).map((l) => l.emitterId))
      setListingsById(listings)
      setEmittersById(emitters)
      setMatches(rawMatches)
      setHasSearched(true)
      setActiveRequestId(requestId)
    } catch (err) {
      setMatchesError(extractErrorMessage(err))
    } finally {
      setLoadingMatches(false)
    }
  }

  async function handleQuickBrowseSearch(params) {
    setSearchingQuickBrowse(true)
    setQuickBrowseError(null)
    try {
      const results = await quickBrowseListings(params)
      const emitters = await getUsersByIds(results.map((r) => r.emitterId))
      setQuickBrowseEmittersById(emitters)
      setQuickBrowseResults(results)
      setQuickRequestState({})
      setHasSearchedQuickBrowse(true)
    } catch (err) {
      setQuickBrowseError(extractErrorMessage(err))
    } finally {
      setSearchingQuickBrowse(false)
    }
  }

  async function handleQuickRequest(listingId, minVolumeNeeded) {
    setQuickRequestState((prev) => ({ ...prev, [listingId]: { busy: true, error: null, requested: false } }))
    try {
      await quickRequestListing(listingId, { buyerId: user.id, minVolumeNeeded })
      setQuickRequestState((prev) => ({ ...prev, [listingId]: { busy: false, error: null, requested: true } }))
    } catch (err) {
      setQuickRequestState((prev) => ({
        ...prev,
        [listingId]: { busy: false, error: extractErrorMessage(err), requested: false },
      }))
    }
  }

  const loadPendingRequests = useCallback(async () => {
    setLoadingPending(true)
    setPendingError(null)
    try {
      const rawMatches = await getSentMatchesForBuyer(user.id, 'REQUESTED')
      const listings = await getListingsByIds(rawMatches.map((m) => m.listingId))
      const emitters = await getUsersByIds(Object.values(listings).map((l) => l.emitterId))
      setPendingListingsById(listings)
      setPendingEmittersById(emitters)
      setPendingMatches(rawMatches)
    } catch (err) {
      setPendingError(extractErrorMessage(err))
    } finally {
      setLoadingPending(false)
    }
  }, [user.id])

  const loadOrderStatus = useCallback(async () => {
    setLoadingFinal(true)
    setFinalError(null)
    try {
      const rawMatches = await getSentMatchesForBuyer(user.id, 'FINAL')
      const listings = await getListingsByIds(rawMatches.map((m) => m.listingId))
      const emitters = await getUsersByIds(Object.values(listings).map((l) => l.emitterId))
      setFinalListingsById(listings)
      setFinalEmittersById(emitters)
      setFinalMatches(rawMatches)
      // This fetch just marked any accepted/rejected/reverted matches as viewed server-side.
      refreshUnviewedBadge()
    } catch (err) {
      setFinalError(extractErrorMessage(err))
    } finally {
      setLoadingFinal(false)
    }
  }, [user.id, refreshUnviewedBadge])

  const loadOrderHistory = useCallback(async () => {
    setLoadingOrders(true)
    setOrdersError(null)
    try {
      const fetchedOrders = await getOrdersForUser(user.id)
      const counterpartyIds = fetchedOrders.map((o) => (o.buyerId === user.id ? o.emitterId : o.buyerId))
      const counterparties = await getUsersByIds(counterpartyIds)
      setOrderCounterpartiesById(counterparties)
      setOrders(fetchedOrders)
    } catch (err) {
      setOrdersError(extractErrorMessage(err))
    } finally {
      setLoadingOrders(false)
    }
  }, [user.id])

  // Log in as an existing buyer with prior requests -> immediately show their most
  // recent request's matches, instead of an empty dashboard requiring a new submission.
  useEffect(() => {
    let cancelled = false
    // Peek the badge BEFORE any tab's own fetch runs, since those fetches mark things
    // viewed server-side as a side effect - checking after would always report zero.
    refreshUnviewedBadge()
    getRequestsByBuyerId(user.id)
      .then((requests) => {
        if (cancelled) return
        setMyRequests(requests)
        if (requests.length > 0) {
          const mostRecent = requests.reduce((a, b) => (a.id > b.id ? a : b))
          loadMatches(mostRecent.id)
        }
      })
      .catch((err) => {
        if (!cancelled) setMatchesError(extractErrorMessage(err))
      })
    return () => {
      cancelled = true
    }
  }, [user.id, refreshUnviewedBadge])

  // Switching tabs refetches that tab's own data fresh - no full page reload needed.
  useEffect(() => {
    if (activeTab === 'pending') loadPendingRequests()
    else if (activeTab === 'orders') {
      loadOrderStatus()
      loadOrderHistory()
    }
  }, [activeTab, loadPendingRequests, loadOrderStatus, loadOrderHistory])

  async function handleCreateRequest(payload) {
    setSubmitting(true)
    setFormError(null)
    try {
      const request = await createRequest({ ...payload, buyerId: user.id })
      setMyRequests((prev) => [...prev, request])
      await loadMatches(request.id)
    } catch (err) {
      setFormError(extractErrorMessage(err))
      throw err
    } finally {
      setSubmitting(false)
    }
  }

  async function handleRequestMatch(matchId) {
    setActionState((prev) => ({ ...prev, [matchId]: { busy: true, error: null } }))
    try {
      await requestMatch(matchId)
      // Now REQUESTED - "Find Matches" only shows SUGGESTED, so it no longer belongs here.
      setMatches((prev) => prev.filter((m) => m.id !== matchId))
      setActionState((prev) => ({ ...prev, [matchId]: { busy: false, error: null } }))
    } catch (err) {
      setActionState((prev) => ({ ...prev, [matchId]: { busy: false, error: extractErrorMessage(err) } }))
    }
  }

  async function handleCancel(matchId) {
    setPendingActionState((prev) => ({ ...prev, [matchId]: { busy: true, error: null, stale: false } }))
    try {
      await cancelMatch(matchId)
      // Now CANCELLED - "Pending Requests" only shows REQUESTED, so it moves to Order Status.
      setPendingMatches((prev) => prev.filter((m) => m.id !== matchId))
      setPendingActionState((prev) => ({ ...prev, [matchId]: { busy: false, error: null, stale: false } }))
    } catch (err) {
      const isStale = err?.response?.status === 409
      setPendingActionState((prev) => ({
        ...prev,
        [matchId]: { busy: false, error: extractErrorMessage(err), stale: isStale },
      }))
      if (isStale) {
        // The emitter already decided this one from under us - reconcile the list.
        loadPendingRequests()
      }
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
          <Leaf size={26} />
        </div>
        <div>
          <h1 className="text-xl font-extrabold" style={{ color: 'var(--text)' }}>Buyer Dashboard</h1>
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

      {activeTab === 'find' && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
          <div className="lg:col-span-7 space-y-8">
            <motion.div variants={itemVariants}>
              <RequestForm onSubmit={handleCreateRequest} submitting={submitting} error={formError} />
            </motion.div>
          </div>

          <div className="lg:col-span-5 space-y-4">
            <motion.div variants={itemVariants} className="surface-card p-5 space-y-4">
              <div className="flex items-center justify-between border-b pb-3" style={{ borderColor: 'var(--border)' }}>
                <div className="flex items-center gap-2 text-lg font-bold" style={{ color: 'var(--text)' }}>
                  <Activity className="text-[var(--accent-text)]" size={20} />
                  <h2>Find Matches</h2>
                </div>
                {activeRequestId && (
                  <button
                    type="button"
                    onClick={() => loadMatches(activeRequestId)}
                    disabled={loadingMatches}
                    className="transition-colors disabled:opacity-50"
                    style={{ color: 'var(--muted)' }}
                    title="Refresh Matches"
                  >
                    <RefreshCw size={18} className={loadingMatches ? 'animate-spin' : ''} />
                  </button>
                )}
              </div>

              {activeRequestId && (
                <p className="text-xs font-mono" style={{ color: 'var(--muted)' }}>
                  Request #{myRequests.find((r) => r.id === activeRequestId)?.personalRequestNumber ?? activeRequestId}
                  {' '}<span className="opacity-60">(ID: {activeRequestId})</span>
                </p>
              )}

              {myRequests.length > 1 && (
                <div className="flex items-center gap-2 flex-1">
                  <Filter size={14} style={{ color: 'var(--muted)' }} />
                  <span className="relative block flex-1">
                    <select
                      value={activeRequestId ?? ''}
                      onChange={(e) => loadMatches(Number(e.target.value))}
                      className="w-full text-sm rounded-lg px-3 py-2 pr-9 outline-none transition-all appearance-none"
                      style={{ background: 'var(--surface-light)', border: '1px solid var(--border)', color: 'var(--text)' }}
                    >
                      {myRequests.map((request) => (
                        <option key={request.id} value={request.id}>
                          Req #{request.personalRequestNumber ?? request.id} — {request.intendedUse}
                        </option>
                      ))}
                    </select>
                    <ChevronDown size={14} className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2" style={{ color: 'var(--muted)' }} />
                  </span>
                </div>
              )}
            </motion.div>

            {loadingMatches && (
              <div className="flex justify-center p-8"><RefreshCw className="animate-spin" style={{ color: 'var(--muted)' }} /></div>
            )}

            {matchesError && <p className="text-[var(--danger-text)] text-sm bg-[var(--danger-soft)] p-3 rounded-lg border border-[var(--danger-border)]">{matchesError}</p>}

            {!loadingMatches && !matchesError && hasSearched && matches.length === 0 && (
              <div className="surface-card flex flex-col items-center justify-center p-10 text-center" style={{ color: 'var(--muted)' }}>
                <Search size={40} className="mb-3 opacity-50 text-[var(--accent-text)]" />
                <p className="text-sm font-medium">No matches found for this request.</p>
                <p className="text-xs mt-1">Try adjusting your budget or distance.</p>
              </div>
            )}
            {!hasSearched && !loadingMatches && (
              <div className="surface-card flex flex-col items-center justify-center p-10 text-center" style={{ color: 'var(--muted)' }}>
                <Inbox size={40} className="mb-3 opacity-50" />
                <p className="text-sm font-medium">Create a request to see ranked matches.</p>
              </div>
            )}

            <motion.div variants={containerVariants} className="space-y-4">
              {matches.map((match) => {
                const listing = listingsById[match.listingId]
                const emitter = listing ? emittersById[listing.emitterId] : undefined
                const action = actionState[match.id] || {}
                return (
                  <motion.div key={match.id} variants={itemVariants}>
                    <MatchCard
                      viewerRole="BUYER"
                      match={match}
                      listing={listing}
                      counterpartyRoleLabel="Emitter"
                      counterpartyName={emitter?.companyName}
                      onRequest={handleRequestMatch}
                      busy={action.busy}
                      actionError={action.error}
                    />
                  </motion.div>
                )
              })}
            </motion.div>
          </div>
        </div>
      )}

      {/* Stacked, not side-by-side. As a 5/7 split the filter panel was a fixed-height block
          next to a list that grows without limit, so scrolling past the first couple of results
          left a tall empty column down the left of the page. Filters now run full width across
          the top and the results sit beneath them in two columns - which also halves how far
          you have to scroll. */}
      {activeTab === 'quickBrowse' && (
        <div className="space-y-6">
          <motion.div variants={itemVariants}>
            <QuickBrowseForm onSearch={handleQuickBrowseSearch} searching={searchingQuickBrowse} error={quickBrowseError} />
          </motion.div>

          <div className="space-y-4">
            {searchingQuickBrowse && (
              <div className="flex justify-center p-8"><RefreshCw className="animate-spin" style={{ color: 'var(--muted)' }} /></div>
            )}

            {!searchingQuickBrowse && hasSearchedQuickBrowse && quickBrowseResults.length === 0 && (
              <div className="surface-card flex flex-col items-center justify-center p-10 text-center" style={{ color: 'var(--muted)' }}>
                <Zap size={40} className="mb-3 opacity-50 text-[var(--accent-text)]" />
                <p className="text-sm font-medium">No active listings match those filters.</p>
                <p className="text-xs mt-1">Try loosening the purity or distance filter.</p>
              </div>
            )}

            {!hasSearchedQuickBrowse && !searchingQuickBrowse && (
              <div className="surface-card flex flex-col items-center justify-center p-10 text-center" style={{ color: 'var(--muted)' }}>
                <Inbox size={40} className="mb-3 opacity-50" />
                <p className="text-sm font-medium">Pick your city and search to browse listings, closest first.</p>
              </div>
            )}

            <motion.div
              variants={containerVariants}
              initial="hidden"
              animate="show"
              className="grid grid-cols-1 xl:grid-cols-2 gap-4 items-start"
            >
              {quickBrowseResults.map((result) => {
                const action = quickRequestState[result.listingId] || {}
                return (
                  <motion.div key={result.listingId} variants={itemVariants}>
                    <QuickBrowseResultCard
                      result={result}
                      emitterName={quickBrowseEmittersById[result.emitterId]?.companyName}
                      onRequest={handleQuickRequest}
                      busy={action.busy}
                      error={action.error}
                      requested={action.requested}
                    />
                  </motion.div>
                )
              })}
            </motion.div>
          </div>
        </div>
      )}

      {activeTab === 'pending' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="font-bold text-lg flex items-center gap-2" style={{ color: 'var(--text)' }}>
              <Clock className="text-[var(--accent-text)]" size={20} /> Pending Requests
            </h2>
            <button
              type="button"
              onClick={loadPendingRequests}
              disabled={loadingPending}
              className="text-sm disabled:opacity-50 flex items-center gap-1.5"
              style={{ color: 'var(--green)' }}
            >
              <RefreshCw size={14} className={loadingPending ? 'animate-spin' : ''} /> Refresh
            </button>
          </div>
          {loadingPending && <p className="text-sm" style={{ color: 'var(--muted)' }}>Loading...</p>}
          {pendingError && <p className="text-[var(--danger-text)] text-sm bg-[var(--danger-soft)] p-3 rounded-lg border border-[var(--danger-border)]">{pendingError}</p>}
          {!loadingPending && !pendingError && pendingMatches.length === 0 && (
            <div className="surface-card flex flex-col items-center justify-center p-10 text-center" style={{ color: 'var(--muted)' }}>
              <Inbox size={40} className="mb-3 opacity-50" />
              <p className="text-sm">
                Nothing pending — click "Request Match" on a match in Find Matches to express interest.
              </p>
            </div>
          )}

          <motion.div variants={containerVariants} initial="hidden" animate="show" className="space-y-4">
            {pendingMatches.map((match) => {
              const listing = pendingListingsById[match.listingId]
              const emitter = listing ? pendingEmittersById[listing.emitterId] : undefined
              const action = pendingActionState[match.id] || {}
              return (
                <motion.div key={match.id} variants={itemVariants}>
                  <MatchCard
                      viewerRole="BUYER"
                    match={match}
                    listing={listing}
                    counterpartyRoleLabel="Emitter"
                    counterpartyName={emitter?.companyName}
                    onCancel={handleCancel}
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

      {activeTab === 'orders' && (
        <div className="space-y-10">
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="font-bold text-lg flex items-center gap-2" style={{ color: 'var(--text)' }}>
                <History className="text-[var(--accent-text)]" size={20} /> Order Status
              </h2>
              <button
                type="button"
                onClick={loadOrderStatus}
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
                const listing = finalListingsById[match.listingId]
                const emitter = listing ? finalEmittersById[listing.emitterId] : undefined
                const order = match.orderId != null ? ordersById[match.orderId] : undefined
                return (
                  <motion.div key={match.id} variants={itemVariants}>
                    <MatchCard
                      viewerRole="BUYER"
                      match={match}
                      listing={listing}
                      counterpartyRoleLabel="Emitter"
                      counterpartyName={emitter?.companyName}
                      onViewReceipt={onViewReceipt}
                      deliveryStatus={order?.deliveryStatus}
                      paymentStatus={order?.paymentStatus}
                      onPay={onPayOrder}
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
                      <th>Emitter</th>
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
                        <td>{orderCounterpartiesById[order.emitterId]?.companyName || '...'}</td>
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
