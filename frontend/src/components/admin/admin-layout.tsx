import { NavLink, Outlet } from 'react-router-dom'
import { Users, CalendarCog, Megaphone, Hammer } from 'lucide-react'
import { cn } from '@/lib/utils'

/**
 * Admin section shell (Day 13). Renders a secondary tab bar and an <Outlet/> for the
 * nested admin routes. Reached only by admins — App.tsx guards /admin on role and
 * redirects everyone else, and every underlying API call is admin-gated server-side.
 *
 * "Build" (contest/problem creation + test-case upload + standings rebuild) predates
 * Day 13 and lives on as its own tab alongside the newer Users/Contest/Announcements
 * pages, rather than being folded into "Contest" — building from scratch and editing an
 * existing contest's timing/state are different enough workflows to stay separate.
 */

const tabs = [
  { to: '/admin', end: true, label: 'Users', icon: Users },
  { to: '/admin/contest', end: false, label: 'Contest', icon: CalendarCog },
  { to: '/admin/announcements', end: false, label: 'Announcements', icon: Megaphone },
  { to: '/admin/build', end: false, label: 'Build', icon: Hammer },
]

export function AdminLayout() {
  return (
    <div className="flex-1 w-full flex flex-col overflow-hidden">
      <div className="border-b border-border glass px-8">
        <nav className="max-w-5xl mx-auto flex items-center gap-1">
          {tabs.map(({ to, end, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-2 px-4 py-3 text-sm font-medium border-b-2 transition -mb-px',
                  isActive
                    ? 'text-foreground border-accent'
                    : 'text-muted-foreground border-transparent hover:text-foreground hover:border-border',
                )
              }
            >
              <Icon size={15} />
              {label}
            </NavLink>
          ))}
        </nav>
      </div>
      <Outlet />
    </div>
  )
}
