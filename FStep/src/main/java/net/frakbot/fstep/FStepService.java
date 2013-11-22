package net.frakbot.fstep;

import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;

/**
 * The main application service. Does stuff.
 */
public class FStepService extends Service implements SensorEventListener {

    private SensorManager mSensorManager;
    private Sensor mStepCounter;
    private Handler mUiHandler;
    private ArrayList<StepCountListener> mListeners = new ArrayList<>();
    private int mLastCount;

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    @Override
    public void onCreate() {
        super.onCreate();

        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mStepCounter = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        mUiHandler = new Handler(Looper.getMainLooper());

        // TODO: use a worker thread
        mSensorManager.registerListener(this, mStepCounter, SensorManager.SENSOR_DELAY_NORMAL, 120000000);
    }

    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Log.v("FStepService", "Sensor event: " + event);
        postSensorChange((int) event.values[0]);
        mLastCount = (int) event.values[0];
    }

    private void postSensorChange(final int value) {
        if (Looper.getMainLooper().equals(Looper.myLooper())) {
            // UI thread
            for (StepCountListener l : mListeners) {
                l.onStepDataUpdate(value);
            }
        } else {
            // Non-UI thread
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (StepCountListener l : mListeners) {
                        l.onStepDataUpdate(value);
                    }
                }
            });
        }
    }

    public void registerListener(StepCountListener l) {
        mListeners.add(l);
        Log.v("FStepService", "Feeding last known value to newly registered listener");
        l.onStepDataUpdate(mLastCount);
    }

    public void unregisterListener(StepCountListener l) {
        mListeners.remove(l);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public class LocalBinder extends Binder {

        public FStepService getService() {
            // Return this instance of LocalService so clients can call public methods
            return FStepService.this;
        }
    }

    public interface StepCountListener {

        void onStepDataUpdate(int stepCount);

    }
}
