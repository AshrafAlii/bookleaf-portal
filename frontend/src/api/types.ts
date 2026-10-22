/**
 * TypeScript mirrors of the backend DTOs (com.bookleaf.portal.dto).
 * Keeping them in one file makes it easy to compare with the Java records.
 */

export type Role = 'AUTHOR' | 'ADMIN'
export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'
export type TicketCategory =
  | 'ROYALTY_PAYMENTS'
  | 'ISBN_METADATA'
  | 'PRINTING_QUALITY'
  | 'DISTRIBUTION_AVAILABILITY'
  | 'PRODUCTION_UPDATES'
  | 'GENERAL_INQUIRY'
export type TicketPriority = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
export type DecisionSource = 'AI' | 'HEURISTIC' | 'MANUAL'

export interface UserSummary {
  id: string
  email: string
  fullName: string
  role: Role
  authorId: string | null
  authorCode: string | null
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  user: UserSummary
}

export interface BookResponse {
  id: string
  bookCode: string
  title: string
  isbn: string
  genre: string | null
  publicationDate: string | null
  status: string
  inProduction: boolean
  mrp: number | null
  authorRoyaltyPerCopy: number | null
  totalCopiesSold: number
  totalRoyaltyEarned: number
  royaltyPaid: number
  royaltyPending: number
  lastRoyaltyPayoutDate: string | null
  printPartner: string | null
  availableOn: string[]
}

export interface AuthorStats {
  bookCount: number
  liveBookCount: number
  inProductionBookCount: number
  totalCopiesSold: number
  totalRoyaltyEarned: number
  royaltyPaid: number
  royaltyPending: number
  lifetimeValue: number
  openTicketCount: number
}

export interface AuthorDashboardResponse {
  authorCode: string
  name: string
  email: string
  city: string | null
  phone: string | null
  joinedDate: string | null
  stats: AuthorStats
  books: BookResponse[]
  recentTickets: TicketSummary[]
}

export interface TicketSummary {
  id: string
  ticketCode: string
  subject: string
  status: TicketStatus
  statusLabel: string
  category: TicketCategory
  categoryLabel: string
  categorySource: DecisionSource
  priority: TicketPriority
  priorityLabel: string
  prioritySource: DecisionSource
  bookId: string | null
  bookTitle: string | null
  bookCode: string | null
  authorId: string
  authorName: string
  createdAt: string
  updatedAt: string
  firstResponseAt: string | null
  assignedToName: string | null
  messageCount: number
  awaitingAdmin: boolean
  ageHours: number
  lastMessagePreview: string | null
}

export interface TicketMessageResponse {
  id: number
  body: string
  internal: boolean
  authorName: string
  authorRole: string
  createdAt: string
}

export interface TicketEventResponse {
  id: number
  eventType: string
  detail: string | null
  actorName: string
  createdAt: string
}

export interface AuthorSummary {
  id: string
  authorCode: string
  name: string
  email: string
  city: string | null
}

export interface AuthorTicketDetailResponse {
  ticket: TicketSummary
  description: string
  attachmentName: string | null
  book: BookResponse | null
  messages: TicketMessageResponse[]
  events: TicketEventResponse[]
}

export interface TicketDetailResponse {
  ticket: TicketSummary
  description: string
  attachmentName: string | null
  author: AuthorSummary
  book: BookResponse | null
  messages: TicketMessageResponse[]
  events: TicketEventResponse[]
  priorityRationale: string | null
  aiNote: string | null
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface QueueStats {
  open: number
  inProgress: number
  resolved: number
  closed: number
  awaitingFirstResponse: number
  overdue: number
  criticalOpen: number
  averageFirstResponseHours: number
}

export interface AiUsageSummary {
  totalCalls: number
  cachedCalls: number
  failedCalls: number
  promptTokens: number
  completionTokens: number
  averageLatencyMs: number
  provider: string | null
  model: string | null
}

export interface AiUsageEntry {
  id: number
  purpose: string
  provider: string
  model: string | null
  promptTokens: number | null
  completionTokens: number | null
  cached: boolean
  latencyMs: number | null
  success: boolean
  errorMessage: string | null
  createdAt: string
}

export interface AdminDashboardResponse {
  queue: QueueStats
  ai: AiUsageSummary
  urgentTickets: TicketSummary[]
  recentTickets: TicketSummary[]
  totalAuthors: number
  totalBooks: number
}

export interface MetaResponse {
  categories: string[]
  priorities: string[]
  statuses: string[]
  aiEnabled: boolean
  aiProvider: string
  aiModel: string | null
}

export interface DraftResult {
  available: boolean
  body: string | null
  provider: string
  model: string
  contextTokens: number
  note: string | null
}

export interface KnowledgeSectionInfo {
  title: string
  approxTokens: number
  body: string
}

export interface KnowledgeBaseInfo {
  sections: KnowledgeSectionInfo[]
  totalTokens: number
  version: string
}

export interface BulkUpdateRequest {
  ticketIds: string[]
  status?: string
  category?: string
  priority?: string
  assignToMe?: boolean
  note?: string
}

export interface BulkUpdateResponse {
  requested: number
  updated: number
  statusChanged: number
  categoryChanged: number
  priorityChanged: number
  assigned: number
  noted: number
  ticketIds: string[]
}

export interface SavedFilter {
  id: string
  name: string
  /** The stored queue filters, serialised as JSON. */
  filters: string
  createdAt: string
}

export interface TicketStreamEvent {
  type: string
  ticketId: string | null
  ticketCode: string | null
  status: string | null
  priority: string | null
  category: string | null
  message: string
  at: string
}

export interface ApiErrorBody {
  timestamp?: string
  status: number
  error: string
  message: string
  path?: string
  correlationId?: string
  fieldErrors?: Record<string, string>
}
