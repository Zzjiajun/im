export type LandingGateResult =
  | { ok: true }
  | { ok: false; code?: string | number };

export async function checkLandingAccess(): Promise<LandingGateResult> {
  if (import.meta.env.VITE_LANDING_GATE_DISABLED === "true") {
    return { ok: true };
  }

  // 默认允许访问；如需落地页校验，可在此处接入后端接口或业务逻辑。
  return { ok: true };
}
