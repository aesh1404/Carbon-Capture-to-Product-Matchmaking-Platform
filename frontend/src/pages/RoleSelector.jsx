import { useCallback, useEffect, useState } from 'react'
import LandingPage from './LandingPage'
import LoginPortal from './LoginPortal'

// Routes the pre-login flow: the landing chooser, then one of two role portals.
//
// The app has no router (state-based view switching, per the brief), so these are hash routes -
// #/login/emitter and #/login/buyer. That buys the two things a real route gives you without
// adding a dependency: the portals are directly linkable (useful for a demo - open the emitter
// portal in one window and the buyer portal in another), and the browser Back button behaves.
//
// Keeps the same `onRegistered` contract it always had, so App.jsx is untouched.
const ROUTES = {
  '#/login/emitter': 'EMITTER',
  '#/login/buyer': 'BUYER',
}
const HASH_FOR_ROLE = {
  EMITTER: '#/login/emitter',
  BUYER: '#/login/buyer',
}

function roleFromHash() {
  return ROUTES[window.location.hash] || null
}

export default function RoleSelector({ onRegistered, theme, onToggleTheme }) {
  const [role, setRole] = useState(roleFromHash)

  // Back/forward buttons and hand-typed URLs both land here.
  useEffect(() => {
    const onHashChange = () => setRole(roleFromHash())
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [])

  const chooseRole = useCallback((chosenRole) => {
    // Setting the hash fires hashchange, which sets the state - one source of truth.
    window.location.hash = HASH_FOR_ROLE[chosenRole]
  }, [])

  const goBack = useCallback(() => {
    window.location.hash = ''
  }, [])

  // Clear the hash on successful login so a later logout returns to the landing page rather
  // than bouncing straight back into the portal the user just came from.
  const handleRegistered = useCallback((user) => {
    window.location.hash = ''
    onRegistered(user)
  }, [onRegistered])

  if (!role) {
    return <LandingPage onChooseRole={chooseRole} theme={theme} onToggleTheme={onToggleTheme} />
  }

  return (
    <LoginPortal
      role={role}
      onRegistered={handleRegistered}
      onBack={goBack}
      appTheme={theme}
      onToggleTheme={onToggleTheme}
    />
  )
}
