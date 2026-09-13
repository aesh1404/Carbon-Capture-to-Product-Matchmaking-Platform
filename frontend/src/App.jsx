import { useEffect, useState } from 'react'
import { Leaf, Recycle, LayoutDashboard, Sprout } from 'lucide-react'
import { applyTheme, initialTheme } from './utils/theme'
import ThemeToggle from './components/ThemeToggle'
import RoleSelector from './pages/RoleSelector'
import EmitterDashboard from './pages/EmitterDashboard'
import BuyerDashboard from './pages/BuyerDashboard'
import Receipt from './pages/Receipt'
import Payment from './pages/Payment'
import ImpactDashboard from './pages/ImpactDashboard'

export default function App() {
  const [user, setUser] = useState(null)
  const [theme, setTheme] = useState(initialTheme)

  // Applied on mount too, not just on change: the initial value may have come from the OS
  // preference rather than a stored choice, and <html> still needs the attribute either way.
  useEffect(() => {
    applyTheme(theme)
  }, [theme])

  const [receiptOrderId, setReceiptOrderId] = useState(null)
  // Platform-wide, not tied to buyer/emitter role - a top-level view switch alongside the
  // user's own role dashboard, not another tab inside it.
  const [view, setView] = useState('dashboard') // 'dashboard' | 'impact'

  // Paying and reading a receipt are separate actions, reached by separate buttons. Viewing a
  // receipt never interrupts you for payment - a receipt is a record, and wanting to read one
  // is not the same as wanting to pay.
  const [payingOrderId, setPayingOrderId] = useState(null)

  const toggleTheme = () => setTheme(theme === 'dark' ? 'light' : 'dark')

  if (!user) {
    return <RoleSelector onRegistered={setUser} theme={theme} onToggleTheme={toggleTheme} />
  }

  function goToView(next) {
    setView(next)
    setReceiptOrderId(null)
    setPayingOrderId(null)
  }

  // Checkout hands straight over to the receipt for the order just settled.
  function handlePaid(orderId) {
    setPayingOrderId(null)
    setReceiptOrderId(orderId)
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand">
          <div className="brand-mark">
            <Leaf size={20} />
          </div>

          <div>
            <strong>
              Carbon<span>Link</span>
            </strong>
            <small>CO₂ exchange marketplace</small>
          </div>
        </div>

        <nav className="header-nav" aria-label="Top-level navigation">
          <button
            type="button"
            onClick={() => goToView('dashboard')}
            className={`header-nav-button ${view === 'dashboard' ? 'active' : ''}`}
          >
            <LayoutDashboard size={14} /> My Dashboard
          </button>
          <button
            type="button"
            onClick={() => goToView('impact')}
            className={`header-nav-button ${view === 'impact' ? 'active' : ''}`}
          >
            <Sprout size={14} /> Impact Dashboard
          </button>
        </nav>

        <div className="top-actions">
          <span className="live-dot">
            <i />
            Marketplace live
          </span>

          <span className="user-company">
            {user.companyName} · {user.role}
          </span>

          <ThemeToggle theme={theme} onToggle={toggleTheme} />

          <button
            type="button"
            data-testid="switch-user-button"
            className="switch-button"
            onClick={() => {
              setUser(null)
              setReceiptOrderId(null)
              setPayingOrderId(null)
              setView('dashboard')
            }}
          >
            <Recycle size={16} />
            Switch user
          </button>
        </div>
      </header>

      {view === 'impact' ? (
        <ImpactDashboard />
      ) : payingOrderId ? (
        <Payment
          orderId={payingOrderId}
          onPaid={() => handlePaid(payingOrderId)}
          onBack={() => setPayingOrderId(null)}
        />
      ) : receiptOrderId ? (
        <Receipt orderId={receiptOrderId} onBack={() => setReceiptOrderId(null)} />
      ) : user.role === 'EMITTER' ? (
        <EmitterDashboard user={user} onViewReceipt={setReceiptOrderId} />
      ) : (
        <BuyerDashboard user={user} onViewReceipt={setReceiptOrderId} onPayOrder={setPayingOrderId} />
      )}
    </div>
  )
}
