import { timingSafeEqual, randomBytes } from "node:crypto";
import { DEFAULT_SCOPES, type RouteScope } from "./config";
import { hashId, tokenHash } from "./observability";

export interface RouteClaim { routeId: string; sessionId: string; projectId: string; scopes: RouteScope[]; issuedAt: string; tokenHash: string; revoked?: boolean }
export const SCOPE_MATRIX: Record<RouteScope, readonly string[]> = { events: ["GET"], prompt: ["POST"], permission: ["POST"], ui_responses: ["GET", "POST"], claim_control: ["POST"] };
const tokenClaims = new Map<string, RouteClaim>();

export function extractBearer(header: string | null | undefined): string | undefined { const m = /^Bearer\s+(.+)$/i.exec(header ?? ""); return m?.[1]; }
export function timingSafeTokenEqual(a: string, b: string): boolean {
  const ab = Buffer.from(a); const bb = Buffer.from(b);
  if (ab.length !== bb.length) { try { timingSafeEqual(Buffer.alloc(Math.max(ab.length, bb.length)), Buffer.concat([bb, Buffer.alloc(Math.max(0, ab.length - bb.length))]).subarray(0, Math.max(ab.length, bb.length))); } catch {} return false; }
  return timingSafeEqual(ab, bb);
}
export function mintScopedToken(claim: Omit<RouteClaim, "tokenHash">, alias?: string): string {
  const token = alias ?? `scoped_${randomBytes(18).toString("base64url")}`;
  tokenClaims.set(token, { ...claim, tokenHash: tokenHash(token) });
  return token;
}
export function revokeScopedToken(token: string): void { const c = tokenClaims.get(token); if (c) c.revoked = true; }
export function parseScopedToken(token: string): RouteClaim | undefined { return tokenClaims.get(token); }
export function validateRouteClaim(token: string | undefined, routeId: string, requiredScope: RouteScope): { ok: true; claim: RouteClaim } | { ok: false; code: "unauthorized" | "route_token_mismatch" | "scope_denied"; details: Record<string, unknown> } {
  if (!token) return { ok: false, code: "unauthorized", details: {} };
  const claim = parseScopedToken(token);
  if (!claim || claim.revoked) return { ok: false, code: "unauthorized", details: {} };
  if (!timingSafeTokenEqual(claim.routeId, routeId)) return { ok: false, code: "route_token_mismatch", details: { requestedRouteHash: hashId(routeId, "hash_route"), tokenRouteHash: hashId(claim.routeId, "hash_route"), bridgeContacted: false } };
  if (requiredScope && !claim.scopes.includes(requiredScope)) return { ok: false, code: "scope_denied", details: { routeIdHash: hashId(routeId, "hash_route") } };
  return { ok: true, claim };
}
export function defaultScopes(): RouteScope[] { return [...DEFAULT_SCOPES]; }
