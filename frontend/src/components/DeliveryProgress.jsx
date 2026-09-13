import { Circle, Lock, PackageCheck, Truck, PackageSearch } from 'lucide-react'

const STEPS = [
  { key: 'CONFIRMED', label: 'Confirmed', icon: PackageSearch },
  { key: 'IN_TRANSIT', label: 'In Transit', icon: Truck },
  { key: 'DELIVERED', label: 'Delivered', icon: PackageCheck },
]

export default function DeliveryProgress({ status, paymentStatus }) {
  const currentIndex = Math.max(0, STEPS.findIndex((step) => step.key === status))
  // Handover waits for the money. Shown as an explicitly locked step rather than a plain
  // not-yet-reached one, so both sides can see *why* it hasn't moved.
  const awaitingPayment = paymentStatus === 'PENDING'

  return (
    <div className="flex items-center">
      {STEPS.map((step, index) => {
        const reached = index <= currentIndex
        const locked = awaitingPayment && step.key === 'DELIVERED' && !reached
        const Icon = locked ? Lock : step.icon
        return (
          <div key={step.key} className="flex items-center flex-1 last:flex-none">
            <div className="flex flex-col items-center gap-1.5">
              <div
                className="flex items-center justify-center w-8 h-8 rounded-full border-2 transition-colors"
                style={
                  reached
                    ? { background: 'var(--green)', borderColor: 'var(--green)', color: 'var(--on-accent)' }
                    : { background: 'var(--surface-light)', borderColor: 'var(--border)', color: 'var(--muted)' }
                }
              >
                {reached ? <Icon size={15} /> : locked ? <Lock size={12} /> : <Circle size={10} />}
              </div>
              <span
                className="text-[11px] font-semibold whitespace-nowrap"
                style={{ color: reached ? 'var(--text)' : 'var(--muted)' }}
              >
                {step.label}
              </span>
              {locked && (
                <span className="text-[10px] whitespace-nowrap" style={{ color: 'var(--muted)' }}>
                  awaiting payment
                </span>
              )}
            </div>
            {index < STEPS.length - 1 && (
              <div
                className="flex-1 h-0.5 mx-2 mb-4 rounded-full transition-colors"
                style={{ background: index < currentIndex ? 'var(--green)' : 'var(--border)' }}
              />
            )}
          </div>
        )
      })}
    </div>
  )
}
