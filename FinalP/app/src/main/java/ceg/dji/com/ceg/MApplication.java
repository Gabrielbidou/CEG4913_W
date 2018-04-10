package ceg.dji.com.ceg;

import android.app.Application;
        import android.content.Context;
        import com.secneo.sdk.Helper;
        import android.support.multidex.MultiDex;


public class MApplication extends Application {

    private DroneApp setupConnection;
    @Override
    protected void attachBaseContext(Context paramContext) {
        super.attachBaseContext(paramContext);
        Helper.install(MApplication.this);

        if (setupConnection == null) {
            setupConnection = new DroneApp();
            setupConnection.setContext(this);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setupConnection.onCreate();
     //   MultiDex.install(this);
    }

}
