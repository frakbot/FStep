package net.frakbot.fstep;

import android.app.Activity;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.*;
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment implements FStepService.StepCountListener {

        private final PlaceholderFragment.FSSConnection mFSSConnection;

        private TextView mStepsCountText;
        private FStepService mFSService;

        public PlaceholderFragment() {
            mFSSConnection = new FSSConnection();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {

            View rootView = inflater.inflate(R.layout.fragment_main, container, false);

            mStepsCountText = (TextView) rootView.findViewById(R.id.txt_steps_count);
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

            mFSService.unregisterListener(PlaceholderFragment.this);

            final Activity activity = getActivity();
            if (activity != null) {
                activity.unbindService(mFSSConnection);
            }
        }

        @Override
        public void onStepDataUpdate(int stepCount) {
            mStepsCountText.setText(String.valueOf(stepCount));
        }

        private class FSSConnection implements ServiceConnection {

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d("MainActivity", "Connected to the FStepService");
                mFSService = ((FStepService.LocalBinder)service).getService();
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
