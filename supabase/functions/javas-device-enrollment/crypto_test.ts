import {
  canonicalChallengeBytes,
  derEcdsaToP1363,
  verifyDeviceProof,
} from "./crypto.ts";

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
    canonicalChallengeBytes(challenge),
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
