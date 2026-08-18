/*
 * Originally from FlightAware ADSB Flight Scanner for Android
 * Copyright (C) FlightAware, LLC
 * Licensed under the GNU General Public License, version 2 (GPLv2).
 *
 * This file has been modified by ebctech (https://github.com/ebc81), 2024-2025.
 * Modifications:
 *   - Removed FlightAware App, MainActivity, BuildConfig imports
 *   - Replaced eu.ebctech export queue integration with AnalyzerBridge.getExportDispatcher()
 *   - Replaced GlobalServiceStatus notification with AnalyzerBridge.getStatusNotifier()
 *   - Replaced LoggerEbc with AnalyzerBridge.getLogger()
 *   - Relocated MovingAverage import to analyzers.util sub-package
 *
 * Source of modified GPLv2 components:
 * https://github.com/ebc81/dump1090andro-gpl-sources
 */
package com.flightaware.android.flightfeeder.analyzers.dump1090;

import android.os.SystemClock;


import com.flightaware.android.flightfeeder.analyzers.Aircraft;
import com.flightaware.android.flightfeeder.analyzers.Analyzer;
import com.flightaware.android.flightfeeder.analyzers.AnalyzerBridge;
import com.flightaware.android.flightfeeder.analyzers.util.MovingAverage;

public class DecodeFramesThread extends Thread {

	public DecodeFramesThread() {
		setName("DecodeAdsbFramesThread");
	}

	@Override
	public void run() {
		AnalyzerBridge.getLogger().i("DecodeFramesThread: Started decoding mode-s messages.");


		// Start a child thread to compute the frame rate at 1 Hertz
		Thread rateThread = new Thread() {
			public void run() {
				while (!Dump1090.sExit) {
					MovingAverage.addSample(Analyzer.sFrameCount);
					Analyzer.sFrameCount = 0;

					SystemClock.sleep(1000);
				}
			}
		};
		rateThread.setName("MovingAverage");
		rateThread.start();

		//Intent intent = new Intent(MainActivity.ACTION_UPDATE_RX);

		while (!Dump1090.sExit) {
			ModeSMessage message = ModeSMessageQueue.take();

			if (message == null)
				continue;

			Aircraft aircraft = Decoder.decodeModeS(message);

			if (aircraft == null)
				continue;

			// Export only after CRC/parity validation and supported-DF decoding.
			// This keeps detector false positives out of the public Beast stream.
			AnalyzerBridge.getExportDispatcher().onRawFrameDetected(message);

			Analyzer.sFrameCount++;

			//LoggerEbc.i("######################################## Aircarft "+aircraft.getIcao());

			//App.sBroadcastManager.sendBroadcast(intent);
			AnalyzerBridge.getStatusNotifier().onNewAircraftDecoded();





			long now = SystemClock.uptimeMillis();
			AnalyzerBridge.getExportDispatcher().onAircraftUpdated(message, aircraft);

			if (!aircraft.isReady(now))
				continue;

			// Maybe to the file export here to reduce the data
			AnalyzerBridge.getExportDispatcher().onReadyAircraftUpdated(message, aircraft, now);

			Analyzer.computeRange(aircraft);

			int altitude = aircraft.getAltitude();
			Integer vertRate = aircraft.getVerticalRate();
			Integer headingDelta = aircraft.getHeadingDelta();
		}

		rateThread.interrupt();
		try {
			rateThread.join(1500);
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		}
		AnalyzerBridge.getLogger().i("DecodeFramesThread: Stopped decoding mode-s messages.");

	}
}
