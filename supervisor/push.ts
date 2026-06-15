import { hashId, FIXED_TIME } from "./observability";
import type { GateKind, GateNotification } from "./gates";

const TITLE_ALIASES = new Set(["input_needed"]);
const BODY_ALIASES = new Set(["open_app_to_continue"]);
const FIVE_MINUTES_MS = 5 * 60 * 1000;

export interface GateLike {
  sessionId: string;
  projectId: string;
  gateKind: GateKind | string;
  frameId?: string;
  createdAt?: string;
  sessionAlias?: string;
  collapseIdHash?: string;
  threadIdHash?: string;
  deepLinkOpaqueId?: string;
  notificationId?: string;
}

export interface GateNotificationInternal extends GateLike {
  safeTitleAlias: "input_needed";
  safeBodyAlias: "open_app_to_continue";
}

export interface PublicPushPayload {
  schemaVersion: 1;
  notificationId: string;
  safeTitleAlias: "input_needed";
  safeBodyAlias: "open_app_to_continue";
  category: string;
  collapseIdHash: string;
  threadIdHash: string;
  deepLinkOpaqueId: string;
  createdAtBucket: string;
  readonly ttlSeconds?: 900;
  readonly priority?: "high";
}

export interface ProviderPushPayload {
  publicPayload: PublicPushPayload;
  collapseIdHash?: string;
  collapseId?: string;
  ttlSeconds: 900;
  priority: "high";
}

export interface ApnsPushRequest {
  provider: "apns";
  headers: {
    "apns-push-type": "alert";
    "apns-priority": "10";
    "apns-topic": string;
    "apns-collapse-id": string;
    "apns-expiration": string;
  };
  payload: {
    aps: {
      alert: {
        "title-loc-key": "input_needed";
        "loc-key": "open_app_to_continue";
      };
      category: string;
      "thread-id": string;
    };
  } & PublicPushPayload;
}

export interface FcmPushRequest {
  provider: "fcm";
  message: {
    tokenAlias: string;
    notification: {
      title_loc_key: "input_needed";
      body_loc_key: "open_app_to_continue";
    };
    data: Record<Exclude<keyof PublicPushPayload, "ttlSeconds" | "priority">, string>;
    android: {
      collapse_key: string;
      ttl: "900s";
      priority: "HIGH";
    };
    apns: {
      headers: {
        "apns-collapse-id": string;
        "apns-expiration": string;
      };
    };
  };
}

export interface DeepLinkPushRequest {
  deepLink: string;
  allowedQueryKeys: ["id"];
  deepLinkOpaqueId: string;
  forbiddenQueryKeys: string[];
}

function createdAtBucket(createdAt: string): string {
  const time = Date.parse(createdAt);
  if (!Number.isFinite(time)) return FIXED_TIME;
  const bucketTime = Math.floor((time - 1) / FIVE_MINUTES_MS) * FIVE_MINUTES_MS;
  return new Date(bucketTime).toISOString().replace(".000Z", "Z");
}

function toInternal(input: GateLike): GateNotificationInternal {
  return {
    ...input,
    safeTitleAlias: "input_needed",
    safeBodyAlias: "open_app_to_continue",
  };
}

export function renderAliasOnlyPush(input: GateLike): PublicPushPayload {
  const internal = toInternal(input);
  const title = internal.safeTitleAlias;
  const body = internal.safeBodyAlias;
  if (!TITLE_ALIASES.has(title) || !BODY_ALIASES.has(body)) throw new Error("Unsafe push alias");

  const frameId = internal.frameId ?? "frame_unknown";
  const gateHash = hashId(`${internal.sessionId}:${frameId}:${internal.gateKind}`, "hash_gate");
  const collapseIdHash = internal.collapseIdHash ?? `col_h_${gateHash.slice(-12)}`;
  const threadIdHash = internal.threadIdHash ?? hashId(internal.sessionAlias ?? internal.sessionId, "thr_h").slice(0, 18);
  const deepLinkOpaqueId = internal.deepLinkOpaqueId ?? `dl_opaque_${gateHash.slice(-26).padEnd(26, "0")}`;
  const createdAt = internal.createdAt ?? FIXED_TIME;

  const payload = {
    schemaVersion: 1,
    notificationId: internal.notificationId ?? `ntf_opaque_${gateHash.slice(-26).padEnd(26, "0")}`,
    safeTitleAlias: title,
    safeBodyAlias: body,
    category: `gate.${title}`,
    collapseIdHash,
    threadIdHash,
    deepLinkOpaqueId,
    createdAtBucket: createdAtBucket(createdAt),
  } as PublicPushPayload;
  Object.defineProperty(payload, "ttlSeconds", { value: 900, enumerable: false });
  Object.defineProperty(payload, "priority", { value: "high", enumerable: false });
  return payload;
}

export function payloadFromGate(gate: GateNotification): ProviderPushPayload {
  const publicPayload = renderAliasOnlyPush(gate);
  return {
    publicPayload,
    collapseIdHash: publicPayload.collapseIdHash,
    collapseId: publicPayload.collapseIdHash,
    ttlSeconds: 900,
    priority: "high",
  };
}

function stringifyData(payload: PublicPushPayload): Record<Exclude<keyof PublicPushPayload, "ttlSeconds" | "priority">, string> {
  return {
    schemaVersion: String(payload.schemaVersion),
    notificationId: payload.notificationId,
    safeTitleAlias: payload.safeTitleAlias,
    safeBodyAlias: payload.safeBodyAlias,
    category: payload.category,
    collapseIdHash: payload.collapseIdHash,
    threadIdHash: payload.threadIdHash,
    deepLinkOpaqueId: payload.deepLinkOpaqueId,
    createdAtBucket: payload.createdAtBucket,
  };
}

export function buildApnsRequest(payload: ProviderPushPayload, topic = "io.devnogari.gajaedeck", now = FIXED_TIME): ApnsPushRequest {
  const expiration = Math.floor(Date.parse(now) / 1000) + payload.ttlSeconds;
  return {
    provider: "apns",
    headers: {
      "apns-push-type": "alert",
      "apns-priority": "10",
      "apns-topic": topic,
      "apns-collapse-id": payload.publicPayload.collapseIdHash,
      "apns-expiration": String(expiration),
    },
    payload: {
      aps: {
        alert: {
          "title-loc-key": payload.publicPayload.safeTitleAlias,
          "loc-key": payload.publicPayload.safeBodyAlias,
        },
        category: payload.publicPayload.category,
        "thread-id": payload.publicPayload.threadIdHash,
      },
      ...payload.publicPayload,
    },
  };
}

export function buildFcmRequest(tokenAlias: string, payload: ProviderPushPayload, now = FIXED_TIME): FcmPushRequest {
  const expiration = Math.floor(Date.parse(now) / 1000) + payload.ttlSeconds;
  return {
    provider: "fcm",
    message: {
      tokenAlias,
      notification: {
        title_loc_key: payload.publicPayload.safeTitleAlias,
        body_loc_key: payload.publicPayload.safeBodyAlias,
      },
      data: stringifyData(payload.publicPayload),
      android: {
        collapse_key: payload.publicPayload.collapseIdHash,
        ttl: "900s",
        priority: "HIGH",
      },
      apns: {
        headers: {
          "apns-collapse-id": payload.publicPayload.collapseIdHash,
          "apns-expiration": String(expiration),
        },
      },
    },
  };
}

export function buildDeepLinkRequest(payload: ProviderPushPayload): DeepLinkPushRequest {
  return {
    deepLink: `gajaedeck://notification/open?id=${payload.publicPayload.deepLinkOpaqueId}`,
    allowedQueryKeys: ["id"],
    deepLinkOpaqueId: payload.publicPayload.deepLinkOpaqueId,
    forbiddenQueryKeys: [
      "token",
      "routeToken",
      "controlToken",
      "scopedToken",
      "bridgeToken",
      "ownerToken",
      "deviceToken",
      "sessionId",
      "projectId",
      "routeId",
      "cwd",
      "path",
      "prompt",
      "commandArgs",
      "displayName",
    ],
  };
}

export function assertAliasOnlyPayload(payload: ProviderPushPayload | PublicPushPayload, forbiddenValues: string[]): void {
  const artifact = JSON.stringify(payload);
  for (const value of forbiddenValues) {
    if (value && artifact.includes(value)) throw new Error(`Forbidden plaintext leaked: ${value}`);
  }
}
