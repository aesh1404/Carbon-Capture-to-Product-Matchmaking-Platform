import ThemeToggle from '../components/ThemeToggle'
import { motion } from 'framer-motion'
import { Factory, Leaf, ArrowRight, Sparkles, ShieldCheck } from 'lucide-react'

// The one question the visitor has to answer before anything else. Everything below the fold
// is context; the two buttons are the page.
export default function LandingPage({ onChooseRole, theme, onToggleTheme }) {
  return (
    <div className="min-h-screen bg-[var(--background)] text-[var(--text)] flex flex-col items-center justify-center p-6 relative overflow-hidden select-none">
      {onToggleTheme && <ThemeToggle theme={theme} onToggle={onToggleTheme} floating />}

      <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[800px] h-[400px] bg-gradient-to-b from-emerald-500/10 via-emerald-900/5 to-transparent rounded-full blur-3xl pointer-events-none" />

      <div className="max-w-4xl mx-auto text-center space-y-10 z-10">
        <motion.div initial={{ opacity: 0, y: -20 }} animate={{ opacity: 1, y: 0 }} className="space-y-3">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[var(--accent-soft)] border border-[var(--accent-soft-border)] text-[var(--accent-text)] text-xs font-semibold tracking-wide uppercase shadow-inner">
            <Sparkles size={12} /> A cleaner supply chain starts here
          </div>

          <div className="flex items-center justify-center gap-2 pt-2">
            <div className="p-2 bg-emerald-500 rounded-xl text-black shadow-lg shadow-emerald-500/20">
              <Leaf size={20} fill="black" />
            </div>
            <span className="text-xl font-bold tracking-tight text-[var(--text-strong)]">
              Carbon<span className="text-[var(--accent-text)]">Link</span>
            </span>
          </div>
          <p className="text-xs text-emerald-500/60 uppercase tracking-widest font-mono">CO₂ Exchange Marketplace</p>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
          className="space-y-4 max-w-2xl mx-auto"
        >
          <h1 className="text-4xl sm:text-6xl font-extrabold tracking-tight text-[var(--text-strong)] leading-[1.1]">
            Move carbon from <span className="text-[var(--accent-text)]">captured</span> to useful.
          </h1>
          <p className="text-[var(--muted)] text-base sm:text-lg font-normal">
            One trusted marketplace for companies capturing CO₂ and the industries ready to use it.
          </p>
        </motion.div>

        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.15 }}
          className="pt-2"
        >
          <p className="text-sm font-semibold uppercase tracking-[0.2em] text-[var(--muted)]">
            Are you an Emitter or a Buyer?
          </p>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 30 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
          className="grid grid-cols-1 md:grid-cols-2 gap-5 w-full max-w-2xl mx-auto"
        >
          {/* Emerald = the supply side, used consistently from here through the whole emitter portal. */}
          <motion.button
            type="button"
            whileTap={{ scale: 0.98 }}
            onClick={() => onChooseRole('EMITTER')}
            data-testid="choose-emitter"
            className="group cursor-pointer text-left bg-gradient-to-b from-[var(--surface-light)]/80 to-[var(--surface)]/90 border border-[var(--accent-soft-border)] hover:border-emerald-500/60 p-7 rounded-2xl shadow-2xl backdrop-blur-md transition-all relative overflow-hidden"
          >
            <div className="absolute top-0 right-0 w-32 h-32 bg-emerald-500/5 rounded-full blur-2xl group-hover:bg-[var(--accent-soft)] transition-all" />
            <div className="flex items-center justify-between mb-4">
              <div className="p-3 bg-[var(--accent-soft)] border border-[var(--accent-soft-border)] text-[var(--accent-text)] rounded-xl group-hover:bg-emerald-500 group-hover:text-black transition-colors">
                <Factory size={24} />
              </div>
              <div className="w-8 h-8 rounded-full bg-[var(--accent-soft)] border border-[var(--accent-soft-border)] flex items-center justify-center text-[var(--accent-text)] transition-colors">
                <ArrowRight size={16} />
              </div>
            </div>
            <p className="text-[11px] font-bold uppercase tracking-widest text-emerald-500/70 mb-1">Emitter</p>
            <h3 className="text-xl font-bold text-[var(--text-strong)] mb-1">I capture CO₂</h3>
            <p className="text-xs text-[var(--muted)] leading-relaxed">
              Publish availability, manage your supply, and connect with qualified buyers.
            </p>
          </motion.button>

          {/* Teal = the demand side. */}
          <motion.button
            type="button"
            whileTap={{ scale: 0.98 }}
            onClick={() => onChooseRole('BUYER')}
            data-testid="choose-buyer"
            className="group cursor-pointer text-left bg-gradient-to-b from-[var(--surface-light)]/80 to-[var(--surface)]/90 border border-[var(--accent-soft-border)] hover:border-teal-400/60 p-7 rounded-2xl shadow-2xl backdrop-blur-md transition-all relative overflow-hidden"
          >
            <div className="absolute top-0 right-0 w-32 h-32 bg-teal-500/5 rounded-full blur-2xl group-hover:bg-[var(--accent-soft)] transition-all" />
            <div className="flex items-center justify-between mb-4">
              <div className="p-3 bg-[var(--accent-soft)] border border-[var(--accent-soft-border)] text-[var(--accent-text)] rounded-xl group-hover:bg-teal-400 group-hover:text-black transition-colors">
                <Leaf size={24} />
              </div>
              <div className="w-8 h-8 rounded-full bg-[var(--accent-soft)] border border-[var(--accent-soft-border)] flex items-center justify-center text-[var(--accent-text)] transition-colors">
                <ArrowRight size={16} />
              </div>
            </div>
            <p className="text-[11px] font-bold uppercase tracking-widest text-[var(--accent-text)]/70 mb-1">Buyer</p>
            <h3 className="text-xl font-bold text-[var(--text-strong)] mb-1">I need CO₂</h3>
            <p className="text-xs text-[var(--muted)] leading-relaxed">
              Find verified supply, compare terms, and request the right match.
            </p>
          </motion.button>
        </motion.div>

        <div className="pt-4 flex items-center justify-center gap-2 text-xs text-[var(--faint)] font-medium">
          <ShieldCheck size={14} className="text-emerald-500" /> Built for verified industrial exchange
        </div>
      </div>
    </div>
  )
}
