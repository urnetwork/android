// Pure mapping shared by command publication and receipt-bound artifact copy.
// A caller's logical label is not a globally unique app-private filename.
import { createHash } from "node:crypto";

export const DIAGNOSTIC_VERBS = Object.freeze({
  "owner-census": ["owners", "json"],
  "heap-profile": ["heap", "pprof"],
  "goroutine-stacks": ["stacks", "txt"],
});
export const diagnosticLabelValid = value => typeof value === "string" && /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(value);
export const diagnosticSessionValid = value => typeof value === "string" &&
  /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(value);

export function diagnosticIdentity(sessionId, verb, label, commandId) {
  if (!diagnosticSessionValid(sessionId) || !Object.hasOwn(DIAGNOSTIC_VERBS, verb) ||
      !diagnosticLabelValid(label) || !diagnosticLabelValid(commandId)) throw new Error("diagnostic-identity-invalid");
  const hash = (kind, value) => createHash("sha256").update(`physical-diagnostic-v2\0${kind}\0${sessionId}\0${value}`).digest("hex");
  // Full-length hashes fit the app's 64-character ASCII command/label limit.
  // Label derivation deliberately excludes commandId: changing a command ID
  // cannot bypass an already-attempted logical label in the same session.
  const wireLabel = hash("label", label);
  const [kind, extension] = DIAGNOSTIC_VERBS[verb];
  return { commandId: hash("command", commandId), label: wireLabel,
    artifactName: `physical-${kind}-${wireLabel}.${extension}` };
}
