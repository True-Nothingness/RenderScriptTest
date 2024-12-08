package com.light.renderscripttest;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TextView textView = findViewById(R.id.textView);
        TextView textView2 = findViewById(R.id.textView2);
        TextView textView3 = findViewById(R.id.textView3);
        Intent intent1 = new Intent(this, GrayscaleActivity.class);
        Intent intent2 = new Intent(this, BlurActivity.class);
        Intent intent3 = new Intent(this, SobelActivity.class);
        textView.setOnClickListener(v -> showOptions(intent1));
        textView2.setOnClickListener(v -> showOptions(intent2));
        textView3.setOnClickListener(v -> showOptions(intent3));
    }
    private void showOptions(Intent intent){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose image size")
                .setItems(new String[]{"256x256", "640x480", "1024x1024", "1920x1080"}, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            intent.putExtra("size", 256);
                            startActivity(intent);
                            break;
                        case 1:
                            intent.putExtra("size", 640);
                            startActivity(intent);
                            break;
                        case 2:
                            intent.putExtra("size", 1024);
                            startActivity(intent);
                            break;
                        case 3:
                            intent.putExtra("size", 1920);
                            startActivity(intent);
                            break;
                    }
                })
                .setCancelable(true); // Optional: dialog can be dismissed by tapping outside

        AlertDialog dialog = builder.create();
        dialog.show();

    }
}