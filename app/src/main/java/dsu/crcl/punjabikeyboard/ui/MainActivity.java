package dsu.crcl.punjabikeyboard.ui;

import android.content.Intent;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import dsu.crcl.punjabikeyboard.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void open_settings(View view) {
        Intent settings = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
        startActivityForResult(settings, 1);
        Toast.makeText(this, "Select Punjabi مکمل Keyboard and press back", Toast.LENGTH_LONG).show();
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

    }

    public void open_enabler(View view) {
        InputMethodManager imeManager = (InputMethodManager) getApplicationContext().getSystemService(INPUT_METHOD_SERVICE);
        Toast.makeText(this, "Choose Punjabi مکمل Keyboard and press back", Toast.LENGTH_LONG).show();
        imeManager.showInputMethodPicker();
    }

}
