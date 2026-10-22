import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from './auth/AuthContext'
import { RequireAuth } from './auth/RequireAuth'
import { AppLayout } from './components/AppLayout'
import { LoginPage } from './pages/LoginPage'
import { AuthorDashboard } from './pages/author/AuthorDashboard'
import { AuthorBooks } from './pages/author/AuthorBooks'
import { AuthorTickets } from './pages/author/AuthorTickets'
import { NewTicket } from './pages/author/NewTicket'
import { AuthorTicketDetail } from './pages/author/AuthorTicketDetail'
import { AdminDashboard } from './pages/admin/AdminDashboard'
import { AdminQueue } from './pages/admin/AdminQueue'
import { AdminTicketDetail } from './pages/admin/AdminTicketDetail'
import { AdminKnowledge } from './pages/admin/AdminKnowledge'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
      staleTime: 15_000,
    },
  },
})

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />

            <Route
              element={
                <RequireAuth role="AUTHOR">
                  <AppLayout />
                </RequireAuth>
              }
            >
              <Route path="/author" element={<AuthorDashboard />} />
              <Route path="/author/books" element={<AuthorBooks />} />
              <Route path="/author/tickets" element={<AuthorTickets />} />
              <Route path="/author/tickets/new" element={<NewTicket />} />
              <Route path="/author/tickets/:ticketId" element={<AuthorTicketDetail />} />
            </Route>

            <Route
              element={
                <RequireAuth role="ADMIN">
                  <AppLayout />
                </RequireAuth>
              }
            >
              <Route path="/admin" element={<AdminDashboard />} />
              <Route path="/admin/tickets" element={<AdminQueue />} />
              <Route path="/admin/tickets/:ticketId" element={<AdminTicketDetail />} />
              <Route path="/admin/knowledge" element={<AdminKnowledge />} />
            </Route>

            <Route path="/" element={<Navigate to="/login" replace />} />
            <Route path="*" element={<Navigate to="/login" replace />} />
          </Routes>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
