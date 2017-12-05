package no.kartverket.geopose;

import no.kartverket.geodesy.OriginData;

/**
 * Created by runaas on 06.09.2017.
 */

public interface TransformUpdateListener extends OriginUpdateListener {
    public void transformChanged(OriginData origin, float[] mat44);
}
