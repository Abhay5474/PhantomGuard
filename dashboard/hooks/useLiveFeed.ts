"use client";

import { Client } from "@stomp/stompjs";
import { useEffect, useRef, useState } from "react";
import SockJS from "sockjs-client";
import { WS_URL } from "@/lib/api";
import type { LiveEvent } from "@/lib/types";

const MAX_EVENTS = 200;

/**
 * Subscribes to /topic/live-feed/{parentId} over STOMP (SockJS transport) and
 * maintains a bounded rolling buffer of telemetry events, newest first.
 */
export function useLiveFeed(parentId: string, initial: LiveEvent[] = []) {
  const [events, setEvents] = useState<LiveEvent[]>(initial);
  const [connected, setConnected] = useState(false);
  const seeded = useRef(false);

  useEffect(() => {
    if (!seeded.current && initial.length > 0) {
      setEvents(initial.slice(0, MAX_EVENTS));
      seeded.current = true;
    }
  }, [initial]);

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL) as WebSocket,
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true);
        client.subscribe(`/topic/live-feed/${parentId}`, (message) => {
          const event = JSON.parse(message.body) as LiveEvent;
          setEvents((prev) => [event, ...prev].slice(0, MAX_EVENTS));
        });
      },
      onDisconnect: () => setConnected(false),
      onWebSocketClose: () => setConnected(false),
    });
    client.activate();
    return () => {
      client.deactivate();
    };
  }, [parentId]);

  return { events, connected };
}
