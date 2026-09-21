package com.example.fn_app.di

import android.content.Context
import com.example.fn_app.BuildConfig
import com.example.fn_app.crypto.AndroidKeystoreCipher
import com.example.fn_app.data.AccountStoreImpl
import com.example.fn_app.data.AppSettings
import com.example.fn_app.data.DataStoreShopCache
import com.example.fn_app.data.FnDogDatabase
import com.example.fn_app.util.LogcatEvidence
import com.fnassistantdog.core.EpicEndpoints
import com.fnassistantdog.core.api.EpicAccountApi
import com.fnassistantdog.core.api.EpicFriendsApi
import com.fnassistantdog.core.api.EpicUserSearchApi
import com.fnassistantdog.core.api.FortniteApi
import com.fnassistantdog.core.auth.AccountStore
import com.fnassistantdog.core.auth.Clock
import com.fnassistantdog.core.auth.CredentialCipher
import com.fnassistantdog.core.auth.EpicAuthRepository
import com.fnassistantdog.core.auth.EpicSession
import com.fnassistantdog.core.friends.FriendsRepository
import com.fnassistantdog.core.http.Http
import com.fnassistantdog.core.http.newRetrofit
import com.fnassistantdog.core.shop.ShopRepository

/**
 * 手写依赖图（不引 DI 框架，见设计 D6）。
 *
 * [bases] 与 [cipherFactory] 是为 Robolectric 冒烟测试留的注入点：
 * 测试里把四个基址指向 MockWebServer、把加密换成可逆实现（AndroidKeyStore 在 JVM 沙箱里不可用），
 * 其余（Activity 生命周期、Compose 树、Room、DataStore、OkHttp/Retrofit）全部走真实实现。
 */
class AppContainer(
    context: Context,
    private val bases: Bases = Bases.PROD,
    cipherFactory: () -> CredentialCipher = { AndroidKeystoreCipher() },
) {
    data class Bases(
        val account: String,
        val friends: String,
        val userSearch: String,
        val fortnite: String,
    ) {
        companion object {
            val PROD = Bases(
                account = EpicEndpoints.ACCOUNT_BASE,
                friends = EpicEndpoints.FRIENDS_BASE,
                userSearch = EpicEndpoints.USER_SEARCH_BASE,
                fortnite = EpicEndpoints.FORTNITE_API_BASE,
            )
        }
    }

    private val appContext = context.applicationContext

    val clock: Clock = SystemClock()
    val cipher: CredentialCipher = cipherFactory()
    val settings = AppSettings(appContext)

    val accountStore: AccountStore by lazy { AccountStoreImpl(database.accountDao()) }

    private val database by lazy { FnDogDatabase.build(appContext) }

    private val http by lazy {
        Http.client(
            bearer = { session.bearerOrNull() },
            evidence = if (BuildConfig.DEBUG) LogcatEvidence() else null,
        )
    }

    private val accountApi by lazy {
        newRetrofit(bases.account, http).create(EpicAccountApi::class.java)
    }
    private val friendsApi by lazy {
        newRetrofit(bases.friends, http).create(EpicFriendsApi::class.java)
    }
    private val userSearchApi by lazy {
        newRetrofit(bases.userSearch, http).create(EpicUserSearchApi::class.java)
    }
    private val fortniteApi by lazy {
        newRetrofit(bases.fortnite, http).create(FortniteApi::class.java)
    }

    val authRepository: EpicAuthRepository by lazy {
        EpicAuthRepository(accountApi, accountStore, cipher, clock)
    }

    val session: EpicSession by lazy { EpicSession(authRepository) }

    val shopRepository: ShopRepository by lazy {
        ShopRepository(fortniteApi, DataStoreShopCache(appContext), clock)
    }

    val friendsRepository: FriendsRepository by lazy {
        FriendsRepository(friendsApi, userSearchApi)
    }

    val viewModelFactory: FnViewModelFactory by lazy { FnViewModelFactory(this) }
}

class SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
