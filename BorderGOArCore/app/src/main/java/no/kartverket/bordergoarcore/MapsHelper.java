package no.kartverket.bordergoarcore;

import android.view.View;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.TileProvider;
import com.google.android.gms.maps.model.UrlTileProvider;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import no.kartverket.bordergoarcore.config.ARConfig;
import no.kartverket.data.dtm.DTMGrid;
import no.kartverket.geodesy.OriginData;
import no.kartverket.geometry.Pos;
import no.kartverket.geopose.Transform;

/**
 * Created by janvin on 23.10.2017.
 */

public class MapsHelper {

    GoogleMap gMap;

    GeoPoseForARCoreManager geoPoseForArCore;

    BorderGoApp app;

    private static String mapServiceUrlTemplateNormal;
    private static String mapServiceUrlTemplateOrtho;
    private static String orthoSessionKey;


    private static Double lat;
    private static Double lng;


    private static ArrayList<MarkerOptions> mapMarkerOptions = new ArrayList();
    private static ArrayList<Marker> mapMarkers = new ArrayList();
    private static ArrayList<Transform.Observation> observations = new ArrayList();

    MapsHelper(GoogleMap googleMap, GeoPoseForARCoreManager geoPoseForArCore, BorderGoApp app){
        this.geoPoseForArCore = geoPoseForArCore;
        this.app = app;
        gMap = googleMap;
        gMap.setMaxZoomPreference(19.0f); // currently the maximum zoom level from Kartverket for the ortho service.
        if(mapServiceUrlTemplateNormal != null && mapServiceUrlTemplateOrtho != null){
            gMap.setMapType(GoogleMap.MAP_TYPE_NONE);
            setTileOverlay();
        } else {
            gMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        }
        refreshMap();
    }

    public void refreshMap(){
        updateMapLocation();
        reInitializeMarkers();
    }

    public static void setLatLng(Double lat, double lng){
        MapsHelper.lat = lat;
        MapsHelper.lng = lng;
    }

    public static void initServiceURLs(){
        mapServiceUrlTemplateOrtho = BorderGoApp.config.getConfigValue(ARConfig.Keys.ORTHO_WMS_SERVICE_URL);
        mapServiceUrlTemplateNormal = BorderGoApp.config.getConfigValue(ARConfig.Keys.BASE_WMS_SERVICE_URL);
    }

    public static void setOrthoSessionKey(String sessionKey){
        MapsHelper.orthoSessionKey = sessionKey;
    }

    public static String getOrthoSessionKey(){
        return orthoSessionKey;
    }

    TileProvider tileProvider;

    public void setTileOverlay(){
        TileProvider tileProvider = new UrlTileProvider(256, 256) {
            @Override
            public synchronized URL getTileUrl(int x, int y, int zoom) {
                String s = "";
                if(app.usesAerialMap() && (orthoSessionKey !=null)){
                    s = String.format(mapServiceUrlTemplateOrtho, x, y, zoom, orthoSessionKey);
                }else{
                    s = String.format(mapServiceUrlTemplateNormal, x, y, zoom);
                }
                URL url = null;
                try {
                    url = new URL(s);
                } catch (MalformedURLException e) {
                    throw new AssertionError(e);
                }
                return url;
            }
        };

        gMap.addTileOverlay(new TileOverlayOptions().tileProvider(tileProvider));
    }


    public void reInitializeMarkers(){
        mapMarkers.clear(); // throw away the old marker references
        for (MarkerOptions mapMarkerOps : mapMarkerOptions) {
            Marker m = gMap.addMarker(mapMarkerOps);
            mapMarkers.add(m); // make new based on mapMarkerOptions
        }
    }

    private void updateMapLocation(){
        if((lat != null) && (lng != null)){
            gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), 19.0f));

        } else { // fallback to Kartverket location
            gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(60.14408, 10.24909), 19.0f));
        }
    }

    public void clearMarkers(){
        for (Marker mapMarker : mapMarkers) {
            mapMarker.remove();
        }

        // Todo: ARCore equivalent
        // Clear observations!
        /*TangoPositionOrientationProvider posOrientationProvider= (TangoPositionOrientationProvider) tangoService.getPositionOrientationProvider();
        posOrientationProvider.removeObservations(observations);*/
        geoPoseForArCore.removeObservations(observations);

        // Clear arrays;
        mapMarkers.clear();
        mapMarkerOptions.clear();
        observations.clear();

        // Ensure that GPS and Compass will be given to position orientation provider.
        BorderGoApp.useGPSAndCompass();



    }

    public LatLng getLatLng(){
        if(gMap != null){
            return gMap.getCameraPosition().target;
        }
        return null;
    }


    public boolean setCurrentPos(){
        LatLng coord = gMap.getCameraPosition().target;
        Transform.Observation obs  = geoPoseForArCore.addMapObservation(coord.latitude, coord.longitude);

        if(obs != null){
            MarkerOptions mo = new MarkerOptions().position(coord);
            mapMarkerOptions.add(mo);
            Marker m = gMap.addMarker(mo);
            mapMarkers.add(m);
            observations.add(obs);
            if(observations.size()>2){
                BorderGoApp.dontUseGpsAndCompass(); // stop listening to gps and compass
            } else {
                BorderGoApp.useGPSAndCompass(); // listen to gps and compass
            }

            return true;

                /*
                TangoPositionOrientationProvider posOrientationProvider= (TangoPositionOrientationProvider) tangoService.getPositionOrientationProvider();
                Transform.Observation obs = posOrientationProvider.handleLatLngHObservation(
                        coord.latitude, coord.longitude,
                        h + tangoService.getDeviceHeight(),
                        tangoService.getMapCalibrationSigma(), tangoService.getDemSigma());
                MarkerOptions mo = new MarkerOptions().position(coord);
                mapMarkerOptions.add(mo);
                Marker m = mMap.addMarker(mo);
                mapMarkers.add(m);
                observations.add(obs);
                if(observations.size()>2){
                    BorderGoApp.dontUseGpsAndCompass(); // stop listening to gps and compass
                } else {
                    BorderGoApp.useGPSAndCompass(); // listen to gps and compass
                }


                Button removeButton = (Button)findViewById(R.id.removePointsButton);
                removeButton.setVisibility(View.VISIBLE);

                return;

            }*/
        }
        return false;


    }




}
