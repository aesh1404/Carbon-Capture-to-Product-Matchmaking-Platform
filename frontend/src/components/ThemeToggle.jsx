import { Moon, Sun } from 'lucide-react'

// Shared by the app shell and the signed-out pages, so the choice is reachable before you
// have an account as well as after.
export default function ThemeToggle({ theme, onToggle, floating = false }) {
  const next = theme === 'dark' ? 'light' : 'dark'
  const label = `Switch to ${next} theme`

  return (
    <button
      type="button"
      data-testid="theme-toggle"
      className={floating ? 'switch-button theme-toggle-floating' : 'switch-button'}
      aria-label={label}
      title={label}
      onClick={onToggle}
    >
      {theme === 'dark' ? <Sun size={16} /> : <Moon size={16} />}
      {theme === 'dark' ? 'Light' : 'Dark'}
    </button>
  )
}
