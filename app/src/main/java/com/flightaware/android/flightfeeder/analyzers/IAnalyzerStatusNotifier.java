/*
 * Added by ebctech (https://github.com/ebc81), 2025.
 * Part of the GPLv2 boundary refactoring — isolates FlightAware (GPLv2) code
 * from eu.ebctech.* proprietary code.
 *
 * Licensed under the GNU General Public License, version 2 (GPLv2),
 * as this file is part of the modified FlightAware ADS-B component.
 *
 * Source of modified GPLv2 components:
 * https://github.com/ebc81/dump1090andro-gpl-sources
 */
package com.flightaware.android.flightfeeder.analyzers;

/**
 * Notifies the application layer about decoder status events.
 * Implemented by the application layer (eu.ebctech) and registered
 * via {@link AnalyzerBridge}.
 */
public interface IAnalyzerStatusNotifier {
    /**
     * Called on the decode thread each time a new aircraft update has been
     * processed and is ready for the UI.
     */
    void onNewAircraftDecoded();
}
