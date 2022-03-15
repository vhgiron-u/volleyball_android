package com.example.volleyball00;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

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
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONArray;
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

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

import static com.example.volleyball00.Constants.MODEL_HEIGHT;
import static com.example.volleyball00.Constants.MODEL_WIDTH;

import com.google.gson.Gson;


public class MainActivity extends AppCompatActivity {

    private int RESULT_LOAD_FILE = 1;
    private int NUM_KEYFRAMES = 5;
    private TextView textViewFileName;
    private ImageView imageViewKeyFrame;
    Boolean estimationDisplayed = false;
    private String videoPath = ""; //ruta del video que se enviara al api
        private String baseUrl = "http://127.0.0.1:6008/";
//    private String baseUrl = "http://10.0.2.2:6008/";

    private Boolean serverlessMode = false;
    private String awsBaseUrl = baseUrl;
    private BinaryApi binApi;
    private ServerlessApi serverlessApi;

    private String[] phasesFileNames = new String[NUM_KEYFRAMES];
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
        setContentView(R.layout.activity_main);
        textViewFileName = findViewById(R.id.textViewFileName);
        imageViewKeyFrame = findViewById(R.id.imageView);
        textViewCurKeyFrame = findViewById(R.id.textViewCurrKeyFrame);
        textViewCurKeyFrame.setText("0/"+NUM_KEYFRAMES); //shows 0/TOT initially
        checkRequestStoragePermission(this); //solicitamos permiso para usar almacenamiento

        //levantamos el cliente "binApi" para consumir APIs
        OkHttpClient okHttpClient = new OkHttpClient
                .Builder()
                .connectTimeout(400, TimeUnit.MINUTES)
                .readTimeout(400, TimeUnit.SECONDS)
                .writeTimeout(400, TimeUnit.SECONDS)
                .build();
        Retrofit retrofitbin = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .build();

        binApi = retrofitbin.create(BinaryApi.class);

        //levantamos el cliente "serverlessApi" para consumir APIs
        OkHttpClient okHttpClient2 = new OkHttpClient
                .Builder()
                .connectTimeout(400, TimeUnit.MINUTES)
                .readTimeout(400, TimeUnit.SECONDS)
                .writeTimeout(400, TimeUnit.SECONDS)
                .build();
        Retrofit retrofitServerless = new Retrofit.Builder()
                .baseUrl(awsBaseUrl)
                //.baseUrl(baseUrl)
                .client(okHttpClient2)
                .build();

        serverlessApi = retrofitServerless.create(ServerlessApi.class);
        if (serverlessMode)
            requestLambdaWarmUp();


        //inicializacion para estim de pose
        dLuvizon2D = new DLuvizon2D(
                getApplicationContext(),
                "modelo2D.tflite",
                Device.CPU);
        initBodyJoints();

        //Log.i("cachedir",getApplicationContext().getCacheDir().toString()); //"/data/user/0/com.example.volleyball00/cache"

        //boton para probar procesamiento local
        Button btnNavLocal =(Button)findViewById(R.id.buttonGoLocal);
        btnNavLocal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, LocalEstimation.class);
                startActivity(intent);
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

    //Funcion onClick. Se activa al dar tap al boton de Obtener Keyframes
    public void startKeyFramesProcess(View v){

        //parte 1: splitVideo
        //por el momento se llamara a la estimacion de pose tambien desde
        //requestSplitVideo debido a que aun no se manejar paralelismo en android
        if (!videoPath.trim().isEmpty()){
            startTimeRequest = Instant.now();

            if (serverlessMode)
                requestLambdaSplitVideo(videoPath);
            else
                requestSplitVideo(videoPath);

        }
        else
            Log.i("tapKeyFrame","attempt to call process before selecting video");

    }

    //Funcion onClick. Se activa al dar tap al boton "derecha".
    public void displayNextKeyFrame(View v){
        if (!estimationDisplayed) //won't show
            return;

        if (currentPhase + 1 >= NUM_KEYFRAMES)
            return;

        currentPhase++;
        updateTextKeyFrame();
        //se asume estimacion de pose realizada:
        String keyframePath = getApplicationContext().getCacheDir()
                + File.separator
                + "estimation__"
                + phasesFileNames[currentPhase];

        drawImageFromPath(keyframePath);

    }

    public void displayPreviousKeyFrame(View v){
        if (!estimationDisplayed) //won't show
            return;

        if (currentPhase - 1 < 0)
            return;

        currentPhase--;
        updateTextKeyFrame();
        //se asume estimacion de pose realizada:
        String keyframePath = getApplicationContext().getCacheDir()
                + File.separator
                + "estimation__"
                + phasesFileNames[currentPhase];

        drawImageFromPath(keyframePath);

    }

    public void updateTextKeyFrame(){
        textViewCurKeyFrame.setText((currentPhase+1)+"/"+NUM_KEYFRAMES);
    }

    //funcion llamada del startActivityForResult de browseVideo
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        //from browseVideo
        if (requestCode == RESULT_LOAD_FILE && resultCode == RESULT_OK && null != data) {
            videoPath = getVideoPath(data);
            Log.i("videoPath", "selected video: " + videoPath);

            //reducimos el texto a mostrar:
            int maxchars = 38;
            int start = videoPath.length() - maxchars;
            String showtext = "..."+videoPath.substring(start, videoPath.length());

            textViewFileName.setText(showtext);
        }
    }

    //funciones para segmentacion: envio de video, ruta de video
    //
    private  String getVideoPath( Intent data){
        Uri selectedVideo = data.getData();
        String[] filePathColumn = {MediaStore.Video.Media.DATA};
        Cursor cursor = getContentResolver().query(selectedVideo,
                filePathColumn, null, null, null);
        cursor.moveToFirst();
        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);

        String filePath = cursor.getString(columnIndex);
        cursor.close();
        return filePath;
    }

    //por el momento, esta funcion se encargara de lo siguiente:
    //llamar al api de splitVideo
    //por cada imagen de fase:
    //   llamar requestSingleImage  //esto guardara la imagen orig, estimara la pose y guardara la nueva img
    private MultipartBody.Part prepareMultipartBodyRequest(String path){
        //preparamos el video para enviar
        File file = new File(path);
        RequestBody requestFile = RequestBody.create(MediaType.parse("multipart/form-data"),file);
        MultipartBody.Part body =
                MultipartBody.Part.createFormData("video", file.getName(), requestFile);
        return body;
    }

    private RequestBody prepareVideoBodyRequest(String path){
        File file = new File(path);
        RequestBody body = RequestBody.create(MediaType.parse("video/mp4"),file);
        return body;
    }

    private void requestSplitVideo(String path){

        //preparamos el video para enviarMultipartBody
        MultipartBody.Part body = prepareMultipartBodyRequest(path);

        Call<ResponseBody> call = binApi.splitVideo(body);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()){
                    try{
                        String responseStr = response.body().string();
                        Instant endTime = Instant.now();
                        Log.i("responseStr:",responseStr);
                        Log.i("responseStr:", "time: " + (Duration.between( startTimeRequest,endTime)));
                        JSONObject jsonResponse = new JSONObject(responseStr);
                        List<String> sortedKeys = jsonStringSortedKeys(responseStr);


                        int ix = 0;
                        for (String key : sortedKeys){
                            Object value = jsonResponse.get(key);
                            if (!(value instanceof  JSONObject)){
                                String valueStr = value.toString();
                                phasesFileNames[ix] = valueStr;

                                //llamamos a la obtencion de imagen y estimacion de pose
                                //lo hacemos desde aqui debido a que no manejo paralelismo
                                requestSingleImage(valueStr, ix, true);
                                ix++;
                            }
                        }

                    }
                    catch (Exception e) {
                        Log.e("responseStr","sth failed: " + e.getMessage());
                        Log.e("response", response.toString());
                    }
                }
                else{
                    Log.e("response","sth failed: " + response.code());
                    Log.e("response", response.toString());
                    Log.e("call", call.toString());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("call", "an error ocurred during call to splitVideo:");
                Log.e("call", call.toString());
                Log.e("error", t.toString());

            }
        });

    }

    private void requestLambdaSplitVideo(String path){

        //preparamos el video para enviar
        RequestBody body = prepareVideoBodyRequest(path);
        File file = new File(path);

        Log.i("splitvideo:","Starting request");
        Call<ResponseBody> call = serverlessApi.splitVideo(file.getName(),body);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()){
                    try{
                        String responseStr = response.body().string();
                        Instant endTime = Instant.now();
                        Log.i("responseStr:",responseStr);
                        Log.i("responseStr:", "time: " + (Duration.between( startTimeRequest,endTime)));
                        JSONObject jsonResponse = new JSONObject(responseStr);
                        JSONArray destPathsJson = jsonResponse.getJSONObject("body")
                                                                 .getJSONArray("destination_paths");
                        List<String> destPaths = new ArrayList<>(destPathsJson.length());
                        for(int i=0; i<destPathsJson.length(); i++)
                            destPaths.add(destPathsJson.optString(i));
                        Collections.sort(destPaths);
                        for(String s3path : destPaths){
                            //llamamos a la obtencion de imagen y estimacion de pose
                            //lo hacemos desde aqui debido a que no manejo paralelismo
                            Log.i("elem:", s3path);
                        }

                    }
                    catch (Exception e){
                        Log.e("splitvideo","sth failed: " + e.getMessage());
                        Log.e("splitvideo", response.toString());
                    }
                }
                else{
                    Log.e("splitvideo","sth failed: " + response.code());
                    Log.e("splitvideo", response.toString());
                    Log.e("call", call.toString());
                }
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("call", "an error ocurred during call to splitvideo:");
                Log.e("call", call.toString());
                Log.e("error", t.toString());
            }
        });

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

    private void requestSingleImage(String imgName, Integer ixPhase, Boolean estimatePose){
        Call<ResponseBody> call = binApi.getImage(imgName);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()){

                    //recibimos imagen original:
                    Bitmap bmp = BitmapFactory.decodeStream(response.body().byteStream());
                    if (ixPhase == 0 && !estimatePose){
                        currentPhase = 0;
                        updateTextKeyFrame();
                        imageViewKeyFrame.setImageBitmap(bmp);
                    }

                    //save frame
                    try{
                        bitmapToFile(bmp, imgName);
                    }catch (Exception e){
                        Log.e("saveImage","sth failed: " + e.getMessage());
                    }

                    //estimamos pose:

                    if (estimatePose){
                        Log.i("processImage","calling preprocessing and estimating pose for keyframe " + ixPhase);
                        Bitmap  bitmapSkeleton = processImage(bmp);
                        //save drawn skeleton:
                        try{
                            bitmapToFile(bitmapSkeleton, "estimation__" + imgName);
                        }catch (Exception e){
                            Log.e("saveImage","sth failed: " + e.getMessage());
                        }
                        if (ixPhase == 0){ //only the first phase is displayed
                            currentPhase = 0;
                            updateTextKeyFrame();
                            imageViewKeyFrame.setImageBitmap(bitmapSkeleton);
                            estimationDisplayed = true;
                            Log.i("response+pose:", "time: " + (Duration.between( startTimeRequest,Instant.now())));
                        }

                    }

                }
                else{
                    Log.e("response","sth failed: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {

            }
        });

    }

    private void requestLambdaWarmUp(){
        Log.i("warmup:","Starting request");
        Call<ResponseBody> call = serverlessApi.warmUp();
        //Call<Object> call = serverlessApi.warmUp();
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()){
                    //recibimos url original:
                    Log.i("response", "llamando a warmUp");
                    try {
                        Log.i("response", response.body().string());
                    }
                    catch (Exception e){
                        Log.e("response", e.getMessage());
                    }
                }
                else{
                    Log.e("response","sth failed while calling warmUp: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("response","sth failed while calling warmUp: " + t.toString());
            }
        });

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