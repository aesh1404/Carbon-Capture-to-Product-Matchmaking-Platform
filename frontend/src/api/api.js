import axios from 'axios'

 const client = axios.create({
-  baseURL: 'http://localhost:8080/api',
+  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080/api',
   headers: { 'Content-Type': 'application/json' },
 })


function unwrap(promise) {
  return promise.then((res) => res.data)
}

// --- Users ---
export function createUser(payload) {
  return unwrap(client.post('/users', payload))
}

// Username + password sign-in for the seeded demo accounts. Returns the same user record the
// one-click profile list hands back, so everything downstream is identical either way.
export function login(payload) {
  return unwrap(client.post('/auth/login', payload))
}

export function getCities() {
  return unwrap(client.get('/cities'))
}

export function getUser(id) {
  return unwrap(client.get(`/users/${id}`))
}

export function getUsersByRole(role) {
  return unwrap(client.get('/users', { params: { role } }))
}

// --- Listings ---
export function createListing(payload) {
  return unwrap(client.post('/listings', payload))
}

export function getListings(params = {}) {
  return unwrap(client.get('/listings', { params }))
}

export function getListing(id) {
  return unwrap(client.get(`/listings/${id}`))
}

// status omitted -> REQUESTED only ("Incoming Matches"); status: 'FINAL' -> ACCEPTED/REJECTED/
// CANCELLED/REVERTED ("Order Status" archive); or a specific status name.
export function getMatchesForListing(id, status) {
  return unwrap(client.get(`/listings/${id}/matches`, { params: status ? { status } : {} }))
}

// Plain filter + sort by distance (closest first), no compatibility scoring - for the buyer's
// "Quick Browse" tab, separate from the full request-based matching flow.
export function quickBrowseListings(params) {
  return unwrap(client.get('/listings/quick-browse', { params }))
}

// "Request This Listing" - creates a minimal request + an already-REQUESTED match in one call.
export function quickRequestListing(listingId, payload) {
  return unwrap(client.post(`/listings/${listingId}/quick-request`, payload))
}

// --- Carbon requests ---
export function createRequest(payload) {
  return unwrap(client.post('/requests', payload))
}

export function getRequest(id) {
  return unwrap(client.get(`/requests/${id}`))
}

export function getRequestsByBuyerId(buyerId) {
  return unwrap(client.get('/requests', { params: { buyerId } }))
}

export function getMatchesForRequest(id) {
  return unwrap(client.get(`/requests/${id}/matches`))
}

// status omitted -> everything ever sent; status: 'REQUESTED' -> "Pending Requests"; status:
// 'FINAL' -> ACCEPTED/REJECTED/CANCELLED/REVERTED ("Order Status" archive).
export function getSentMatchesForBuyer(buyerId, status) {
  return unwrap(client.get(`/requests/${buyerId}/sent-matches`, { params: status ? { status } : {} }))
}

// --- Matches ---
export function requestMatch(id) {
  return unwrap(client.post(`/matches/${id}/request`))
}

export function acceptMatch(id) {
  return unwrap(client.post(`/matches/${id}/accept`))
}

export function rejectMatch(id) {
  return unwrap(client.post(`/matches/${id}/reject`))
}

export function cancelMatch(id) {
  return unwrap(client.post(`/matches/${id}/cancel`))
}

export function revertMatch(id) {
  return unwrap(client.post(`/matches/${id}/revert`))
}

export function getUnviewedCount(params) {
  return unwrap(client.get('/matches/unviewed-count', { params }))
}

// --- Orders ---
export function getOrder(id) {
  return unwrap(client.get(`/orders/${id}`))
}

export function getOrdersForUser(userId) {
  return unwrap(client.get(`/orders/by-user/${userId}`))
}

// Records that the buyer settled this order. Still no payment provider and no money movement -
// it flips a flag the emitter can see, which is what gates their "Mark as Delivered".
export function payOrder(orderId) {
  return unwrap(client.post(`/orders/${orderId}/pay`))
}

// status: 'IN_TRANSIT' | 'DELIVERED' - only the next step in sequence is accepted.
export function updateDeliveryStatus(orderId, status) {
  return unwrap(client.post(`/orders/${orderId}/delivery-status`, { status }))
}

// --- Analytics ---
export function getAnalyticsSummary() {
  return unwrap(client.get('/analytics/summary'))
}

// --- Enrichment helpers: match responses only carry ids, so dashboards batch-fetch
// the listing/user/request rows they need for display, deduped, in parallel. ---
function uniqueIds(ids) {
  return [...new Set(ids.filter((id) => id !== undefined && id !== null))]
}

export async function getListingsByIds(ids) {
  const listings = await Promise.all(uniqueIds(ids).map((id) => getListing(id)))
  return Object.fromEntries(listings.map((listing) => [listing.id, listing]))
}

export async function getUsersByIds(ids) {
  const users = await Promise.all(uniqueIds(ids).map((id) => getUser(id)))
  return Object.fromEntries(users.map((user) => [user.id, user]))
}

export async function getRequestsByIds(ids) {
  const requests = await Promise.all(uniqueIds(ids).map((id) => getRequest(id)))
  return Object.fromEntries(requests.map((request) => [request.id, request]))
}

export function extractErrorMessage(error) {
  return error?.response?.data?.error || error?.message || 'Something went wrong'
}
