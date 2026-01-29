package com.owlsoda.pageportal.services.booklore

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Url

interface OpdsApi {
    @GET
    suspend fun getFeed(@Url url: String): ResponseBody
    
    @GET
    suspend fun downloadBook(@Url url: String): ResponseBody
}
