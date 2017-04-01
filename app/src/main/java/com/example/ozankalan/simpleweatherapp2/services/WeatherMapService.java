package com.example.ozankalan.simpleweatherapp2.services;

import com.example.ozankalan.simpleweatherapp2.models.WeatherMapData;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Created by ozan.kalan on 3/29/17.
 *
 * our interface for Retrofit to call
 * weather forecast
 *
 */
public interface WeatherMapService {

    @GET("data/2.5/weather")
    Call<WeatherMapData> getZipWeather(@Query("zip") int zipCode,
                                       @Query("units") String units,
                                       @Query("APPID") String apiKey);

    @GET("data/2.5/weather")
    Call<WeatherMapData> getGeoWeather(@Query("lat") double lat,
                                       @Query("lon") double lon,
                                       @Query("units") String units,
                                       @Query("APPID") String apiKey);
}





