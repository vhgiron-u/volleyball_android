package com.example.volleyball00;

import static com.example.volleyball00.Constants.MODEL_HEIGHT;
import static com.example.volleyball00.Constants.MODEL_WIDTH;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;



public class LocalEstimation extends AppCompatActivity {

    private int RESULT_LOAD_FILE = 1;
    private int NUM_KEYFRAMES = 5;
    private TextView textViewFileName;
    private ImageView imageViewKeyFrame;
    Boolean estimationDisplayed = false;
    private String imagePath = ""; //ruta de la imagen a la que se estimara la pose


    private int currentPhase = 0; //current phase/keyframe for the selector
    private TextView textViewCurKeyFrame;
    private Instant startTimeRequest;

    /** An object for the DLuvizon2D library.    */
    private DLuvizon2D dLuvizon2D;

    /** Threshold for confidence score. */
    private Float minConfidence = 0.5f;

    /** Radius of circle used to draw keypoints.  */
    private Float circleRadius = 8.0f;

    /** Paint class holds the style and color information to draw geometries,text and bitmaps. */
    private Paint paint = new Paint();

    //:vh: editing this is enough to draw/notdraw lines
    /** List of body joints that should be connected.    */
    private List<Pair> bodyJoints;



    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local);
        textViewFileName = findViewById(R.id.textViewFileName);
        imageViewKeyFrame = findViewById(R.id.imageView);
        checkRequestStoragePermission(this); //solicitamos permiso para usar almacenamiento


        //inicializacion para estim de pose
        dLuvizon2D = new DLuvizon2D(
                getApplicationContext(),
                "modelo2D.tflite",
                Device.CPU);
        initBodyJoints();

        //Log.i("cachedir",getApplicationContext().getCacheDir().toString()); //"/data/user/0/com.example.volleyball00/cache"
        //boton para regresar al procesamiento por servidor con requests
        Button btnNavServer =(Button)findViewById(R.id.buttonGoServer);
        btnNavServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LocalEstimation.this, MainActivity.class);
                startActivity(intent);
            }
        });

        Button btnBrowse = (Button)findViewById(R.id.buttonBrowse);
        btnBrowse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                browseImage(v);
            }
        });

        Button btnPredict = (Button)findViewById(R.id.buttonPredict);
        btnPredict.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSingleFrameProcess(v);
            }
        });

    }

    private void initBodyJoints(){
        bodyJoints = new ArrayList<>();
        bodyJoints.add(new Pair(BodyPart.PA16J_00, BodyPart.PA16J_01));
        bodyJoints.add(new Pair(BodyPart.PA16J_01, BodyPart.PA16J_02));
        bodyJoints.add(new Pair(BodyPart.PA16J_02, BodyPart.PA16J_03));
        bodyJoints.add(new Pair(BodyPart.PA16J_04, BodyPart.PA16J_06));
        bodyJoints.add(new Pair(BodyPart.PA16J_06, BodyPart.PA16J_08));
        bodyJoints.add(new Pair(BodyPart.PA16J_05, BodyPart.PA16J_07));
        bodyJoints.add(new Pair(BodyPart.PA16J_07, BodyPart.PA16J_09));
        bodyJoints.add(new Pair(BodyPart.PA16J_10, BodyPart.PA16J_12));
        bodyJoints.add(new Pair(BodyPart.PA16J_12, BodyPart.PA16J_14));
        bodyJoints.add(new Pair(BodyPart.PA16J_11, BodyPart.PA16J_13));
        bodyJoints.add(new Pair(BodyPart.PA16J_13, BodyPart.PA16J_15));

    }


    //Funcion onClick. Se activa al dar tap al boton de explorar
    public void browseVideo(View v){
        Intent intent = new Intent(Intent.ACTION_PICK,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, RESULT_LOAD_FILE);
    }
    //Funcion onClick. Se activa al dar tap al boton de explorar
    public void browseImage(View v){
        Intent intent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, RESULT_LOAD_FILE);
    }

    //Funcion onClick. Se activa al dar tap al boton de Obtener Keyframes
    public void startKeyFramesProcess(View v){

        //parte 1: splitVideo
        //por el momento se llamara a la estimacion de pose tambien desde
        //requestSplitVideo debido a que aun no se manejar paralelismo en android


        ////parte 2: estimacion de pose

        ////descomentar lo siguiente si se quiere probar solo mostrar imagen o estimacion de pose
//        String filePath = "data/data/com.example.volleyball00/cache/image_00100.jpg";
//        String filePath = "/storage/emulated/0/Download/volley0/imgs/fase-1__image_00171.jpeg"; //:deb:
//        drawImageFromPath(filePath);
//        requestSingleImage("image_00011.jpg", 0, true);
    }





    //funcion llamada del startActivityForResult de browseVideo browseImage
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        //from browseVideo
        if (requestCode == RESULT_LOAD_FILE && resultCode == RESULT_OK && null != data) {
            imagePath = getImagePath(data);
            Log.i("videoPath", "selected video: " + imagePath);
            Log.i("onActivityResult", "imagepath: " + imagePath);
            //reducimos el texto a mostrar:
            int maxchars = 38;
            int start = imagePath.length() - maxchars;
            String showtext = "..."+imagePath.substring(start, imagePath.length());

            textViewFileName.setText(showtext);

            //dibujamos imagen cargada
            drawImageFromPath(imagePath);
        }
    }

    //funciones para segmentacion: envio de video, ruta de video
    //

    private  String getImagePath( Intent data){
        Uri selectedImage = data.getData();
        String[] filePathColumn = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(selectedImage,
                filePathColumn, null, null, null);
        cursor.moveToFirst();
        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);

        String filePath = cursor.getString(columnIndex);
        cursor.close();
        return filePath;
    }

    //Funcion onClick. Se activa al dar tap al boton "Obtener Keyframes".
    public void startSingleFrameProcess(View v){
        //estimamos pose:
        if (!imagePath.trim().isEmpty()) {

            String path = imagePath;

            startTimeRequest = Instant.now(); //:deb:


            Bitmap bmp = BitmapFactory.decodeFile(path);


            Log.i("singleFrameProcess", "calling preprocessing and estimating pose");
            Bitmap bitmapSkeleton = processImage(bmp);
            //save drawn skeleton:
            try {
                bitmapToFile(bitmapSkeleton, "latest.jpg");
            } catch (Exception e) {
                Log.e("saveImage", "sth failed: " + e.getMessage());
            }


            imageViewKeyFrame.setImageBitmap(bitmapSkeleton);
            estimationDisplayed = true;
            Log.i("response+pose:", "time: " + (Duration.between(startTimeRequest, Instant.now())));
        }
        else
            Log.i("tapEstimate","attempt to call process before selecting video");



    }



    public static List<String> jsonStringSortedKeys(String jsonStringObj) throws Exception{
        JSONObject jsonResponse = new JSONObject(jsonStringObj);
        Iterator<String> keys = jsonResponse.keys();
        List<String> sortedKeys = new ArrayList<>();
        while (keys.hasNext())
            sortedKeys.add(keys.next());
        Collections.sort(sortedKeys);
        return sortedKeys;
    }




    //funciones para el lado de estimacion de pose: solicitar imagenes, mostrar imagenes
    public void drawImageFromPath(String filePath){
        Bitmap bitmap = BitmapFactory.decodeFile(filePath);
        imageViewKeyFrame.setImageBitmap(bitmap);
    }


    /* Preprocess image and run inference. Returns bitmap with joints and skeleton drawn.*/
    private Bitmap processImage(Bitmap bitmap){
//        Bitmap croppedBitmap  = cropBitmap(bitmap);
        Bitmap croppedBitmap  = bitmap;
        // Created scaled version of bitmap for model input.
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, MODEL_WIDTH, MODEL_HEIGHT, true);

        // Perform inference. //:vh: AQUI SE REALIZA LA INFERENCIA
        //Person will contain body joints and score (if given)
        Person person = dLuvizon2D.estimateSinglePose(scaledBitmap);

        Bitmap bitmapSkeleton = draw(bitmap, person, scaledBitmap);
        return bitmapSkeleton;

    }

    /* draw the skeleton and return a bitmap with the skeleton drawn.*/
    private Bitmap draw(Bitmap bitmap, Person person, Bitmap scaledBitmap){


        Bitmap baseBitmap = Bitmap.createBitmap(bitmap.getWidth(),
                bitmap.getHeight(),
                Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(baseBitmap);



        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        Integer screenWidth;
        Integer screenHeight;
        Integer left;
        Integer right;
        Integer top;
        Integer bottom;


        //:deb: 'heredamos' los valores del canvas
        screenWidth = canvas.getWidth();
        screenHeight = canvas.getHeight();
        left = 0;
        top = 0;

        right = left + screenWidth;
        bottom = top + screenHeight;


        setPaint();

        canvas.drawBitmap(
                scaledBitmap,
                new Rect(0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight()),
                new Rect(left, top, right, bottom),
                paint
        );

        Float widthRatio = ((float)screenWidth) / MODEL_WIDTH;
        Float heightRatio = ((float)screenHeight) / MODEL_HEIGHT;

        // Draw key points over the image.
        for (KeyPoint keyPoint : person.getKeyPoints()) {
            if (keyPoint.getScore() > minConfidence) {
                Position position = keyPoint.getPosition();
                Float adjustedX = ((float)position.getX()) * widthRatio + left;
                Float adjustedY = ((float)position.getY()) * heightRatio + top;
                canvas.drawCircle(adjustedX, adjustedY, circleRadius, paint);
            }
        }

        for (Pair<BodyPart, BodyPart> line : bodyJoints) {
            if (
                    (person.getKeyPoints().get(line.first.ordinal()).getScore() > minConfidence) &&
                            (person.getKeyPoints().get(line.second.ordinal()).getScore() > minConfidence)
            ) {
                canvas.drawLine(
                        ((float)person.getKeyPoints().get(line.first.ordinal()).getPosition().getX()) * widthRatio + left,
                        ((float)person.getKeyPoints().get(line.first.ordinal()).getPosition().getY()) * heightRatio + top,
                        ((float)person.getKeyPoints().get(line.second.ordinal()).getPosition().getX()) * widthRatio + left,
                        ((float)person.getKeyPoints().get(line.second.ordinal()).getPosition().getY()) * heightRatio + top,
                        paint
                );
            }
        }

        return baseBitmap;
    }

    /** Set the paint color and size.    */
    private void setPaint() {
        paint.setColor(Color.RED);
        paint.setTextSize(80.0f);
        paint.setStrokeWidth(8.0f);
    }

    private Bitmap cropBitmap(Bitmap bitmap){
        Float bitmapRatio = ((float)bitmap.getHeight()) / bitmap.getWidth();
        Float modelInputRatio = ((float) MODEL_HEIGHT) / MODEL_WIDTH;
        Bitmap croppedBitmap = bitmap; //originally intended to be a copy?
        // Acceptable difference between the modelInputRatio and bitmapRatio to skip cropping.
        Double maxDifference = 1e-5;

        // Checks if the bitmap has similar aspect ratio as the required model input.
        if (Math.abs(modelInputRatio - bitmapRatio) < maxDifference)
            return croppedBitmap;
        else if(modelInputRatio < bitmapRatio){
            // New image is taller so we are height constrained.
            Float cropHeight = bitmap.getHeight() - ((float)bitmap.getWidth()) / modelInputRatio;
            croppedBitmap = Bitmap.createBitmap(
                    bitmap,
                    0,
                    (int)(cropHeight / 2),
                    bitmap.getWidth(),
                    (int)(bitmap.getHeight() - cropHeight)
            );
        }
        else{
            Float cropWidth = bitmap.getWidth() - (((float)bitmap.getHeight()) * modelInputRatio);
            croppedBitmap = Bitmap.createBitmap(
                    bitmap,
                    (int)(cropWidth / 2),
                    0,
                    (int)(bitmap.getWidth() - cropWidth),
                    bitmap.getHeight()
            );
        }
        return croppedBitmap;
    }

    public void saveInputStream(InputStream byteStream, String outputPath) throws Exception{
        byte[] buffer = new byte[byteStream.available()];
        byteStream.read(buffer);

        File targetFile = new File(outputPath);
        OutputStream outStream = new FileOutputStream(targetFile);
        outStream.write(buffer);

    }

    public void bitmapToFile(Bitmap bitmap, String outFilename) throws Exception{
        File f = new File(getApplicationContext().getCacheDir(), outFilename);
        f.createNewFile();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100 /*ignored for PNG*/, bos);
        byte[] bitmapdata = bos.toByteArray();

        //write the bytes in file
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(bitmapdata);
        fos.flush();
        fos.close();
    }




    //funciones para solicitar permiso
    public static Boolean verifyPermission(Activity activity, String manifestPermission){
        int permission = ActivityCompat.checkSelfPermission(activity, manifestPermission);
        return permission == PackageManager.PERMISSION_GRANTED;
    }

    public static void checkRequestStoragePermission(Activity activity){
        //request permissions:
        if (!verifyPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            ActivityCompat.requestPermissions(activity,
                    new String[]{
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE},
                    1);


    }



}
