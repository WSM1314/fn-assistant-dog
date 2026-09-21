package com.fnassistantdog.core.api

import com.fnassistantdog.core.model.ShopResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/** fortnite-api.com（免认证第三方）。language=zh-Hans 才有中文装扮名。 */
interface FortniteApi {
    @GET("v2/shop")
    suspend fun shop(@Query("language") language: String): Response<ShopResponse>
}
