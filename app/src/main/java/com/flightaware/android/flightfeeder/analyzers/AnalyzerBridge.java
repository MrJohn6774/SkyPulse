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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.mrjohn6774.skypulse.model.GeoPoint;

/**
 * Central dependency-injection registry for the FlightAware analyzer library.
 *
 * <p>All callback interfaces ({@link ILocationProvider}, {@link IAnalyzerLogger},
 * {@link IAnalyzerStatusNotifier}, {@link IAnalyzerExportDispatcher}) must be
 * registered by the application layer (eu.ebctech) via the {@code register*()}
 * methods <em>before</em> any analyzer thread is started.
 *
 * <p>Every getter provides a safe no-op fallback so the analyzer threads never
 * crash if a registration is missing.
 */
public final class AnalyzerBridge {

    private static volatile boolean sDebug = false;

    /**
     * How many seconds of silence before an aircraft is considered stale.
     * Default is 60 s (safe fallback). The application layer (eu.ebctech) must
     * call {@link #setStallTimeoutSec} in {@code configureSubServices()} to
     * override this value before any decoder thread starts.
     */
    private static volatile int sStallTimeoutSec = 60;

    private static volatile ILocationProvider       sLocationProvider       = null;
    private static volatile IAnalyzerLogger         sLogger                 = null;
    private static volatile IAnalyzerStatusNotifier sStatusNotifier         = null;
    private static volatile IAnalyzerExportDispatcher sExportDispatcher     = null;

    private AnalyzerBridge() {}

    // -------------------------------------------------------------------------
    // Registration (called by eu.ebctech before starting analyzer threads)
    // -------------------------------------------------------------------------

    public static void setDebug(boolean debug) {
        sDebug = debug;
    }

    /**
     * Sets the aircraft stall timeout. Call this from the application layer
     * (eu.ebctech) in {@code configureSubServices()} before starting any
     * FlightAware decoder thread.
     *
     * @param seconds seconds of silence after which an aircraft is considered stale
     */
    public static void setStallTimeoutSec(int seconds) {
        sStallTimeoutSec = seconds;
    }

    public static void registerLocationProvider(@NonNull ILocationProvider provider) {
        sLocationProvider = provider;
    }

    public static void registerLogger(@NonNull IAnalyzerLogger logger) {
        sLogger = logger;
    }

    public static void registerStatusNotifier(@NonNull IAnalyzerStatusNotifier notifier) {
        sStatusNotifier = notifier;
    }

    public static void registerExportDispatcher(@NonNull IAnalyzerExportDispatcher dispatcher) {
        sExportDispatcher = dispatcher;
    }

    /**
     * Convenience method: registers a single object that implements all four
     * bridge interfaces. Call this instead of the individual {@code register*()} methods.
     */
    public static <T extends ILocationProvider & IAnalyzerLogger & IAnalyzerStatusNotifier & IAnalyzerExportDispatcher>
    void registerAll(@NonNull T impl) {
        sLocationProvider = impl;
        sLogger           = impl;
        sStatusNotifier   = impl;
        sExportDispatcher = impl;
    }

    // -------------------------------------------------------------------------
    // Accessors (called by FlightAware analyzer code)
    // -------------------------------------------------------------------------

    public static boolean isDebug() {
        return sDebug;
    }

    /** Returns the aircraft stall timeout in seconds. */
    public static int getStallTimeoutSec() {
        return sStallTimeoutSec;
    }

    /** Returns current receiver location, or {@code null} if unknown / not registered. */
    @Nullable
    public static GeoPoint getLocation() {
        ILocationProvider p = sLocationProvider;
        return p != null ? p.getLocation() : null;
    }

    @NonNull
    public static IAnalyzerLogger getLogger() {
        IAnalyzerLogger l = sLogger;
        return l != null ? l : NO_OP_LOGGER;
    }

    public static void notifyNewAircraftDecoded() {
        IAnalyzerStatusNotifier n = sStatusNotifier;
        if (n != null) n.onNewAircraftDecoded();
    }

    @NonNull
    public static IAnalyzerStatusNotifier getStatusNotifier() {
        IAnalyzerStatusNotifier n = sStatusNotifier;
        return n != null ? n : NO_OP_NOTIFIER;
    }

    @NonNull
    public static IAnalyzerExportDispatcher getExportDispatcher() {
        IAnalyzerExportDispatcher d = sExportDispatcher;
        return d != null ? d : NO_OP_DISPATCHER;
    }

    // -------------------------------------------------------------------------
    // No-op fallbacks
    // -------------------------------------------------------------------------

    private static final IAnalyzerLogger NO_OP_LOGGER = new IAnalyzerLogger() {
        @Override public void d(String msg) {}
        @Override public void i(String msg) {}
        @Override public void e(String msg) {}
        @Override public void e(String msg, @Nullable Throwable t) {}
    };

    private static final IAnalyzerStatusNotifier NO_OP_NOTIFIER = new IAnalyzerStatusNotifier() {
        @Override public void onNewAircraftDecoded() {}
    };

    private static final IAnalyzerExportDispatcher NO_OP_DISPATCHER = new IAnalyzerExportDispatcher() {
        @Override public void onRawFrameDetected(com.flightaware.android.flightfeeder.analyzers.dump1090.ModeSMessage message) {}
        @Override public void onAircraftUpdated(com.flightaware.android.flightfeeder.analyzers.dump1090.ModeSMessage message, Aircraft aircraft) {}
        @Override public void onReadyAircraftUpdated(com.flightaware.android.flightfeeder.analyzers.dump1090.ModeSMessage message, Aircraft aircraft, long uptimeMs) {}
    };
}
