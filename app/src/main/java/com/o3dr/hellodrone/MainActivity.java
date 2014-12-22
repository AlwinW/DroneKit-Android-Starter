package com.o3dr.hellodrone;

import android.content.Context;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import com.o3dr.android.client.Drone;
import com.o3dr.android.client.ServiceManager;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.ServiceListener;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.connection.ConnectionResult;

// Event not found
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.connection.ConnectionType;
import com.o3dr.services.android.lib.drone.connection.StreamRates;
import com.o3dr.services.android.lib.drone.property.VehicleMode;

import java.util.List;


public class MainActivity extends ActionBarActivity implements DroneListener, ServiceListener {

    private Drone drone;
    private ServiceManager serviceManager;
    private final Handler handler = new Handler();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Context context = getApplicationContext();
        serviceManager = new ServiceManager(context);
        drone = new Drone(serviceManager, handler);

    }

    @Override
    public void onStart() {
        super.onStart();
        serviceManager.connect(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        this.drone.destroy();
        serviceManager.disconnect();
    }

    // Service Manager
    @Override
    public void onServiceConnected() {
        alertUser("3DR Services Connected");
        this.drone.start();
        this.drone.registerDroneListener(this);
    }

    @Override
    public void onServiceInterrupted() {
        alertUser("3DR Service Interrupted");
    }


    // Drone Listener

    @Override
    public void onDroneEvent(String event, Bundle extras) {

        // Should be DroneEvent?
        if(AttributeEvent.STATE_CONNECTED.equals(event)){
            alertUser("Connected!");
            updateConnectedButton(true);
        }
        else if(AttributeEvent.STATE_DISCONNECTED.equals(event)){
            alertUser("Disconnected!");
            updateConnectedButton(false);

        } else if (AttributeEvent.HEARTBEAT_FIRST.equals(event)) {
            alertUser("heartbeat");
        } else if (AttributeEvent.ATTITUDE_UPDATED.equals(event)) {
            updateAltitude();
        } else if (AttributeEvent.SPEED_UPDATED.equals(event)) {
            updateSpeed();
        } else if(AttributeEvent.HOME_UPDATED.equals(event)) {
            updateDistanceFromHome();
        }
        else if(AttributeEvent.STATE_VEHICLE_MODE.equals(event)){
            loadVehicleModes();
        }

    }

    @Override
    public void onDroneConnectionFailed(ConnectionResult result) {
        alertUser("Connection Failed:" + result.getErrorMessage());
    }


    @Override
    public void onDroneServiceInterrupted(String errorMsg) {

    }

    // UI Events
    public void onBtnConnectTap(View view) {

        if(this.drone.isConnected()) {
            this.drone.disconnect();

        } else {
            Spinner connectionSelector = (Spinner)findViewById(R.id.selectConnectionType);
            int selectedConnectionType = connectionSelector.getSelectedItemPosition();

            final StreamRates streamRates = new StreamRates();
            streamRates.setExtendedStatus(2);
            streamRates.setExtra1(2);
            streamRates.setExtra2(2);
            streamRates.setExtra3(2);
            streamRates.setPosition(2);
            streamRates.setRcChannels(2);
            streamRates.setRawSensors(2);
            streamRates.setRawController(2);

            Bundle extraParams = new Bundle();
            if (selectedConnectionType == ConnectionType.TYPE_USB) {
                extraParams.putInt(ConnectionType.EXTRA_USB_BAUD_RATE, 57600); // Set default baud rate to 57600
            } else {
                extraParams.putInt(ConnectionType.EXTRA_UDP_SERVER_PORT, 14550); // Set default baud rate to 14550
            }
            ConnectionParameter connectionParams = new ConnectionParameter(selectedConnectionType, extraParams, streamRates, null);
            this.drone.connect(connectionParams);
        }

    }

    public void onSelectFlightMode(View view) {

    }

    // UI update

    protected void updateConnectedButton(Boolean isConnected) {
        Button connectButton = (Button)findViewById(R.id.btnConnect);
        if (isConnected) {
            connectButton.setText("Disconnect");
        } else {
            connectButton.setText("Connect");
        }
    }

    protected void updateAltitude(){
        TextView altitudeTextView = (TextView)findViewById(R.id.altitudeValueTextView);
        altitudeTextView.setText(this.drone.getAltitude().getAltitude() + "m");
    }

    protected void updateSpeed() {
        TextView speedTextView = (TextView)findViewById(R.id.speedValueTextView);
        speedTextView.setText(this.drone.getSpeed().getGroundSpeed() + "m");
    }

    protected void updateDistanceFromHome() {
        TextView distanceTextView = (TextView)findViewById(R.id.distanceValueTextView);
        double vehicleAltitude = this.drone.getAltitude().getAltitude();
        LatLong vehiclePosition = this.drone.getGps().getPosition();

        double distanceFromHome =  0;

        if (this.drone.getGps().isValid()) {
            LatLongAlt vehicle3DPosition = new LatLongAlt(vehiclePosition.getLatitude(), vehiclePosition.getLongitude(), vehicleAltitude);
            distanceFromHome = distanceBetweenPoints(this.drone.getHome().getCoordinate(), vehicle3DPosition);
        } else {
            alertUser("Vehicle position null");
        }

        distanceTextView.setText(distanceFromHome + "m");
    }

    protected void loadVehicleModes() {
        Spinner modeSelector = (Spinner)findViewById(R.id.modeSelect);
        List<VehicleMode> vehicleModes =  VehicleMode.getVehicleModePerDroneType(this.drone.getType().getDroneType());
        ArrayAdapter<VehicleMode> arrayAdapter = new ArrayAdapter<VehicleMode>(this, android.R.layout.simple_spinner_item, vehicleModes);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSelector.setAdapter(arrayAdapter);

//        alertUser(this.drone.getState().getVehicleMode().getLabel());
    }

    // Convenience methods

    protected void alertUser(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    protected double distanceBetweenPoints(LatLongAlt pointA, LatLongAlt pointB) {
        double dx = pointA.getLatitude() - pointB.getLatitude();
        double dy  = pointA.getLongitude() - pointB.getLongitude();
        double dz = pointA.getAltitude() - pointB.getAltitude();
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

}
