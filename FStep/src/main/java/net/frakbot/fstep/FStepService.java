package net.frakbot.fstep;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

    public static final String ACTION_FLUSH_SENSOR = "net.frakbot.FStepService.FlushSensorData";
    private SensorManager mSensorManager;
    private Sensor mStepCounter, mStepDetector;
    private Handler mUiHandler;
    private ArrayList<StepCountListener> mListeners = new ArrayList<>();
    private int mLastCount, mInitialCount;
    private boolean mInitialCountInitialized;
    private int mLastDetectorCount;

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();
    private PendingIntent mSensorUpdatePIntent;
    private IntentFilter mIntentFilter;
    private BroadcastReceiver mUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_FLUSH_SENSOR.equals(intent.getAction())) {
                Log.i("FStepService", "Flushing the sensor's data");
                mSensorManager.flush(FStepService.this);
            }
        }
    };

    @Override
    public void onCreate() {
        Log.v("FStepService", "Creating the service");
        super.onCreate();

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(ACTION_FLUSH_SENSOR);
        registerReceiver(mUpdateReceiver, mIntentFilter);

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mStepCounter = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        mStepDetector = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        mUiHandler = new Handler(Looper.getMainLooper());

        mSensorUpdatePIntent = PendingIntent.getService(this, 1, new Intent(ACTION_FLUSH_SENSOR), 0);

        // Batching for the step counter doesn't make sense (the buffer holds
        // just one step counter event anyway, as it's not a continuous event)
        mSensorManager.registerListener(this, mStepCounter, SensorManager.SENSOR_DELAY_NORMAL);

        // We do instead use batching for the step detector sensor
        final int reportInterval = calcSensorReportInterval(mStepDetector);
        mSensorManager.registerListener(this, mStepDetector, SensorManager.SENSOR_DELAY_NORMAL,
                                        reportInterval * 1000 /*  micro seconds */);

        if (reportInterval > 0) {
            Log.i("FStepService", "Setting up batched data retrieval every " + reportInterval + " ms");
            setupSensorUpdateAlarm(reportInterval);
        }
        else {
            Log.w("FStepService", "This device doesn't support events batching!");
        }
    }

    /**
     * Calculates the maximum sensor report interval, based on the
     * hardware sensor events buffer size, to avoid dropping steps.
     *
     * @param stepCounter The Step Counter sensor
     *
     * @return Returns the optimal update interval, in milliseconds
     */
    private static int calcSensorReportInterval(Sensor stepCounter) {
        // We assume that, normally, a person won't do more than
        // two steps in a second (worst case: running)
        final int fifoSize = stepCounter.getFifoReservedEventCount();
        if (fifoSize > 1) {
            return (fifoSize / 2) * 1000;
        }

        // In this case, the device seems not to have an HW-backed
        // sensor events buffer. We're assuming that there's no
        // batching going on, so we don't really need the alarms.
        return 0;
    }

    /**
     * Sets up a wakelock-based alarm that allows this service
     * to retrieve sensor events before they're dropped out of
     * the FIFO buffer.
     */
    private void setupSensorUpdateAlarm(int interval) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + interval,
                                  interval, mSensorUpdatePIntent);
    }

    public IBinder onBind(Intent intent) {
        Log.v("FStepService", "Binding the service");
        return mBinder;
    }

    @Override
    public void onDestroy() {
        Log.v("FStepService", "Destroying the service");
        super.onDestroy();

        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmManager.cancel(mSensorUpdatePIntent);
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        final int type = event.sensor.getType();
        if (type == Sensor.TYPE_STEP_COUNTER) {
            Log.v("FStepService", "New step counter event. Value: " + (int)event.values[0]);

            if (!mInitialCountInitialized) {
                Log.i("FStepService", "Initializing initial steps count: " + (int) event.values[0]);
                mInitialCount = (int) event.values[0];
                mInitialCountInitialized = true;
            }

            mLastCount = (int) event.values[0] - mInitialCount;

            postSensorChange(mLastCount, Sensor.TYPE_STEP_COUNTER);
        }
        else if (type == Sensor.TYPE_STEP_DETECTOR) {
            mLastDetectorCount++;
            Log.v("FStepService", "New step detector event. Updated count: " + mLastDetectorCount);

            postSensorChange((int) mLastDetectorCount, Sensor.TYPE_STEP_DETECTOR);
        }
    }

    /**
     * Posts a step sensor event, both for the counter and the detector,
     * to the registered listeners.
     *
     * @param value The event value, if any
     * @param type  The sensor type
     */
    private void postSensorChange(final int value, final int type) {
        if (Looper.getMainLooper().equals(Looper.myLooper())) {
            // UI thread
            for (StepCountListener l : mListeners) {
                if (type == Sensor.TYPE_STEP_COUNTER) {
                    l.onStepDataUpdate(value);
                }
                else if (type == Sensor.TYPE_STEP_DETECTOR) {
                    l.onStep(value);
                }
            }
        }
        else {
            // Non-UI thread
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (StepCountListener l : mListeners) {
                        if (type == Sensor.TYPE_STEP_COUNTER) {
                            l.onStepDataUpdate(value);
                        }
                        else if (type == Sensor.TYPE_STEP_DETECTOR) {
                            l.onStep(value);
                        }
                    }
                }
            });
        }
    }

    public void registerListener(StepCountListener l) {
        mListeners.add(l);
        Log.v("FStepService", "Feeding last known step count to newly registered listener");
        l.onStepDataUpdate(mLastCount);
        mSensorManager.flush(this);
    }

    public void unregisterListener(StepCountListener l) {
        Log.v("FStepService", "Unregistering a listener");
        mListeners.remove(l);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.v("FStepService", "Sensor accuracy changed. New value: " + accuracy);
    }

    public class LocalBinder extends Binder {

        public FStepService getService() {
            // Return this instance of LocalService so clients can call public methods
            return FStepService.this;
        }
    }

    public interface StepCountListener {

        void onStepDataUpdate(int stepCount);

        void onStep(int count);

    }
}
