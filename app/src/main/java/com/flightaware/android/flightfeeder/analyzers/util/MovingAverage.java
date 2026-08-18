/*
 * Originally from FlightAware ADSB Flight Scanner for Android
 * Copyright (C) FlightAware, LLC
 * Licensed under the GNU General Public License, version 2 (GPLv2).
 *
 * This file has been modified by ebctech (https://github.com/ebc81), 2024-2025.
 * Modifications:
 *   - Relocated from com.flightaware.android.flightfeeder.util to
 *     com.flightaware.android.flightfeeder.analyzers.util
 *
 * Source of modified GPLv2 components:
 * https://github.com/ebc81/dump1090andro-gpl-sources
 */
package com.flightaware.android.flightfeeder.analyzers.util;

import java.util.Vector;

public class MovingAverage {
    // Vectors or synchronized
    private static Vector<Double> sSamples = new Vector<Double>();
    private static volatile int sPeriod = 10;
    private static volatile double sSum;

    public static void setPeriod(int period) {
        sPeriod = period;
    }

    public static void addSample(double sample) {
        sSum += sample;
        sSamples.add(sample);
        if (sSamples.size() > sPeriod) {
            Double old = sSamples.firstElement();
            sSum -= old;
            sSamples.remove(old);
        }
    }

    public static double getCurrentAverage() {
        if (sSamples.isEmpty())
            return 0; // technically the average is undefined

        return sSum / sPeriod;
    }

    public static void reset() {
        sSamples.clear();
        sSum = 0;
    }
}
