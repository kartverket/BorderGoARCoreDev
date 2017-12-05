package no.kartverket.bordergoarcore;

import android.app.Application;
import android.widget.Toast;

import no.kartverket.bordergoarcore.config.ARConfig;
import no.kartverket.data.DataLogger;
import no.kartverket.glrenderer.ArScene;

/**
 * Created by janvin on 08/05/17.
 */



public class BorderGoApp extends Application {
    static final String TAG = BorderGoApp.class.getSimpleName();

    private DataLogger dataLogger;
    private ArScene scene;

    public static ARConfig config;

    private static boolean _useGPSAndCompass = true;

    private long startTime;
    //private static BorderGoApp bgInstance;
    private static BGState bgState = new BGState();
    private boolean showAerialMap = true;
    private String orthoSessionKey;

    public static final String PREFERENCE_FILE= "BorderGoARCorePreferenceFile";

    public static class PrefNames{
        public static final String DEVICE_HEIGHT = "device_height";
        public static final String DTM_ZIGMA = "dtm_zigma";
        public static final String MAP_CALIBRATION_ZIGMA = "map_calibration_zigma";
        public static final String POINT_CLOUD_ZIGMA = "point_cloud_zigma";
    }

    public static class LoggNames{
        public static final String X = "X";
        public static final String X_TIMESTAMP = "X_TimeStamp";

        public static final String Y = "Y";
        public static final String Y_TIMESTAMP= "Y_TimeStamp";

        public static final String Z = "Z";
        public static final String Z_TIMESTAMPED_DATA = "Z_TimeStamped_Data";

        public static final String Z_TIMESTAMP= "Z_TimeStamp";

        public static final String LAT_GPS = "Latitude_GPS";
        public static final String LNG_GPS = "Longitude_GPS";

        public static final String LAT_GEOPOSE = "Latitude_GeoPose";
        public static final String LNG_GEOPOSE= "Longitude_GeoPose";
        public static final String ZIG_GEOPOSE = "Accuracy_GeoPose";


        public static final String ALT_INTERPOLATED= "Altitude_interpolated_from_grid";


    }

    public BorderGoApp(){
        super();

        dataLogger = new DataLogger(this);
        startTime = System.currentTimeMillis();



    }



    public static BGState getBGState(){
        return bgState;
    }

    public DataLogger getDataLogger() { return dataLogger; }

    public ArScene getScene() { return scene; }
    public void setScene(ArScene scene) { this.scene = scene; }

    public boolean usesAerialMap(){
        return showAerialMap;
    }

    public static boolean usesGPSAndCompass()   {return _useGPSAndCompass;}

    public static void dontUseGpsAndCompass()   { _useGPSAndCompass = false; }

    public static void useGPSAndCompass()       { _useGPSAndCompass = true; }

    //public String getOrthoSessionKey(){ return orthoSessionKey; }
    //public void setOrthoSessionKey(String orthoSessionKey){ this.orthoSessionKey =  orthoSessionKey; }

    public void toggleAerialMap(){
        showAerialMap = !showAerialMap;
    }

    public long getStartTime(){
        return startTime;
    }

    public void shortToast(String message){
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }



}
