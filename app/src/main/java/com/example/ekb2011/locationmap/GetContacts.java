package com.example.ekb2011.locationmap;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class GetContacts extends AppCompatActivity{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_get_contacts);
        EditText editText=(EditText)findViewById(R.id.editText);
        EditText editText2=(EditText)findViewById(R.id.editText2);
        EditText editText3=(EditText)findViewById(R.id.editText3);
        EditText editText4=(EditText)findViewById(R.id.editText4);
        Button applyButton=(Button)findViewById(R.id.applyButton);

        String contact1=editText.getText().toString();
        String contact2=editText2.getText().toString();
        String contact3=editText3.getText().toString();
        String contact4=editText4.getText().toString();

        Intent intt=new Intent(GetContacts.this, MainActivity.class);
        intt.putExtra("contact1", contact1);
        intt.putExtra("contact1", contact2);
        intt.putExtra("contact1", contact3);
        intt.putExtra("contact1", contact4);
        GetContacts.this.startActivity(intt);
        applyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent intent=new Intent(GetContacts.this, MainActivity.class);
                GetContacts.this.startActivity(intent);
                AlertDialog.Builder builder = new AlertDialog.Builder(GetContacts.this);
                builder.setMessage("Setting Contacts has been completed!")
                        .setPositiveButton("OK", null)
                        .create()
                        .show();
            }
        });
    }
}
