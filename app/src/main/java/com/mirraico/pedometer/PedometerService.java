package com.mirraico.pedometer;

import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

public class PedometerService extends Service implements StepListener {

    public static String ACTION = "com.mirraico.action.ADD_STEP";

    private SensorChangeListener sensorChangeListener;
    private SensorManager sensorManager;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        //获取传感器的服务，初始化传感器
        sensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
        sensorChangeListener = new SensorChangeListener(this);
        //注册传感器，注册监听器
        sensorManager.registerListener(sensorChangeListener,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_UI); //在我手机上测试UI是最精准的，太快太慢都不好
        //Log.e("PedometerService", "REGISTER SENSOR_LISTENER SUCCESSFULLY");
    }

    //步数增加后回调，这里发送一个广播通知主界面
    @Override
    public void onStep(int cnt) {
        Log.e("PedometerService", "ADD STEP");
        Intent intent = new Intent();
        intent.setAction(PedometerService.ACTION);
        intent.putExtra("cnt", cnt);
        sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(null != sensorManager){
            sensorManager.unregisterListener(sensorChangeListener);
        }
    }
}
