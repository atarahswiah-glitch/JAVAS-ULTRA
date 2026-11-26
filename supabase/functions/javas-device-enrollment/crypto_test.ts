import {
  canonicalChallengeBytes,
  derEcdsaToP1363,
  verifyDeviceProof,
  issueEnrollmentChallenge,
  sha256Base64,
} from "./crypto.ts";

function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  const copy = new Uint8Array(bytes.byteLength);
  copy.set(bytes);
  return copy.buffer;
}

function toBase64(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function rawToDer(raw: Uint8Array): Uint8Array {
  const integer = (bytes: Uint8Array) => {
    let start = 0;
    while (start < bytes.length - 1 && bytes[start] === 0) start += 1;
    let value = bytes.subarray(start);
    if (value[0] & 0x80) value = Uint8Array.from([0, ...value]);
    return Uint8Array.from([0x02, value.length, ...value]);
  };
  const r = integer(raw.subarray(0, 32));
  const s = integer(raw.subarray(32));
  const body = Uint8Array.from([...r, ...s]);
  return Uint8Array.from([0x30, body.length, ...body]);
}

Deno.test("canonical challenge bytes are stable and bind every field", () => {
  const challenge = {
    version: 1 as const,
    challengeId: "challenge-1",
    creatorId: "creator-1",
    deviceId: "device-1",
    purpose: "ENROLL_DEVICE" as const,
    expiresAt: "2026-11-26T16:00:00.000Z",
    nonceBase64: toBase64(new Uint8Array(32).fill(7)),
  };
  const bytes = canonicalChallengeBytes(challenge);
  if (new TextDecoder().decode(bytes).split("\n").length !== 8) {
    throw new Error("Unexpected canonical challenge format");
  }
  const changed = canonicalChallengeBytes({ ...challenge, deviceId: "device-2" });
  if (new TextDecoder().decode(bytes) === new TextDecoder().decode(changed)) {
    throw new Error("Device binding was not included");
  }
});

Deno.test("canonical challenge rejects delimiter injection and oversized fields", () => {
  const base = {
    version: 1 as const,
    challengeId: "challenge-1",
    creatorId: "creator-1",
    deviceId: "device-1",
    purpose: "ENROLL_DEVICE" as const,
    expiresAt: "2026-11-26T16:00:00.000Z",
    nonceBase64: toBase64(new Uint8Array(32).fill(7)),
  };
  for (const invalid of [
    { ...base, creatorId: "creator-1\\nattacker" },
    { ...base, deviceId: "device-1\\rattacker" },
    { ...base, challengeId: "x".repeat(65) },
    { ...base, nonceBase64: "x".repeat(129) },
  ]) {
    let rejected = false;
    try {
      canonicalChallengeBytes(invalid);
    } catch {
      rejected = true;
    }
    if (!rejected) throw new Error("Malformed challenge field was accepted");
  }
});

Deno.test("DER signature conversion returns fixed-width P-256 signature", () => {
  const raw = new Uint8Array(64);
  raw[31] = 1;
  raw[63] = 2;
  const converted = derEcdsaToP1363(rawToDer(raw));
  if (converted.length !== 64 || converted.some((value, index) => value !== raw[index])) {
    throw new Error("DER conversion did not preserve r and s");
  }
});

Deno.test("rejects wrong Creator, wrong device and expired challenge", async () => {
  const challenge = {
    version: 1 as const,
    challengeId: "challenge-1",
    creatorId: "creator-1",
    deviceId: "device-1",
    purpose: "ENROLL_DEVICE" as const,
    expiresAt: "2026-11-26T16:00:00.000Z",
    nonceBase64: toBase64(new Uint8Array(32).fill(7)),
  };
  const pair = await crypto.subtle.generateKey(
    { name: "ECDSA", namedCurve: "P-256" },
    true,
    ["sign", "verify"],
  );
  const spki = new Uint8Array(await crypto.subtle.exportKey("spki", pair.publicKey));
  const rawSignature = new Uint8Array(await crypto.subtle.sign(
    { name: "ECDSA", hash: "SHA-256" },
    pair.privateKey,
    toArrayBuffer(canonicalChallengeBytes(challenge)),
  ));
  const proof = {
    challenge,
    publicKeySpkiBase64: toBase64(spki),
    signatureDerBase64: toBase64(rawToDer(rawSignature)),
  };
  const now = new Date("2026-11-26T15:59:00.000Z");
  if (!await verifyDeviceProof(proof, { creatorId: "creator-1", deviceId: "device-1", now })) {
    throw new Error("Valid proof was rejected");
  }
  if (await verifyDeviceProof(proof, { creatorId: "other", deviceId: "device-1", now })) {
    throw new Error("Wrong Creator was accepted");
  }
  if (await verifyDeviceProof(proof, { creatorId: "creator-1", deviceId: "other", now })) {
    throw new Error("Wrong device was accepted");
  }
  if (await verifyDeviceProof(proof, {
    creatorId: "creator-1",
    deviceId: "device-1",
    now: new Date("2026-11-26T16:00:00.000Z"),
  })) {
    throw new Error("Expired challenge was accepted");
  }
});

Deno.test("rejects malformed DER signatures", () => {
  let rejected = false;
  try {
    derEcdsaToP1363(new Uint8Array([0x30, 0x02, 0x02, 0x00]));
  } catch {
    rejected = true;
  }
  if (!rejected) throw new Error("Malformed signature was accepted");
});

Deno.test("issues a fresh random challenge and stores only its digest", async () => {
  const now = new Date("2026-11-26T15:00:00.000Z");
  const first = await issueEnrollmentChallenge({ creatorId: "creator", deviceId: "device", now });
  const second = await issueEnrollmentChallenge({ creatorId: "creator", deviceId: "device", now });
  if (first.challenge.challengeId === second.challenge.challengeId) {
    throw new Error("Challenge identifiers must be unique");
  }
  if (first.challenge.nonceBase64 === second.challenge.nonceBase64) {
    throw new Error("Challenge nonces must be random");
  }
  if (first.nonceDigestBase64 === first.challenge.nonceBase64) {
    throw new Error("Stored nonce digest must not equal the raw nonce");
  }
  const rawNonce = Uint8Array.from(atob(first.challenge.nonceBase64), (c) => c.charCodeAt(0));
  if (await sha256Base64(rawNonce) !== first.nonceDigestBase64) {
    throw new Error("Stored nonce digest does not match the issued nonce");
  }
  if (Date.parse(first.challenge.expiresAt) - now.getTime() !== 120_000) {
    throw new Error("Default challenge lifetime must be two minutes");
  }
});

Deno.test("rejects challenge lifetimes outside the allowed range", async () => {
  for (const ttlMs of [0, 14_999, 120_001, 1.5]) {
    let rejected = false;
    try {
      await issueEnrollmentChallenge({ creatorId: "creator", deviceId: "device", ttlMs });
    } catch {
      rejected = true;
    }
    if (!rejected) throw new Error(`Invalid TTL accepted: ${ttlMs}`);
  }
});
