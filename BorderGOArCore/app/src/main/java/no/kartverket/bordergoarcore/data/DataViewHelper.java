package no.kartverket.bordergoarcore.data;

import android.app.Activity;
import android.widget.ListView;

import no.kartverket.bordergoarcore.BorderGoApp;
import no.kartverket.bordergoarcore.MainActivity;
import no.kartverket.bordergoarcore.R;
import no.kartverket.data.DataLogger;

/**
 * Created by janvin on 09.11.2017.
 */

public class DataViewHelper {
    private BorderGoApp app;
    private DataLogger logger;
    private Activity activity;
    private MainActivity.ShowDataGraphCallback callback;

    public DataViewHelper(Activity rootActivity, BorderGoApp borderGoApp, MainActivity.ShowDataGraphCallback showDataGraphCallback){
        app = borderGoApp;
        activity = rootActivity;
        callback = showDataGraphCallback;

        logger = app.getDataLogger();
        update();
    }

    public void update(){
        LogItemAdapter adapter = new LogItemAdapter(activity, logger, callback);
        ListView listView = (ListView) activity.findViewById(R.id.logList);
        listView.setAdapter(adapter);

    }

}
