/*
 * Originally from FlightAware ADSB Flight Scanner for Android
 * Copyright (C) FlightAware, LLC
 * Licensed under the GNU General Public License, version 2
 * or (at your option) any later version.
 *
 * This file has been modified by ebctech (https://github.com/ebc81), 2024-2025.
 * Modifications:
 *   - Removed USB/RTL-SDR integration (UsbManager, UsbDevice, RtlTcp, RtlTcpProcessListener)
 *   - Replaced start(UsbManager, UsbDevice) with startEbc()
 *   - Replaced stop() with stopEbc()
 *   - Removed sListener callback system
 *   - Removed unused MySettings import (GPL-2.0-or-later boundary refactoring, 2025)
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Upstream licensing:
 * https://github.com/ebc81/dump1090andro-gpl-sources/blob/main/LICENSE.md
 */
package com.flightaware.android.flightfeeder.analyzers.dump1090;

//import com.flightaware.android.flightfeeder.BuildConfig;
import com.flightaware.android.flightfeeder.analyzers.Analyzer;

//import marto.rtl_tcp_andro.core.RtlTcp;
//import marto.rtl_tcp_andro.core.RtlTcp.RtlTcpProcessListener;

public class Dump1090 extends Analyzer {

	static {
		Decoder.init(Decoder.CorrectionLevel.ONE_BIT);
	}

	private static Thread sComputeThread;
	private static Thread sDetectThread;

	public static volatile boolean sExit;

	public synchronized void startEbc()
	{
		RtlSdrDataQueue.clear();
		MagnitudeVectorQueue.clear();
		ModeSMessageQueue.clear();
		sExit = false;

		// from settings
		//if ( MySettings.getInstance().export_avr())
		//	AvrFormatExporter.start();
		//if ( MySettings.getInstance().export_beast())
		//	BeastFormatExporter.start();
		//if ( MySettings.getInstance().export_basestation())
		//	BeastFormatExporter.start();

		// Start threads in this order so that each consumer thread is
		// running before its upstream producer
		if (sDecodeThread == null) {
			sDecodeThread = new DecodeFramesThread();
			sDecodeThread.start();
		}

		if (sDetectThread == null) {
			sDetectThread = new DetectModeSThread();
			sDetectThread.start();
		}

		if (sComputeThread == null) {
			sComputeThread = new ComputeMagnitudeVectorThread();
			sComputeThread.start();
		}
		/*
		if (sReadThread == null) {
			sReadThread = new GetRtlSdrDataThread();
			sReadThread.start();
		}*/

	}

	public synchronized void stopEbc()
	{
		sExit = true;
		// Interrupt all workers before joining. Keeping sExit true until every
		// worker has exited prevents a rapid restart from reviving an old loop.
		/*
		if (sReadThread != null) {
			sReadThread.interrupt();
			sReadThread = null;
		}*/

		Thread computeThread = sComputeThread;
		Thread detectThread = sDetectThread;
		Thread decodeThread = sDecodeThread;
		if (computeThread != null) computeThread.interrupt();
		if (detectThread != null) detectThread.interrupt();
		if (decodeThread != null) decodeThread.interrupt();
		joinWorker(computeThread);
		joinWorker(detectThread);
		joinWorker(decodeThread);
		sComputeThread = null;
		sDetectThread = null;
		sDecodeThread = null;

		RtlSdrDataQueue.clear();
		MagnitudeVectorQueue.clear();
		ModeSMessageQueue.clear();

		//AvrFormatExporter.stop();
		//BeastFormatExporter.stop();

	}

	private static void joinWorker(Thread worker) {
		if (worker == null || worker == Thread.currentThread()) return;
		try {
			worker.join(2000);
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		}
	}

	public static boolean isRunning() {
		return !sExit && sComputeThread != null && sComputeThread.isAlive()
				&& sDetectThread != null && sDetectThread.isAlive()
				&& sDecodeThread != null && sDecodeThread.isAlive();
	}


	/* ebc
	private static RtlTcpProcessListener sListener = new RtlTcpProcessListener() {

		@Override
		public void onProcessStarted() {
			// Start threads in this order so that each consumer thread is
			// running before its upstream producer
			if (sDecodeThread == null) {
				sDecodeThread = new DecodeFramesThread();
				sDecodeThread.start();
			}

			if (sDetectThread == null) {
				sDetectThread = new DetectModeSThread();
				sDetectThread.start();
			}

			if (sComputeThread == null) {
				sComputeThread = new ComputeMagnitudeVectorThread();
				sComputeThread.start();
			}

			if (sReadThread == null) {
				sReadThread = new GetRtlSdrDataThread();
				sReadThread.start();
			}
		}

		@Override
		public void onProcessStdOutWrite(String line) {
			//if (BuildConfig.DEBUG)
			//	System.out.println(line);
		}

		@Override
		public void onProcessStopped(int exitCode, Exception e) {
			if (sReadThread != null) {
				sReadThread.interrupt();
				sReadThread = null;
			}

			if (sComputeThread != null) {
				sComputeThread.interrupt();
				sComputeThread = null;
			}

			if (sDetectThread != null) {
				sDetectThread.interrupt();
				sDetectThread = null;
			}

			if (sDecodeThread != null) {
				sDecodeThread.interrupt();
				sDecodeThread = null;
			}
		}
	};*/
/*
	public static void start(UsbManager usbManager, UsbDevice usbDevice)
			throws Exception {
		UsbDeviceConnection connection = usbManager.openDevice(usbDevice);

		int fileDescriptor = connection.getFileDescriptor();
		String deviceName = getDeviceName(usbDevice.getDeviceName());

		if (fileDescriptor == -1 || TextUtils.isEmpty(deviceName))
			throw new RuntimeException(
					"USB file descriptor or device name is invalid");

		sExit = false;

		//RtlTcp.start("-f 1090e6 -s 2.4e6", fileDescriptor, deviceName,
		//		sListener);
	}

	public static void stop() {
		//RtlTcp.stop();

		sExit = true;
	}
	*/

}
