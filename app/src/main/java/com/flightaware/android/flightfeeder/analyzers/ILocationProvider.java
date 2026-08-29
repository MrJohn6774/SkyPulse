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
