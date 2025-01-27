/*
 * Copyright (C) 2015 The CyanogenMod Project
 *               2017-2018 The LineageOS Project
 *               2020 Paranoid Android
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package co.aospa.doze;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PickupSensor implements SensorEventListener {

    private static final boolean DEBUG = false;
    private static final String TAG = "PickupSensor";

    private static final int MIN_PULSE_INTERVAL_MS = 2500;
    private static final int WAKELOCK_TIMEOUT_MS = 150;

    private SensorManager mSensorManager;
    private Sensor mSensor;
    private String mSensorName;
    private int mSensorValue, mSensorLowerValue;
    private Context mContext;
    private ExecutorService mExecutorService;
    private PowerManager mPowerManager;
    private WakeLock mWakeLock;

    private long mEntryTimestamp;

    public PickupSensor(Context context) {
        mContext = context;
        mSensorManager = mContext.getSystemService(SensorManager.class);
        mSensorName = SystemProperties.get("ro.sensor.pickup");
        mSensorValue = SystemProperties.getInt("ro.sensor.pickup.value", 1);
        mSensorLowerValue = SystemProperties.getInt("ro.sensor.pickup.lower.value", -1);
        mSensor = DozeUtils.getSensor(mSensorManager, mSensorName);
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        mExecutorService = Executors.newSingleThreadExecutor();
    }

    private Future<?> submit(Runnable runnable) {
        return mExecutorService.submit(runnable);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float ev = event.values[0];
        if (DEBUG) Log.d(TAG, "Got sensor event: " + ev);
        if (ev == mSensorValue) {
            long delta = SystemClock.elapsedRealtime() - mEntryTimestamp;
            if (delta < MIN_PULSE_INTERVAL_MS) {
                if (DEBUG) Log.d(TAG, "Too soon, skipping");
                return;
            }
            mEntryTimestamp = SystemClock.elapsedRealtime();
            if (DozeUtils.isRaiseToWakeEnabled(mContext)) {
                mWakeLock.acquire(WAKELOCK_TIMEOUT_MS);
                mPowerManager.wakeUp(SystemClock.uptimeMillis(),
                    PowerManager.WAKE_REASON_GESTURE, TAG);
            } else {
                DozeUtils.launchDozePulse(mContext);
            }
        } else if (ev == mSensorLowerValue) {
            mPowerManager.goToSleep(SystemClock.uptimeMillis());
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        /* Empty */
    }

    protected void enable() {
        if (DEBUG) Log.d(TAG, "Enabling");
        submit(() -> {
            mEntryTimestamp = SystemClock.elapsedRealtime();
            mSensorManager.registerListener(this, mSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        });
    }

    protected void disable() {
        if (DEBUG) Log.d(TAG, "Disabling");
        submit(() -> {
            mSensorManager.unregisterListener(this, mSensor);
        });
    }
}
