/*
	Copyright (c) 2013-2016 EasyDarwin.ORG.  All rights reserved.
	Github: https://github.com/EasyDarwin
	WEChat: EasyDarwin
	Website: http://www.easydarwin.org
*/

package org;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dds.skywebrtc.PrefSingleton;

import org.easydarwin.easypusher.R;

/**
 * 设置页
 * */
public class SetupActivity extends AppCompatActivity implements View.OnClickListener{

    private ImageButton Back;
    private EditText editText;
    private Button resolv_one,resolv_two,resolv_thr;
    private Button program_one,program_two,program_thr,program_fou,program_fiv,program_six;
    private Button Enter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        Back = findViewById(R.id.back);
        Back.setOnClickListener(this);
        Enter = findViewById(R.id.enter);
        Enter.setOnClickListener(this);
        editText = findViewById(R.id.url);
        editText.setText(PrefSingleton.getInstance().getString("Url"));
        init_resolv(); // 初始化分辨率控件
        init_program(); // 初始化键盘方案控件
    }

    private int Resolv = 0;
    private int Program = 0;
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.back:
                finish();
                break;
            case R.id.resolv_one:
                resolv_one.setBackground(getResources().getDrawable(R.drawable.button_on));
                resolv_two.setBackground(getResources().getDrawable(R.drawable.button_off));
                resolv_thr.setBackground(getResources().getDrawable(R.drawable.button_off));
                Resolv = 1;
                break;
            case R.id.resolv_two:
                resolv_one.setBackground(getResources().getDrawable(R.drawable.button_off));
                resolv_two.setBackground(getResources().getDrawable(R.drawable.button_on));
                resolv_thr.setBackground(getResources().getDrawable(R.drawable.button_off));
                Resolv = 2;
                break;
            case R.id.resolv_thr:
                resolv_one.setBackground(getResources().getDrawable(R.drawable.button_off));
                resolv_two.setBackground(getResources().getDrawable(R.drawable.button_off));
                resolv_thr.setBackground(getResources().getDrawable(R.drawable.button_on));
                Resolv = 3;
                break;

            case R.id.program_one:
                program_one.setBackground(getResources().getDrawable(R.drawable.button_on));
                program_two.setBackground(getResources().getDrawable(R.drawable.button_off));
                program_thr.setBackground(getResources().getDrawable(R.drawable.button_off));
                program_fou.setBackground(getResources().getDrawable(R.drawable.button_off));
                program_fiv.setBackground(getResources().getDrawable(R.drawable.button_off));
                program_six.setBackground(getResources().getDrawable(R.drawable.button_off));
                Program = 1;
                break;
            case R.id.program_two:
                program_one.setBackground(getResources().getDrawable(R.drawable.button_off));
                program_two.setBackground(getResources().getDrawable(R.drawable.button_on));
                program_thr.setBackground(getResources().getDrawable(R.drawable.button_off));
                program_fou.setBackground(getResources().getDrawable(R.drawable.button_off));
                program_fiv.setBackground(getResources().getDrawable(R.drawable.button_off));
                program_six.setBackground(getResources().getDrawable(R.drawable.button_off));
                Program = 2;
                break;
            case R.id.program_thr:
                program_one.setBackground(getResources().getDrawable(R.drawable.button_off));
                program_two.setBackground(getResources().getDrawable(R.drawable.button_off));
                program_thr.setBackground(getResources().getDrawable(R.drawable.button_on));
                program_fou.setBackground(getResources().getDrawable(R.drawable.button_off));
                program_fiv.setBackground(getResources().getDrawable(R.drawable.button_off));
                program_six.setBackground(getResources().getDrawable(R.drawable.button_off));
                Program = 3;
                break;
            case R.id.program_fou:
                program_one.setBackground(getResources().getDrawable(R.drawable.button_off));
                program_two.setBackground(getResources().getDrawable(R.drawable.button_off));
                program_thr.setBackground(getResources().getDrawable(R.drawable.button_off));
                program_fou.setBackground(getResources().getDrawable(R.drawable.button_on));
                program_fiv.setBackground(getResources().getDrawable(R.drawable.button_off));
                program_six.setBackground(getResources().getDrawable(R.drawable.button_off));
                Program = 4;
                break;
            case R.id.program_fiv:
                program_one.setBackground(getResources().getDrawable(R.drawable.button_off));
                program_two.setBackground(getResources().getDrawable(R.drawable.button_off));
                program_thr.setBackground(getResources().getDrawable(R.drawable.button_off));
                program_fou.setBackground(getResources().getDrawable(R.drawable.button_off));
                program_fiv.setBackground(getResources().getDrawable(R.drawable.button_on));
                program_six.setBackground(getResources().getDrawable(R.drawable.button_off));
                Program = 5;
                break;
            case R.id.program_six:
                program_one.setBackground(getResources().getDrawable(R.drawable.button_off));
                program_two.setBackground(getResources().getDrawable(R.drawable.button_off));
                program_thr.setBackground(getResources().getDrawable(R.drawable.button_off));
                program_fou.setBackground(getResources().getDrawable(R.drawable.button_off));
                program_fiv.setBackground(getResources().getDrawable(R.drawable.button_off));
                program_six.setBackground(getResources().getDrawable(R.drawable.button_on));
                Program = 6;
                break;
            case R.id.enter:
                Save_data();
                Toast.makeText(getApplicationContext(), "设置已保存", Toast.LENGTH_SHORT).show();
                break;
                default:
                    break;
        }
    }

    public void init_resolv(){
        resolv_one = findViewById(R.id.resolv_one);
        resolv_one.setOnClickListener(this);
        resolv_two = findViewById(R.id.resolv_two);
        resolv_two.setOnClickListener(this);
        resolv_thr = findViewById(R.id.resolv_thr);
        resolv_thr.setOnClickListener(this);
        int resolv = PrefSingleton.getInstance().getInt("resolv");
        switch(resolv){
            case 1:
                resolv_one.setBackground(getResources().getDrawable(R.drawable.button_on));
                break;
            case 2:
                resolv_two.setBackground(getResources().getDrawable(R.drawable.button_on));
                break;
            case 3:
                resolv_thr.setBackground(getResources().getDrawable(R.drawable.button_on));
                break;
        }
    }

    public void init_program() {
        program_one = findViewById(R.id.program_one);
        program_one.setOnClickListener(this);
        program_two = findViewById(R.id.program_two);
        program_two.setOnClickListener(this);
        program_thr = findViewById(R.id.program_thr);
        program_thr.setOnClickListener(this);
        program_fou = findViewById(R.id.program_fou);
        program_fou.setOnClickListener(this);
        program_fiv = findViewById(R.id.program_fiv);
        program_fiv.setOnClickListener(this);
        program_six = findViewById(R.id.program_six);
        program_six.setOnClickListener(this);
        if (!PrefSingleton.getInstance().getBoolean("key_mode")) {
            program_one.setEnabled(false);
            program_two.setEnabled(false);
            program_thr.setEnabled(false);
            program_fou.setEnabled(false);
            program_fiv.setEnabled(false);
            program_six.setEnabled(false);
            PrefSingleton.getInstance().remove("program");
        }
        int program = PrefSingleton.getInstance().getInt("program");
        switch(program){
            case 1:
                program_one.setBackground(getResources().getDrawable(R.drawable.button_on));
                break;
            case 2:
                program_two.setBackground(getResources().getDrawable(R.drawable.button_on));
                break;
            case 3:
                program_thr.setBackground(getResources().getDrawable(R.drawable.button_on));
                break;
            case 4:
                program_fou.setBackground(getResources().getDrawable(R.drawable.button_on));
                break;
            case 5:
                program_fiv.setBackground(getResources().getDrawable(R.drawable.button_on));
                break;
            case 6:
                program_six.setBackground(getResources().getDrawable(R.drawable.button_on));
                break;
        }
    }

    public void Save_data() {
        if (Resolv == 0) {
            Resolv = PrefSingleton.getInstance().getInt("resolv");
        }
        if (Resolv == 1) {
            PrefSingleton.getInstance().putInt("resolv",1);
        } else if (Resolv == 2) {
            PrefSingleton.getInstance().putInt("resolv",2);
        } else {
            PrefSingleton.getInstance().putInt("resolv",3);
        }

        if (Program == 0) {
            Program = PrefSingleton.getInstance().getInt("program");
        }
        if (Program == 1) {
            PrefSingleton.getInstance().putInt("program",1);
        } else if (Program == 2) {
            PrefSingleton.getInstance().putInt("program",2);
        } else if (Program == 3) {
            PrefSingleton.getInstance().putInt("program",3);
        } else if (Program == 4) {
            PrefSingleton.getInstance().putInt("program",4);
        } else if (Program == 5) {
            PrefSingleton.getInstance().putInt("program",5);
        } else {
            PrefSingleton.getInstance().putInt("program",6);
        }

        PrefSingleton.getInstance().putString("Url",editText.getText().toString());
    }

}