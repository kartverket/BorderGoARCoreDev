package no.kartverket.geopose;

/**
 * Created by janvin on 12.10.2017.
 */

public class Pose {
    private float[] _rotation = new float[4]; // Rotation is a quaternion following the Hamilton convention (x,y,z,w)
    private float[] _translation = new float[3]; // x,y,z

    public Pose( float[] translation, float[] rotation){
        this.setRotation(rotation);
        this.setTranslation(translation);
    }

    public double[] roationtAsDouble(){

        double[] r = new double[4];
        r[0] = _rotation[0];
        r[1] = _rotation[1];
        r[2] = _rotation[2];
        r[3] = _rotation[3];
        return r;
    }

    public double[] translationtAsDouble(){
        double[] t = new double[3];
        t[0] = _translation[0];
        t[1] = _translation[1];
        t[2] = _translation[2];
        return t;
    }

    public float[]  getRotation(){        return _rotation;    }
    public float[]  getTranslation(){     return _translation;    }

    public void setRotation(float[] rotation){
        _rotation[0] = rotation[0];
        _rotation[1] = rotation[1];
        _rotation[2] = rotation[2];
        _rotation[3] = rotation[3];
    }

    public void setTranslation(float[] translation){
        _translation[0] = translation[0];
        _translation[1] = translation[1];
        _translation[2] = translation[2];


    }
}
