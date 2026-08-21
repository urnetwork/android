#!/usr/bin/env node

// Privacy-safe host-side telemetry for physical Android low-bar runs.
//
// Raw dumpsys output can contain subscriber, carrier, cell, address, DNS, and
// application data. This tool parses each command in memory and writes only an
// explicit allow-list of radio quality, network properties, resource state,
// and monotonically increasing interface counters as NDJSON.

import { spawnSync } from "node:child_process";
import { appendFileSync, chmodSync, existsSync, writeFileSync } from "node:fs";
import process from "node:process";
import { setTimeout as sleep } from "node:timers/promises";
import { pathToFileURL } from "node:url";

const SCHEMA_VERSION = 1;
const ANDROID_UNKNOWN_INT = 2_147_483_647;
const DEFAULT_PACKAGE = "com.bringyour.network";

function parsePositiveNumber(value, name) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    throw new Error(`${name} must be a positive number`);
  }
  return parsed;
}

function parseNonNegativeInteger(value, name) {
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 0) {
    throw new Error(`${name} must be a non-negative integer`);
  }
  return parsed;
}

export function parseArgs(argv) {
  const options = {
    serial: undefined,
    packageName: DEFAULT_PACKAGE,
    label: "physical-lowbar",
    intervalMs: 1_000,
    samples: 1,
    durationSeconds: undefined,
    stopFile: undefined,
    output: undefined,
    requireCellular: false,
    requireWifi: false,
    requireVpn: false,
    requireNoVpn: false,
    requireUnmeteredVpn: false,
    requireIpv4OnlyVpn: false,
    requireUnplugged: false,
    maxSignalLevel: undefined,
    help: false,
  };
  let samplesExplicit = false;
  let durationExplicit = false;

  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    const next = () => {
      const value = argv[++i];
      if (value === undefined || value.startsWith("--")) {
        throw new Error(`${arg} requires a value`);
      }
      return value;
    };
    switch (arg) {
      case "--serial":
        options.serial = next();
        break;
      case "--package":
        options.packageName = next();
        break;
      case "--label":
        options.label = next();
        break;
      case "--interval-ms":
        options.intervalMs = parsePositiveNumber(next(), "interval-ms");
        break;
      case "--samples":
        options.samples = parsePositiveNumber(next(), "samples");
        if (!Number.isInteger(options.samples)) {
          throw new Error("samples must be an integer");
        }
        samplesExplicit = true;
        break;
      case "--duration-seconds":
        options.durationSeconds = parsePositiveNumber(
          next(),
          "duration-seconds",
        );
        durationExplicit = true;
        break;
      case "--output":
        options.output = next();
        break;
      case "--stop-file":
        options.stopFile = next();
        break;
      case "--require-cellular":
        options.requireCellular = true;
        break;
      case "--require-wifi":
        options.requireWifi = true;
        break;
      case "--require-vpn":
        options.requireVpn = true;
        break;
      case "--require-no-vpn":
        options.requireNoVpn = true;
        break;
      case "--require-unmetered-vpn":
        options.requireVpn = true;
        options.requireUnmeteredVpn = true;
        break;
      case "--require-ipv4-only-vpn":
        options.requireVpn = true;
        options.requireIpv4OnlyVpn = true;
        break;
      case "--require-unplugged":
        options.requireUnplugged = true;
        break;
      case "--max-signal-level":
        options.maxSignalLevel = parseNonNegativeInteger(
          next(),
          "max-signal-level",
        );
        if (options.maxSignalLevel > 4) {
          throw new Error("max-signal-level must be between 0 and 4");
        }
        break;
      case "--help":
      case "-h":
        options.help = true;
        break;
      default:
        throw new Error(`unknown option: ${arg}`);
    }
  }

  if (samplesExplicit && durationExplicit) {
    throw new Error("use either --samples or --duration-seconds, not both");
  }
  if (options.requireCellular && options.requireWifi) {
    throw new Error("use either --require-cellular or --require-wifi, not both");
  }
  if (options.requireVpn && options.requireNoVpn) {
    throw new Error("use either --require-vpn or --require-no-vpn, not both");
  }
  if (!options.packageName || !options.label) {
    throw new Error("package and label must not be empty");
  }
  if (durationExplicit) {
    options.samples = Math.floor(
      (options.durationSeconds * 1_000) / options.intervalMs,
    ) + 1;
  }
  return options;
}

export function usage() {
  return [
    "usage: physical_lowbar_capture.mjs [options]",
    "",
    "options:",
    "  --serial SERIAL             adb device (required with multiple devices)",
    `  --package NAME              app package (default ${DEFAULT_PACKAGE})`,
    "  --label LABEL               correlation label (default physical-lowbar)",
    "  --interval-ms MS            target sample interval (default 1000)",
    "  --samples COUNT             exact sample count (default 1)",
    "  --duration-seconds SECONDS  sample at t=0 through this duration",
    "  --output FILE               also write the NDJSON stream to FILE",
    "  --stop-file FILE            finish cleanly once this host file exists",
    "  --require-cellular          invalidate samples without cellular underlay",
    "  --require-wifi              invalidate samples without Wi-Fi underlay",
    "  --require-vpn               invalidate samples without an active VPN",
    "  --require-no-vpn            invalidate samples with an active VPN",
    "  --require-unmetered-vpn     require VPN and Android NOT_METERED",
    "  --require-ipv4-only-vpn     require VPN to advertise IPv4 but not IPv6",
    "  --max-signal-level 0..4     invalidate samples above this Android level",
    "  --require-unplugged         invalidate AC/USB/wireless/dock-powered samples",
  ].join("\n");
}

function firstLineValue(text, key) {
  const escaped = key.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return text.match(new RegExp(`^\\s*${escaped}:\\s*(.+?)\\s*$`, "m"))?.[1];
}

function parseBoolean(value) {
  if (value === "true") return true;
  if (value === "false") return false;
  return undefined;
}

function parseFiniteNumber(value) {
  const number = Number(value);
  return Number.isFinite(number) && number !== ANDROID_UNKNOWN_INT
    ? number
    : undefined;
}

function compactObject(object) {
  return Object.fromEntries(
    Object.entries(object).filter(([, value]) => value !== undefined),
  );
}

function numericPairs(text) {
  const pairs = {};
  for (const match of text.matchAll(/([A-Za-z][A-Za-z0-9]*)\s*=\s*(-?\d+(?:\.\d+)?)/g)) {
    const value = parseFiniteNumber(match[2]);
    if (value !== undefined) {
      pairs[match[1]] = value;
    }
  }
  return pairs;
}

const SIGNAL_KEYS = new Set([
  "level",
  "rssi",
  "rsrp",
  "rsrq",
  "rssnr",
  "sinr",
  "cqi",
  "ssRsrp",
  "ssRsrq",
  "ssSinr",
  "csiRsrp",
  "csiRsrq",
  "csiSinr",
]);

function signalMeasurements(text) {
  return Object.fromEntries(
    Object.entries(numericPairs(text)).filter(([key]) => SIGNAL_KEYS.has(key)),
  );
}

function signalBlock(signalLine, primary) {
  if (!primary) return signalLine;
  const escaped = primary.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return signalLine.match(
    new RegExp(`${escaped}:\\{([^}]*)\\}`),
  )?.[1] ?? signalLine;
}

function registeredSignal(section) {
  const cellInfo = section.match(/^\s*mCellInfo=(.*)$/m)?.[1];
  if (!cellInfo || !cellInfo.includes("mRegistered=YES")) return undefined;
  const match = cellInfo.match(
    /mRegistered=YES.*?CellSignalStrength([A-Za-z0-9_]+):(?:\{([^}]*)\}|([^}\]]*))/,
  );
  if (!match) return undefined;
  return compactObject({
    technology: match[1].replace(/^CellSignalStrength/i, "").toUpperCase(),
    measurements: signalMeasurements(match[2] ?? match[3] ?? ""),
  });
}

export function parseTelephonyRegistry(text) {
  const sections = text
    .split(/\n(?=\s*Phone Id=)/)
    .filter((section) => /Phone Id=\d+/.test(section));
  const phones = sections.map((section) => {
    const phoneId = Number(section.match(/Phone Id=(\d+)/)?.[1]);
    const dataConnectionState = Number(
      section.match(/^\s*mDataConnectionState=(\d+)/m)?.[1],
    );
    const serviceState = section.match(/^\s*mServiceState=(.*)$/m)?.[1] ?? "";
    const dataTechnology = serviceState.match(
      /getRilDataRadioTechnology=\d+\(([^)]+)\)/,
    )?.[1];
    const dataRegistration = serviceState.match(
      /mDataRegState=\d+\(([^)]+)\)/,
    )?.[1];
    const signalLine = section.match(/^\s*mSignalStrength=(.*)$/m)?.[1] ?? "";
    const primary = signalLine.match(/primary=([A-Za-z0-9_]+)/)?.[1];
    const barMatch = signalLine.match(
      /(?:nr|lte|gsm|wcdma|tdscdma|cdma)Level\s*=\s*(\d+)/i,
    );
    const measurements = signalMeasurements(signalBlock(signalLine, primary));
    const platformLevel = barMatch
      ? Number(barMatch[1])
      : measurements.level;
    return compactObject({
      phoneId,
      dataConnected: dataConnectionState === 2,
      dataConnectionState: Number.isFinite(dataConnectionState)
        ? dataConnectionState
        : undefined,
      dataRegistration,
      dataTechnology,
      primary: primary
        ?.replace(/^CellSignalStrength/i, "")
        .toUpperCase(),
      platformLevel,
      measurements,
      registeredSignal: registeredSignal(section),
    });
  });
  return phones.find((phone) => phone.dataConnected) ?? phones[0] ?? null;
}

function parseAddressFamilies(line) {
  const addresses = line.match(/LinkAddresses:\s*\[([^\]]*)\]/)?.[1] ?? "";
  const families = [];
  if (/\d+\.\d+\.\d+\.\d+/.test(addresses)) families.push("ipv4");
  if (/[0-9a-f]+:[0-9a-f:]+/i.test(addresses)) families.push("ipv6");
  return families;
}

function parseNetworkLine(line, activeNetworkId) {
  const networkId = Number(
    line.match(/NetworkAgentInfo\{network\{(\d+)\}/)?.[1],
  );
  if (!Number.isFinite(networkId)) return undefined;
  const transports = (
    line.match(/Transports:\s*([A-Z0-9_|]+)/)?.[1] ?? ""
  ).split("|").filter(Boolean);
  const capabilities = new Set(
    (line.match(/Capabilities:\s*([A-Z0-9_&]+)/)?.[1] ?? "")
      .split("&")
      .filter(Boolean),
  );
  const underlyingText = line.match(
    /UnderlyingNetworks:\s*(\[[^\]]*\]|Null)/,
  )?.[1] ?? "";
  const underlyingNetworkIds = [
    ...underlyingText.matchAll(/\d+/g),
  ].map((match) => Number(match[0]));
  return compactObject({
    networkId,
    active: networkId === activeNetworkId,
    connected: /\bCONNECTED\b/.test(line),
    transports,
    interfaceName: line.match(/InterfaceName:\s*([A-Za-z0-9_.:-]+)/)?.[1],
    mtu: parseFiniteNumber(line.match(/\bMTU:\s*(\d+)/)?.[1]),
    addressFamilies: parseAddressFamilies(line),
    validated: capabilities.has("VALIDATED"),
    metered: !capabilities.has("NOT_METERED"),
    bandwidthConstrained: !capabilities.has("NOT_BANDWIDTH_CONSTRAINED"),
    roaming: !capabilities.has("NOT_ROAMING"),
    upBandwidthKbps: parseFiniteNumber(
      line.match(/LinkUpBandwidth>=(\d+)Kbps/)?.[1],
    ),
    downBandwidthKbps: parseFiniteNumber(
      line.match(/LinkDnBandwidth>=(\d+)Kbps/)?.[1],
    ),
    underlyingNetworkIds,
  });
}

export function parseConnectivity(text) {
  const activeNetworkId = Number(
    text.match(/Active default network:\s*(\d+)/)?.[1],
  );
  const networks = text
    .split("\n")
    .filter((line) => line.includes("NetworkAgentInfo{network{"))
    .map((line) => parseNetworkLine(line, activeNetworkId))
    .filter(Boolean);
  const systemDefaultNetwork = networks.find((network) => network.active) ?? null;
  const connectedVpnNetworks = networks.filter(
    (network) => network.connected && network.transports.includes("VPN"),
  );
  // `dumpsys connectivity` reports the shell UID's global default network.
  // On current physical Android releases that remains Wi-Fi/cellular while a
  // per-app VpnService is connected and routing Chrome. When exactly one VPN
  // is present, it is the unambiguous measured network even if the shell's
  // `Active default network` marker points at its physical underlay.
  const measuredNetwork = systemDefaultNetwork?.transports.includes("VPN")
    ? systemDefaultNetwork
    : connectedVpnNetworks.length === 1
      ? connectedVpnNetworks[0]
      : systemDefaultNetwork;
  const activeNetwork = measuredNetwork === null
    ? null
    : { ...measuredNetwork, active: true };
  let underlayNetworks = [];
  if (activeNetwork) {
    if (!activeNetwork.transports.includes("VPN")) {
      underlayNetworks = [activeNetwork];
    } else if (activeNetwork.underlyingNetworkIds.length > 0) {
      const ids = new Set(activeNetwork.underlyingNetworkIds);
      underlayNetworks = networks.filter((network) => ids.has(network.networkId));
    } else {
      underlayNetworks = networks.filter(
        (network) =>
          network.connected &&
          network.validated &&
          !network.transports.includes("VPN") &&
          (network.transports.includes("CELLULAR") ||
            network.transports.includes("WIFI") ||
            network.transports.includes("ETHERNET")),
      );
    }
  }
  return { activeNetwork, underlayNetworks };
}

export function parseBattery(text) {
  const level = parseFiniteNumber(firstLineValue(text, "level"));
  const scale = parseFiniteNumber(firstLineValue(text, "scale"));
  const temperatureTenthsC = parseFiniteNumber(
    firstLineValue(text, "temperature"),
  );
  return compactObject({
    levelPercent: level !== undefined && scale
      ? (level / scale) * 100
      : undefined,
    statusCode: parseFiniteNumber(firstLineValue(text, "status")),
    healthCode: parseFiniteNumber(firstLineValue(text, "health")),
    present: parseBoolean(firstLineValue(text, "present")),
    acPowered: parseBoolean(firstLineValue(text, "AC powered")),
    usbPowered: parseBoolean(firstLineValue(text, "USB powered")),
    wirelessPowered: parseBoolean(firstLineValue(text, "Wireless powered")),
    dockPowered: parseBoolean(firstLineValue(text, "Dock powered")),
    voltageMv: parseFiniteNumber(firstLineValue(text, "voltage")),
    temperatureC: temperatureTenthsC === undefined
      ? undefined
      : temperatureTenthsC / 10,
    chargeCounterMicroAh: parseFiniteNumber(
      firstLineValue(text, "Charge counter"),
    ),
  });
}

export function parseThermal(text) {
  const status = parseFiniteNumber(
    text.match(/^Thermal Status:\s*(\d+)/m)?.[1],
  );
  const currentSection = text.match(
    /Current temperatures from HAL:\s*\n([\s\S]*?)(?=Current cooling devices|Temperature static thresholds|$)/,
  )?.[1] ?? "";
  const temperatures = [];
  for (const match of currentSection.matchAll(
    /Temperature\{mValue=(-?\d+(?:\.\d+)?),\s*mType=(\d+),\s*mName=([A-Za-z0-9_.-]{1,24}),\s*mStatus=(\d+)\}/g,
  )) {
    temperatures.push({
      celsius: Number(match[1]),
      type: Number(match[2]),
      name: match[3],
      status: Number(match[4]),
    });
  }
  return compactObject({ status, temperatures });
}

export function parseNetDev(text) {
  const interfaces = {};
  for (const line of text.split("\n")) {
    const match = line.match(/^\s*([A-Za-z0-9_.-]+):\s*(.*)$/);
    if (!match || match[1] === "lo") continue;
    const values = match[2].trim().split(/\s+/).map(Number);
    if (values.length < 16 || values.some((value) => !Number.isFinite(value))) {
      continue;
    }
    if (values[0] === 0 && values[8] === 0) continue;
    interfaces[match[1]] = {
      rxBytes: values[0],
      rxPackets: values[1],
      rxErrors: values[2],
      rxDrops: values[3],
      txBytes: values[8],
      txPackets: values[9],
      txErrors: values[10],
      txDrops: values[11],
    };
  }
  return interfaces;
}

export function parseMeminfo(text) {
  if (!text || /No process found/i.test(text)) return null;
  const totals = text.match(/TOTAL PSS:\s*(\d+).*?TOTAL RSS:\s*(\d+)/s);
  const value = (label) => parseFiniteNumber(
    text.match(new RegExp(`^\\s*${label}:\\s*(\\d+)`, "m"))?.[1],
  );
  const memory = compactObject({
    totalPssKib: parseFiniteNumber(totals?.[1]),
    totalRssKib: parseFiniteNumber(totals?.[2]),
    javaHeapKib: value("Java Heap"),
    nativeHeapKib: value("Native Heap"),
    codeKib: value("Code"),
    stackKib: value("Stack"),
    graphicsKib: value("Graphics"),
    privateOtherKib: value("Private Other"),
    systemKib: value("System"),
    totalSwapPssKib: parseFiniteNumber(
      text.match(/\bTOTAL SWAP PSS:\s*(\d+)/)?.[1],
    ),
  });
  return Object.keys(memory).length > 0 ? memory : null;
}

function powered(battery) {
  return Boolean(
    battery?.acPowered ||
      battery?.usbPowered ||
      battery?.wirelessPowered ||
      battery?.dockPowered,
  );
}

export function evaluateEligibility(sample, requirements) {
  const reasons = [];
  const active = sample.network?.activeNetwork;
  const underlays = sample.network?.underlayNetworks ?? [];
  const underlayTransports = new Set(
    underlays.flatMap((network) => network.transports ?? []),
  );
  if (requirements.requireCellular && !underlayTransports.has("CELLULAR")) {
    reasons.push("cellular-underlay-required");
  }
  if (requirements.requireWifi && !underlayTransports.has("WIFI")) {
    reasons.push("wifi-underlay-required");
  }
  const vpnActive = active?.transports?.includes("VPN") ?? false;
  if (requirements.requireVpn && !vpnActive) {
    reasons.push("active-vpn-required");
  }
  if (requirements.requireNoVpn && vpnActive) {
    reasons.push("active-vpn-forbidden");
  }
  if (requirements.requireUnmeteredVpn && vpnActive && active.metered !== false) {
    reasons.push("vpn-is-metered-or-unknown");
  }
  if (requirements.requireIpv4OnlyVpn && vpnActive) {
    const families = new Set(active.addressFamilies ?? []);
    if (!families.has("ipv4") || families.has("ipv6")) {
      reasons.push("vpn-is-not-ipv4-only");
    }
  }
  if (requirements.maxSignalLevel !== undefined) {
    const level = sample.radio?.platformLevel;
    if (!Number.isFinite(level)) {
      reasons.push("signal-level-unavailable");
    } else if (level > requirements.maxSignalLevel) {
      reasons.push("signal-level-above-limit");
    }
  }
  if (requirements.requireUnplugged && powered(sample.battery)) {
    reasons.push("external-power-connected");
  }
  if (sample.telemetryErrors?.length > 0) {
    reasons.push("telemetry-incomplete");
  }
  return { eligible: reasons.length === 0, reasons };
}

function median(values) {
  if (values.length === 0) return undefined;
  const sorted = [...values].sort((a, b) => a - b);
  const middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 0
    ? (sorted[middle - 1] + sorted[middle]) / 2
    : sorted[middle];
}

function interfaceDeltas(first, last) {
  const deltas = {};
  for (const name of new Set([
    ...Object.keys(first ?? {}),
    ...Object.keys(last ?? {}),
  ])) {
    const start = first?.[name];
    const end = last?.[name];
    if (!start || !end) continue;
    const delta = {};
    for (const key of Object.keys(start)) {
      const value = end[key] - start[key];
      if (Number.isFinite(value) && value >= 0) delta[key] = value;
    }
    deltas[name] = delta;
  }
  return deltas;
}

export function summarizeSamples(samples) {
  const reasonCounts = {};
  for (const sample of samples) {
    for (const reason of sample.eligibility.reasons) {
      reasonCounts[reason] = (reasonCounts[reason] ?? 0) + 1;
    }
  }
  const levels = samples
    .map((sample) => sample.radio?.platformLevel)
    .filter(Number.isFinite);
  const pss = samples
    .map((sample) => sample.memory?.totalPssKib)
    .filter(Number.isFinite);
  const rss = samples
    .map((sample) => sample.memory?.totalRssKib)
    .filter(Number.isFinite);
  const thermalStatuses = samples
    .map((sample) => sample.thermal?.status)
    .filter(Number.isFinite);
  const first = samples[0];
  const last = samples.at(-1);
  return compactObject({
    sampleCount: samples.length,
    eligibleSampleCount: samples.filter((sample) => sample.eligibility.eligible).length,
    invalidReasonCounts: reasonCounts,
    startTimeUnixMs: first?.startTimeUnixMs,
    endTimeUnixMs: last?.endTimeUnixMs,
    elapsedMs: first && last ? last.endTimeUnixMs - first.startTimeUnixMs : undefined,
    signalLevelMin: levels.length ? Math.min(...levels) : undefined,
    signalLevelMedian: median(levels),
    signalLevelMax: levels.length ? Math.max(...levels) : undefined,
    peakTotalPssKib: pss.length ? Math.max(...pss) : undefined,
    peakTotalRssKib: rss.length ? Math.max(...rss) : undefined,
    maxThermalStatus: thermalStatuses.length
      ? Math.max(...thermalStatuses)
      : undefined,
    startBatteryLevelPercent: first?.battery?.levelPercent,
    endBatteryLevelPercent: last?.battery?.levelPercent,
    interfaceDeltas: interfaceDeltas(first?.interfaces, last?.interfaces),
  });
}

function adbCommand(serial, args) {
  const result = spawnSync(
    "adb",
    [...(serial ? ["-s", serial] : []), ...args],
    { encoding: "utf8", maxBuffer: 32 * 1024 * 1024 },
  );
  return {
    ok: result.status === 0,
    stdout: result.status === 0 ? result.stdout : "",
  };
}

function adbShell(serial, ...args) {
  return adbCommand(serial, ["shell", ...args]);
}

function collectParsed(errors, name, command, parser) {
  const result = command();
  if (!result.ok) {
    errors.push(`${name}-unavailable`);
    return null;
  }
  try {
    return parser(result.stdout);
  } catch {
    errors.push(`${name}-unparseable`);
    return null;
  }
}

function packageMetadata(serial, packageName) {
  const path = adbShell(serial, "pm", "path", packageName);
  if (!path.ok || !path.stdout.trim()) {
    return { installed: false };
  }
  const dump = adbShell(serial, "dumpsys", "package", packageName);
  const text = dump.ok ? dump.stdout : "";
  return compactObject({
    installed: true,
    versionName: text.match(/\bversionName=([^\s]+)/)?.[1],
    versionCode: parseFiniteNumber(text.match(/\bversionCode=(\d+)/)?.[1]),
  });
}

function getProperty(serial, property) {
  const result = adbShell(serial, "getprop", property);
  return result.ok ? result.stdout.trim() : undefined;
}

export function emitFactory(output, stdout = process.stdout) {
  if (output) {
    writeFileSync(output, "", { encoding: "utf8", mode: 0o600 });
    chmodSync(output, 0o600);
  }
  return (record) => {
    const line = `${JSON.stringify(record)}\n`;
    stdout.write(line);
    if (output) appendFileSync(output, line, { encoding: "utf8" });
  };
}

async function collectSample(options, index, packageInstalled) {
  const startTimeUnixMs = Date.now();
  const errors = [];
  const radio = collectParsed(
    errors,
    "radio",
    () => adbShell(options.serial, "dumpsys", "telephony.registry"),
    parseTelephonyRegistry,
  );
  const network = collectParsed(
    errors,
    "network",
    () => adbShell(options.serial, "dumpsys", "connectivity"),
    parseConnectivity,
  );
  const battery = collectParsed(
    errors,
    "battery",
    () => adbShell(options.serial, "dumpsys", "battery"),
    parseBattery,
  );
  const thermal = collectParsed(
    errors,
    "thermal",
    () => adbShell(options.serial, "dumpsys", "thermalservice"),
    parseThermal,
  );
  const interfaces = collectParsed(
    errors,
    "interfaces",
    () => adbShell(options.serial, "cat", "/proc/net/dev"),
    parseNetDev,
  );
  let memory = null;
  if (packageInstalled) {
    memory = collectParsed(
      errors,
      "memory",
      () => adbShell(options.serial, "dumpsys", "meminfo", options.packageName),
      parseMeminfo,
    );
  }
  const endTimeUnixMs = Date.now();
  const sample = {
    type: "sample",
    schemaVersion: SCHEMA_VERSION,
    label: options.label,
    index,
    startTimeUnixMs,
    endTimeUnixMs,
    collectionDurationMs: endTimeUnixMs - startTimeUnixMs,
    radio,
    network,
    battery,
    thermal,
    memory,
    interfaces: interfaces ?? {},
    telemetryErrors: errors,
  };
  sample.eligibility = evaluateEligibility(sample, options);
  return sample;
}

export async function runCapture(options) {
  const state = adbCommand(options.serial, ["get-state"]);
  if (!state.ok || state.stdout.trim() !== "device") {
    throw new Error("adb device is not ready");
  }
  const emit = emitFactory(options.output);
  const packageInfo = packageMetadata(options.serial, options.packageName);
  emit({
    type: "environment",
    schemaVersion: SCHEMA_VERSION,
    platform: "android",
    label: options.label,
    manufacturer: getProperty(options.serial, "ro.product.manufacturer"),
    model: getProperty(options.serial, "ro.product.model"),
    osRelease: getProperty(options.serial, "ro.build.version.release"),
    sdkLevel: parseFiniteNumber(
      getProperty(options.serial, "ro.build.version.sdk"),
    ),
    packageName: options.packageName,
    package: packageInfo,
    requirements: {
      requireCellular: options.requireCellular,
      requireWifi: options.requireWifi,
      requireVpn: options.requireVpn,
      requireNoVpn: options.requireNoVpn,
      requireUnmeteredVpn: options.requireUnmeteredVpn,
      requireIpv4OnlyVpn: options.requireIpv4OnlyVpn,
      requireUnplugged: options.requireUnplugged,
      maxSignalLevel: options.maxSignalLevel ?? null,
    },
    intervalMs: options.intervalMs,
    requestedSamples: options.samples,
  });

  const samples = [];
  const monotonicStart = performance.now();
  for (let index = 0; index < options.samples; index += 1) {
    if (index > 0 && options.stopFile && existsSync(options.stopFile)) break;
    const target = monotonicStart + index * options.intervalMs;
    const waitMs = target - performance.now();
    if (waitMs > 0) await sleep(waitMs);
    const sample = await collectSample(
      options,
      index,
      packageInfo.installed,
    );
    samples.push(sample);
    emit(sample);
  }
  const summary = {
    type: "summary",
    schemaVersion: SCHEMA_VERSION,
    label: options.label,
    ...summarizeSamples(samples),
  };
  emit(summary);
  return summary;
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    process.stdout.write(`${usage()}\n`);
    return;
  }
  await runCapture(options);
}

const isMain = process.argv[1]
  ? import.meta.url === pathToFileURL(process.argv[1]).href
  : false;
if (isMain) {
  main().catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
