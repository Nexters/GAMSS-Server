import { Authenticated, Refine } from '@refinedev/core'
import routerBindings, { CatchAllNavigate, NavigateToResource } from '@refinedev/react-router-v6'
import { BrowserRouter, Outlet, Route, Routes } from 'react-router-dom'
import { mockAuthProvider } from '@/providers/authProvider'
import { mockDataProvider } from '@/providers/dataProvider'
import { AdminLayout } from '@/components/layout/AdminLayout'
import { LoginPage } from '@/pages/login'
import { MemberList } from '@/pages/members/list'
import { MemberShow } from '@/pages/members/show'

export default function App() {
  return (
    <BrowserRouter>
      <Refine
        dataProvider={mockDataProvider}
        authProvider={mockAuthProvider}
        routerProvider={routerBindings}
        resources={[{ name: 'members', list: '/members', show: '/members/:id', meta: { label: '회원 관리' } }]}
        options={{ syncWithLocation: true, disableTelemetry: true }}
      >
        <Routes>
          <Route
            element={
              <Authenticated key="protected" fallback={<CatchAllNavigate to="/login" />}>
                <AdminLayout />
              </Authenticated>
            }
          >
            <Route index element={<NavigateToResource resource="members" />} />
            <Route path="/members" element={<MemberList />} />
            <Route path="/members/:id" element={<MemberShow />} />
          </Route>

          <Route
            element={
              <Authenticated key="public" fallback={<Outlet />}>
                <NavigateToResource resource="members" />
              </Authenticated>
            }
          >
            <Route path="/login" element={<LoginPage />} />
          </Route>
        </Routes>
      </Refine>
    </BrowserRouter>
  )
}
