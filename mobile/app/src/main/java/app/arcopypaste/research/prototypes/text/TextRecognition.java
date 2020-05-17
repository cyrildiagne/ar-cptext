package app.arcopypaste.research.prototypes.text;

import androidx.annotation.NonNull;

import android.graphics.Bitmap;
import android.util.Log;
import android.media.Image;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import com.google.firebase.ml.common.FirebaseMLException;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;

interface TextRecognitionCallback {
    void onSuccess(String text);

    void onError(String err);
}

public class TextRecognition {
    private static final String TAG = TextRecognition.class.getSimpleName();

    private final FirebaseVisionTextRecognizer detector;

    public TextRecognition() {
        detector = FirebaseVision.getInstance().getOnDeviceTextRecognizer();
    }

    private int degreesToFirebaseRotation(int degrees) {
        switch (degrees) {
            case 0:
                return FirebaseVisionImageMetadata.ROTATION_0;
            case 90:
                return FirebaseVisionImageMetadata.ROTATION_90;
            case 180:
                return FirebaseVisionImageMetadata.ROTATION_180;
            case 270:
                return FirebaseVisionImageMetadata.ROTATION_270;
            default:
                throw new IllegalArgumentException(
                        "Rotation must be 0, 90, 180, or 270.");
        }
    }

    public void detect(Bitmap bmp, int degrees, TextRecognitionCallback cb) {
        // Convert image to firebase image.
        int rotation = degreesToFirebaseRotation(degrees);
        FirebaseVisionImage fbImage = FirebaseVisionImage.fromBitmap(bmp);
                //FirebaseVisionImage.fromMediaImage(image, rotation);
        // Detect.
        detector.processImage(fbImage)
                .addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
                    @Override
                    public void onSuccess(FirebaseVisionText result) {
                        cb.onSuccess(result.getText());
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        cb.onError(e.toString());
                    }
                });
    }
}
