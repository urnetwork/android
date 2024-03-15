export * from "./CreateCliPublisher.js";
export * from "./CreateCliApp.js";
export * from "./CreateCliRelease.js";

/*
 * Module responsible for creating publishers, apps, and releases (in that order)
 * Anything that is out-of-order will be prompted back into order
 * And steps that happen more than once will do their best to remember as much information as possible.
 * We will ask questions and do our best to answer anything that's already been configured, and prompt for anything that's not
 */

// We'll never ask for private keys or seed phrases
// You can use a burner signer to publish; all NFTs require verification from the publisher signer
// You can use a multisig or Ledger for that purpose

// Publisher
// Public key attached to a publisher must also verify applications and releases
// Most information here can be be edited after the fact
// Only required fields are name, address, publisher website, and contact
// Optional fields are: description, image_url (need dimensions!)

// App
// Publisher creator key required
// Most information can be edited after the fact
// Required: name, description, `android_package`
// Optional: Any additional creator keys
// TODO(jon): Probably okay to capture more information here like:
// - `license_url`
// - `copyright_url`
// - `privacy_policy_url`
// Release
// Publisher creator key required
// Immutable; information cannot be edited after publishing.
// Change based on the review process must result in a new release
// Required:
// - version (automatically prompt with semver + 1)
// - release notes (description)
// - publisher creator key
// - path to the APK
// Optional:
// - Media related to the release
// - New permissions (prompted)
// - New languages (prompted)
// Handles uploads of all files, sha'ing them
// Handles i18n (later)

// We'll attempt to read as much as possible from a provided `.yml` file
// If there are provided folders that are well-structured, we'll opt to use that too.
