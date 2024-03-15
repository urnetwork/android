import { Connection } from "@solana/web3.js";
import type { Publisher } from "../types.js";
import { createAttestationPayload } from "./PublishCoreAttestation.js";
import {
  CONTACT_OBJECT_ID,
  CONTACT_PROPERTY_COMPANY,
  CONTACT_PROPERTY_EMAIL,
  CONTACT_PROPERTY_WEBSITE,
  submitRequestToSolanaDappPublisherPortal,
  TICKET_OBJECT_ID,
  TICKET_PROPERTY_ATTESTATION_PAYLOAD,
  TICKET_PROPERTY_AUTHORIZED_REQUEST,
  TICKET_PROPERTY_CRITICAL_UPDATE,
  TICKET_PROPERTY_DAPP_COLLECTION_ACCOUNT_ADDRESS,
  TICKET_PROPERTY_DAPP_RELEASE_ACCOUNT_ADDRESS,
  TICKET_PROPERTY_REQUEST_UNIQUE_ID,
  URL_FORM_REMOVE
} from "./dapp_publisher_portal.js";
import { PublishSolanaNetworkInput, SignWithPublisherKeypair } from "./types.js";

const createRemoveRequest = async (
  connection: Connection,
  sign: SignWithPublisherKeypair,
  appMintAddress: string,
  releaseMintAddress: string,
  publisherDetails: Publisher,
  requestorIsAuthorized: boolean,
  criticalUpdate: boolean
) => {
  const { attestationPayload, requestUniqueId } = await createAttestationPayload(connection, sign);

  const request = {
    fields: [
      {
        objectTypeId: CONTACT_OBJECT_ID,
        name: CONTACT_PROPERTY_COMPANY,
        value: publisherDetails.name
      },
      {
        objectTypeId: CONTACT_OBJECT_ID,
        name: CONTACT_PROPERTY_EMAIL,
        value: publisherDetails.email
      },
      {
        objectTypeId: CONTACT_OBJECT_ID,
        name: CONTACT_PROPERTY_WEBSITE,
        value: publisherDetails.website
      },
      {
        objectTypeId: TICKET_OBJECT_ID,
        name: TICKET_PROPERTY_ATTESTATION_PAYLOAD,
        value: attestationPayload
      },
      {
        objectTypeId: TICKET_OBJECT_ID,
        name: TICKET_PROPERTY_DAPP_COLLECTION_ACCOUNT_ADDRESS,
        value: appMintAddress
      },
      {
        objectTypeId: TICKET_OBJECT_ID,
        name: TICKET_PROPERTY_DAPP_RELEASE_ACCOUNT_ADDRESS,
        value: releaseMintAddress
      },
      {
        objectTypeId: TICKET_OBJECT_ID,
        name: TICKET_PROPERTY_REQUEST_UNIQUE_ID,
        value: requestUniqueId
      },
      {
        objectTypeId: TICKET_OBJECT_ID,
        name: TICKET_PROPERTY_AUTHORIZED_REQUEST,
        value: requestorIsAuthorized
      },
    ]
  };

  if (criticalUpdate) {
    request.fields.push(
      {
        objectTypeId: TICKET_OBJECT_ID,
        name: TICKET_PROPERTY_CRITICAL_UPDATE,
        value: criticalUpdate
      }
    );
  }

  return request;
};

export type PublishRemoveInput = {
  appMintAddress: string;
  releaseMintAddress: string;
  publisherDetails: Publisher;
  requestorIsAuthorized: boolean;
  criticalUpdate: boolean;
};

export const publishRemove = async (
  publishSolanaNetworkInput: PublishSolanaNetworkInput,
  {
    appMintAddress,
    releaseMintAddress,
    publisherDetails,
    requestorIsAuthorized,
    criticalUpdate,
  } : PublishRemoveInput,
  dryRun: boolean,
) => {
  const removeRequest = await createRemoveRequest(
    publishSolanaNetworkInput.connection,
    publishSolanaNetworkInput.sign,
    appMintAddress,
    releaseMintAddress,
    publisherDetails,
    requestorIsAuthorized,
    criticalUpdate);

  return submitRequestToSolanaDappPublisherPortal(removeRequest, URL_FORM_REMOVE, dryRun);
};
