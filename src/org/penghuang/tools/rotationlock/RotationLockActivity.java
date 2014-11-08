/* vim:set noet ts=4 sts=4:
 *
 * RotationLock - Rotation Lock for Android
 *
 * Copyright (c) 2011 Peng Huang <shawn.p.huang@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package org.penghuang.tools.rotationlock;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Bundle;
import android.provider.Settings.SettingNotFoundException;
import android.widget.Toast;


public class RotationLockActivity extends Activity {
	private static final int NOTIFICATION_ID = 8899;

	private static final int RETRY_COUNT = 5;

	private static long mTime = 0;

	private NotificationManager mNotificationManager;

	private int mLocked = 0;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mLocked = isOrientationLocked();

		mNotificationManager =
				(NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		// Listen system settings changes
		getApplicationContext().getContentResolver().registerContentObserver(
				android.provider.Settings.System.CONTENT_URI, true,
				new ContentObserver(null) {
					@Override
					public void onChange(boolean selfChange) {
						super.onChange(selfChange);
						systemSettingsChange();
					}
				});
	}

	@Override
	public void onStart() {
		super.onStart();
		long currentTime = System.currentTimeMillis();
		// Avoid toggling the setting twice in one second.
		if (currentTime > mTime + 1000) {
			mTime = currentTime;
			if (supportAccelerometerRotation())
				toggleOrientationLock();
				//togglePortraitOrientationLock();
			else {
				Toast.makeText(this,
						R.string.accelerometer_rotation_is_not_supported,
						Toast.LENGTH_SHORT).show();
			}
		}
		moveTaskToBack(true);
	}

	@Override
	public void onDestroy() {
		// Cancel the notification if activity is destroyed.
		// mNotificationManager.cancel(NOTIFICATION_ID);

		// Flush all IPC commands to make sure the notification is cancelled
		// before returning.
		Binder.flushPendingCommands();

		super.onDestroy();
	}

	// Check accelerometer.
	private boolean supportAccelerometerRotation() {
		SensorManager sensorManager =
				(SensorManager)getSystemService(SENSOR_SERVICE);
		if (sensorManager == null)
			return false;

		Sensor accelerometer =
				sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		// Does not have accelerometer sensor.
		if (accelerometer == null)
			return false;

		// Try reading system Auto-Rotate setting.
		try {
			android.provider.Settings.System.getInt(getContentResolver(),
					android.provider.Settings.System.ACCELEROMETER_ROTATION);
		} catch (SettingNotFoundException e) {
			return false;
		}

		return true;
	}

	// Show popup message.
	private void showPopupMessage(boolean portlocked, boolean landlocked) {
		int message ;//= locked ?
//				R.string.portrait_orientation_locked :
//				R.string.portrait_orientation_unlocked;
		if(portlocked){
			message = R.string.portrait_orientation_locked;
		}else{
			message = R.string.portrait_orientation_unlocked;
		}
		if(landlocked){
			message = R.string.landscape_orientation_locked;
		}
		Toast.makeText(this, message,
				Toast.LENGTH_SHORT).show();
	}

	// Show notification
	private void showNotification(boolean portlocked, boolean landlocked) {
		int messageId;
		boolean locked=false;
		if(portlocked){
			messageId = R.string.portrait_orientation_locked;
			locked = true;
		}else{
			messageId = R.string.portrait_orientation_unlocked;
			locked = false;
		}
		if(landlocked){
			messageId = R.string.landscape_orientation_locked;
			locked = true;
		}

		Notification notification = new Notification(
				locked ? R.drawable.ic_locked : R.drawable.ic_unlocked,
						getText(messageId),
						System.currentTimeMillis());

		// Notification is on going event, it will not be cleared.
		notification.flags |=
				Notification.FLAG_NO_CLEAR |
				Notification.FLAG_ONGOING_EVENT;

		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, RotationLockActivity.class), 0);
		notification.setLatestEventInfo((Context)this,
				getText(R.string.app_name),
				(CharSequence)getText(messageId),
				contentIntent);
		notification.icon = locked ?
				R.drawable.ic_stat_locked : R.drawable.ic_stat_unlocked;

		mNotificationManager.notify(NOTIFICATION_ID, notification);
	}

	// Toggle system Auto-Rotate setting.
	private boolean togglePortraitOrientationLock() {
		boolean locked = isPortraitOrientationLocked();
		for (int count = 0; count < RETRY_COUNT; count++) {
			setPortraitOrientationLock(!locked);
			if (isPortraitOrientationLocked() != locked) {
				showPopupMessage (!locked,false);
				return true;
			}
		}
		return false;
	}

	private boolean toggleLandscapeOrientationLock() {
		boolean locked = isLandscapeOrientationLocked();
		for (int count = 0; count < RETRY_COUNT; count++) {
			setLandscapeOrientationLock(!locked);
			if (isLandscapeOrientationLocked() != locked) {
				showPopupMessage (false,!locked);
				return true;
			}
		}
		return false;
	}

	// Get system portrait orientation lock.
	private boolean isPortraitOrientationLocked() {
		int value = 1;
		try {
			if(android.provider.Settings.System.getInt(
			getContentResolver(), 
			android.provider.Settings.System.ACCELEROMETER_ROTATION) == 0){
				value = android.provider.Settings.System.getInt(
				getContentResolver(), android.provider.Settings.System.USER_ROTATION);
			}
		} catch (SettingNotFoundException e) {
			value = 1;
		}
		return value == 0;
	}

	private boolean isLandscapeOrientationLocked() {
		int value = 2;
		try {
			if(android.provider.Settings.System.getInt(
			getContentResolver(), 
			android.provider.Settings.System.ACCELEROMETER_ROTATION) == 0){
				value = android.provider.Settings.System.getInt(
						getContentResolver(),
						android.provider.Settings.System.USER_ROTATION);
			}
		} catch (SettingNotFoundException e) {
			value = 2;
		}
		return value == 1;
	}

	// Set system portrait orientation lock.
	private void setPortraitOrientationLock(boolean lock) {
		android.provider.Settings.System.putInt(getContentResolver(),
				android.provider.Settings.System.USER_ROTATION,
				0);
		android.provider.Settings.System.putInt(getContentResolver(),
				android.provider.Settings.System.ACCELEROMETER_ROTATION, 0);
//				lock ? 0 : 1);
	}

	private void setLandscapeOrientationLock(boolean lock) {
		android.provider.Settings.System.putInt(getContentResolver(),
				android.provider.Settings.System.USER_ROTATION,
				1);
		android.provider.Settings.System.putInt(getContentResolver(),
				android.provider.Settings.System.ACCELEROMETER_ROTATION, 0);
//				lock ? 0 : 1);
	}
	public int isOrientationLocked(){
		int value = 0;
		if(isPortraitOrientationLocked()){
			value = 1;
		}
		if(isLandscapeOrientationLocked()){
			value = 2;
		}
		return value;
	}
	public boolean toggleOrientationUnlock(){
		android.provider.Settings.System.putInt(getContentResolver(),
				android.provider.Settings.System.USER_ROTATION,
				0);
		android.provider.Settings.System.putInt(getContentResolver(),
				android.provider.Settings.System.ACCELEROMETER_ROTATION,
				1);
		return false;
	}
	// Count touch duration
	public boolean toggleOrientationLock(){
		boolean value;
		int orientationValue = isOrientationLocked();
		if(orientationValue==0){
			value = togglePortraitOrientationLock();
		}else if(orientationValue==1){
			value = toggleLandscapeOrientationLock();
		}else{
			value = toggleOrientationUnlock();
		}
		return value;
	}

	// Handle system settings change.
	private void systemSettingsChange() {
		boolean portlocked = isPortraitOrientationLocked();
		boolean landlocked = isLandscapeOrientationLocked();
		int locked = isOrientationLocked();
		if (mLocked != locked) {
			mLocked = locked;
			showNotification(portlocked, landlocked);
		}
	}
}
