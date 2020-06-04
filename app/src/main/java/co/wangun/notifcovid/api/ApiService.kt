package co.wangun.notifcovid.api

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*

interface ApiService {

    @FormUrlEncoded
    @POST("api/send-message")
    fun postMsg(
        @Header("Authorization") auth: String,
        @Field("phone") phone: String,
        @Field("message") msg: String
    ): Call<ResponseBody>

    @GET("public/api/kecamatan_rawan.json")
    fun getRawan(): Call<ResponseBody>

    @GET("api/v1/sebaran/jabar")
    fun getJabar(): Call<ResponseBody>

    @GET("api/v1/sebaran/jabar/faskes")
    fun getFaskes(): Call<ResponseBody>

    @GET("data/kecamatan_rawan.json")
    fun getRawanYas(): Call<ResponseBody>

    @GET("data/jabar.json")
    fun getJabarYas(): Call<ResponseBody>

    @GET("data/jatim.json")
    fun getJatimYas(): Call<ResponseBody>

    @GET("data/faskes.json")
    fun getFaskesYas(): Call<ResponseBody>

    @GET("feeds/cells/10rn94NU8Yz7mubc2IS6lT365ulGRU13bSA45wOf7nNM/1/public/full?alt=json")
    fun getUser(): Call<ResponseBody>

    @GET("maps/api/geocode/json")
    fun getCity(
        @Query("address") loc: String,
        @Query("key") key: String
    ): Call<ResponseBody>
}