import { useState, useEffect } from 'react'
import { NavLink, useLocation } from 'react-router-dom'

const nav = [
  { to: '/',           label: 'Live' },
  { to: '/logs',       label: 'Logs' },
  { to: '/detections', label: 'Detections' },
  { to: '/reactions',  label: 'Reactions' },
  { to: '/system',     label: 'System' },
]

function HamburgerIcon() {
  return (
    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M4 6h16M4 12h16M4 18h16" />
    </svg>
  )
}

export default function Layout({ children }) {
  const [open, setOpen] = useState(false)
  const location = useLocation()

  // Close drawer on navigation
  useEffect(() => { setOpen(false) }, [location.pathname])

  return (
    <div className="flex h-screen bg-gray-950 text-gray-300">

      {/* Mobile backdrop */}
      {open && (
        <div
          className="fixed inset-0 bg-black/50 z-20 lg:hidden"
          onClick={() => setOpen(false)}
        />
      )}

      {/* Sidebar */}
      <nav className={[
        'fixed lg:static inset-y-0 left-0 z-30',
        'w-44 shrink-0 border-r border-gray-800 flex flex-col bg-gray-950',
        'transition-transform duration-200 ease-in-out',
        open ? 'translate-x-0' : '-translate-x-full lg:translate-x-0',
      ].join(' ')}>
        <div className="px-4 py-5 border-b border-gray-800 flex items-center justify-between">
          <span className="text-sm font-semibold text-white tracking-wide uppercase">Log Analyzer</span>
        </div>
        <ul className="flex flex-col gap-0.5 p-2 mt-1">
          {nav.map(({ to, label }) => (
            <li key={to}>
              <NavLink
                to={to}
                end={to === '/'}
                className={({ isActive }) =>
                  `block px-3 py-2 rounded text-sm transition-colors ${
                    isActive
                      ? 'bg-gray-800 text-white'
                      : 'text-gray-400 hover:text-white hover:bg-gray-800/50'
                  }`
                }
              >
                {label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>

      {/* Content area */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* Mobile top bar */}
        <div className="lg:hidden flex items-center h-12 px-4 border-b border-gray-800 shrink-0">
          <button
            onClick={() => setOpen(true)}
            className="text-gray-400 hover:text-white"
            aria-label="Open menu"
          >
            <HamburgerIcon />
          </button>
          <span className="ml-3 text-sm font-semibold text-white tracking-wide uppercase">
            Log Analyzer
          </span>
        </div>

        <main className="flex-1 overflow-y-auto">{children}</main>
      </div>
    </div>
  )
}
