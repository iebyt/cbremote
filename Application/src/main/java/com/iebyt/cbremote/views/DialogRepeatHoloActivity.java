package com.iebyt.cbremote.views;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.RadioGroup;

import com.iebyt.cbremote.CBRBluetoothLeService;
import com.iebyt.cbremote.R;

public class DialogRepeatHoloActivity extends AppCompatActivity {

    private NumberPicker numPickerRptIntHour;
    private NumberPicker numPickerRptIntMin;
    private NumberPicker numPickerRptIntSec;


    private NumberPicker numPickerRptDurHour;
    private NumberPicker numPickerRptDurMin;
    private NumberPicker numPickerRptDurSec;

    private EditText countEditText;

    private boolean isDuration = false;

    private RadioGroup radioGroup;

    private LinearLayout linCountTimesGroup;
    private LinearLayout linCountDurationGroup;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dialog_repeat_holo);

        countEditText = findViewById(R.id.rpt_count_times_edtTxt);
        numPickerRptIntHour = findViewById(R.id.rpt_int_hour_picker);
        numPickerRptIntMin = findViewById(R.id.rpt_int_min_picker);
        numPickerRptIntSec = findViewById(R.id.rpt_int_sec_picker);

        numPickerRptIntHour.setMinValue(0);
        numPickerRptIntMin.setMinValue(0);
        numPickerRptIntSec.setMinValue(0);

        numPickerRptIntHour.setMaxValue(23);
        numPickerRptIntMin.setMaxValue(59);
        numPickerRptIntSec.setMaxValue(59);


        numPickerRptDurHour = findViewById(R.id.rpt_dur_hour_picker);
        numPickerRptDurMin = findViewById(R.id.rpt_dur_min_picker);
        numPickerRptDurSec = findViewById(R.id.rpt_dur_sec_picker);

        numPickerRptDurHour.setMinValue(0);
        numPickerRptDurMin.setMinValue(0);
        numPickerRptDurSec.setMinValue(0);

        numPickerRptDurHour.setMaxValue(23);
        numPickerRptDurMin.setMaxValue(59);
        numPickerRptDurSec.setMaxValue(59);

        linCountTimesGroup = findViewById(R.id.lin_count_times_group);
        linCountDurationGroup = findViewById(R.id.lin_count_duration_group);

        radioGroup = findViewById(R.id.radiogroup_repeat);
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup rg, int i) {
                if (i == R.id.radio_count) {
                    linCountDurationGroup.setVisibility(View.GONE);
                    linCountTimesGroup.setVisibility(View.VISIBLE);
                    isDuration = false;
                }
                if (i == R.id.radio_duration) {
                    linCountTimesGroup.setVisibility(View.GONE);
                    linCountDurationGroup.setVisibility(View.VISIBLE);
                    isDuration = true;
                }
            }
        });
    }

    public void cancelRepeatDialog(View v) {
        finish();
    }

    public void setRepeatOptions(View v) {
        int intervalHour = 0;
        int intervalMin = 0;
        int intervalSec = 0;
        int totalIntervalSecs = 0;

        if (numPickerRptIntHour.getValue() > 0) {
            intervalHour = numPickerRptIntHour.getValue();
        }
        if (numPickerRptIntMin.getValue() > 0) {
            intervalMin = numPickerRptIntMin.getValue();
        }
        if (numPickerRptIntSec.getValue() > 0) {
            intervalSec = numPickerRptIntSec.getValue();
        }

        totalIntervalSecs = (intervalHour * 3600) + (intervalMin * 60) + (intervalSec);
        CBRBluetoothLeService.setIntervalValue(totalIntervalSecs);


        if (isDuration) {
            int durationHour = 0;
            int durationMin = 0;
            int durationSec = 0;
            int totalDurationSecs = 0;
            if (numPickerRptDurHour.getValue() > 0) {
                durationHour = numPickerRptDurHour.getValue();
            }
            if (numPickerRptDurMin.getValue() > 0) {
                durationMin = numPickerRptDurMin.getValue();
            }
            if (numPickerRptDurSec.getValue() > 0) {
                durationSec = numPickerRptDurSec.getValue();
            }
            totalDurationSecs = (durationHour * 3600) + (durationMin * 60) + (durationSec);
            CBRBluetoothLeService.setRepeatCountValue(totalDurationSecs / totalIntervalSecs);
        } else {
            int count = 0;
            if (countEditText.getText().toString().length() > 0 && Integer.parseInt(countEditText.getText().toString()) > 0) {
                count = Integer.parseInt(countEditText.getText().toString());
            }
            CBRBluetoothLeService.setRepeatCountValue(count);
        }
        CBRBluetoothLeService.currentProgressValue = 0;
        finish();
    }
}
