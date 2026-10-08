package com.bringyour.network.acceptance

/** Reads one snapshot without retaining its captured device or getters. */
internal fun <Device : Any, Stats : Any> physicalSnapshotMemoryStats(
    device: Device?,
    readDevice: (Device) -> Stats,
    readProcess: () -> Stats,
): Stats = if (device == null) readProcess() else readDevice(device)
