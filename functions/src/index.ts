/**
 * NEXLIB backend entry point.
 *
 * Three responsibilities, and nothing else lives here:
 *
 *   claims.ts        auth custom claims — the fast path firestore.rules reads
 *   registration.ts  institution onboarding and the directorate approval gate
 *   summary.ts       the denormalized rollup the director dashboard reads
 *
 * All functions are deployed to a single region (see config.ts). Firestore
 * should be in that same region.
 */

import { initializeApp } from "firebase-admin/app";
import { setGlobalOptions } from "firebase-functions/v2";

import { REGION } from "./config";

initializeApp();

// Bounded so a runaway trigger cannot scale to the project's whole quota and
// starve the callables a librarian is waiting on.
setGlobalOptions({ region: REGION, maxInstances: 20 });

export { syncUserClaims, refreshMyClaims, setUserRole } from "./claims";
export { registerInstitution, setInstitutionStatus } from "./registration";
export {
  buildDirectorateSummary,
  rebuildDirectorateSummary,
  refreshInstitutionSummary,
} from "./summary";
