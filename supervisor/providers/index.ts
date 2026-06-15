import type { ProviderPushPayload } from "../push";
import { buildApnsRequest, buildFcmRequest } from "../push";
import type { ProtectedPushToken } from "../devices";
import { tokenHash } from "../observability";

export type PushProviderName = "apns" | "fcm";
export type PushFailureCode = "transient" | "invalid_token" | "provider_unhealthy";

export interface PushSendResult {
  ok: boolean;
  provider: PushProviderName;
  retryable: boolean;
  code?: PushFailureCode;
  messageId?: string;
  invalidToken?: ProtectedPushToken;
  invalidTokenHash?: string;
  attempt: number;
}

export interface PushProvider {
  readonly name: PushProviderName;
  health(): { healthy: boolean; reason?: string };
  send(deviceToken: ProtectedPushToken, payload: ProviderPushPayload): Promise<PushSendResult>;
}

function protectedToken(input: ProtectedPushToken | string): ProtectedPushToken {
  if (typeof input !== "string") return input;
  const hash = tokenHash(input);
  return { tokenAlias: hash, tokenHash: hash };
}

export abstract class StructuredPushProvider implements PushProvider {
  abstract readonly name: PushProviderName;
  constructor(private readonly credentialRef?: string) {}

  health(): { healthy: boolean; reason?: string } {
    return this.credentialRef ? { healthy: true } : { healthy: false, reason: "missing_credentials" };
  }

  async send(deviceToken: ProtectedPushToken, payload: ProviderPushPayload): Promise<PushSendResult> {
    const health = this.health();
    if (!health.healthy) return { ok: false, provider: this.name, retryable: true, code: "provider_unhealthy", attempt: 1 };
    this.buildRequest(deviceToken, payload);
    return { ok: true, provider: this.name, retryable: false, messageId: `${this.name}_accepted`, attempt: 1 };
  }

  protected abstract buildRequest(deviceToken: ProtectedPushToken, payload: ProviderPushPayload): unknown;
}

export class ApnsProvider extends StructuredPushProvider {
  readonly name = "apns" as const;

  protected buildRequest(_deviceToken: ProtectedPushToken, payload: ProviderPushPayload) {
    return buildApnsRequest(payload);
  }
}

export class FcmProvider extends StructuredPushProvider {
  readonly name = "fcm" as const;

  protected buildRequest(deviceToken: ProtectedPushToken, payload: ProviderPushPayload) {
    return buildFcmRequest(deviceToken.tokenAlias, payload);
  }
}

export class FakePushProvider implements PushProvider {
  sends: { deviceToken: ProtectedPushToken; payload: ProviderPushPayload; attempt: number }[] = [];
  failures: Omit<PushSendResult, "provider" | "attempt">[] = [];
  healthy = true;

  constructor(readonly name: PushProviderName) {}

  enqueueFailure(code: PushFailureCode, retryable: boolean): void {
    this.failures.push({ ok: false, retryable, code });
  }

  health(): { healthy: boolean; reason?: string } {
    return this.healthy ? { healthy: true } : { healthy: false, reason: "fake_unhealthy" };
  }

  async send(deviceTokenInput: ProtectedPushToken, payload: ProviderPushPayload): Promise<PushSendResult> {
    const deviceToken = protectedToken(deviceTokenInput);
    const attempt = this.sends.length + 1;
    this.sends.push({ deviceToken, payload, attempt });
    if (!this.healthy) return { ok: false, provider: this.name, retryable: true, code: "provider_unhealthy", attempt };
    const failure = this.failures.shift();
    if (failure) {
      const result: PushSendResult = { ...failure, provider: this.name, attempt };
      if (failure.code === "invalid_token") {
        result.invalidToken = deviceToken;
        result.invalidTokenHash = deviceToken.tokenHash;
      }
      return result;
    }
    return { ok: true, provider: this.name, retryable: false, messageId: `fake_${attempt}`, attempt };
  }
}

export async function sendWithRetry(
  provider: PushProvider,
  deviceTokenInput: ProtectedPushToken | string,
  payload: ProviderPushPayload,
  options: { maxAttempts?: number; backoffMs?: number[] } = {},
): Promise<PushSendResult> {
  const deviceToken = protectedToken(deviceTokenInput);
  const maxAttempts = options.maxAttempts ?? 3;
  const backoffMs = options.backoffMs ?? [1, 2];
  let last: PushSendResult = { ok: false, provider: provider.name, retryable: true, code: "transient", attempt: 0 };
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    last = await provider.send(deviceToken, payload);
    if (last.ok || !last.retryable) return last;
    const delay = backoffMs[attempt - 1] ?? 0;
    if (delay > 0) await Bun.sleep(delay);
  }
  return last;
}
