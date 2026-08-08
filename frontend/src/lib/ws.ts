import { Client } from "@stomp/stompjs"
import { getAccessToken, type AppNotification } from "@/lib/api"

/**
 * Live notifications over STOMP (FR-12). The CONNECT frame carries the current
 * access token; beforeConnect refreshes it on reconnects so a 15-minute token
 * lifetime doesn't kill the stream.
 */
export function connectNotifications(onMessage: (n: AppNotification) => void): () => void {
  const protocol = window.location.protocol === "https:" ? "wss" : "ws"
  const client = new Client({
    brokerURL: `${protocol}://${window.location.host}/ws`,
    reconnectDelay: 5000,
    beforeConnect: () => {
      client.connectHeaders = { Authorization: `Bearer ${getAccessToken() ?? ""}` }
    },
  })
  client.onConnect = () => {
    client.subscribe("/user/queue/notifications", (message) => {
      onMessage(JSON.parse(message.body) as AppNotification)
    })
  }
  client.activate()
  return () => {
    client.deactivate()
  }
}

/**
 * Live ambient weather band (F4). No history to replay on reconnect — the
 * caller should also GET /api/v1/weather once on mount for the catch-up
 * value, since this only fires on the next committed transition.
 */
export function connectWeather(onBand: (band: string) => void): () => void {
  const protocol = window.location.protocol === "https:" ? "wss" : "ws"
  const client = new Client({
    brokerURL: `${protocol}://${window.location.host}/ws`,
    reconnectDelay: 5000,
    beforeConnect: () => {
      client.connectHeaders = { Authorization: `Bearer ${getAccessToken() ?? ""}` }
    },
  })
  client.onConnect = () => {
    client.subscribe("/user/queue/weather", (message) => {
      const payload = JSON.parse(message.body) as { band?: string }
      if (payload.band) onBand(payload.band)
    })
  }
  client.activate()
  return () => {
    client.deactivate()
  }
}
