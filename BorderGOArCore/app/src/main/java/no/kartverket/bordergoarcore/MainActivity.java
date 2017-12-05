package no.kartverket.bordergoarcore;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;


import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraException;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

import no.kartverket.bordergoarcore.config.ARConfig;
import no.kartverket.bordergoarcore.data.DataViewHelper;
import no.kartverket.data.DataLogger;
import no.kartverket.data.dtm.DTMGrid;
import no.kartverket.data.dtm.DTMGridProvider;
import no.kartverket.data.repository.ITokenServiceRepository;
import no.kartverket.data.repository.TokenServiceRepository;
import no.kartverket.data.utils.DTMRequest;
import no.kartverket.geodesy.Geodesy;
import no.kartverket.geodesy.OriginData;
import no.kartverket.geometry.IndexedTriangleMesh;
import no.kartverket.geometry.Pos;
import no.kartverket.geopose.OriginUpdateListener;
import no.kartverket.glrenderer.*;
import no.kartverket.glrenderer.ArGLRenderer;




public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final String TAG = MainActivity.class.getSimpleName();
    private BorderGoApp app;

    // GUI
    private TextView textA, textB,textC,textD, textD1, textE,textF, textF1, textG;
    private EditText deviceHeightEdit, demZigmaEdit, mapCalibrationZigmaEdit, pointCloudZigmaEdit;
    public UXState uxState = UXState.MAIN;

    // OpenGL related members
    private ArGLRenderer arRenderer;
    private ArScene arScene;
    private GLSurfaceView surfaceView;
    private GestureDetectorCompat gestureDetector;

    // map component
    private GoogleMap googleMapInstance;
    private SupportMapFragment mapFragment;

    // legacy tango?
    private int displayRotation = 0;
    boolean isBound = false;
    //TangoService tangoService;

    // New ARCore
    GeoPoseForARCoreManager geoPoseForARCore;
    Session arSession;
    Config arConfig;
    private boolean arCoreIsReady = false;
    // Data
    private DTMRequest heightService;

    //GPS and COMPASS
    private LocationManager locationManager;
    //private LocationListener locationListener;
    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    //private SensorEventListener rotationListener;


    //Permissions
    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;
    private static final String LOCATION_PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        app = (BorderGoApp) getApplication();
        app.setScene(new ArScene());
        arSession = new Session( /*activitycontext=*/ this);
        // Create default config, check is supported, create session from that config.
        arConfig = Config.createDefaultConfig();
        if (!arSession.isSupported(arConfig)) {
            Toast.makeText(this, "This device does not support AR", Toast.LENGTH_LONG).show();
            finish();
            return;
        }



        //setupGeoPoseForARManager();
        geoPoseForARCore = new GeoPoseForARCoreManager(arSession, app);
        setupContentInfo(); // needed by updateFromPreferencesOrDefault();
        updateFromPreferencesOrDefault(); // with member instantiated we can call updateFromPreferencesOrDefault()
        geoPoseForARCore.addOriginUpdateListener(originUpdateListener);


        setupGPSAndCompass();
        setupGL();
        setupLogger();
        Geodesy.initHREF(app.getApplicationContext());
        setupMap();

        initGuiUpdater();

    }

    private void setupGeoPoseForARManager(){
        geoPoseForARCore = new GeoPoseForARCoreManager(arSession, app);
        geoPoseForARCore.addOriginUpdateListener(originUpdateListener);
        arReadyListener = new ArReadyListener(this.getApplicationContext()){

            @Override
            public void onReady(){
                //geoPoseForARCore.initOnARReady();
                //geoPoseForARCore.addOriginUpdateListener(originUpdateListener);
            }

        };

    }

    private void setupGPSAndCompass(){
        //GPS and compass:
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if ( locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ) {
            if (ContextCompat.checkSelfPermission(app, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, locationListener);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 500, 0, locationListener);

            }

        }

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        sensorManager.registerListener(rotationListener, rotationVectorSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private final LocationListener locationListener = new LocationListener() {

        public void onLocationChanged(Location location) {

            BGState st = BorderGoApp.getBGState();
            st.altGPS = location.getAltitude();
            st.latGPS = location.getLatitude();
            st.lngGPS = location.getLongitude();
            st.zigGPS = location.getAccuracy();

            if(BorderGoApp.usesGPSAndCompass()){
                if(arCoreIsReady && location != null){
                    geoPoseForARCore.handleLocation(location);
                    Log.d(TAG, "onLocationChanged() -> " + location.getProvider());
                }
            }

        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) { }

        @Override
        public void onProviderEnabled(String provider) { }

        @Override
        public void onProviderDisabled(String provider) { }


    };

    private final SensorEventListener rotationListener = new SensorEventListener() {
        long last_t = 0;
        @Override
        public void onSensorChanged(SensorEvent event) {
            if(BorderGoApp.usesGPSAndCompass()){
                if(arCoreIsReady){
                    geoPoseForARCore.handleRotation(event);
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {        }
    };


    private  ArReadyListener arReadyListener;
    private MapsHelper mapsHelper;

    private void setupMap(){


        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        MapsHelper.initServiceURLs(); // get urls for the tile services (from ARConfig)

        requestOrthoSessionKey(new SessionKeyCallback(){

            @Override
            public void sessionKey(String sessionKey){
                MapsHelper.setOrthoSessionKey(sessionKey);
                if(mapsHelper == null){
                    initMapHelperIfPossible();
                }

            }


        });
    }



    private void initMapHelperIfPossible(){
        if((googleMapInstance) != null && (geoPoseForARCore != null)){
            mapsHelper = new MapsHelper(googleMapInstance,geoPoseForARCore,app);
        }
    }

    private void setupContentInfo(){
        textA = (TextView)findViewById(R.id.textA);
        textB = (TextView)findViewById(R.id.textB);
        textC = (TextView)findViewById(R.id.textC);
        textD = (TextView)findViewById(R.id.textD);
        textD1 = (TextView)findViewById(R.id.textD1);
        textE = (TextView)findViewById(R.id.textE);
        textF = (TextView)findViewById(R.id.textF);
        textF1 = (TextView)findViewById(R.id.textF1);
        textG = (TextView)findViewById(R.id.textG);
        textA.setText("");textB.setText("");textC.setText("");textD.setText("");textD1.setText("");textE.setText("");textF.setText("");textF1.setText("");textG.setText("");

        deviceHeightEdit        = (EditText)findViewById(R.id.deviceHeight);
        demZigmaEdit            = (EditText)findViewById(R.id.demZigma);
        mapCalibrationZigmaEdit = (EditText)findViewById(R.id.mapCalibrationZigma);
        pointCloudZigmaEdit     = (EditText)findViewById(R.id.pointCloudZigma);
        deviceHeightEdit.setOnEditorActionListener(editorActionListener);
        demZigmaEdit.setOnEditorActionListener(editorActionListener);
        mapCalibrationZigmaEdit.setOnEditorActionListener(editorActionListener);
        pointCloudZigmaEdit.setOnEditorActionListener(editorActionListener);
    }

    private void updateTangoValuesInBGState(BGState s){
        if(geoPoseForARCore != null){

            Location l = geoPoseForARCore.getLocation();
            if(l != null){
                s.latGeoPose = l.getLatitude();
                s.lngGeoPose = l.getLongitude();
                s.altGeoPose = l.getAltitude();
                s.zigGeoPose = l.getAccuracy();
            }
        }
    }

    final Handler mHandler = new Handler();
    final Runnable updateBGStateUI = new Runnable() {
        public void run() {

            BGState s = BorderGoApp.getBGState();
            updateTangoValuesInBGState(s);
            textA.setText(  "Altitude from grid         :" + String.format("%.6f", s.altInterpolated));
            textB.setText(  "Altitude from 'pop/tango'  :" + String.format("%.6f", s.altGeoPose));
            textC.setText(  "Lat from GeoPose           :" + String.format("%.6f", s.latGeoPose));
            textD.setText(  "Lng from GeoPose           :" + String.format("%.6f", s.lngGeoPose));
            textD1.setText( "Accuracy GeoPose           :" + String.format("%.6f", s.zigGeoPose));
            textE.setText(  "Lat gps                    :" + String.format("%.6f", s.latGPS));
            textF.setText(  "Lng gps                    :" + String.format("%.6f", s.lngGPS));
            textF1.setText( "Accuracy gps               :" + String.format("%.6f", s.zigGPS));
            textG.setText(  "Altitude gps               :" + String.format("%.6f", s.altGPS));



        }
    };

    private void updateGUI(){
        mHandler.post(updateBGStateUI);
    }

    private void initGuiUpdater(){

        Timer myTimer = new Timer();
        myTimer.schedule(new TimerTask() {
            @Override
            public void run() {updateGUI();}
        }, 0, 500);

    }



    @Override
    protected void onResume() {
        super.onResume();

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (hasCameraPermission()) {
            // Note that order matters - see the note in onPause(), the reverse applies here.
            arSession.resume(arConfig);
            surfaceView.onResume();
        }
    }

    /**
     * Result for requesting camera permission.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (hasCameraPermission() && hasLocationPermission()) {
            // bindTangoService();
            // requestLocationUpdatesFromProvider();
        } else {
            Toast.makeText(this, "BORDER GO requires camera and location permission",
                    Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, LOCATION_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED;
    }



    private void displayManagerStuff(){
        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (displayManager != null) {
            displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {


                @Override
                public void onDisplayAdded(int displayId) {
                }


                @Override
                public void onDisplayRemoved(int displayId) {
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    synchronized (this) {
                        setDisplayRotation();
                    }
                }
            }, null);
        }
    }
    private void setDisplayRotation() {
        Display display = getWindowManager().getDefaultDisplay();
        displayRotation = display.getRotation();

        // We also need to update the camera texture UV coordinates. This must be run in the OpenGL
        // thread.
        surfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (isBound) {
                    arRenderer.updateColorCameraTextureUv(displayRotation);
                }
            }
        });
    }


    private void setupLogger(){
        DataLogger logger = app.getDataLogger();
        if(!logger.hasLog(BorderGoApp.LoggNames.LAT_GPS)){
            logger.startLog(BorderGoApp.LoggNames.Z_TIMESTAMPED_DATA, DataLogger.LogTypes.TIME_SERIES_DOUBLE);
            logger.startLog(BorderGoApp.LoggNames.LAT_GPS, DataLogger.LogTypes.DOUBLE);
            logger.startLog(BorderGoApp.LoggNames.LNG_GPS, DataLogger.LogTypes.DOUBLE);
            logger.startLog(BorderGoApp.LoggNames.LAT_GEOPOSE, DataLogger.LogTypes.DOUBLE);
            logger.startLog(BorderGoApp.LoggNames.LNG_GEOPOSE, DataLogger.LogTypes.DOUBLE);
            logger.startLog(BorderGoApp.LoggNames.ZIG_GEOPOSE, DataLogger.LogTypes.DOUBLE);
            logger.startLog(BorderGoApp.LoggNames.ALT_INTERPOLATED, DataLogger.LogTypes.DOUBLE);


        }
    }

    class MyGestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final String DEBUG_TAG = "Gestures";

        @Override
        public void onLongPress (MotionEvent event) {
            geoPoseForARCore.snapCloud();
            Log.d(DEBUG_TAG,"onLongPress : " + event.toString());
        }
    }



    private void  setupGL(){
        surfaceView = (GLSurfaceView) findViewById(R.id.surfaceview);
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        setupRenderer();
        //arScene =  new ArScene();

        gestureDetector = new GestureDetectorCompat(this, new MyGestureListener());
        surfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gestureDetector.onTouchEvent(event);
                return true;
            }
        });




    }



    @Override
    public void onMapReady(GoogleMap googleMap) {
        googleMapInstance = googleMap;
        if(MapsHelper.getOrthoSessionKey() != null){
            mapsHelper = new MapsHelper(googleMap,geoPoseForARCore,app);
        }

    }



    public void showMap(View view){
        GridLayout mapContainer = (GridLayout)findViewById(R.id.mapcontainer);

        Location loc = geoPoseForARCore.getLocation();
        if(loc != null){
            MapsHelper.setLatLng(loc.getLatitude(),loc.getLongitude());
        }

        if(mapsHelper == null) {
            initMapHelperIfPossible();
            if(mapsHelper != null){ mapsHelper.refreshMap(); }
        } else {
            mapsHelper.refreshMap();
        }


        hideMainUX();

        uxState = UXState.MAP;
        mapContainer.setVisibility(View.VISIBLE);

    }


    private void hideMainUX(){
        LinearLayout menuButtonContainer = (LinearLayout)findViewById(R.id.menuButtonContainer);
        menuButtonContainer.setVisibility(View.INVISIBLE);

        LinearLayout menuContainer = (LinearLayout)findViewById(R.id.content_menu);
        menuContainer.setVisibility(View.INVISIBLE);

        Button calibrationButton = (Button)findViewById(R.id.mapButton);
        calibrationButton.setVisibility(View.INVISIBLE);
    }

    private void showMainUX(){
        Button calibrationButton = (Button)findViewById(R.id.mapButton);
        calibrationButton.setVisibility(View.VISIBLE);

        LinearLayout menuButtonContainer = (LinearLayout)findViewById(R.id.menuButtonContainer);
        menuButtonContainer.setVisibility(View.VISIBLE);

        LinearLayout menuContainer = (LinearLayout)findViewById(R.id.content_menu);
        menuContainer.setVisibility(View.VISIBLE);

    }

    public void endMapCalibration(View view){
        endMapCalibration();
    }

    public void endMapCalibration(){
        GridLayout mapContainer = (GridLayout)findViewById(R.id.mapcontainer);
        mapContainer.setVisibility(View.INVISIBLE);
        uxState = UXState.MAIN;
        showMainUX();
    }

    @Override
    public void onBackPressed() {
        // your code.
        switch(uxState){
            case DATA_GRAPH:
                closeGraph();
                break;
            case DATA_LIST:
                closeLogList();
                break;
            case MAIN:
                break;
            case MAP:
                endMapCalibration();
                break;
            default:
                break;
        }
    }

    public void toggleAerialClick(View view){
        Button toggleButton = (Button)findViewById(R.id.toggleAerialButton);
        if(app.usesAerialMap()){
            toggleButton.setText("Vis flyfoto");
        } else {
            toggleButton.setText("Vis grunnkart");
        }
        app.toggleAerialMap();

        googleMapInstance.clear();

        mapsHelper.setTileOverlay();

        mapsHelper.reInitializeMarkers();
    }



    public void showMenu(View view){
        DrawerLayout drawer = (DrawerLayout)findViewById(R.id.drawer_layout);
        drawer.openDrawer(Gravity.RIGHT);
    }

    public void toggleLineRemoval(View view){
        Button button = (Button)view;
        if(this.arScene.hasHiddenLineRemoval()){
            //button.setText("Høydegrid: vis bakside");
            button.setText("Høydegrid: skjul bakside");
            this.arScene.setHiddenLineRemoval(false);
        } else {
            //button.setText("Høydegrid: skjul bakside");
            button.setText("Høydegrid: vis bakside");
            this.arScene.setHiddenLineRemoval(true);
        }

    }


    /**
     * Initialize 3D rendering. Called in {@link #onCreate(Bundle)}
     */
    private void setupRenderer() {



        arScene = app.getScene();

        arRenderer = new ArGLRenderer(this,arScene,
                new ArGLRenderer.RenderCallback() {
                    private double lastRenderedTimeStamp;

                    @Override
                    public void preRender() {

                        if(arCoreIsReady){
                            try{
                                Frame frame = arSession.update();


                                float[] t = new float[3];
                                Pose p = frame.getPose();
                                p.getTranslation(t,0);
                                //Log.i("MainActivity:preRender", "x: " + t[0] + ", y: " + t[1] + ", z: " + t[2]);

                                geoPoseForARCore.handleARCorePoseObservation(p);

                                arSession.setCameraTextureName(arRenderer.getTextureId());

                                if (!arRenderer.isProjectionMatrixConfigured()) {
                                    /*TangoCameraIntrinsics intrinsics =
                                            TangoSupport.getCameraIntrinsicsBasedOnDisplayRotation(
                                                    TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                                    displayRotation);*/


                                    float[] projmtx = new float[16];
                                    arSession.getProjectionMatrix(projmtx, 0, 0.1f, 500.0f);
                                    arRenderer.setProjectionMatrix(projmtx);
                                    //arRenderer.setProjectionMatrix(ArGlRenderer.projectionMatrixFromCameraIntrinsics(intrinsics));
                                    //projectionMatrixFromCameraIntrinsics(intrinsics));

                                    float[] textureCoordinates = arRenderer.getCameraTextureCoordinatesCopy();

                                    if(textureCoordinates != null){
                                        FloatBuffer oldtextureCoordinatesBuffer = FloatBuffer.wrap(textureCoordinates);
                                        FloatBuffer newtextureCoordinatesBuffer = FloatBuffer.wrap(textureCoordinates.clone());


                                        frame.transformDisplayUvCoords(oldtextureCoordinatesBuffer, newtextureCoordinatesBuffer);
                                        if(newtextureCoordinatesBuffer.hasArray()){
                                            arRenderer.setCameraTextureCoordinates(newtextureCoordinatesBuffer.array().clone());
                                        }
                                    }

                                }

                                float[] projmtx = new float[16];
                                arSession.getProjectionMatrix(projmtx, 0, 0.1f, 500.0f);
                                arRenderer.setProjectionMatrix(projmtx);
                                float[] transmtx = geoPoseForARCore.getTransformationMatrix();
                                arScene.setArWorldMatrix( transmtx);
                                float[] viewMatrix = new float[16];
                                frame.getViewMatrix(viewMatrix,0);
                                arRenderer.updateViewMatrix(viewMatrix);

                                if(geoPoseForARCore.getDtmGrid() != null){
                                    BGState st = BorderGoApp.getBGState();
                                    Location loc = geoPoseForARCore.getLocation();
                                    double lat = loc.getLatitude();
                                    double lng = loc.getLongitude();
                                    double zig = loc.getAccuracy();

                                    double h = geoPoseForARCore.getDtmGrid().getInterpolatedAltitude(lat,lng);
                                    st.altInterpolated = (h == Double.NEGATIVE_INFINITY ? 0 : h);
                                    st.altGeoPose = loc.getAltitude();
                                    st.latGeoPose = lat;
                                    st.lngGeoPose = lng;
                                    st.zigGeoPose = zig;

                                    DataLogger logger = app.getDataLogger();
                                    logger.log(BorderGoApp.LoggNames.ALT_INTERPOLATED, h);
                                    logger.log(BorderGoApp.LoggNames.LAT_GEOPOSE, lat);
                                    logger.log(BorderGoApp.LoggNames.LNG_GEOPOSE, lng);
                                    logger.log(BorderGoApp.LoggNames.ZIG_GEOPOSE, zig);

                                }



                                geoPoseForARCore.handleCurrentFrame(frame);

                            } catch (CameraException e){
                                Log.i("MainActivity", e.getMessage());
                            }


                        } else {
                            arSession.setCameraTextureName(arRenderer.getTextureId());
                            arCoreIsReady = geoPoseForARCore.isArCoreReady();
                        }

                        //Legacy Tango ?
                        // This is the work that you would do on your main OpenGL render thread.

                        /*try {
                            // Synchronize against concurrently disconnecting the service triggered
                            // from the UI thread.
                            synchronized (MainActivity.this) {
                                // We need to be careful not to run any Tango-dependent code in the
                                // OpenGL thread unless we know the Tango Service is properly
                                // set up and connected.
                                /*if (!isBound || tangoService == null || !tangoService.isConnected()) {
                                    return;
                                }

                                // Set up scene camera projection to match RGB camera intrinsics.
                                if (!arRenderer.isProjectionMatrixConfigured()) {
                                    TangoCameraIntrinsics intrinsics =
                                            TangoSupport.getCameraIntrinsicsBasedOnDisplayRotation(
                                                    TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                                    displayRotation);
                                    arRenderer.setProjectionMatrix(ArGLRenderer.projectionMatrixFromCameraIntrinsics(intrinsics));
                                    //projectionMatrixFromCameraIntrinsics(intrinsics));
                                }
                                // Connect the Tango SDK to the OpenGL texture ID where we are
                                // going to render the camera.
                                // NOTE: This must be done after both the texture is generated
                                // and the Tango Service is connected.
                                if (connectedTextureIdGlThread != arRenderer.getTextureId()) {
                                    tangoService.getTango().connectTextureId(
                                            TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                            renderer.getTextureId());
                                    connectedTextureIdGlThread = arRenderer.getTextureId();
                                    Log.d(TAG, "connected to texture id: " +
                                            renderer.getTextureId());
                                }
                                // If there is a new RGB camera frame available, update the texture
                                // and scene camera pose.
                                if (isFrameAvailableTangoThread.compareAndSet(true, false)) {
                                    // {@code mRgbTimestampGlThread} contains the exact timestamp at
                                    // which the rendered RGB frame was acquired.
                                    rgbTimestampGlThread =
                                            tangoService.getTango().updateTexture(TangoCameraIntrinsics.
                                                    TANGO_CAMERA_COLOR);

                                    // Get the transform from color camera to Start of Service
                                    // at the timestamp of the RGB image in OpenGL coordinates.
                                    //
                                    // When drift correction mode is enabled in config file, we need
                                    // to query the device with respect to Area Description pose in
                                    // order to use the drift-corrected pose.
                                    //
                                    // Note that if you don't want to use the drift corrected pose,
                                    // the normal device with respect to start of service pose is
                                    // still available.
                                    TangoSupport.TangoMatrixTransformData transform =
                                            TangoSupport.getMatrixTransformAtTime(
                                                    rgbTimestampGlThread,
                                                    TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                                                    TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                                                    TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                                    TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                                    displayRotation);
                                    if (transform.statusCode == TangoPoseData.POSE_VALID) {
                                        PositionOrientationProvider pop = tangoService.getPositionOrientationProvider();
                                        textA = (TextView)findViewById(R.id.textA);// new thread????
                                        if(tangoService.getDtmGrid() != null){
                                            BGState st = BorderGoApp.getBGState();
                                            Location loc = tangoService.getPositionOrientationProvider().getLocation();
                                            double lat = loc.getLatitude();
                                            double lng = loc.getLongitude();
                                            double zig = loc.getAccuracy();

                                            double h = tangoService.getDtmGrid().getInterpolatedAltitude(lat,lng);
                                            st.altInterpolated = (h == Double.NEGATIVE_INFINITY ? 0 : h);
                                            st.altTango = loc.getAltitude();
                                            st.latTango = lat;
                                            st.lngTango = lng;
                                            st.zigTango = zig;

                                            DataLogger logger = app.getDataLogger();
                                            logger.log(BorderGoApp.LoggNames.ALT_INTERPOLATED, h);
                                            logger.log(BorderGoApp.LoggNames.LAT_POP, lat);
                                            logger.log(BorderGoApp.LoggNames.LNG_POP, lng);
                                            logger.log(BorderGoApp.LoggNames.ZIG_POP, zig);

                                        }


                                        arScene.setTangoWorldMatrix(pop.getTransformationMatrix());


                                        arRenderer.updateViewMatrix(transform.matrix);

                                        double deltaTime = rgbTimestampGlThread
                                                - lastRenderedTimeStamp;
                                        lastRenderedTimeStamp = rgbTimestampGlThread;


                                    } else {
                                        // When the pose status is not valid, it indicates tracking
                                        // has been lost. In this case, we simply stop rendering.
                                        //
                                        // This is also the place to display UI to suggest that the
                                        // user walk to recover tracking.


                                    }
                                }
                            }
                            // Avoid crashing the application due to unhandled exceptions.
                        } catch (TangoErrorException e) {
                            Log.e(TAG, "Tango API call error within the OpenGL render thread", e);
                        } catch (Throwable t) {
                            Log.e(TAG, "Exception on the OpenGL thread", t);
                        }*/
                    }

                    @Override
                    public void surfaceChanged(int width, int height) {
                        arSession.setDisplayGeometry(width, height);
                    }
                });

        surfaceView.setRenderer(arRenderer);

    }

    private TextView.OnEditorActionListener editorActionListener = new TextView.OnEditorActionListener() {

        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            boolean retval = false;

            try {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    EditText textWid = (EditText) v;
                    String valueStr = textWid.getText().toString();
                    float value = Float.parseFloat(valueStr);
                    String pref = "_";

                    // update service
                    switch (v.getId()) {
                        case R.id.deviceHeight:
                            geoPoseForARCore.setDeviceHeight(value);
                            pref = BorderGoApp.PrefNames.DEVICE_HEIGHT;
                            retval = true;
                            break;
                        case R.id.demZigma:
                            geoPoseForARCore.setDemSigma(value);
                            pref = BorderGoApp.PrefNames.DTM_ZIGMA;
                            retval = true;
                            break;
                        case R.id.mapCalibrationZigma:
                            geoPoseForARCore.setMapCalibrationSigma(value);
                            pref = BorderGoApp.PrefNames.MAP_CALIBRATION_ZIGMA;
                            retval = true;
                            break;
                        case R.id.pointCloudZigma:
                            geoPoseForARCore.setPointCloudSigma(value);
                            pref = BorderGoApp.PrefNames.POINT_CLOUD_ZIGMA;
                            retval = true;
                            break;
                    }

                    // update preference
                    SharedPreferences settings = getSharedPreferences(BorderGoApp.PREFERENCE_FILE, 0);
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putFloat(pref ,value);
                    editor.commit();
                }

            }
            catch (Exception ex) {
                Log.e(TAG, "EditorListener", ex);
            }

            hideKeyboard();
            return retval;
        }


    };

    private void hideKeyboard(){
        View view = getCurrentFocus();

        if (view != null) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private static final float D_DEVICE_HEIGHT = 1.0f;
    private static final float D_DEM_SIGMA = 1.5f;
    private static final float D_MAP_CALIBRATION_ZIGMA = 0.3f;
    private static final float D_POINT_CLOUD_ZIGMA = 0.3f;

    public void resetToDefault(View view){
        // update service
        geoPoseForARCore.setDemSigma(D_DEM_SIGMA);
        geoPoseForARCore.setDeviceHeight(D_DEVICE_HEIGHT);
        geoPoseForARCore.setMapCalibrationSigma(D_MAP_CALIBRATION_ZIGMA);
        geoPoseForARCore.setPointCloudSigma(D_POINT_CLOUD_ZIGMA);

        // update  UI
        deviceHeightEdit.setText(String.valueOf(D_DEVICE_HEIGHT));
        demZigmaEdit.setText(String.valueOf(D_DEM_SIGMA));
        mapCalibrationZigmaEdit.setText(String.valueOf(D_MAP_CALIBRATION_ZIGMA));
        pointCloudZigmaEdit.setText(String.valueOf(D_POINT_CLOUD_ZIGMA));

        // update preferences
        SharedPreferences settings = getSharedPreferences(BorderGoApp.PREFERENCE_FILE, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putFloat(BorderGoApp.PrefNames.DTM_ZIGMA ,D_DEM_SIGMA);
        editor.putFloat(BorderGoApp.PrefNames.DEVICE_HEIGHT ,D_DEVICE_HEIGHT);
        editor.putFloat(BorderGoApp.PrefNames.MAP_CALIBRATION_ZIGMA ,D_MAP_CALIBRATION_ZIGMA);
        editor.putFloat(BorderGoApp.PrefNames.POINT_CLOUD_ZIGMA ,D_POINT_CLOUD_ZIGMA);
        editor.commit();
    }

    public void updateFromPreferencesOrDefault(){
        SharedPreferences settings = getSharedPreferences(BorderGoApp.PREFERENCE_FILE, 0);

        // assign from preferences or fallback to default values
        float deviceHeight = settings.getFloat(BorderGoApp.PrefNames.DEVICE_HEIGHT, D_DEVICE_HEIGHT);
        float demZigma = settings.getFloat(BorderGoApp.PrefNames.DTM_ZIGMA, D_DEM_SIGMA);
        float mapCalibrationZigma = settings.getFloat(BorderGoApp.PrefNames.MAP_CALIBRATION_ZIGMA, D_MAP_CALIBRATION_ZIGMA);
        float pointCloudZigma = settings.getFloat(BorderGoApp.PrefNames.POINT_CLOUD_ZIGMA, D_POINT_CLOUD_ZIGMA);

        // update service
        geoPoseForARCore.setDeviceHeight(deviceHeight);
        geoPoseForARCore.setDemSigma(demZigma);
        geoPoseForARCore.setMapCalibrationSigma(mapCalibrationZigma);
        geoPoseForARCore.setPointCloudSigma(pointCloudZigma);

        // update UI
        deviceHeightEdit.setText(String.valueOf(deviceHeight));
        demZigmaEdit.setText(String.valueOf(demZigma));
        mapCalibrationZigmaEdit.setText(String.valueOf(mapCalibrationZigma));
        pointCloudZigmaEdit.setText(String.valueOf(pointCloudZigma));
    }

    private DataViewHelper dataViewHelper;
    private DataLogger logger;

    public void showDataView(View view){

        // TODO implement data view that does not start up as a new activity

        if(dataViewHelper == null){
            dataViewHelper = new DataViewHelper(this, app, new ShowDataGraphCallback(){

                @Override
                public void dataToGraph(String name){
                    //GridLayout dataListContainer = (GridLayout)findViewById(R.id.logListContainer);
                    //dataListContainer.setVisibility(View.INVISIBLE);
                    uxState = UXState.DATA_GRAPH;
                    GridLayout graphContainer = (GridLayout)findViewById(R.id.graphContainer);
                    graphContainer.setVisibility(View.VISIBLE);
                    logger = app.getDataLogger();

                    if(logger.hasLog(name)) {


                        DataLogger.LogInfoItem item = logger.getLogInfoItem(name);
                        if (item.size > 0) {
                            GraphView graph = (GraphView) findViewById(R.id.graph);
                            setData(item,graph,logger);


                        } else {
                            app.shortToast("Log " + name +" has no data");
                        }
                    } else {
                        app.shortToast("No log with name: " + name);
                    }
                }

            } );
        } else {
            dataViewHelper.update();
        }

        hideMainUX();
        hideDrawer();

        uxState = UXState.DATA_LIST;
        GridLayout dataListContainer = (GridLayout)findViewById(R.id.logListContainer);
        dataListContainer.setVisibility(View.VISIBLE);
    }

    public void hideDrawer(){
        DrawerLayout drawer = (DrawerLayout)findViewById(R.id.drawer_layout);
        drawer.closeDrawer(Gravity.RIGHT);
    }

    public void showDrawer(){
        DrawerLayout drawer = (DrawerLayout)findViewById(R.id.drawer_layout);
        drawer.openDrawer(Gravity.RIGHT);
    }

    public void closeLogList(){
        GridLayout dataListContainer = (GridLayout)findViewById(R.id.logListContainer);
        dataListContainer.setVisibility(View.INVISIBLE);
        showDrawer();
        showMainUX();
    }

    public void closeLogList(View view){
        closeLogList();
    }

    public void closeGraph(){
        uxState = UXState.DATA_LIST;
        GridLayout graphContainer = (GridLayout)findViewById(R.id.graphContainer);
        graphContainer.setVisibility(View.INVISIBLE);
    }

    public void closeGraph(View view){
        closeGraph();
    }


    private void setData(DataLogger.LogInfoItem  item, GraphView graph, DataLogger logger){
        DataPoint[] dataPoints = new DataPoint[item.size];

        switch (item.logType) {
            case DOUBLE:
                double[] doubleValues = logger.getDoubleArray(item.name);
                for(int i =0; i<item.size; i++){
                    dataPoints[i] =  new DataPoint(i,doubleValues[i]);
                }
                break;
            case TIME_SERIES_DOUBLE:
                DataLogger.TimeStampedData<Double>[] tsValues = logger.getTimeStampedDoubleArray(item.name);
                for(int i =0; i<item.size; i++){
                    DataLogger.TimeStampedData<Double> tsData =  tsValues[i];
                    dataPoints[i] =  new DataPoint(tsData.timestamp,tsData.value);
                }
                break;

            default:
                this.app.shortToast("no graph view for log: " + item.name + " with type: " + item.logType.toString());
        }

        LineGraphSeries<DataPoint> series = new LineGraphSeries<>(dataPoints);
        graph.removeAllSeries();
        graph.addSeries(series);
        graph.getViewport().setScalable(true);
        graph.getViewport().setScalableY(true);
    }

    public void showInfoBox(View view) {
        Button button = (Button)view;
        button.setText("Skjul måleverdier");
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideInfoBox(v);
            }
        });
        LinearLayout infoBox = (LinearLayout) findViewById(R.id.infoBox);
        infoBox.setVisibility(View.VISIBLE);

    }

    public void hideInfoBox(View view) {
        Button button = (Button)view;
        button.setText("Vis måleverdier");
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInfoBox(v);
            }
        });
        LinearLayout infoBox = (LinearLayout)findViewById(R.id.infoBox);
        infoBox.setVisibility(View.INVISIBLE);
    }
    // maps component GUI events
    public void clearMarkersClick(View view){
        mapsHelper.clearMarkers();
        Button removeButton = findViewById(R.id.removePointsButton);
        removeButton.setVisibility(View.INVISIBLE);
    }
    public void setCurrentPos(View view){
        boolean succeeded  = mapsHelper.setCurrentPos();
        if(succeeded){
            Button removeButton = findViewById(R.id.removePointsButton);
            removeButton.setVisibility(View.VISIBLE);

            LatLng coord = mapsHelper.getLatLng();
            Pos p = new Pos();

            double h = geoPoseForARCore.getInterpolatedAltitude(coord.latitude, coord.longitude);
            OriginData origin = geoPoseForARCore.getOrigin();
            OriginData.Position pos = origin.latLongHToLocalPosition(coord.latitude,coord.longitude,h);
            p.x = (float)pos.x;
            p.y = (float)pos.y;
            p.z = (float)pos.z;
            //p.x = (float)origin.longitudeToLocalOrigin(coord.longitude);
            //p.y = (float)origin.latitudeToLocalOrigin(coord.latitude);
            //p.z = (float)origin.heightToLocalOrigin(h);
            app.getScene().addCalibrationMarker(p);

        }
    }

    public class SessionKeyCallback{        public void sessionKey(String sessionKey){ }    }

    public class ShowDataGraphCallback{ public void dataToGraph(String logName){}}

    private void requestOrthoSessionKey(final SessionKeyCallback sessionKeyCallback){
        String baseUrl = BorderGoApp.config.getConfigValue(ARConfig.Keys.KARTVERKET_NORGESKART_URL);
        String token =     BorderGoApp.config.getConfigValue(ARConfig.Keys.KARTVERKET_TOKEN_KEY);
        TokenServiceRepository tokenService =  TokenServiceRepository.newInstance(baseUrl);

        try {
            tokenService.getSessionKeyAsync(token, new ITokenServiceRepository.SessionKeyCallback() {
                @Override
                public void onSuccess(@Nullable String response) {
                    sessionKeyCallback.sessionKey(response);                }

                @Override
                public void onError(Throwable error, int code) {
                    //TODO: display error message to user?
                    Log.i("MainActivity", "Ortho Session Key Error: "+error.getMessage());
                }
            });
        } catch (IOException e) {
            e.printStackTrace();

        }
    }

    private static int cutoff = 1000; // distance we dont want want to display data for.

    /**
     * Asynchronous data loader. Also generates graphical objects for the loaded data.
     */
    private class LoadDataTask extends AsyncTask<OriginData, Void, Integer> {

        @Override
        protected Integer doInBackground(OriginData... origins) {
            OriginData origin = origins[0];

            Context context = app.getApplicationContext();

            arScene.clearScene();


            try {
                // Load grid
                DTMGridProvider grid_provider = new DTMGridProvider();
                DTMGrid grid = grid_provider.get_grid(context, origin.lat_0, origin.lon_0, heightService);
                grid.setOrigin(origin);
                geoPoseForARCore.setDtmGrid(grid);

                // Create an artificial terrain point at the feet of the user
                FloatBuffer artificialPoint = FloatBuffer.allocate(4);
                artificialPoint.clear();
                artificialPoint.put(0).put(0).put(-geoPoseForARCore.getDeviceHeight()).put(1).rewind();
                geoPoseForARCore.handlePointCloudObservation(1, artificialPoint, geoPoseForARCore.getDemSigma());

                // Create gridlines for visualization
                float height = (float) origin.h_0;
                int w = (int) grid.getCols();
                int h = (int) grid.getRows();
                Pos[] gridPositions = new Pos[w * h];

                // calculate all positions
                for (int i = 0; i < h; i++) {
                    for (int j = 0; j < w; j++) {
                        int index = i * w + j;
                        Pos p = new Pos();
                        p.z = grid.gridValue(j, i) - height;
                        p.x = grid.gridToWorldX(j, i);
                        p.y = grid.gridToWorldY(j, i);

                        gridPositions[index] = p;
                    }
                }

                // Create lines to draw
                drawPolyLineGrid(gridPositions, w, h);

                // Compute grid indexes
                short [] indexes = new short[(w-1)*(h-1)*2*3];
                int ix = 0;
                for (int i = 0; i < h-1; i++) {
                    for (int j = 0; j < w-1; j++) {
                        short i00 = (short)(i * w + j);
                        short i10 = (short)((i+1) * w + j);
                        short i01 = (short)(i * w + j + 1);
                        short i11 = (short)((i+1) * w + j + 1);
                        indexes[ix++] = i00;
                        indexes[ix++] = i10;
                        indexes[ix++] = i01;
                        indexes[ix++] = i11;
                        indexes[ix++] = i01;
                        indexes[ix++] = i10;
                    }
                }

                // Create terrain surface to draw (used for hidden gridline removal)
                IndexedTriangleMesh mesh = new IndexedTriangleMesh();
                mesh.positions = gridPositions;
                mesh.indexes = indexes;
                arScene.addDepthSurface(mesh);
            }
            catch (IOException ex) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        messageBox("Error loading grid", "Is network enabled?");
                    }
                });
                Log.e(TAG, "Error in loading of grid", ex);
            }


            // Read matrikkel-data:
            String wfsUser = BorderGoApp.config.getConfigValue(ARConfig.Keys.WFS_USER);
            String wfsPass = BorderGoApp.config.getConfigValue(ARConfig.Keys.WFS_PASSWRD);
            String wfsUrl = BorderGoApp.config.getConfigValue(ARConfig.Keys.WFS_BASE_URL);
            DataToDrawProvider data_provider = new DataToDrawProvider(geoPoseForARCore.getDtmGrid(), wfsUser,wfsPass,wfsUrl);
            DataToDrawProvider.DataToDraw data =  data_provider.getData(context, origin.lat_0, origin.lon_0);

            // Draw polylines:

            int num_polylines = 0;

            for ( int i=0; i<data._objects.size() ; i++ ){

                ArrayList<Pos> positions = new ArrayList<Pos>();
                for ( int j=0 ; j<data._objects.get(i)._points.size() ; j++ ){

                    DataToDrawProvider.DataPoint P1 = data._objects.get(i)._points.get(j);


                    double north = origin.latitudeToLocalOrigin(P1._x[0]);
                    double east  = origin.longitudeToLocalOrigin(P1._x[1]);
                    double alt = origin.heightToLocalOrigin(P1._x[2]);


                    if ( Math.abs(north) < cutoff && Math.abs(east) < cutoff && Math.abs(alt) < cutoff ) {
                        Pos p = new Pos();
                        p.x = (float)east;
                        p.y = (float)north;
                        p.z = (float)alt;
                        positions.add(p);
                    } else {
                        // find out if number of we have a viable polyline
                        if(positions.size()>=2){
                            arScene.addPolyLine(positions.toArray(new Pos[positions.size()]));
                            positions = new ArrayList<Pos>();
                        }
                    }
                }

                if(positions.size() >= 2){ // needs at least two points to draw polyline.
                    arScene.addPolyLine(positions.toArray(new Pos[positions.size()]), ArScene.BORDER_COLOR, ArScene.BORDER_WIDTH);
                    num_polylines++;
                }
            }

            // Draw singlepoints:

            for ( int i=0 ; i<data._points.size() ; i++ ) {
                double north = origin.latitudeToLocalOrigin(data._points.get(i)._x[0]);
                double east  = origin.longitudeToLocalOrigin(data._points.get(i)._x[1]);
                double alt = origin.heightToLocalOrigin(data._points.get(i)._x[2]);
                Pos p = new Pos();
                p.x = (float)east;
                p.y = (float)north;
                p.z = (float)alt;
                arScene.addPos(p,ArScene.BORDER_POINT_COLOR, ArScene.BORDER_POINT_WIDTH);
            }

            return num_polylines;
        }

        protected void onPostExecute(Integer num_polylines) {
            String mess = String.format("GNSS ok: %d polylines loaded", num_polylines);
            Toast.makeText(app.getApplicationContext(), mess, Toast.LENGTH_SHORT).show();
        }
    }

    private OriginUpdateListener originUpdateListener = new OriginUpdateListener() {
        @Override
        public void originChanged(OriginData origin) {
            BGState st = BorderGoApp.getBGState();
            st.latGeoPose = origin.lat_0;
            st.lngGeoPose = origin.lon_0;
            st.altGeoPose = origin.h_0;
            st.zigGeoPose = 0;

            new LoadDataTask().execute(origin);
        }
    };


    /**
     * Draw gridlines based on a list of gridpoints
     *
     * @param grid
     * @param w
     * @param h
     */
    private void drawPolyLineGrid(Pos[] grid, int w, int h){
        // Rows "east/west"
        Pos[] positions = new Pos[2*w*h];
        if(grid.length == w*h){
            int f = 1; // forward
            int b = 0; // backward
            int index = 0;
            int resIndex = 0;
            for(int i = 0;i<h;i++){
                if(i%2 == 0){ // to the "east"
                    f = 1;
                    b = 0;
                } else { // the the "west"
                    f = 0;
                    b = 1;
                }

                for(int j = 0;j<w;j++){
                    index = w*i + f*j + b*(w-j-1);
                    positions[resIndex] = grid[index];
                    resIndex++;
                }
            }

            // Columns "north/south"

            for(int i = 0;i<w;i++){
                if(i%2 == 0){ // go down the column
                    f = 1;
                    b = 0;
                } else { // go up the columen
                    f = 0;
                    b = 1;
                }
                for(int j = 0;j<h;j++){
                    index = f*w*j + b*w*(h-j-1) + i;
                    positions[resIndex] = grid[index];
                    resIndex++;
                }
            }
            arScene.addHiddenPolyLine(positions, new GlColor(){{r=0.0f;g=0.6f;b=0f; a=0.1f;}}, ArScene.LINE_WIDTH/4);

        } else {
            Log.i("MainActivity", "drawPolyLineGrid:Faulty gridlength" );
        }
    }

    private void messageBox(String title, String message) {
        // TODO Auto-generated method stub
        Log.d("EXCEPTION: " + title,  message);

        AlertDialog.Builder message_box = new AlertDialog.Builder(this);
        message_box.setTitle(title);
        message_box.setMessage(message);
        message_box.setCancelable(false);
        message_box.setNeutralButton("OK", null);
        message_box.show();
    }







}
