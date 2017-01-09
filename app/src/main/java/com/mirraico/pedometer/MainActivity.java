package com.mirraico.pedometer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView countView; //计步值界面显示
    private int count = 0; //计步值

    Intent serviceIntent; //计步服务
    private PedometerReceiver pedometerReceiver; //计步广播接收器

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        countView = (TextView) findViewById(R.id.count);

        serviceIntent = new Intent(this, PedometerService.class);
        startService(serviceIntent);

        pedometerReceiver = new PedometerReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(PedometerService.ACTION);
        MainActivity.this.registerReceiver(pedometerReceiver, filter);
    }

    /* 好像和service之间没法传递handler过去，改用广播了
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            count += 1;
            countView.setText(count + "");
        }
    };
    */

    public class PedometerReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int ret = intent.getIntExtra("cnt", 1);
            count += ret;
            countView.setText(count + "");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopService(serviceIntent);
    }
}
