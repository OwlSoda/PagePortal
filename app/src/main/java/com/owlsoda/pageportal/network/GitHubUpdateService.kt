package com.owlsoda.pageportal.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("name") val name: String,
    @SerializedName("body") val body: String,
    @SerializedName("assets") val assets: List<GitHubAsset>
)

data class GitHubAsset(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String,
    @SerializedName("size") val size: Long
)

interface GitHubUpdateService {
    @GET("repos/OwlSoda/PagePortal/releases/latest")
    suspend fun getLatestRelease(): GitHubRelease
}
