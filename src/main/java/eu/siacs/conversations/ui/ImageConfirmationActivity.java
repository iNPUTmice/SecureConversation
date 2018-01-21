package eu.siacs.conversations.ui;

import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;

public class ImageConfirmationActivity extends XmppActivity {

    private List<Uri> mPendingImageUris;
    private ImageView imageView;
    private ImageButton cancelButton, acceptButton;
    private int imageCount, i = 0;

    @Override
    protected void refreshUiReal() {}

    @Override
    void onBackendConnected() {}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_confirmation);

        mPendingImageUris = getIntent().getParcelableArrayListExtra("confirmImageUris");
        imageView = (ImageView) findViewById(R.id.imageConfirmation_imageView);
        cancelButton = (ImageButton) findViewById(R.id.imageConfirmation_cancelImageButton);
        acceptButton = (ImageButton) findViewById(R.id.imageConfirmation_acceptImageButton);
        imageCount = mPendingImageUris.size();
    }

    @Override
    protected void onStart(){
        super.onStart();

        imageView.setImageURI(mPendingImageUris.get(i));

        acceptButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (++i < imageCount){
                    imageView.setImageURI(mPendingImageUris.get(i));
                }
                else{
                    setResultAndFinish();
                }
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPendingImageUris.remove(i);
                imageCount--;
                if (i < imageCount){
                    imageView.setImageURI(mPendingImageUris.get(i));
                }
                else{
                    setResultAndFinish();
                }
            }
        });
    }

    @Override
    protected void onStop(){
        unregisterListeners();
        super.onStop();
    }

    private void setResultAndFinish(){
        Intent resultIntent = new Intent();
        resultIntent.putParcelableArrayListExtra("pendingImageUris", (ArrayList<Uri>)mPendingImageUris);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

}
