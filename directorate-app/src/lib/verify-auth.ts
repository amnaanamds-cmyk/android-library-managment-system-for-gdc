// lib/verify-auth.ts
//
// Server-side verification of a Firebase ID token WITHOUT the Admin SDK —
// ID token signature verification only needs Google's public signing keys,
// never a service account, so this needs no new secret provisioned for this
// deployment.
//
// Known gap, documented rather than silently assumed away: this confirms
// the token is a genuine, unexpired Firebase Auth token for SOME signed-in
// user of this project — it does NOT confirm that user is specifically a
// directorate_admin. That role lives on a Firestore users/{uid} document
// (see lib/auth-context.tsx), not in the token's own claims, and reading
// Firestore server-side would need a real Admin SDK service account, which
// this deployment does not have configured. Every caller of this function
// is blocking anonymous/forged requests, not re-enforcing the role check
// the client already does — call sites must say so, not imply more than
// this actually proves.

import { jwtVerify, createRemoteJWKSet } from "jose";

const PROJECT_ID = "nexlib-e7970";
const ISSUER = `https://securetoken.google.com/${PROJECT_ID}`;
const JWKS = createRemoteJWKSet(
  new URL(
    "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com",
  ),
);

export interface VerifiedToken {
  uid: string;
  email?: string;
}

export async function verifyFirebaseIdToken(
  authorizationHeader: string | null,
): Promise<VerifiedToken | null> {
  if (!authorizationHeader?.startsWith("Bearer ")) return null;
  const token = authorizationHeader.slice("Bearer ".length).trim();
  if (!token) return null;

  try {
    const { payload } = await jwtVerify(token, JWKS, {
      issuer: ISSUER,
      audience: PROJECT_ID,
    });
    if (typeof payload.sub !== "string") return null;
    return {
      uid: payload.sub,
      email: typeof payload.email === "string" ? payload.email : undefined,
    };
  } catch {
    return null;
  }
}
