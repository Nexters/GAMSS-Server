import { Authenticated, Refine } from '@refinedev/core'
import routerBindings, { CatchAllNavigate, NavigateToResource } from '@refinedev/react-router-v6'
import { BrowserRouter, Outlet, Route, Routes } from 'react-router-dom'
import { firebaseAuthProvider } from '@/providers/authProvider'
import { devAuthProvider } from '@/providers/devAuthProvider'
import { dataProvider } from '@/providers/dataProvider'
import { AdminLayout } from '@/components/layout/AdminLayout'
import { LoginPage } from '@/pages/login'
import { MemberList } from '@/pages/members/list'
import { MemberShow } from '@/pages/members/show'
import { LlmSettingsPage } from '@/pages/llm-settings'

// mock(로컬) 모드에서는 Firebase 없이 dev-login 을 쓴다.
const authProvider = import.meta.env.VITE_AUTH_MODE === 'mock' ? devAuthProvider : firebaseAuthProvider

export default function App() {
  return (
    <BrowserRouter>
      <Refine
        dataProvider={dataProvider}
        authProvider={authProvider}
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
            <Route path="/llm-settings" element={<LlmSettingsPage />} />
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
