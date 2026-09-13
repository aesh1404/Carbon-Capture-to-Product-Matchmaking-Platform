// Theme persistence.
//
// The choice is written to <html data-theme> because that's where the token blocks in
// index.css are scoped, and to localStorage so it survives a reload.
//
// Dark is the product's identity and stays the default. The OS preference is deliberately NOT
// consulted: honouring it meant anyone on a light-mode machine landed on the light theme
// without asking, which is a surprising first impression for an app designed dark-first.
// Light is opt-in, and once opted into it sticks.
//
// Every read and write is guarded: localStorage throws outright in a private window or when a
// browser is set to block site data, and a theme toggle is not worth crashing the app over.

const STORAGE_KEY = 'carbonlink-theme'
export const THEMES = ['dark', 'light']

function stored() {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return THEMES.includes(value) ? value : null
  } catch {
    return null
  }
}

/** An explicit past choice wins; otherwise dark. */
export function initialTheme() {
  return stored() ?? 'dark'
}

export function applyTheme(theme) {
  const next = THEMES.includes(theme) ? theme : 'dark'
  document.documentElement.setAttribute('data-theme', next)
  // Tells the browser to style its own chrome - scrollbars, form controls, the gap behind
  // the page - to match. Without it a light page keeps dark scrollbars.
  document.documentElement.style.colorScheme = next
  try {
    localStorage.setItem(STORAGE_KEY, next)
  } catch {
    // Preference just won't persist; the current session still switches.
  }
}
