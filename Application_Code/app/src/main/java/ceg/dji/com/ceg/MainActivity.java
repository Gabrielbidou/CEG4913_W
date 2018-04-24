package ceg.dji.com.ceg;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dji.common.flightcontroller.FlightControllerState;
import dji.common.mission.hotpoint.HotpointHeading;
import dji.common.mission.hotpoint.HotpointMission;
import dji.common.mission.hotpoint.HotpointStartPoint;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionDownloadEvent;
import dji.common.mission.waypoint.WaypointMissionExecutionEvent;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionUploadEvent;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.common.error.DJIError;
import dji.sdk.mission.timeline.TimelineElement;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.useraccount.UserAccountManager;

import dji.sdk.mission.hotpoint.HotpointMissionOperator;
import dji.common.model.LocationCoordinate2D;
import dji.sdk.mission.timeline.actions.*;
import dji.sdk.mission.MissionControl;
import 	dji.sdk.mission.hotpoint.*;
import dji.common.mission.hotpoint.*;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import dji.sdk.mission.timeline.TimelineEvent;


public class MainActivity extends FragmentActivity implements View.OnClickListener, GoogleMap.OnMapClickListener,OnMapReadyCallback  {

    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice = null;

    final byte delimiter = 33;
    int readBufferPosition = 0;

    private GoogleMap gMap;

    private Button locate, config, upload, start, stop;

    private FlightController mFlightController;
    private int counterConnection = 0;

    private Marker droneMarker = null;

    private double droneLocationLat, droneLocationLng;

    private MissionControl missionControl;

    // Hotpoint Parameters Variables
    LocationCoordinate2D hotpoint;
    private double altitude ;
    private double radius ;
    private int angVeloc=10;  //in degrees/s
    boolean clockwise=true;
    HotpointStartPoint startPoint;
    HotpointHeading heading;

    // Hotpoint Mission Object
    HotpointMission circleMission;

    // Mission List and Emergency Stop List
    private List<TimelineElement> missionList = new ArrayList<>();
    private List<TimelineElement> stopList = new ArrayList<>();


//////// Activity Initialisation ////////
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.VIBRATE,
                            Manifest.permission.INTERNET, Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.SYSTEM_ALERT_WINDOW,
                            Manifest.permission.READ_PHONE_STATE,
                    }
                    , 1);
        }
        setContentView(R.layout.activity_main);

        IntentFilter filter = new IntentFilter();
        filter.addAction(DroneApp.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);

        initUI();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        initBluetooth();
    }


    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            onProductConnectionChange();
        }
    };


    private void onProductConnectionChange() {
        initFlightController();
        // loginAccount();
    }

    @Override
    protected void onResume() {
        super.onResume();
        initFlightController();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        if (gMap == null) {
            gMap = googleMap;
            setUpMap();
        }

        LatLng ottawa = new LatLng(45.4215, -75.6972);
        gMap.addMarker(new MarkerOptions().position(ottawa).title("Marker in Ottawa"));
        gMap.moveCamera(CameraUpdateFactory.newLatLng(ottawa));

    }

    private void setUpMap() {
        gMap.setOnMapClickListener(this);// add the listener for click for amap object

    }

    @Override
    public void onMapClick(LatLng point) {

    }


    @Override
    protected void onDestroy(){
        unregisterReceiver(mReceiver);
        //  removeListener();
        if(mFlightController!=null) {
            missionControl.removeAllListeners();
        }
        super.onDestroy();
    }


    // Initialise Buttons and their listeners
    private void initUI() {

        locate = (Button) findViewById(R.id.locate);
        config = (Button) findViewById(R.id.configMission);
        upload = (Button) findViewById(R.id.uploadMission);
        start = (Button) findViewById(R.id.start);
        stop = (Button) findViewById(R.id.stop);


        locate.setOnClickListener(this);
        config.setOnClickListener(this);
        upload.setOnClickListener(this);
        start.setOnClickListener(this);
        stop.setOnClickListener(this);


    }
    //////////////// Bluetooth Initialisation ///////////////////
    private void initBluetooth(){

        final Handler handler = new Handler();

        final Button tempButton = (Button) findViewById(R.id.connectButton);

        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        final class workerThread implements Runnable {

            private String btMsg;

            public workerThread(String msg) {
                btMsg = msg;
            }

            public void run()
            {
                sendBtMsg(btMsg);
                while(!Thread.currentThread().isInterrupted())
                {
                    int bytesAvailable;
                    boolean workDone = false;

                    try {
                        final InputStream mmInputStream;
                        mmInputStream = mmSocket.getInputStream();
                        bytesAvailable = mmInputStream.available();
                        if(bytesAvailable > 0)
                        {

                            byte[] packetBytes = new byte[bytesAvailable];
                            byte[] readBuffer = new byte[1024];
                            mmInputStream.read(packetBytes);

                            for(int i=0;i<bytesAvailable;i++)
                            {
                                byte b = packetBytes[i];
                                if(b == delimiter)
                                {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;

                                    //The variable data now contains our full command
                                    handler.post(new Runnable()
                                    {
                                        public void run()
                                        {
                                            firePopUp();
                                            stop();
                                        }
                                    });

                                    workDone = true;
                                    break;


                                }
                                else
                                {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }

                            if (workDone == true){
                                mmSocket.close();
                                break;
                            }
                        }
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        }

        tempButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                (new Thread(new workerThread("Connecting"))).start();

            }
        });

        if(!mBluetoothAdapter.isEnabled())
        {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if (device.getAddress().equals("B8:27:EB:48:5E:1A")) //Note, you will need to change this to match the name of your device
                {
                    Log.e("Raspeberry", device.getName());
                    mmDevice = device;
                    break;
                }
            }
        }

    }
    //////////////// Bluetooth Initialisation Done //////////////

    // Set up the methods to execute for each button
    @Override
    public void onClick(View vw){
        switch (vw.getId()) {
            case R.id.locate:{
                updateDroneLocation();
                cameraUpdate(); // Locate the drone's place
                //set position on map if used
                break;
            }
            case R.id.start:{ // start mission
                startMission();
                break;
            }
            case R.id.stop:{ // abort mission
                stop();

                break;
            }
            case R.id.configMission:{ // set radius and altitude for hotpoint mission
                circleSettings();
                break;
            }
            case R.id.uploadMission:{ // set the parameters of the hotpoint mission
                config_mission();
                break;
            }
            default:break;
        }
    }
//////// Activity Initialisation Done////////

    // Initialise the drone and get current drone location
    private void initFlightController() {

        BaseProduct product = DroneApp.getProductInstance();
        if (product != null && product.isConnected()) {
            if (product instanceof Aircraft) {
                mFlightController = ((Aircraft) product).getFlightController();
            }  else
            {
                setResultToToast("connected but there is no instance of Aircraft");
            }
        }
        else{
            setResultToToast("product might be null or just not connected");
        }

        if (mFlightController != null) {
            mFlightController.setStateCallback(new FlightControllerState.Callback() {


                @Override
                public void onUpdate(FlightControllerState djiFlightControllerCurrentState) {
                    droneLocationLat = djiFlightControllerCurrentState.getAircraftLocation().getLatitude();
                    droneLocationLng = djiFlightControllerCurrentState.getAircraftLocation().getLongitude();

                    if(counterConnection == 0){
                        setResultToToast("latitude location " + Double.toString(droneLocationLat));
                        setResultToToast("longitude location " + Double.toString(droneLocationLng));
                        counterConnection++;
                    }
                    updateDroneLocation();
                }
            });


        }

    }


    // Update drone location on the map
    private void updateDroneLocation() {

        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        //Create MarkerOptions object
        final MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(pos);
        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft));

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (droneMarker != null) {
                    droneMarker.remove();
                }

                if (checkGpsCoordination(droneLocationLat, droneLocationLng)) {
                    droneMarker = gMap.addMarker(markerOptions);
                }
            }
        });
    }


    // Update Camera when locating the drone
    private void cameraUpdate() {
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        float zoomlevel = (float) 18.0;
        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(pos, zoomlevel);
        gMap.moveCamera(cu);

    }


    // Verify if coordinates are valid coordinates
    public static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    //
    public void config_mission(){
        // Verify if altitude and radius have been set
        if(altitude == 0 || radius == 0){
            setResultToToast("Altitude and Radius need to be set before uploading");
        }
        // Verify if Altitude and Radius are greater than 5 meters which is the minimum accepted by HotPoint Mission
        else if(altitude < 5 || radius < 5){
            setResultToToast("Altitude and Radius are not set correctly");
        }
        // If Altitude and Radius are correct, set up the Hotpoint Mission
        else {
            missionControl = MissionControl.getInstance();
            MissionControl.Listener listener = new MissionControl.Listener() {
                @Override
                public void onEvent(@Nullable TimelineElement element, TimelineEvent event, DJIError error) {
                }
            };

            // Hotpoint mission here
            startPoint = HotpointStartPoint.NEAREST;
            heading = HotpointHeading.TOWARDS_HOT_POINT;

            clockwise = false;
            hotpoint = new LocationCoordinate2D(droneLocationLat, droneLocationLng);
            circleMission = new HotpointMission(hotpoint, altitude, radius, angVeloc, clockwise, startPoint, heading);

            // The drone need to takeOff before doing the Hotpoint mission
            missionList.add(new TakeOffAction());

            // Adding the circle mission to the mission list
            missionList.add(new HotpointAction(circleMission, 360));

            // Landing the drone after completing the mission
            missionList.add(new LandAction());

            // If there are any scheduled elements in the MissionControl objects, they will be cleared
            if (missionControl.scheduledCount() > 0) {
                MissionControl.getInstance().unscheduleEverything();
            }
            // Adding mission list to the Timeline
            missionControl.scheduleElements(missionList);
            missionControl.addListener(listener);

            setResultToToast("Mission Uploaded Successfully");
        }
    }


    // Start the timeline containing the mission list
    public void startMission(){

        // If mFlightController is null, it means that the drone is not connected
        if(mFlightController!=null) {
            if (MissionControl.getInstance().scheduledCount() > 0) {
                // Start the mission
                missionControl.startTimeline();
                setResultToToast("Mission Starting");
            } else {
                setResultToToast("Please initialise mission first with Config Mission button");
            }
        }
        else{
            setResultToToast("Drone is not connected");

        }

    }


    // Stops timeline in case of fire or emergency. Drone will go to initial position and land
    public void stop(){
        if(mFlightController != null) {
            setResultToToast("Stopping Mission");
            MissionControl.getInstance().stopTimeline();

            //missionControl = MissionControl.getInstance();
            MissionControl.Listener listener = new MissionControl.Listener() {
                @Override
                public void onEvent(@Nullable TimelineElement element, TimelineEvent event, DJIError error) {

                }
            };


            if (missionControl.scheduledCount() > 0) {
                MissionControl.getInstance().unscheduleEverything();
            }

            // Go to hotpoint which is the TakeOff location
            stopList.add(new GoToAction(hotpoint));

            // Landing after arriving to TakeOff location
            stopList.add(new LandAction());


            missionControl.scheduleElements(stopList);
            missionControl.addListener(listener);

            // Starting the Emergency Stop Timeline
            setResultToToast("Returning to home");
            missionControl.startTimeline();
        }
        else{
            setResultToToast("Drone is not connected");
        }
    }



    private void circleSettings(){
        LinearLayout hotPointSettings = (LinearLayout) getLayoutInflater().inflate(R.layout.circle_config, null);

        final TextView hotPointAltitude = (TextView) hotPointSettings.findViewById(R.id.altitude);
        final TextView hotPointRadius = (TextView) hotPointSettings.findViewById(R.id.radiusC);

        final AlertDialog dialog =  new AlertDialog.Builder(this)
                .setMessage("Hotpoint Configuration")
                .setView(hotPointSettings)
                .setPositiveButton("Config", null)
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();

                    }
                })
                .create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {

            @Override
            public void onShow(DialogInterface dialogInterface) {

                Button buttonPositive = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                buttonPositive.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        String altitudeString = hotPointAltitude.getText().toString();
                        altitude = Double.parseDouble(verifyIfAltitudeDouble(altitudeString));

                        String radiusString = hotPointRadius.getText().toString();
                        radius = Double.parseDouble(verifyIfRadiusDouble(radiusString));

                        if(altitude >= 5 && radius >= 5){
                            setResultToToast("Mission configured successfully");
                            dialog.dismiss();
                        }
                        else{}

                    }
                });
            }
        });
        dialog.show();

        // Change font size of the tile
        TextView textView = (TextView) dialog.findViewById(android.R.id.message);
        textView.setTextSize(25);


    }


    // Verify if the altitude the user entered is valid
    String verifyIfAltitudeDouble(String value){
        try{
            value = value.replace(" ", "");
            double altitude_verify =  Double.parseDouble(value);
            if(altitude_verify < 5.0)
                setResultToToast("Altitude must be greater than or equal to 5 meters");
        } catch (Exception e){
            setResultToToast("Altitude is not a valid value");
            return "0";
        }
        return value;
    }


    // Verify if the radius the user entered is valid
    String verifyIfRadiusDouble(String value){
        try{
            value = value.replace(" ", "");
            double radius_verify =  Double.parseDouble(value);
            if(radius_verify < 5.0)
                setResultToToast("Radius must be greater than or equal to 5 meters");
        } catch (Exception e){
            setResultToToast("Radius is not a valid value");
            return "0";
        }
        return value;
    }


    private void firePopUp(){
        double latitude, longitude;
        FlightControllerState djiFlightControllerCurrentState= new FlightControllerState();

        // Get current drone location
        latitude =  djiFlightControllerCurrentState.getAircraftLocation().getLatitude();
        longitude =  djiFlightControllerCurrentState.getAircraftLocation().getLongitude();

        LinearLayout fireLayout = (LinearLayout) getLayoutInflater().inflate(R.layout.firepopup, null);

        TextView fireLatitude = (TextView) fireLayout.findViewById(R.id.latitude);
        TextView fireLongitude = (TextView) fireLayout.findViewById(R.id.longitude);

        fireLatitude.setText(Double.toString(latitude));
        fireLongitude.setText(Double.toString(longitude));

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage("A FIRE HAS BEEN DETECTED")
                .setView(fireLayout)
                .setPositiveButton("Finish", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                    }
                })
                .create();
        dialog.show();
        TextView textView = (TextView) dialog.findViewById(android.R.id.message);
        textView.setTextSize(25);


    }

    public void sendBtMsg(String msg2send){
        //UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID
        UUID uuid = UUID.fromString("9f4a4f86-fe45-48ca-8241-dbfac243ce42"); //Standard SerialPortService ID
        try {

            mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
            if (!mmSocket.isConnected()){
                mmSocket.connect();
            }

            String msg = msg2send;
            //msg += "\n";
            OutputStream mmOutputStream = mmSocket.getOutputStream();
            mmOutputStream.write(msg.getBytes());

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        //if(Thread.activeCount() > 0)
          //  Thread.currentThread().destroy();

    }


    private void setResultToToast(final String string) {
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, string, Toast.LENGTH_LONG).show();
            }
        });
    }
}


