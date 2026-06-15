import { hashId, FIXED_TIME, tokenHash } from "./observability";

export type PushPlatform = "ios" | "android";
export type PushEnvironment = "sandbox" | "production";
export type PushProviderName = "apns" | "fcm";

export interface DeviceRegistrationRequest {
  installId: string;
  platform: PushPlatform;
  environment: PushEnvironment;
  pushToken: string;
  appVersion: string;
  locale?: string;
  capabilities: string[];
}

export interface ProtectedPushToken {
  tokenAlias: string;
  tokenHash: string;
}

export interface RegisteredDevice {
  deviceId: string;
  installIdHash: string;
  tokenHash: string;
  tokenAlias: string;
  provider: PushProviderName;
  platform: PushPlatform;
  environment: PushEnvironment;
  appVersion: string;
  locale?: string;
  capabilities: string[];
  registeredAt: string;
  status: "registered" | "pruned";
}

export interface DeviceRegistration {
  deviceHash: string;
  status: "registered" | "pruned";
}

function assertPlatform(value: unknown): asserts value is PushPlatform {
  if (value !== "ios" && value !== "android") throw new Error("Invalid device platform");
}

function assertEnvironment(value: unknown): asserts value is PushEnvironment {
  if (value !== "sandbox" && value !== "production") throw new Error("Invalid device environment");
}

export class DeviceRegistry {
  private devices = new Map<string, RegisteredDevice>();

  register(input: DeviceRegistrationRequest, now = FIXED_TIME): { deviceId: string; registeredAt: string } {
    assertPlatform(input.platform);
    assertEnvironment(input.environment);
    if (!input.installId || !input.pushToken || !input.appVersion || !Array.isArray(input.capabilities)) {
      throw new Error("Invalid device registration");
    }

    const provider = input.platform === "ios" ? "apns" : "fcm";
    const installIdHash = hashId(input.installId, "hash_install");
    const pushTokenHash = tokenHash(input.pushToken);
    const deviceId = `dev_${installIdHash.slice(-20)}`;
    const device: RegisteredDevice = {
      deviceId,
      installIdHash,
      tokenHash: pushTokenHash,
      tokenAlias: hashId(pushTokenHash, "device_h").slice(0, 23),
      provider,
      platform: input.platform,
      environment: input.environment,
      appVersion: input.appVersion,
      capabilities: [...input.capabilities].sort(),
      registeredAt: now,
      status: "registered",
    };
    if (input.locale) device.locale = input.locale;
    this.devices.set(deviceId, device);
    return { deviceId, registeredAt: now };
  }

  registered(): RegisteredDevice[] {
    return [...this.devices.values()].filter(device => device.status === "registered");
  }

  protectedToken(device: RegisteredDevice): ProtectedPushToken {
    return { tokenAlias: device.tokenAlias, tokenHash: device.tokenHash };
  }

  pruneToken(deviceToken: string): boolean {
    return this.pruneTokenHash(tokenHash(deviceToken));
  }

  pruneTokenHash(invalidTokenHash: string): boolean {
    let pruned = false;
    for (const device of this.devices.values()) {
      if (device.tokenHash === invalidTokenHash && device.status === "registered") {
        device.status = "pruned";
        pruned = true;
      }
    }
    return pruned;
  }

  get(deviceId: string): RegisteredDevice | undefined {
    return this.devices.get(deviceId);
  }
}
