package com.owlsoda.pageportal.services.storyteller

import retrofit2.http.*
import okhttp3.RequestBody
import okhttp3.MultipartBody

/**
 * Retrofit interface for Storyteller API.
 * Based on v2 API endpoints.
 */
interface StorytellerApi {
    
    @Multipart
    @POST("api/v2/token")
    suspend fun login(
        @Part("usernameOrEmail") username: RequestBody,
        @Part("password") password: RequestBody
    ): TokenResponse
    
    @GET("api/v2/books")
    suspend fun listBooks(
        @Query("synced") synced: Boolean? = null,
        @Query("query") query: String? = null
    ): List<BookResponse>
    
    @GET("api/v2/books/{uuid}")
    suspend fun getBookDetails(
        @Path("uuid") uuid: String
    ): BookResponse
    
    @GET("api/v2/books/{uuid}/positions")
    suspend fun getPosition(
        @Path("uuid") uuid: String
    ): Position?
    
    @POST("api/v2/books/{uuid}/positions")
    suspend fun updatePosition(
        @Path("uuid") uuid: String,
        @Body position: Position
    )
    
    @POST("api/v2/books/{uuid}/process")
    suspend fun processBook(
        @Path("uuid") uuid: String,
        @Query("restart") restart: Boolean? = null
    )
    
    @DELETE("api/v2/books/{uuid}/process")
    suspend fun cancelProcessing(
        @Path("uuid") uuid: String
    )
    
    @Multipart
    @PUT("api/v2/books/{uuid}")
    suspend fun updateBook(
        @Path("uuid") uuid: String,
        @Part parts: List<MultipartBody.Part>
    ): BookResponse

    @PUT("api/v2/books/{uuid}")
    suspend fun updateMetadataJson(
        @Path("uuid") uuid: String,
        @Body metadata: MetadataRequest
    ): BookResponse

    // FUTURE: Annotations Sync
    @PUT("api/v2/books/{uuid}/annotations")
    suspend fun syncAnnotations(
        @Path("uuid") uuid: String,
        @Body annotations: List<com.owlsoda.pageportal.core.database.entity.BookmarkEntity>
    )

    @Multipart
    @PUT("api/v2/books/{uuid}/cover")
    suspend fun updateCover(
        @Path("uuid") uuid: String,
        @Part cover: MultipartBody.Part
    ): BookResponse
    
    @GET("api/v2/creators")
    suspend fun getCreators(): List<AuthorResponse>
    
    @GET("api/v2/series")
    suspend fun getSeries(): List<SeriesResponse>
    
    @GET("api/v2/tags")
    suspend fun getTags(): List<TagResponse>
    
    @GET("api/v2/collections")
    suspend fun getCollections(): List<CollectionResponse>

    @GET("api/v2/users/me")
    suspend fun getCurrentUser(): UserResponse
}
