import assert from "node:assert/strict";
import {
  chmodSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  statSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";

import {
  emitFactory,
  evaluateEligibility,
  parseArgs,
  parseBattery,
  parseConnectivity,
  parseMeminfo,
  parseNetDev,
  parseTelephonyRegistry,
  parseThermal,
  summarizeSamples,
} from "./physical_lowbar_capture.mjs";

test("parseArgs resolves strict physical low-bar requirements", () => {
  const options = parseArgs([
    "--serial", "device",
    "--duration-seconds", "2",
    "--interval-ms", "500",
    "--stop-file", "/tmp/physical-lowbar.stop",
    "--require-cellular",
    "--require-unmetered-vpn",
    "--require-ipv4-only-vpn",
    "--require-unplugged",
    "--max-signal-level", "1",
  ]);
  assert.equal(options.samples, 5);
  assert.equal(options.stopFile, "/tmp/physical-lowbar.stop");
  assert.equal(options.requireVpn, true);
  assert.equal(options.requireWifi, false);
  assert.equal(options.requireUnmeteredVpn, true);
  assert.equal(options.maxSignalLevel, 1);
  assert.throws(
    () => parseArgs(["--samples", "2", "--duration-seconds", "5"]),
    /either/,
  );
  assert.throws(
    () => parseArgs(["--require-cellular", "--require-wifi"]),
    /either/,
  );
  assert.throws(
    () => parseArgs(["--require-vpn", "--require-no-vpn"]),
    /either/,
  );
});

test("telephony parser selects the connected subscription without identities", () => {
  const text = `last known state:
  Phone Id=0
    mServiceState={mDataRegState=0(IN_SERVICE), getRilDataRadioTechnology=20(NR_SA), mOperatorAlphaLong=private-carrier}
    mSignalStrength=SignalStrength:{mNr=CellSignalStrengthNr:{ ssRsrp = -109 ssRsrq = -11 ssSinr = 13 level = 1 timingAdvance = 2147483647 },SignalBarInfo{ nrLevel=1 },rat=20,primary=CellSignalStrengthNr}
    mDataConnectionState=2
    mCellInfo=[CellInfoNr:{ mRegistered=YES CellIdentityNr:{ secret=123 } CellSignalStrengthNr:{ ssRsrp = -111 ssRsrq = -13 ssSinr = 7 level = 1 } }]
  Phone Id=1
    mServiceState={mDataRegState=1(OUT_OF_SERVICE), getRilDataRadioTechnology=0(Unknown)}
    mSignalStrength=SignalStrength:{mLte=Invalid,SignalBarInfo{ no level },rat=14,primary=CellSignalStrengthLte}
    mDataConnectionState=0`;
  const radio = parseTelephonyRegistry(text);
  assert.deepEqual(radio, {
    phoneId: 0,
    dataConnected: true,
    dataConnectionState: 2,
    dataRegistration: "IN_SERVICE",
    dataTechnology: "NR_SA",
    primary: "NR",
    platformLevel: 1,
    measurements: { ssRsrp: -109, ssRsrq: -11, ssSinr: 13, level: 1 },
    registeredSignal: {
      technology: "NR",
      measurements: { ssRsrp: -111, ssRsrq: -13, ssSinr: 7, level: 1 },
    },
  });
  assert.equal(JSON.stringify(radio).includes("private-carrier"), false);
  assert.equal(JSON.stringify(radio).includes("secret"), false);
});

test("connectivity parser emits a sanitized VPN and cellular underlay", () => {
  const text = `Active default network: 200
  NetworkAgentInfo{network{200} ni{VPN CONNECTED} lp{{InterfaceName: tun0 LinkAddresses: [ 10.0.0.2/32 ] MTU: 1100}} nc{[ Transports: VPN Capabilities: INTERNET&NOT_RESTRICTED&TRUSTED&NOT_METERED&VALIDATED&NOT_BANDWIDTH_CONSTRAINED UnderlyingNetworks: [100]]}}
  NetworkAgentInfo{network{100} ni{MOBILE CONNECTED extra: private-apn} lp{{InterfaceName: rmnet_data0 LinkAddresses: [ 192.0.0.2/27,2001:db8::2/64 ] MTU: 1440}} nc{[ Transports: CELLULAR Capabilities: INTERNET&VALIDATED&NOT_VPN&NOT_ROAMING&NOT_BANDWIDTH_CONSTRAINED LinkUpBandwidth>=250Kbps LinkDnBandwidth>=1000Kbps UnderlyingNetworks: Null]}}`;
  const network = parseConnectivity(text);
  assert.deepEqual(network.activeNetwork.addressFamilies, ["ipv4"]);
  assert.equal(network.activeNetwork.metered, false);
  assert.deepEqual(network.activeNetwork.transports, ["VPN"]);
  assert.equal(network.underlayNetworks[0].interfaceName, "rmnet_data0");
  assert.equal(network.underlayNetworks[0].metered, true);
  assert.equal(JSON.stringify(network).includes("private-apn"), false);
  assert.equal(JSON.stringify(network).includes("192.0.0.2"), false);
});

test("connectivity parser selects one app VPN when the shell default is physical", () => {
  const text = `Active default network: 100
  NetworkAgentInfo{network{200} ni{VPN CONNECTED} lp{{InterfaceName: tun0 LinkAddresses: [ 10.0.0.2/32 ] MTU: 1100}} nc{[ Transports: VPN Capabilities: INTERNET&NOT_RESTRICTED&TRUSTED&NOT_METERED&VALIDATED&NOT_BANDWIDTH_CONSTRAINED UnderlyingNetworks: [100]]}}
  NetworkAgentInfo{network{100} ni{WIFI CONNECTED extra: private-ssid} lp{{InterfaceName: wlan0 LinkAddresses: [ 192.0.0.2/24,2001:db8::2/64 ] MTU: 1500}} nc{[ Transports: WIFI Capabilities: INTERNET&VALIDATED&NOT_VPN&NOT_METERED&NOT_ROAMING&NOT_BANDWIDTH_CONSTRAINED UnderlyingNetworks: Null]}}`;
  const network = parseConnectivity(text);
  assert.deepEqual(network.activeNetwork.transports, ["VPN"]);
  assert.equal(network.activeNetwork.active, true);
  assert.deepEqual(network.activeNetwork.addressFamilies, ["ipv4"]);
  assert.deepEqual(network.underlayNetworks[0].transports, ["WIFI"]);
  assert.equal(JSON.stringify(network).includes("private-ssid"), false);
  assert.equal(JSON.stringify(network).includes("192.0.0.2"), false);
});

test("resource parsers retain only useful allow-listed fields", () => {
  assert.deepEqual(parseBattery(`
    AC powered: false
    USB powered: false
    Wireless powered: false
    Dock powered: false
    Charge counter: 4200000
    status: 3
    health: 2
    present: true
    level: 75
    scale: 100
    voltage: 4100
    temperature: 287
    private serial: do-not-copy
  `), {
    levelPercent: 75,
    statusCode: 3,
    healthCode: 2,
    present: true,
    acPowered: false,
    usbPowered: false,
    wirelessPowered: false,
    dockPowered: false,
    voltageMv: 4100,
    temperatureC: 28.7,
    chargeCounterMicroAh: 4200000,
  });
  assert.deepEqual(parseThermal(`
Thermal Status: 1
Current temperatures from HAL:
  Temperature{mValue=36.2, mType=0, mName=AP, mStatus=1}
  Temperature{mValue=33.1, mType=3, mName=SKIN, mStatus=0}
Current cooling devices from HAL:
`), {
    status: 1,
    temperatures: [
      { celsius: 36.2, type: 0, name: "AP", status: 1 },
      { celsius: 33.1, type: 3, name: "SKIN", status: 0 },
    ],
  });
  assert.deepEqual(parseNetDev(`
Inter-| Receive | Transmit
 rmnet0: 1000 10 1 2 0 0 0 0 2000 20 3 4 0 0 0 0
 lo: 500 5 0 0 0 0 0 0 500 5 0 0 0 0 0 0
`), {
    rmnet0: {
      rxBytes: 1000,
      rxPackets: 10,
      rxErrors: 1,
      rxDrops: 2,
      txBytes: 2000,
      txPackets: 20,
      txErrors: 3,
      txDrops: 4,
    },
  });
  assert.deepEqual(parseMeminfo(`
 App Summary
                       Pss(KB)                        Rss(KB)
                        ------                         ------
           Java Heap:     1000
         Native Heap:     2000
                Code:      300
               Stack:      100
            Graphics:      400
       Private Other:      500
              System:      600
               TOTAL:     4900       TOTAL SWAP PSS:       25
  TOTAL PSS:     4900            TOTAL RSS:     8000
`), {
    totalPssKib: 4900,
    totalRssKib: 8000,
    javaHeapKib: 1000,
    nativeHeapKib: 2000,
    codeKib: 300,
    stackKib: 100,
    graphicsKib: 400,
    privateOtherKib: 500,
    systemKib: 600,
    totalSwapPssKib: 25,
  });
});

test("eligibility rejects strong, metered, dual-stack, powered samples", () => {
  const sample = {
    radio: { platformLevel: 3 },
    network: {
      activeNetwork: {
        transports: ["VPN"],
        metered: true,
        addressFamilies: ["ipv4", "ipv6"],
      },
      underlayNetworks: [{ transports: ["CELLULAR"] }],
    },
    battery: { usbPowered: true },
    telemetryErrors: [],
  };
  assert.deepEqual(evaluateEligibility(sample, {
    requireCellular: true,
    requireVpn: true,
    requireUnmeteredVpn: true,
    requireIpv4OnlyVpn: true,
    requireUnplugged: true,
    maxSignalLevel: 1,
  }), {
    eligible: false,
    reasons: [
      "vpn-is-metered-or-unknown",
      "vpn-is-not-ipv4-only",
      "signal-level-above-limit",
      "external-power-connected",
    ],
  });
});

test("eligibility enforces a Wi-Fi underlay independently of VPN state", () => {
  const sample = {
    network: {
      activeNetwork: { transports: ["VPN"], addressFamilies: ["ipv4"] },
      underlayNetworks: [{ transports: ["CELLULAR"] }],
    },
    telemetryErrors: [],
  };
  assert.deepEqual(evaluateEligibility(sample, {
    requireWifi: true,
    requireVpn: true,
  }), {
    eligible: false,
    reasons: ["wifi-underlay-required"],
  });
});

test("eligibility rejects an active VPN for a Direct control", () => {
  const sample = {
    network: {
      activeNetwork: { transports: ["VPN"], addressFamilies: ["ipv4"] },
      underlayNetworks: [{ transports: ["WIFI"] }],
    },
    telemetryErrors: [],
  };
  assert.deepEqual(evaluateEligibility(sample, {
    requireWifi: true,
    requireNoVpn: true,
  }), {
    eligible: false,
    reasons: ["active-vpn-forbidden"],
  });
});

test("summary reports validity, peaks, and non-negative interface deltas", () => {
  const samples = [
    {
      startTimeUnixMs: 1000,
      endTimeUnixMs: 1010,
      radio: { platformLevel: 1 },
      memory: { totalPssKib: 100, totalRssKib: 200 },
      thermal: { status: 0 },
      battery: { levelPercent: 80 },
      interfaces: { tun0: { rxBytes: 10, txBytes: 20 } },
      eligibility: { eligible: true, reasons: [] },
    },
    {
      startTimeUnixMs: 2000,
      endTimeUnixMs: 2010,
      radio: { platformLevel: 0 },
      memory: { totalPssKib: 125, totalRssKib: 250 },
      thermal: { status: 2 },
      battery: { levelPercent: 79 },
      interfaces: { tun0: { rxBytes: 110, txBytes: 220 } },
      eligibility: { eligible: false, reasons: ["telemetry-incomplete"] },
    },
  ];
  assert.deepEqual(summarizeSamples(samples), {
    sampleCount: 2,
    eligibleSampleCount: 1,
    invalidReasonCounts: { "telemetry-incomplete": 1 },
    startTimeUnixMs: 1000,
    endTimeUnixMs: 2010,
    elapsedMs: 1010,
    signalLevelMin: 0,
    signalLevelMedian: 0.5,
    signalLevelMax: 1,
    peakTotalPssKib: 125,
    peakTotalRssKib: 250,
    maxThermalStatus: 2,
    startBatteryLevelPercent: 80,
    endBatteryLevelPercent: 79,
    interfaceDeltas: { tun0: { rxBytes: 100, txBytes: 200 } },
  });
});

test("output replaces permissive existing permissions with private mode", () => {
  const directory = mkdtempSync(join(tmpdir(), "android-lowbar-output-"));
  const path = join(directory, "capture.ndjson");
  try {
    writeFileSync(path, "stale\n", { mode: 0o644 });
    chmodSync(path, 0o644);
    const stdout = [];
    const emit = emitFactory(path, { write: (value) => stdout.push(value) });
    emit({ type: "summary", eligibleSampleCount: 1 });
    assert.equal(statSync(path).mode & 0o777, 0o600);
    assert.deepEqual(
      JSON.parse(readFileSync(path, "utf8")),
      { type: "summary", eligibleSampleCount: 1 },
    );
    assert.equal(stdout.length, 1);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});
