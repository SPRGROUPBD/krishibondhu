package com.example.data.remote

import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Production ASP.NET Core 10 REST API Integration Contract.
 *
 * Notice: Because this build runs in standalone Android Development Mode (no live SQL Server /
 * ASP.NET Core API endpoint configured in Secrets), the repository serves local Room SQLite
 * clearly labeled as Development / Demo Mode and exposes this typed Retrofit client for
 * seamless production backend binding.
 */
object IntegrationConfig {
    // Configure API_BASE_URL in AI Studio Secrets or environment before enabling remote sync.
    const val DEFAULT_DEV_MODE = true
    const val PLACEHOLDER_BASE_URL = "https://api.krishibondhu.bd/"
}

@JsonClass(generateAdapter = true)
data class ApiResponseDto<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null
)

@JsonClass(generateAdapter = true)
data class AuthLoginRequest(
    val phone: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class AuthRegisterRequest(
    val fullName: String,
    val phone: String,
    val password: String,
    val role: String,
    val district: String,
    val upazila: String,
    val unionName: String,
    val village: String
)

@JsonClass(generateAdapter = true)
data class AuthTokenDto(
    val accessToken: String,
    val userId: String,
    val role: String
)

@JsonClass(generateAdapter = true)
data class ProductDto(
    val id: String,
    val farmerId: String,
    val nameBn: String,
    val categoryId: String,
    val availableQuantity: Double,
    val unit: String,
    val pricePerUnit: Double,
    val district: String,
    val upazila: String,
    val status: String
)

@JsonClass(generateAdapter = true)
data class CreateOrderRequest(
    val farmerId: String,
    val deliveryOption: String,
    val deliveryAddress: String,
    val paymentMethod: String,
    val transactionReference: String?,
    val items: List<OrderLineDto>
)

@JsonClass(generateAdapter = true)
data class OrderLineDto(
    val productId: String,
    val quantity: Double
)

@JsonClass(generateAdapter = true)
data class OrderDto(
    val id: String,
    val orderCode: String,
    val farmerId: String,
    val buyerId: String,
    val status: String,
    val productTotal: Double,
    val deliveryCharge: Double,
    val grandTotal: Double,
    val paymentMethod: String,
    val paymentStatus: String
)

interface KrishiBondhuApiService {
    @POST("api/auth/login")
    suspend fun login(@Body request: AuthLoginRequest): Response<ApiResponseDto<AuthTokenDto>>

    @POST("api/auth/register")
    suspend fun register(@Body request: AuthRegisterRequest): Response<ApiResponseDto<AuthTokenDto>>

    @GET("api/products")
    suspend fun getProducts(
        @Query("district") district: String? = null,
        @Query("upazila") upazila: String? = null,
        @Query("categoryId") categoryId: String? = null,
        @Query("page") page: Int = 1
    ): Response<ApiResponseDto<List<ProductDto>>>

    @POST("api/products")
    suspend fun createProduct(@Body product: ProductDto): Response<ApiResponseDto<ProductDto>>

    @PUT("api/products/{id}")
    suspend fun updateProduct(@Path("id") id: String, @Body product: ProductDto): Response<ApiResponseDto<ProductDto>>

    @POST("api/orders")
    suspend fun createOrder(@Body request: CreateOrderRequest): Response<ApiResponseDto<OrderDto>>

    @GET("api/orders")
    suspend fun getOrders(@Query("role") role: String): Response<ApiResponseDto<List<OrderDto>>>

    companion object {
        fun create(baseUrl: String = IntegrationConfig.PLACEHOLDER_BASE_URL): KrishiBondhuApiService {
            val logger = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logger)
                .build()
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(KrishiBondhuApiService::class.java)
        }
    }
}
