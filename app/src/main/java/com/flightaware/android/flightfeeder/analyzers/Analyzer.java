/*
 * Originally from FlightAware ADSB Flight Scanner for Android
 * Copyright (C) FlightAware, LLC
 * Licensed under the GNU General Public License, version 2
 * or (at your option) any later version.
 *
 * This file has been modified by ebctech (https://github.com/ebc81), 2024-2025.
 * Modifications:
 *   - Removed getDeviceName() utility method (USB path logic no longer needed)
 *   - Removed sRange, sReadThread fields
 *   - Stubbed computeRange() to no-op (range computation moved to higher-level layer)
 *   - GPL-2.0-or-later boundary refactoring (2025): removed eu.ebctech back-import (MyService was
 *     only referenced in the now-stubbed computeRange body)
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Upstream licensing:
 * https://github.com/ebc81/dump1090andro-gpl-sources/blob/main/LICENSE.md
 */
package com.flightaware.android.flightfeeder.analyzers;
public class Analyzer {

	//private static final String DEFAULT_USBFS_PATH = "/dev/bus/usb";

	protected static Thread sDecodeThread;
	public static volatile int sFrameCount;
	//public static volatile float sRange;
	//protected static Thread sReadThread;

	/*
	protected static String getDeviceName(String deviceName) {
		deviceName = deviceName.trim();

		if (TextUtils.isEmpty(deviceName))
			return DEFAULT_USBFS_PATH;

		final String[] paths = deviceName.split("/");

		final StringBuilder sb = new StringBuilder();

		for (int i = 0; i < paths.length - 2; i++)
			if (i == 0)
				sb.append(paths[i]);
			else
				sb.append("/" + paths[i]);

		final String stripped_name = sb.toString().trim();

		if (stripped_name.isEmpty())
			return DEFAULT_USBFS_PATH;
		else
			return stripped_name;
	}*/
	public static void computeRange(final Aircraft aircraft) {
		return; //ebc

		/*
		if (MyService.sLocation == null)
			return;

		float[] results = new float[1];
		Location.distanceBetween(MyService.sLocation.latitude,
				MyService.sLocation.longitude, aircraft.getLatitude(),
				aircraft.getLongitude(), results);

		// convert to nautical miles
		float tempRange = results[0] / 1852;
		if (tempRange > sRange && tempRange < 300)
			sRange = tempRange;*/
	}
}
