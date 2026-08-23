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

import androidx.annotation.Nullable;
import io.github.mrjohn6774.skypulse.model.GeoPoint;

/**
 * Provides the current receiver location to FlightAware decoder logic.
 * Implemented by the application layer (eu.ebctech) and registered
 * via {@link AnalyzerBridge}.
 */
public interface ILocationProvider {
    /**
     * Returns the current device/receiver position, or {@code null} if unknown.
     */
    @Nullable
    GeoPoint getLocation();
}
