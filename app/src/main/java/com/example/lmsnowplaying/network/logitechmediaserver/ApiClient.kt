package com.example.lmsnowplaying.network.logitechmediaserver
import com.example.lmsnowplaying.settings.AppConfig
import com.example.lmsnowplaying.settings.SettingsRepository
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // Rebuilt whenever AppConfig.current changes (e.g. settings edited via
    // the reopened Settings screen), instead of caching forever.
    private var cachedSettings: SettingsRepository.Settings? = null
    private var cachedRetrofit: Retrofit? = null

    val retrofit: Retrofit
        get() {
            val settings = AppConfig.current
            val existing = cachedRetrofit
            if (existing != null && cachedSettings == settings) return existing

            val client = OkHttpClient.Builder()
                .addInterceptor(BasicAuthInterceptor(settings.lmsUsername, settings.lmsPassword))
                .addInterceptor(AcceptLanguageInterceptor())
                .build()

            val built = Retrofit.Builder()
                .client(client)
                .baseUrl(settings.lmsUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            cachedSettings = settings
            cachedRetrofit = built
            return built
        }
}

object ApiClient {
    // Follows the current server address: a new service is made only when the Retrofit instance was rebuilt.
    private var serviceFor: Retrofit? = null
    private var service: ApiService? = null

    val apiService: ApiService
        @Synchronized get() {
            val r = RetrofitClient.retrofit
            val existing = service
            if (existing != null && serviceFor === r) return existing
            return r.create(ApiService::class.java).also { service = it; serviceFor = r }
        }
}


// Lyrion returns texts of its plugin menus (settings, choices) in the language
// of the request's Accept-Language header instead of its own server language,
// so they match the language the app itself is shown in.
class AcceptLanguageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val language = java.util.Locale.getDefault().language.ifBlank { "en" }
        val request = chain.request().newBuilder().header("Accept-Language", language).build()
        return chain.proceed(request)
    }
}

class BasicAuthInterceptor(username: String, password: String): Interceptor {
    private var credentials: String = Credentials.basic(username, password)

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        var request = chain.request()
        request = request.newBuilder().header("Authorization", credentials).build()
        return chain.proceed(request)
    }
}