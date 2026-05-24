package com.example.cdtn.api;

import android.content.Context;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    private static Retrofit retrofit;

    public static Retrofit getClient(Context context) {

        if (retrofit == null) {

            OkHttpClient client =
                    new OkHttpClient.Builder()
                            .addInterceptor(
                                    new AuthInterceptor(context)
                            )
                            .build();

            retrofit = new Retrofit.Builder()
                    //.baseUrl("http://10.0.2.2:8002/api/")
                    //.baseUrl("http://192.168.100.164:8002/api/")
                    .baseUrl("http://192.168.137.1:8002/api/")
                    //.baseUrl("172.20.10.2")
                    .client(client)
                    .addConverterFactory(
                            GsonConverterFactory.create()
                    )
                    .build();
        }

        return retrofit;
    }
}