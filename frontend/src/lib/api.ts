// Cliente HTTP del panel: agrega el JWT, traduce errores y avisa cuando la sesión expira.

export type ConversationStatus = 'BOT_ACTIVO' | 'ESCALADO_PENDIENTE' | 'HUMANO_CONTROL' | 'ARCHIVADO'
export type Sender = 'CLIENTE' | 'BOT' | 'COMERCIAL'
export type DeliveryStatus = 'PENDING' | 'SENT' | 'DELIVERED' | 'READ' | 'FAILED'

export interface Conversation {
  id: string
  customerName: string
  customerPhone: string
  status: ConversationStatus
  leadScore: number
  lastMessage: string | null
  lastMessageSender: Sender | null
  lastMessageAt: string | null
  /** Cierre de la ventana de 24 h para escribir texto libre (null si el cliente nunca escribió). */
  windowClosesAt: string | null
  summary: string | null
  assignedSalespersonId: string | null
  createdAt: string
  updatedAt: string
}

export interface ChatMessage {
  id: string
  sender: Sender
  content: string
  mediaType: 'TEXT' | 'IMAGE' | 'DOCUMENT' | 'TEMPLATE'
  mediaId: string | null
  mediaFilename: string | null
  deliveryStatus: DeliveryStatus | null
  deliveryError: string | null
  createdAt: string
}

export interface Session {
  token: string
  userId: string
  name: string
  email: string
  role: string
  companyId: string
  companyName: string
  mustChangePassword: boolean
  platformAdmin: boolean
}

export interface Kpis {
  activeConversations: number
  newConversations: number
  botResolutionRate: number | null
  hotLeads: number
  escalations: number
  medianAgentResponseSeconds: number | null
  agentResponsesMeasured: number
  clientMessages: number
  botMessages: number
  agentMessages: number
}

export interface DayPoint {
  date: string
  client: number
  bot: number
  agent: number
}

export interface Dashboard {
  days: number
  timezone: string
  kpis: Kpis
  previous: Kpis
  series: DayPoint[]
  statusNow: Record<ConversationStatus, number>
  topLeads: {
    id: string
    customerName: string | null
    customerPhone: string
    leadScore: number
    status: ConversationStatus
    updatedAt: string
  }[]
}

export interface CompanySettings {
  name: string
  whatsappPhoneNumberId: string | null
  whatsappBusinessAccountId: string | null
  hasOwnWhatsappToken: boolean
  knowledgeBase: string | null
  customPrompt: string | null
  brandColor: string | null
  logoDataUrl: string | null
  updatedAt: string
}

export type Role = 'ADMIN' | 'COMERCIAL'

export interface Member {
  id: string
  name: string
  email: string
  role: Role
  active: boolean
  mustChangePassword: boolean
  createdAt: string
}

export interface Invitation {
  member: Member
  temporaryPassword: string
}

export interface WhatsAppVerification {
  ok: boolean
  displayPhoneNumber: string | null
  verifiedName: string | null
  qualityRating: string | null
  error: string | null
}

export interface WhatsAppStatus {
  phoneNumberId: string | null
  hasOwnToken: boolean
  verification: WhatsAppVerification
}

export interface OnboardingStep {
  key: string
  title: string
  done: boolean
  detail: string
}

export interface Onboarding {
  steps: OnboardingStep[]
  platformWebhook: { callbackUrl: string } | null
}

export type LeadStatus = 'NUEVA' | 'CONTACTADA' | 'DESCARTADA' | 'CONVERTIDA'

export interface WhatsAppTemplate {
  name: string
  language: string
  category: string
  body: string
  paramCount: number
}

export interface ClientCompany {
  id: string
  name: string
  active: boolean
  whatsappConnected: boolean
  users: number
  conversations: number
  yours: boolean
  createdAt: string
}

export interface NewClient {
  company: ClientCompany
  invitation: Invitation
}

export interface SalesLead {
  id: string
  name: string
  companyName: string
  email: string
  phone: string
  message: string | null
  status: LeadStatus
  createdAt: string
}

export interface Product {
  id: string
  name: string
  category: string | null
  material: string | null
  description: string | null
  price: number
  currency: string
  deliveryTimeDays: number | null
  warrantyMonths: number | null
  available: boolean
  updatedAt: string
}

export type ProductInput = Omit<Product, 'id' | 'updatedAt'>

export interface BotAnswer {
  respuesta: string
  leadScore: number
  requiereEscalamiento: boolean
}

export interface PreviewMessage {
  role: 'user' | 'assistant'
  content: string
}

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

const SESSION_KEY = 'ch.session'

export function loadSession(): Session | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    return raw ? (JSON.parse(raw) as Session) : null
  } catch {
    return null
  }
}

export function saveSession(session: Session | null) {
  if (session) localStorage.setItem(SESSION_KEY, JSON.stringify(session))
  else localStorage.removeItem(SESSION_KEY)
}

let onUnauthorized: () => void = () => {}

export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const session = loadSession()
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (session) headers.set('Authorization', `Bearer ${session.token}`)

  let res: Response
  try {
    res = await fetch(path, { ...init, headers })
  } catch {
    throw new ApiError(0, 'Sin conexión con el servidor')
  }

  if (res.status === 401 && session) {
    onUnauthorized()
    throw new ApiError(401, 'Tu sesión expiró. Vuelve a iniciar sesión.')
  }
  if (!res.ok) {
    throw new ApiError(res.status, await errorMessage(res))
  }
  if (res.status === 204 || res.status === 202) return undefined as T
  const type = res.headers.get('Content-Type') ?? ''
  if (type.includes('application/json')) return (await res.json()) as T
  return (await res.blob()) as T
}

async function errorMessage(res: Response): Promise<string> {
  const text = await res.text().catch(() => '')
  try {
    const json = JSON.parse(text)
    return json.error ?? json.message ?? `Error ${res.status}`
  } catch {
    return text || `Error ${res.status}`
  }
}

export const api = {
  login: (email: string, password: string) =>
    request<Session>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),

  conversations: () => request<Conversation[]>('/api/conversations'),

  messages: (conversationId: string) =>
    request<ChatMessage[]>(`/api/conversations/${conversationId}/mensajes`),

  takeControl: (id: string) =>
    request<Conversation>(`/api/conversations/${id}/tomar-control`, { method: 'POST' }),

  releaseControl: (id: string) =>
    request<Conversation>(`/api/conversations/${id}/liberar-control`, { method: 'POST' }),

  archive: (id: string) =>
    request<Conversation>(`/api/conversations/${id}/archivar`, { method: 'POST' }),

  sendMessage: (id: string, content: string) =>
    request<void>(`/api/conversations/${id}/mensajes`, {
      method: 'POST',
      body: JSON.stringify({ content }),
    }),

  media: (mediaId: string) => request<Blob>(`/api/media/${encodeURIComponent(mediaId)}`),

  dashboard: (days: number) =>
    request<Dashboard>(`/api/dashboard?days=${days}&tz=${encodeURIComponent(Intl.DateTimeFormat().resolvedOptions().timeZone)}`),

  companySettings: () => request<CompanySettings>('/api/company'),

  saveKnowledge: (knowledgeBase: string, customPrompt: string) =>
    request<CompanySettings>('/api/company/knowledge', {
      method: 'PUT',
      body: JSON.stringify({ knowledgeBase, customPrompt }),
    }),

  products: () => request<Product[]>('/api/products'),

  saveProduct: (product: ProductInput, id?: string) =>
    request<Product>(id ? `/api/products/${id}` : '/api/products', {
      method: id ? 'PUT' : 'POST',
      body: JSON.stringify(product),
    }),

  deleteProduct: (id: string) => request<void>(`/api/products/${id}`, { method: 'DELETE' }),

  previewBot: (messages: PreviewMessage[]) =>
    request<BotAnswer>('/api/bot/preview', { method: 'POST', body: JSON.stringify({ messages }) }),

  changePassword: (currentPassword: string, newPassword: string) =>
    request<void>('/api/account/password', { method: 'PUT', body: JSON.stringify({ currentPassword, newPassword }) }),

  team: () => request<Member[]>('/api/team'),

  invite: (name: string, email: string, role: Role) =>
    request<Invitation>('/api/team', { method: 'POST', body: JSON.stringify({ name, email, role }) }),

  updateMember: (id: string, role: Role, active: boolean) =>
    request<Member>(`/api/team/${id}`, { method: 'PUT', body: JSON.stringify({ role, active }) }),

  resetPassword: (id: string) => request<Invitation>(`/api/team/${id}/reset-password`, { method: 'POST' }),

  saveBranding: (brandColor: string | null, logoDataUrl: string | null) =>
    request<CompanySettings>('/api/company/branding', {
      method: 'PUT',
      body: JSON.stringify({ brandColor: brandColor ?? '', logoDataUrl: logoDataUrl ?? '' }),
    }),

  connectWhatsapp: (phoneNumberId: string, accessToken: string, businessAccountId: string) =>
    request<WhatsAppStatus>('/api/company/whatsapp', {
      method: 'PUT',
      body: JSON.stringify({ phoneNumberId, accessToken, businessAccountId }),
    }),

  templates: () => request<WhatsAppTemplate[]>('/api/company/whatsapp/templates'),

  sendTemplate: (conversationId: string, name: string, language: string, params: string[]) =>
    request<void>(`/api/conversations/${conversationId}/plantilla`, {
      method: 'POST',
      body: JSON.stringify({ name, language, params }),
    }),

  clients: () => request<ClientCompany[]>('/api/platform/companies'),

  createClient: (companyName: string, adminName: string, adminEmail: string, leadId?: string) =>
    request<NewClient>('/api/platform/companies', {
      method: 'POST',
      body: JSON.stringify({ companyName, adminName, adminEmail, leadId }),
    }),

  setClientActive: (id: string, active: boolean) =>
    request<ClientCompany>(`/api/platform/companies/${id}`, { method: 'PUT', body: JSON.stringify({ active }) }),

  testWhatsapp: () => request<WhatsAppStatus>('/api/company/whatsapp/test', { method: 'POST' }),

  onboarding: () => request<Onboarding>('/api/company/onboarding'),

  platformLeads: () => request<SalesLead[]>('/api/platform/leads'),

  updatePlatformLead: (id: string, status: LeadStatus) =>
    request<SalesLead>(`/api/platform/leads/${id}`, { method: 'PUT', body: JSON.stringify({ status }) }),
}
