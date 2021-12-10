package com.example.volleyball00;

import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FieldMap;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;

public interface BinaryApi {

    @FormUrlEncoded
    @POST("getImage")
    Call<ResponseBody> getImage(@Field("filename") String filename);

    @FormUrlEncoded
    @POST("getImage")
    Call<ResponseBody> getImage(@FieldMap Map<String, String> fields);

    @Multipart
    @POST("splitVideo")
    Call<ResponseBody> splitVideo(@Part MultipartBody.Part video);

}
