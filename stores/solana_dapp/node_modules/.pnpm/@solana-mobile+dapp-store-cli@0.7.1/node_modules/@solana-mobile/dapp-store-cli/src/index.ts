import { mainCli } from "./CliSetup.js";

async function main() {
  await mainCli.parseAsync(process.argv);
}

main();
