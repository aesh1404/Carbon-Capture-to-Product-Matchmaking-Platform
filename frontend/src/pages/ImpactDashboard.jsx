import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { Leaf, IndianRupee, Layers, PackageCheck, Sprout, RefreshCw } from 'lucide-react'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import { getAnalyticsSummary, extractErrorMessage } from '../api/api'
import { formatCurrencyWhole, formatNumber } from '../utils/format'

const containerVariants = {
  hidden: { opacity: 0 },
  show: { opacity: 1, transition: { staggerChildren: 0.1 } },
}

const itemVariants = {
  hidden: { opacity: 0, y: 20 },
  show: { opacity: 1, y: 0, transition: { type: 'spring', stiffness: 300, damping: 24 } },
}

const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

// The API groups by "2026-06"; an axis should read "Jun 2026".
function formatMonthLabel(isoMonth) {
  const [year, month] = String(isoMonth).split('-')
  const name = MONTH_NAMES[Number(month) - 1]
  return name ? `${name} ${year}` : isoMonth
}

// A little headroom above the tallest bar, rounded to a clean step, so the best month doesn't
// sit flush against the top of the plot and the quieter months stay readable against it.
function axisMaxFor(rows) {
  const peak = Math.max(0, ...rows.map((r) => r.volumeTons || 0))
  if (peak === 0) return 10
  const step = Math.pow(10, Math.floor(Math.log10(peak))) / 2
  return Math.ceil((peak * 1.15) / step) * step
}

function StatCard({ icon: Icon, label, value }) {
  return (
    <motion.div variants={itemVariants} className="surface-card p-6 space-y-3">
      <div className="flex items-center gap-2 text-[var(--accent-text)]">
        <Icon size={18} />
        <span className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--muted)' }}>{label}</span>
      </div>
      <p className="text-3xl font-extrabold" style={{ color: 'var(--text)' }}>{value}</p>
    </motion.div>
  )
}

export default function ImpactDashboard() {
  const [summary, setSummary] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  function load() {
    setLoading(true)
    setError(null)
    getAnalyticsSummary()
      .then(setSummary)
      .catch((err) => setError(extractErrorMessage(err)))
      .finally(() => setLoading(false))
  }

  useEffect(load, [])

  const chartData = (summary?.ordersByMonth ?? []).map((row) => ({
    ...row,
    label: formatMonthLabel(row.month),
  }))
  const yAxisMax = axisMaxFor(chartData)

  return (
    <motion.div variants={containerVariants} initial="hidden" animate="show" className="dashboard-container space-y-8">
      <motion.div variants={itemVariants} className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="p-3 bg-[var(--accent-soft)] text-[var(--accent-text)] rounded-xl border border-[var(--accent-soft-border)]">
            <Sprout size={26} />
          </div>
          <div>
            <h1 className="text-xl font-extrabold" style={{ color: 'var(--text)' }}>Impact Dashboard</h1>
            <p className="text-sm" style={{ color: 'var(--muted)' }}>Platform-wide CO₂ exchange metrics</p>
          </div>
        </div>
        <button
          type="button"
          onClick={load}
          disabled={loading}
          className="text-sm disabled:opacity-50 flex items-center gap-1.5"
          style={{ color: 'var(--green)' }}
        >
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> Refresh
        </button>
      </motion.div>

      {loading && <p className="text-sm" style={{ color: 'var(--muted)' }}>Loading...</p>}
      {error && <p className="text-[var(--danger-text)] text-sm bg-[var(--danger-soft)] p-3 rounded-lg border border-[var(--danger-border)]">{error}</p>}

      {summary && !loading && (
        <>
          <motion.div variants={containerVariants} className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <StatCard
              icon={Leaf}
              label="Total CO2 Diverted"
              value={`${formatNumber(summary.totalCo2DivertedTons)} t`}
            />
            <StatCard
              icon={IndianRupee}
              label="Total Value Transacted"
              value={formatCurrencyWhole(summary.totalValueTransacted)}
            />
            <StatCard
              icon={Layers}
              label="Active Listings"
              value={formatNumber(summary.totalActiveListings)}
            />
            <StatCard
              icon={PackageCheck}
              label="Confirmed Orders"
              value={formatNumber(summary.totalOrdersConfirmed)}
            />
          </motion.div>

          <motion.div variants={itemVariants} className="surface-card p-6 space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="font-bold text-lg" style={{ color: 'var(--text)' }}>Volume Transacted per Month</h2>
              {summary.topCaptureMethod && (
                <span className="text-xs" style={{ color: 'var(--muted)' }}>
                  Top capture method: <span className="font-semibold" style={{ color: 'var(--text)' }}>{summary.topCaptureMethod}</span>
                </span>
              )}
            </div>

            {summary.ordersByMonth.length === 0 ? (
              <p className="text-sm" style={{ color: 'var(--muted)' }}>No confirmed orders yet.</p>
            ) : (
              <div style={{ width: '100%', height: 300 }}>
                <ResponsiveContainer>
                  <BarChart data={chartData} margin={{ top: 8, right: 8, left: 4, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                    <XAxis dataKey="label" stroke="var(--muted)" fontSize={12} tickLine={false} />
                    {/* Zero-based with headroom. Letting Recharts auto-pick a non-zero floor
                        would exaggerate the month-to-month swings, and clamping the top to the
                        tallest bar leaves the strongest month touching the frame. */}
                    <YAxis
                      stroke="var(--muted)"
                      fontSize={12}
                      tickLine={false}
                      domain={[0, yAxisMax]}
                      allowDecimals={false}
                      width={56}
                      tickFormatter={(v) => formatNumber(v)}
                    />
                    <Tooltip
                      contentStyle={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 8, color: 'var(--text)' }}
                      labelStyle={{ color: 'var(--text)' }}
                      cursor={{ fill: 'var(--accent-soft)' }}
                      formatter={(value, name) => [`${formatNumber(value)} t`, name]}
                    />
                    <Bar dataKey="volumeTons" name="Volume (tons)" fill="var(--accent)" radius={[6, 6, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </motion.div>

          <motion.p variants={itemVariants} className="text-sm italic text-center" style={{ color: 'var(--muted)' }}>
            Every ton matched here is a ton kept out of the atmosphere and turned into product instead of waste.
          </motion.p>
        </>
      )}
    </motion.div>
  )
}
