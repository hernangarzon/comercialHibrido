import { Client } from '@stomp/stompjs'
import { useEffect, useRef, useState } from 'react'
import SockJS from 'sockjs-client'
import type { Session } from './api'

export type RealtimeEvent =
  | { type: 'NEW_MESSAGE' | 'BOT_REPLY' | 'STATUS_CHANGED' | 'DELIVERY'; conversationId: string }
  | {
      type: 'ESCALATION'
      conversationId: string
      customerName: string | null
      customerPhone: string | null
      summary: string | null
      leadScore: number | null
    }

/**
 * Conexión STOMP del panel. El JWT va en el CONNECT y solo se puede escuchar
 * el canal de la propia empresa. Reconecta solo si se cae la red.
 */
export function useRealtime(session: Session, onEvent: (event: RealtimeEvent) => void): boolean {
  const [connected, setConnected] = useState(false)
  const handler = useRef(onEvent)
  useEffect(() => {
    handler.current = onEvent
  }, [onEvent])

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      connectHeaders: { Authorization: `Bearer ${session.token}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 20000,
      heartbeatOutgoing: 20000,
      onConnect: () => {
        setConnected(true)
        client.subscribe(`/topic/empresa/${session.companyId}`, (frame) => {
          try {
            handler.current(JSON.parse(frame.body) as RealtimeEvent)
          } catch {
            // Mensaje no reconocido: se ignora.
          }
        })
      },
      onWebSocketClose: () => setConnected(false),
      onStompError: () => setConnected(false),
    })
    client.activate()
    return () => {
      void client.deactivate()
    }
  }, [session.token, session.companyId])

  return connected
}
