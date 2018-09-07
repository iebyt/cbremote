package com.iebyt.cbremote.views;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;

import com.iebyt.cbremote.CBRBluetoothLeService;
import com.iebyt.cbremote.R;

public class DialogBulbHoloActivity extends AppCompatActivity {

    private RadioGroup radioGroupBulb;

    private LinearLayout linGroupAutoBulb;
    private LinearLayout linGroupManualBulb;

    private EditText shutterTimerTxt;

    private boolean isShutterAuto=false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dialog_bulb_holo);

        linGroupAutoBulb=findViewById(R.id.lin_bulb_auto_group);
        linGroupManualBulb=findViewById(R.id.lin_bulb_manual_group);

        shutterTimerTxt=findViewById(R.id.edt_bulb_timer);

        radioGroupBulb=findViewById(R.id.radiogroup_bulb);
        radioGroupBulb.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                if (i == R.id.radio_bulb_auto) {
                    linGroupManualBulb.setVisibility(View.GONE);
                    linGroupAutoBulb.setVisibility(View.VISIBLE);
                    isShutterAuto=true;
                }
                if (i == R.id.radio_bulb_manual) {
                    linGroupAutoBulb.setVisibility(View.GONE);
                    linGroupManualBulb.setVisibility(View.VISIBLE);
                    isShutterAuto=false;
                }
            }
        });
    }


    public void cancelBulbDialog(View v){
        finish();
    }

    public void setBulbOptions(View v){
        int shutterDelay=0;
        if(isShutterAuto){
            if (shutterTimerTxt.getText().toString().length() > 0 && Integer.parseInt(shutterTimerTxt.getText().toString()) > 0) {
                shutterDelay = Integer.parseInt(shutterTimerTxt.getText().toString());
            }
        }
        CBRBluetoothLeService.setBulbConfig(isShutterAuto,shutterDelay);
        CBRBluetoothLeService.currentProgressValue = 0;
        finish();
    }
}
