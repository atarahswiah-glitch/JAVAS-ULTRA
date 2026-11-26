const MIN_NONCE_BYTES = 32;
const MAX_NONCE_BYTES = 64;
const P256_COMPONENT_BYTES = 32;

function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  const copy = new Uint8Array(bytes.byteLength);
  copy.set(bytes);
  return copy.buffer;
}

export type EnrollmentChallenge = {
  version: 1;
  challengeId: string;
  creatorId: string;
  deviceId: string;
  purpose: "ENROLL_DEVICE";
  expiresAt: string;
  nonceBase64: string;
};

export type DeviceProof = {
  challenge: EnrollmentChallenge;
  publicKeySpkiBase64: string;
  signatureDerBase64: string;
};

function decodeBase64(value: string): Uint8Array {
  if (!/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(value)) {
    throw new Error("Invalid base64 encoding");
  }
  const binary = atob(value);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

function encodeBase64(value: Uint8Array): string {
  let binary = "";
  for (const byte of value) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function derIntegerToFixedBytes(value: Uint8Array): Uint8Array {
  if (value.length === 0 || value[0] >= 0x80) {
    throw new Error("Invalid ECDSA integer");
  }
  let start = 0;
  while (start < value.length - 1 && value[start] === 0) start += 1;
  const significant = value.subarray(start);
  if (significant.length > P256_COMPONENT_BYTES) {
    throw new Error("ECDSA integer exceeds P-256 width");
  }
  const output = new Uint8Array(P256_COMPONENT_BYTES);
  output.set(significant, P256_COMPONENT_BYTES - significant.length);
  return output;
}

/** Convert Android's ASN.1 DER ECDSA signature to WebCrypto's IEEE-P1363 form. */
export function derEcdsaToP1363(der: Uint8Array): Uint8Array {
  if (der.length < 8 || der[0] !== 0x30 || der[1] !== der.length - 2) {
    throw new Error("Invalid ECDSA signature sequence");
  }
  let offset = 2;
  if (der[offset++] !== 0x02) throw new Error("Missing ECDSA r integer");
  const rLength = der[offset++];
  if (!rLength || offset + rLength >= der.length) throw new Error("Invalid ECDSA r length");
  const r = derIntegerToFixedBytes(der.subarray(offset, offset + rLength));
  offset += rLength;
  if (der[offset++] !== 0x02) throw new Error("Missing ECDSA s integer");
  const sLength = der[offset++];
  if (!sLength || offset + sLength !== der.length) throw new Error("Invalid ECDSA s length");
  const s = derIntegerToFixedBytes(der.subarray(offset, offset + sLength));
  const output = new Uint8Array(P256_COMPONENT_BYTES * 2);
  output.set(r, 0);
  output.set(s, P256_COMPONENT_BYTES);
  return output;
}

export function canonicalChallengeBytes(challenge: EnrollmentChallenge): Uint8Array {
  const fields = [
    "JAVAS-DEVICE-ENROLLMENT",
    String(challenge.version),
    challenge.challengeId,
    challenge.creatorId,
    challenge.deviceId,
    challenge.purpose,
    challenge.expiresAt,
    challenge.nonceBase64,
  ];
  const limits = [64, 128, 128, 128, 32, 40, 128];
  if (fields.some((field, index) => field.length === 0 || field.length > (limits[index] ?? 128) || /[\r\n]/.test(field))) {
    throw new Error("Invalid challenge field");
  }
  return new TextEncoder().encode(fields.join("\n"));
}

export async function verifyDeviceProof(
  proof: DeviceProof,
  expected: { creatorId: string; deviceId: string; now?: Date },
): Promise<boolean> {
  const { challenge } = proof;
  if (
    challenge.version !== 1 ||
    challenge.purpose !== "ENROLL_DEVICE" ||
    challenge.creatorId !== expected.creatorId ||
    challenge.deviceId !== expected.deviceId
  ) return false;

  const expiresAt = Date.parse(challenge.expiresAt);
  const now = (expected.now ?? new Date()).getTime();
  if (!Number.isFinite(expiresAt) || expiresAt <= now || expiresAt - now > 5 * 60_000) {
    return false;
  }

  try {
    const nonce = decodeBase64(challenge.nonceBase64);
    if (nonce.length < MIN_NONCE_BYTES || nonce.length > MAX_NONCE_BYTES) return false;

    const spki = decodeBase64(proof.publicKeySpkiBase64);
    const derSignature = decodeBase64(proof.signatureDerBase64);
    const signature = derEcdsaToP1363(derSignature);
    const key = await crypto.subtle.importKey(
      "spki",
      toArrayBuffer(spki),
      { name: "ECDSA", namedCurve: "P-256" },
      false,
      ["verify"],
    );
    return await crypto.subtle.verify(
      { name: "ECDSA", hash: "SHA-256" },
      key,
      toArrayBuffer(signature),
      toArrayBuffer(canonicalChallengeBytes(challenge)),
    );
  } catch {
    return false;
  }
}

export async function sha256Base64(value: Uint8Array): Promise<string> {
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", toArrayBuffer(value)));
  return encodeBase64(digest);
}

export async function issueEnrollmentChallenge(
  input: { creatorId: string; deviceId: string; now?: Date; ttlMs?: number },
): Promise<{ challenge: EnrollmentChallenge; nonceDigestBase64: string }> {
  if (!input.creatorId.trim() || !input.deviceId.trim()) {
    throw new Error("Creator and device identifiers are required");
  }
  const ttlMs = input.ttlMs ?? 120_000;
  if (!Number.isInteger(ttlMs) || ttlMs < 15_000 || ttlMs > 120_000) {
    throw new Error("Challenge lifetime must be between 15 and 120 seconds");
  }
  const nonce = crypto.getRandomValues(new Uint8Array(32));
  const now = input.now ?? new Date();
  const challenge: EnrollmentChallenge = {
    version: 1,
    challengeId: crypto.randomUUID(),
    creatorId: input.creatorId,
    deviceId: input.deviceId,
    purpose: "ENROLL_DEVICE",
    expiresAt: new Date(now.getTime() + ttlMs).toISOString(),
    nonceBase64: encodeBase64(nonce),
  };
  return {
    challenge,
    nonceDigestBase64: await sha256Base64(nonce),
  };
}
