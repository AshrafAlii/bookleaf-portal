import { api, buildQuery } from './client'
import type {
  AdminDashboardResponse,
  AiUsageEntry,
  AiUsageSummary,
  AuthorDashboardResponse,
  AuthorTicketDetailResponse,
  AuthResponse,
  BookResponse,
  BulkUpdateRequest,
  BulkUpdateResponse,
  DraftResult,
  KnowledgeBaseInfo,
  MetaResponse,
  PageResponse,
  QueueStats,
  SavedFilter,
  TicketDetailResponse,
  TicketSummary,
} from './types'

// ---------------------------------------------------------------- auth

export const authApi = {
  login: (email: string, password: string) =>
    api.post<AuthResponse>('/auth/login', { email, password }),
  logout: () => api.post<void>('/auth/logout'),
  me: () => api.get<AuthResponse['user']>('/auth/me'),
}

// ---------------------------------------------------------------- author

export const authorApi = {
  dashboard: () => api.get<AuthorDashboardResponse>('/author/dashboard'),
  books: () => api.get<BookResponse[]>('/author/books'),
  book: (id: string) => api.get<BookResponse>(`/author/books/${id}`),
  tickets: () => api.get<TicketSummary[]>('/author/tickets'),
  ticket: (id: string) => api.get<AuthorTicketDetailResponse>(`/author/tickets/${id}`),
  createTicket: (payload: {
    bookId: string | null
    subject: string
    description: string
    attachmentName?: string | null
  }) => api.post<TicketSummary>('/author/tickets', payload),
  reply: (id: string, body: string) =>
    api.post<TicketSummary>(`/author/tickets/${id}/replies`, { body }),
}

// ---------------------------------------------------------------- admin

export interface QueueFilters {
  status?: string
  category?: string
  priority?: string
  assignedTo?: string
  from?: string
  to?: string
  search?: string
  sort?: string
  page?: number
  size?: number
}

export const adminApi = {
  dashboard: () => api.get<AdminDashboardResponse>('/admin/dashboard'),
  stats: () => api.get<QueueStats>('/admin/stats'),
  aiUsage: () => api.get<AiUsageSummary>('/admin/ai-usage'),
  recentAiUsage: (limit = 25) => api.get<AiUsageEntry[]>(`/admin/ai-usage/recent?limit=${limit}`),
  queue: (filters: QueueFilters) =>
    api.get<PageResponse<TicketSummary>>(`/admin/tickets${buildQuery(filters as Record<string, string>)}`),
  ticket: (id: string) => api.get<TicketDetailResponse>(`/admin/tickets/${id}`),
  updateStatus: (id: string, status: string) =>
    api.put<TicketDetailResponse>(`/admin/tickets/${id}/status`, { status }),
  overrideCategory: (id: string, category: string) =>
    api.put<TicketDetailResponse>(`/admin/tickets/${id}/category`, { category }),
  overridePriority: (id: string, priority: string) =>
    api.put<TicketDetailResponse>(`/admin/tickets/${id}/priority`, { priority }),
  assignToMe: (id: string) => api.post<TicketDetailResponse>(`/admin/tickets/${id}/assign`),
  reply: (id: string, body: string) => api.post<TicketDetailResponse>(`/admin/tickets/${id}/replies`, { body }),
  note: (id: string, body: string) => api.post<TicketDetailResponse>(`/admin/tickets/${id}/notes`, { body }),
  /**
   * @param refresh ask the model again for a different draft, bypassing the
   *                response cache (otherwise an identical cached draft is returned)
   */
  draft: (id: string, refresh = false) =>
    api.post<DraftResult>(`/admin/tickets/${id}/draft${refresh ? '?refresh=true' : ''}`),
  bulkUpdate: (payload: BulkUpdateRequest) =>
    api.post<BulkUpdateResponse>('/admin/tickets/bulk', payload),
}

// ---------------------------------------------------------------- saved filters

export const filterApi = {
  list: () => api.get<SavedFilter[]>('/admin/filters'),
  create: (name: string, filters: QueueFilters) =>
    api.post<SavedFilter>('/admin/filters', { name, filters: JSON.stringify(filters) }),
  remove: (id: string) => api.delete<void>(`/admin/filters/${id}`),
}

// ---------------------------------------------------------------- meta

export const metaApi = {
  meta: () => api.get<MetaResponse>('/meta'),
  knowledgeBase: () => api.get<KnowledgeBaseInfo>('/meta/knowledge-base'),
}
