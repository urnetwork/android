import { beforeEach, expect } from "@jest/globals";
import {
  createAppCliCmd,
  createCliCmd,
  createPublisherCliCmd,
  createReleaseCliCmd,
  initCliCmd,
  mainCli
} from "../CliSetup";
import { Constants } from "../CliUtils";

describe("Cli Setup & Execution", () => {
  const outputHelpReference = "(outputHelp)"

  let errorOutput: string = ""
  let otherOutput: string = ""

  beforeEach(() => {
    errorOutput = "";
    otherOutput = "";
    mainCli.exitOverride();

    mainCli.configureOutput({
      getOutHelpWidth(): number { return 250; },
      getErrHelpWidth(): number { return 250;},

      writeOut(str: string) {
        otherOutput = str;
      },

      writeErr(str: string) {
        errorOutput = str;
      }
    });
  });

  test("Cli version argument reports correct version", () => {

    expect(() => {
      mainCli.parse(["npx", "dapp-store", "-V"]);
    }).toThrow(Constants.CLI_VERSION)
  });

  test("Calling cli with no parameters displays general help", () => {
    expect(() => {
        mainCli.parse(["npx", "dapp-store"]);
      }
    ).toThrow(outputHelpReference);

    expect(generalHelp).toEqual(errorOutput)
  });

  test("Calling init command with help parameter shows contextual help info", () => {
    initCliCmd.exitOverride()

    expect(() => {
      initCliCmd.parse(["dapp-store", "init", "-h"])
    }).toThrow(outputHelpReference)

    expect(otherOutput).toEqual(initHelp)
  })

  test("Calling create command with no options lists all options", () => {
    createCliCmd.exitOverride()

    expect(() => {
        createCliCmd.parse(["dapp-store", "create"]);
      }
    ).toThrow(outputHelpReference);

    expect(errorOutput).toEqual(createHelp)
  });

  test("Calling create publisher command with no arguments warns about required argument", () => {
    createPublisherCliCmd.exitOverride()

    expect(() => {
        createPublisherCliCmd.parse(["dapp-store", "create", "publisher"]);
      }
    ).toThrow(keyPairArgHelp);
  });

  test("Calling create publisher command with help flag shows contextual help", () => {
    createPublisherCliCmd.exitOverride()

    expect(() => {
      createPublisherCliCmd.parse(["dapp-store", "create", "publisher", "-h"]);
    }).toThrow(outputHelpReference)

    expect(otherOutput).toEqual(createPublisherHelp)
  });

  test("Calling create app command with no arguments warns about required argument", () => {
    createAppCliCmd.exitOverride()

    expect(() => {
      createAppCliCmd.parse(["dapp-store", "create", "app"]);
      }
    ).toThrow(keyPairArgHelp);
  });

  test("Calling create app command with help flag shows contextual help", () => {
    createAppCliCmd.exitOverride()

    expect(() => {
      createAppCliCmd.parse(["dapp-store", "create", "app", "-h"]);
    }).toThrow(outputHelpReference)

    expect(otherOutput).toEqual(createAppHelp)
  });

  test("Calling create release command with no arguments warns about required argument", () => {
    createReleaseCliCmd.exitOverride()

    expect(() => {
        createReleaseCliCmd.parse(["dapp-store", "create", "release"]);
      }
    ).toThrow(keyPairArgHelp);
  });

  test("Calling create release command with help flag shows contextual help", () => {
    createReleaseCliCmd.exitOverride()

    expect(() => {
      createReleaseCliCmd.parse(["dapp-store", "create", "release", "-h"]);
    }).toThrow(outputHelpReference)

    expect(otherOutput).toEqual(createReleaseHelp)
  });

  //--------------------------------------------------

  const generalHelp = `Usage: dapp-store [options] [command]

CLI to assist with publishing to the Saga Dapp Store

Options:
  -V, --version       output the version number
  -h, --help          display help for command

Commands:
  init                First-time initialization of tooling configuration
  create              Create a \`publisher\`, \`app\`, or \`release\`
  validate [options]  Validates details prior to publishing
  publish             Submit a publishing request (\`submit\`, \`update\`, \`remove\`, or \`support\`) to the Solana Mobile dApp publisher portal
  help [command]      display help for command
`;

  const initHelp = `Usage: dapp-store init [options]

First-time initialization of tooling configuration

Options:
  -h, --help  display help for command
`;

  const keyPairArgHelp = "error: required option '-k, --keypair <path-to-keypair-file>' not specified"

  const createHelp = `Usage: dapp-store create [options] [command]

Create a \`publisher\`, \`app\`, or \`release\`

Options:
  -h, --help           display help for command

Commands:
  publisher [options]  Create a publisher
  app [options]        Create a app
  release [options]    Create a release
  help [command]       display help for command
`;

  const createPublisherHelp = `Usage: dapp-store create publisher [options]

Create a publisher

Options:
  -k, --keypair <path-to-keypair-file>   Path to keypair file
  -u, --url <url>                        RPC URL (default: "https://api.devnet.solana.com")
  -d, --dry-run                          Flag for dry run. Doesn't mint an NFT
  -s, --storage-config <storage-config>  Provide alternative storage configuration details
  -h, --help                             display help for command
`;

  const createAppHelp = `Usage: dapp-store create app [options]

Create a app

Options:
  -k, --keypair <path-to-keypair-file>                   Path to keypair file
  -p, --publisher-mint-address <publisher-mint-address>  The mint address of the publisher NFT
  -u, --url <url>                                        RPC URL (default: "https://api.devnet.solana.com")
  -d, --dry-run                                          Flag for dry run. Doesn't mint an NFT
  -s, --storage-config <storage-config>                  Provide alternative storage configuration details
  -h, --help                                             display help for command
`;

  const createReleaseHelp = `Usage: dapp-store create release [options]

Create a release

Options:
  -k, --keypair <path-to-keypair-file>       Path to keypair file
  -a, --app-mint-address <app-mint-address>  The mint address of the app NFT
  -u, --url <url>                            RPC URL (default: "https://api.devnet.solana.com")
  -d, --dry-run                              Flag for dry run. Doesn't mint an NFT
  -b, --build-tools-path <build-tools-path>  Path to Android build tools which contains AAPT2
  -s, --storage-config <storage-config>      Provide alternative storage configuration details
  -h, --help                                 display help for command
`;

});