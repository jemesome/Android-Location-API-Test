package com.test.myapplication;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.internal.ca;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;

import android.content.IntentSender;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
//import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Date;

public class MainActivity extends
        FragmentActivity implements
        GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener,
        LocationListener{

    //GLOBAL CONSTANT
    private final static int
            CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;

    // A request to connect to Location Services
    private LocationRequest mLocationRequest;

    // Stores the current instantiation of the location client in this object
    private LocationClient mLocationClient;

    // Handles to UI widgets
    private TextView mLatLng;
    private TextView mAddress;
    private ProgressBar mActivityIndicator;
    private TextView mConnectionState;
    private TextView mConnectionStatus;
    private Location previousLocation;
    private String address;
    private BufferedWriter output;
    File outputFile;


    // Handle to SharedPreferences for this app
    SharedPreferences mPrefs;

    // Handle to a SharedPreferences editor
    SharedPreferences.Editor mEditor;

    /*
     * Note if updates have been turned on. Starts out as "false"; is set to "true" in the
     * method handleRequestSuccess of LocationUpdateReceiver.
     *
     */
    boolean mUpdatesRequested = false;
    /*
     * Initialize the Activity
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mLocationClient = new LocationClient(this, this, this);
        // Get handles to the UI view objects
        mLatLng = (TextView) findViewById(R.id.lat_lng);
        mAddress = (TextView) findViewById(R.id.address);
        mActivityIndicator = (ProgressBar) findViewById(R.id.address_progress);
        mConnectionState = (TextView) findViewById(R.id.text_connection_state);
        mConnectionStatus = (TextView) findViewById(R.id.text_connection_status);

        // Create a new global location parameters object
        mLocationRequest = LocationRequest.create();



        /*
         * Set the update interval
         */
        mLocationRequest.setInterval(LocationUtils.UPDATE_INTERVAL_IN_MILLISECONDS);

        // Use high accuracy
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        // Set the interval ceiling to one minute
        //If your app accesses the network or does other long-running work after receiving
        // a location update, adjust the fastest interval to a slower value.
        // This prevents your app from receiving updates it can't use.
        // Once the long-running work is done, set the fastest interval back to a fast value.
        //http://developer.android.com/training/location/receive-location-updates.html
        mLocationRequest.setFastestInterval(LocationUtils.FAST_INTERVAL_CEILING_IN_MILLISECONDS);

        // Note that location updates are off until the user turns them on
        mUpdatesRequested = false;

        // Open Shared Preferences
        mPrefs = getSharedPreferences(LocationUtils.SHARED_PREFERENCES, Context.MODE_PRIVATE);

        // Get an editor
        mEditor = mPrefs.edit();

        /*
         * Create a new location client, using the enclosing class to
         * handle callbacks.
         */
        mLocationClient = new LocationClient(this, this, this);
        try{
            //outputFile.delete();
            outputFile= new File(this.getFilesDir(),"location.txt");
        }
        catch(Exception e){
            Log.d("outputerrpr",e.getMessage());
        }

    }
    /*
    * Called when the Activity is no longer visible at all.
    * Stop updates and disconnect.
    */
    @Override
    public void onStop() {

        // If the client is connected
        if (mLocationClient.isConnected()) {
            stopPeriodicUpdates();
        }

        // After disconnect() is called, the client is considered "dead".
        mLocationClient.disconnect();

        super.onStop();
    }

    /*
     * Called when the Activity is going into the background.
     * Parts of the UI may be visible, but the Activity is inactive.
     */
    @Override
    public void onPause() {

        // Save the current setting for updates
        mEditor.putBoolean(LocationUtils.KEY_UPDATES_REQUESTED, mUpdatesRequested);
        mEditor.commit();

        super.onPause();
    }

    /*
     * Called when the Activity is restarted, even before it becomes visible.
     */
    @Override
    public void onStart() {

        super.onStart();

        /*
         * Connect the client. Don't re-start any requests here;
         * instead, wait for onResume()
         */
        mLocationClient.connect();

    }
    /*
     * Called when the system detects that this Activity is now visible.
     */
    @Override
    public void onResume() {
        super.onResume();

        // If the app already has a setting for getting location updates, get it
        if (mPrefs.contains(LocationUtils.KEY_UPDATES_REQUESTED)) {
            mUpdatesRequested = mPrefs.getBoolean(LocationUtils.KEY_UPDATES_REQUESTED, false);

            // Otherwise, turn off location updates until requested
        } else {
            mEditor.putBoolean(LocationUtils.KEY_UPDATES_REQUESTED, false);
            mEditor.commit();
        }

    }


    /*
    * Handle results returned to this Activity by other Activities.
    * Triggerred with startActivityForResult().
    * This is to handle results returned by Google Play Services
    * In particular, the method onConnectionFailed() in
    * LocationUpdateRemover and LocationUpdateRequester may call startResolutionForResult() to
    * start an Activity that handles Google Play services problems. The result of this
    * call returns here, to onActivityResult.
    */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {

        // Choose what to do based on the request code
        switch (requestCode) {

            // If the request code matches the code sent in onConnectionFailed
            case LocationUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST :

                switch (resultCode) {
                    // If Google Play services resolved the problem
                    case Activity.RESULT_OK:

                        // Log the result
                        Log.d(LocationUtils.APPTAG, getString(R.string.resolved));

                        // Display the result
                        mConnectionState.setText(R.string.connected);
                        mConnectionStatus.setText(R.string.resolved);
                        break;

                    // If any other result was returned by Google Play services
                    default:
                        // Log the result
                        Log.d(LocationUtils.APPTAG, getString(R.string.no_resolution));

                        // Display the result
                        mConnectionState.setText(R.string.disconnected);
                        mConnectionStatus.setText(R.string.no_resolution);

                        break;
                }

                // If any other request code was received
            default:
                // Report that this Activity received an unknown requestCode
                Log.d(LocationUtils.APPTAG,
                        getString(R.string.unknown_activity_request_code, requestCode));

                break;
        }
    }

    /**
     * Verify that Google Play services is available before making a request.
     *
     * @return true if Google Play services is available, otherwise false
     */
    private boolean servicesConnected() {

        // Check that Google Play services is available
        int resultCode =
                GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);

        // If Google Play services is available
        if (ConnectionResult.SUCCESS == resultCode) {
            // In debug mode, log the status
            Log.d(LocationUtils.APPTAG, getString(R.string.play_services_available));

            // Continue
            return true;
            // Google Play services was not available for some reason
        } else {
            // Display an error dialog
            Dialog dialog = GooglePlayServicesUtil.getErrorDialog(resultCode, this, 0);
            if (dialog != null) {
                ErrorDialogFragment errorFragment = new ErrorDialogFragment();
                errorFragment.setDialog(dialog);
                errorFragment.show(getSupportFragmentManager(), LocationUtils.APPTAG);
            }
            return false;
        }
    }

    /*
 * Called by Location Services when the request to connect the
 * client finishes successfully. At this point, you can
 * request the current location or start periodic updates
 */
    @Override
    public void onConnected(Bundle bundle) {
        mConnectionStatus.setText(R.string.connected);

        if (mUpdatesRequested) {
            startPeriodicUpdates();
        }
    }

    /**
     * In response to a request to start updates, send a request
     * to Location Services
     */
    private void startPeriodicUpdates() {

        mLocationClient.requestLocationUpdates(mLocationRequest, this);
        mConnectionState.setText(R.string.location_requested);
    }

    /*
     * Called by Location Services if the connection to the
     * location client drops because of an error.
     */
    @Override
    public void onDisconnected() {
        mConnectionStatus.setText(R.string.disconnected);
    }

    /*
     * Called by Location Services if the attempt to
     * Location Services fails.
     */
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

        /*
         * Google Play services can resolve some errors it detects.
         * If the error has a resolution, try sending an Intent to
         * start a Google Play services activity that can resolve
         * error.
         */
        if (connectionResult.hasResolution()) {
            try {

                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(
                        this,
                        LocationUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST);

                /*
                * Thrown if Google Play services canceled the original
                * PendingIntent
                */

            } catch (IntentSender.SendIntentException e) {

                // Log the error
                e.printStackTrace();
            }
        } else {

            // If no resolution is available, display a dialog to the user with the error.
            showErrorDialog(connectionResult.getErrorCode());
        }
    }


    /**
     * Show a dialog returned by Google Play services for the
     * connection error code
     *
     * @param errorCode An error code returned from onConnectionFailed
     */
    private void showErrorDialog(int errorCode) {

        // Get the error dialog from Google Play services
        Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(
                errorCode,
                this,
                LocationUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST);

        // If Google Play services can provide an error dialog
        if (errorDialog != null) {

            // Create a new DialogFragment in which to show the error dialog
            ErrorDialogFragment errorFragment = new ErrorDialogFragment();

            // Set the dialog in the DialogFragment
            errorFragment.setDialog(errorDialog);

            // Show the error dialog in the DialogFragment
            errorFragment.show(getSupportFragmentManager(), LocationUtils.APPTAG);
        }
    }

    /**
     * location update callback. If you want continious location update, need to
     * implement LocationListener. LocationListener will call this method and provide
     * location update perdiocically.
     * Report location updates to the UI.
     *
     * @param location The updated location.
     */
    @Override
    public void onLocationChanged(Location location) {

        // Report to the UI that the location was updated

        mLocationRequest.setFastestInterval(100000);
        SimpleDateFormat dateFormat= new SimpleDateFormat("MM/dd/yyyy kk:mm:ss");
        String time = dateFormat.format(Calendar.getInstance().getTime());
        Date now=new Date();


        //if(previousLocation.getLatitude() != location.getLatitude() || previousLocation.getLongitude()!=location.getLongitude()){

            String latLong="";
            latLong = String.format("%s,%s", location.getLatitude() ,location.getLongitude());
            address=this.GetAddressTask(location);

            mConnectionStatus.setText(time + "  -  " + R.string.location_updated);
            int i=0;
            try {
                output = new BufferedWriter(new FileWriter(outputFile,true));
                String outputDetail= String.format("%s\t%s\t%s", time,latLong, address);
                //output= openFileOutput(fileName,Context.MODE_APPEND);
                output.write(outputDetail);
                output.newLine();
                output.flush();
                output.close();
                //mConnectionStatus.setText(++i);
            }catch (Exception e){
                e.printStackTrace();
            }
            previousLocation=location;
        //}

        // In the UI, set the latitude and longitude to the value received
        mLatLng.setText(LocationUtils.getLatLng(this, location));
        mLocationRequest.setFastestInterval(LocationUtils.FAST_INTERVAL_CEILING_IN_MILLISECONDS);
    }


    /**
     * Define a DialogFragment to display the error dialog generated in
     * showErrorDialog.
     * this is a result of error returned by Google Play Services
     * due to device issue (don't have google play installed)
     * see possible scenario at
     */
    public static class ErrorDialogFragment extends DialogFragment {

        // Global field to contain the error dialog
        private Dialog mDialog;

        /**
         * Default constructor. Sets the dialog field to null
         */
        public ErrorDialogFragment() {
            super();
            mDialog = null;
        }

        /**
         * Set the dialog to display
         *
         * @param dialog An error dialog
         */
        public void setDialog(Dialog dialog) {
            mDialog = dialog;
        }

        /*
         * This method must return a Dialog to the DialogFragment.
         */
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return mDialog;
        }
    }
    /**
     * In response to a request to stop updates, send a request to
     * Location Services
     */
    private void stopPeriodicUpdates() {
        mLocationClient.removeLocationUpdates(this);
        mConnectionState.setText(R.string.location_updates_stopped);
    }

    /**
     * Invoked by the "Get Address" button.
     * Get the address of the current location, using reverse geocoding. This only works if
     * a geocoding service is available.
     *
     * @param //v The view object associated with this method, in this case a Button.
     */
    // For Eclipse with ADT, suppress warnings about Geocoder.isPresent()
    /*@SuppressLint("NewApi")
    public void getAddress(Location location) {

        // In Gingerbread and later, use Geocoder.isPresent() to see if a geocoder is available.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD && !Geocoder.isPresent()) {
            // No geocoder is present. Issue an error message
            Toast.makeText(this, R.string.no_geocoder_available, Toast.LENGTH_LONG).show();
            return;
        }

        if (servicesConnected()) {

            // Get the current location
            Location currentLocation = mLocationClient.getLastLocation();

            // Turn the indefinite activity indicator on
            mActivityIndicator.setVisibility(View.VISIBLE);

            // Start the background task
            //(new MainActivity.GetAddressTask(this)).execute(currentLocation);
            (new MainActivity.GetAddressTask(this)).execute(currentLocation);
        }
    }*/
    /**
     * An AsyncTask that calls getFromLocation() in the background.
     * The class uses the following generic types:
     * Location - A {@link android.location.Location} object containing the current location,
     *            passed as the input parameter to doInBackground()
     * Void     - indicates that progress units are not used by this subclass
     * String   - An address passed to onPostExecute()
     */
    //protected class GetAddressTask extends AsyncTask<Location, Void, String> {
    private String GetAddressTask(Location loc) {

        // Store the context passed to the AsyncTask when the system instantiates it.
        //Context localContext;

        // Constructor called by the system to instantiate the task
        /*public GetAddressTask(Context context) {

            // Required by the semantics of AsyncTask
            super();

            // Set a Context for the background task
            localContext = context;
        }
        /**
         * Get a geocoding service instance, pass latitude and longitude to it, format the returned
         * address, and return the address to the UI thread.
         */
        /*@Override
        protected String doInBackground(Location... params) {
            /*
             * Get a new geocoding service instance, set for localized addresses. This example uses
             * android.location.Geocoder, but other geocoders that conform to address standards
             * can also be used.
             */
            //Geocoder geocoder = new Geocoder(localContext, Locale.getDefault());
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());

            // Get the current location from the input parameter list
            //Location location = params[0];
            Location location = loc;

            // Create a list to contain the result address
            List <Address> addresses = null;

            // Try to get an address for the current location. Catch IO or network problems.
            try {

                /*
                 * Call the synchronous getFromLocation() method with the latitude and
                 * longitude of the current location. Return at most 1 address.
                 */
                addresses = geocoder.getFromLocation(location.getLatitude(),
                        location.getLongitude(), 1
                );

                // Catch network or other I/O problems.
            } catch (IOException exception1) {

                // Log an error and return an error message
                Log.e(LocationUtils.APPTAG, getString(R.string.IO_Exception_getFromLocation));

                // print the stack trace
                exception1.printStackTrace();

                // Return an error message
                //return (getString(R.string.IO_Exception_getFromLocation)+"NA");
                return ("N/A");

                // Catch incorrect latitude or longitude values
            } catch (IllegalArgumentException exception2) {

                // Construct a message containing the invalid arguments
                String errorString = getString(
                        R.string.illegal_argument_exception,
                        location.getLatitude(),
                        location.getLongitude()
                );
                // Log the error and print the stack trace
                Log.e(LocationUtils.APPTAG, errorString);
                exception2.printStackTrace();

                //
                return errorString;
            }
            // If the reverse geocode returned an address
            if (addresses != null && addresses.size() > 0) {

                // Get the first address
                Address address = addresses.get(0);

                // Format the first line of address
                String addressText = getString(R.string.address_output_string,

                        // If there's a street address, add it
                        address.getMaxAddressLineIndex() > 0 ?
                                address.getAddressLine(0) : "",

                        // Locality is usually a city
                        address.getLocality(),

                        // The country of the address
                        address.getCountryName()
                );

                // Return the text
                return addressText;

                // If there aren't any addresses, post a message
            } else {
                return getString(R.string.no_address_found);
            }
        //}

        /**
         * A method that's called once doInBackground() completes. Set the text of the
         * UI element that displays the address. This method runs on the UI thread.
         */
        /*@Override
        protected void onPostExecute(String returnedAddress) {

            // Turn off the progress bar
            mActivityIndicator.setVisibility(View.GONE);

            // Set the address in the UI
            address = returnedAddress;
        }*/
    }

    /**
     * Invoked by the "Stop Updates" button
     * Sends a request to remove location updates
     * request them.
     *
     * @param v The view object associated with this method, in this case a Button.
     */
    public void stopUpdates(View v) {
        mUpdatesRequested = false;
        if (servicesConnected()) {

            stopPeriodicUpdates();
            EditText txtResult = (EditText) findViewById(R.id.txtEdit);
            String newText="";
            try{
                BufferedReader results= new BufferedReader(new FileReader(outputFile));
                String lines;
                lines = results.readLine();
                while(lines!=null){
                    newText+=(txtResult.getText()+lines+"\n");
                    lines=results.readLine();
                }
            }catch(Exception e){
                String a;

            }
            txtResult.setText(newText);

            Intent email = new Intent(Intent.ACTION_SEND);
            email.setType("txt/rtf");
            email.putExtra(Intent.EXTRA_EMAIL,"heisbudi@umich.edu");
            email.putExtra(Intent.EXTRA_SUBJECT,"GPS LOCATION");
            email.putExtra(Intent.EXTRA_TEXT,newText);
            //email.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(outputFile));
            startActivity(Intent.createChooser(email,"sending GPS Location log.."));

            outputFile.delete();
        }
    }

    /**
     * Invoked by the "Start Updates" button
     * Sends a request to start location updates
     *
     * @param v The view object associated with this method, in this case a Button.
     */
    public void startUpdates(View v) {
        mUpdatesRequested = true;

        if (servicesConnected()) {
            startPeriodicUpdates();
        }
    }


    /**
     * Invoked by the "Get Location" button.
     *
     * Calls getLastLocation() to get the current location
     *
     * @param v The view object associated with this method, in this case a Button.
     */
    public void getLocation(View v) {

        // If Google Play Services is available
        if (servicesConnected()) {
            mUpdatesRequested = true;
            startPeriodicUpdates();
            //mLocationClient.requestLocationUpdates(mLocationRequest, this);

            // Get the current location
            Location currentLocation = mLocationClient.getLastLocation();
            previousLocation=currentLocation;

            if (previousLocation==currentLocation){
                address= GetAddressTask(currentLocation);
            }

            // Display the current location in the UI
            //mLatLng.setText(LocationUtils.getLatLng(this, currentLocation) + address);

        }
    }

}
