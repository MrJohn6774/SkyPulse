/*
 * Added by ebctech (https://github.com/ebc81), 2025.
 * Part of the GPL-2.0-or-later boundary refactoring; isolates FlightAware code
 * from eu.ebctech.* proprietary code.
 *
 * Licensed under the GNU General Public License, version 2
 * or (at your option) any later version,
 * as this file is part of the modified FlightAware ADS-B component.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Upstream licensing:
 * https://github.com/ebc81/dump1090andro-gpl-sources/blob/main/LICENSE.md
 */
package com.flightaware.android.flightfeeder.analyzers;

import com.flightaware.android.flightfeeder.analyzers.dump1090.ModeSMessage;

/**
 * Dispatches decoded ADS-B messages to the application-layer export pipeline.
 * Implemented by the application layer (eu.ebctech) and registered
 * via {@link AnalyzerBridge}.
 *
 * <p>All methods are called on FlightAware decoder threads and must be
 * non-blocking (the implementation uses internal queues).</p>
 */
public interface IAnalyzerExportDispatcher {

    /**
     * Called for every raw Mode S frame right after detection, before full
     * aircraft decoding. Used for AVR and Beast raw-frame export.
     *
     * @param message the freshly detected Mode S message
     */
    void onRawFrameDetected(ModeSMessage message);

    /**
     * Called for every successfully decoded aircraft update.
     * Used for BaseStation TCP server, TCP client, and file export
     * (when reduce-data mode is OFF).
     *
     * @param message  the Mode S message that triggered the update
     * @param aircraft the aircraft state after decoding
     */
    void onAircraftUpdated(ModeSMessage message, Aircraft aircraft);

    /**
     * Called only for aircraft that have passed the {@code isReady()} check,
     * when file export is in reduce-data mode.
     * The implementation should stamp the aircraft's file-export timestamp.
     *
     * @param message  the Mode S message
     * @param aircraft the ready aircraft state
     * @param uptimeMs current {@code SystemClock.uptimeMillis()}
     */
    void onReadyAircraftUpdated(ModeSMessage message, Aircraft aircraft, long uptimeMs);
}
