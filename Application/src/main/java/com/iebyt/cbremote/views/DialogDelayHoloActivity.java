package com.iebyt.cbremote.views;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.NumberPicker;

import com.iebyt.cbremote.CBRBluetoothLeService;
import com.iebyt.cbremote.R;

public class DialogDelayHoloActivity extends AppCompatActivity {

    private NumberPicker numberPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dialog_delay_holo);
        numberPicker=findViewById(R.id.delayNumPicker);
        numberPicker.setMaxValue(59);
        numberPicker.setMinValue(0);
    }

    public void cancelDelayDialog(View v){
        finish();
    }

    public void setDelayDialog(View v){
        CBRBluetoothLeService.setDelayValue(numberPicker.getValue());
        CBRBluetoothLeService.currentProgressValue = 0;
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // TODO read and display last selected value in number picker
    }
}
