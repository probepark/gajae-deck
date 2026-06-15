import { hashId, FIXED_TIME } from "./observability";

export const USER_INPUT_GATE_KINDS = [
  "permission_request",
  "workflow_gate",
  "ui_request",
  "elicitation",
  "host_uri_request",
] as const;

export type GateKind = typeof USER_INPUT_GATE_KINDS[number];

export interface GateEvent {
  gateHash: string;
  sessionHash: string;
  kind: string;
  status: "detected" | "collapsed" | "resolved";
}

export interface GateNotification {
  schemaVersion: 1;
  notificationId: string;
  projectId: string;
  sessionId: string;
  sessionAlias: string;
  gateKind: GateKind;
  safeTitleAlias: "input_needed";
  safeBodyAlias: "open_app_to_continue";
  collapseIdHash: string;
  deepLinkOpaqueId: string;
  frameId: string;
  createdAt: string;
}

export function recordGate(event: GateEvent): GateEvent {
  return event;
}

function stringField(value: unknown): string | undefined {
  return typeof value === "string" && value.length > 0 ? value : undefined;
}

function objectField(value: unknown): Record<string, unknown> | undefined {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : undefined;
}

function parseFrame(frame: unknown): Record<string, unknown> | undefined {
  if (typeof frame === "string") {
    const trimmed = frame.trim();
    if (trimmed.startsWith("data:")) return parseFrame(trimmed.replace(/^data:\s*/u, ""));
    try { return objectField(JSON.parse(trimmed)); } catch { return undefined; }
  }
  return objectField(frame);
}

export function detectGateNotification(frame: unknown, defaults: { projectId: string; sessionId: string; sessionAlias?: string; now?: string }): GateNotification | undefined {
  const root = parseFrame(frame);
  if (!root) return undefined;
  const payload = objectField(root.payload) ?? objectField(root.data) ?? root;
  const type = stringField(payload.type) ?? stringField(payload.kind) ?? stringField(root.type) ?? stringField(root.event);
  if (!type || !USER_INPUT_GATE_KINDS.includes(type as GateKind)) return undefined;

  const sessionId = stringField(payload.sessionId) ?? stringField(root.sessionId) ?? defaults.sessionId;
  const projectId = stringField(payload.projectId) ?? stringField(root.projectId) ?? defaults.projectId;
  const frameId = stringField(payload.frameId) ?? stringField(root.frameId) ?? `frame_${hashId(JSON.stringify(root), "hash_frame").slice(-8)}`;
  const createdAt = stringField(payload.createdAt) ?? stringField(root.createdAt) ?? defaults.now ?? FIXED_TIME;
  const sessionHash = hashId(sessionId, "hash_session");
  const gateHash = hashId(`${sessionId}:${frameId}:${type}`, "hash_gate");

  return {
    schemaVersion: 1,
    notificationId: `notif_${gateHash.slice(-26).padEnd(26, "0")}`,
    projectId,
    sessionId,
    sessionAlias: defaults.sessionAlias ?? `session_alias_${sessionHash.slice(-8)}`,
    gateKind: type as GateKind,
    safeTitleAlias: "input_needed",
    safeBodyAlias: "open_app_to_continue",
    collapseIdHash: `hash_collapse_${gateHash.slice(-12)}`,
    deepLinkOpaqueId: `dl_opaque_${gateHash.slice(-26).padEnd(26, "0")}`,
    frameId,
    createdAt,
  };
}
