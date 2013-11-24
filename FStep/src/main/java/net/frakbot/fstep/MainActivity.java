package net.frakbot.fstep;

import android.app.Activity;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                .add(R.id.container, new PlaceholderFragment())
                .commit();
        }
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

        // Start the service (idempotent)
        startService(new Intent(this, FStepService.class));
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment implements FStepService.StepCountListener {

        private final PlaceholderFragment.FSSConnection mFSSConnection;

        private TextView mTxtStepsCount, mTxtStepsDetectedCount, mTxtStepEvent;
        private FStepService mFSService;
        private ViewPropertyAnimator mStepEventAnim;

        public PlaceholderFragment() {
            mFSSConnection = new FSSConnection();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {

            View rootView = inflater.inflate(R.layout.fragment_main, container, false);

            mTxtStepsCount = (TextView) rootView.findViewById(R.id.txt_steps_count);
            mTxtStepEvent = (TextView) rootView.findViewById(R.id.txt_step_event);
            mTxtStepsDetectedCount = (TextView) rootView.findViewById(R.id.txt_steps_detected_count);

            mTxtStepEvent.setAlpha(0f);

            return rootView;
        }

        @Override
        public void onResume() {
            super.onResume();
            final Activity activity = getActivity();
            if (activity != null) {
                activity.bindService(new Intent(activity, FStepService.class),
                                     mFSSConnection, 0);
            }
        }

        @Override
        public void onPause() {
            super.onPause();

            if (mFSService != null) {
                mFSService.unregisterListener(PlaceholderFragment.this);
            }

            final Activity activity = getActivity();
            if (activity != null) {
                activity.unbindService(mFSSConnection);
            }
        }

        @Override
        public void onStepDataUpdate(int stepCount) {
            mTxtStepsCount.setText(String.valueOf(stepCount));
        }

        @Override
        public void onStep(int count) {
            if (mStepEventAnim != null) {
                mStepEventAnim.cancel();
            }
            mTxtStepsDetectedCount.setText(String.valueOf(count));
            mTxtStepEvent.setAlpha(1f);
            mStepEventAnim = mTxtStepEvent.animate().setDuration(500).alpha(0f);
        }

        private class FSSConnection implements ServiceConnection {

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d("MainActivity", "Connected to the FStepService");
                mFSService = ((FStepService.LocalBinder) service).getService();
                mFSService.registerListener(PlaceholderFragment.this);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mFSService = null;
                Log.d("MainActivity", "The FStepService has disconnected");
            }
        }
    }

}
