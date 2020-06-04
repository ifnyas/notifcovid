package co.wangun.notifcovid

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Environment
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import co.wangun.notifcovid.api.ApiClient.geoClient
import co.wangun.notifcovid.api.ApiClient.jabarClient
import co.wangun.notifcovid.api.ApiClient.sheetsClient
import co.wangun.notifcovid.api.ApiClient.waClient
import co.wangun.notifcovid.api.ApiClient.yasClient
import co.wangun.notifcovid.api.ApiService
import co.wangun.notifcovid.utils.BootReceiver
import co.wangun.notifcovid.utils.SessionManager
import com.livinglifetechway.quickpermissions_kotlin.runWithPermissions
import com.livinglifetechway.quickpermissions_kotlin.util.QuickPermissionsOptions
import kotlinx.android.synthetic.main.activity_home.*
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.math.RoundingMode
import java.util.*
import kotlin.collections.ArrayList

@ExperimentalStdlibApi
class HomeActivity : AppCompatActivity() {

    //init data
    data class Faskes(
        var nama: String,
        var tipe: Int,
        var rujukan: Boolean,
        var alamat: String,
        var telepon: String,
        var url: String,
        var lat: Double,
        var lng: Double
    )

    val faskesArray = ArrayList<Faskes>()

    data class User(
        var phone: String,
        var lat: String,
        var lng: String
    )

    val userArray = ArrayList<User>()

    data class Cases(
        var positif: Int,
        var pdp: Int,
        var odp: Int
    )

    var casesArray = ArrayList<Cases>()

    data class UserFaskes(
        val faskesNama: String,
        val faskesTelepon: String,
        val faskesRadius: Double,
        val faskesArah: String
    )

    var rawanArray = ArrayList<String>()
    var cityArray = ArrayList<String>()

    // init var
    private lateinit var sessionManager: SessionManager
    private var mAlarmManager: AlarmManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // init session manager
        sessionManager = SessionManager(this)

        // check permissions first
        checkPermit()

        // clear cases sent on different day
        val c = Calendar.getInstance()
        val day = c.get(Calendar.DAY_OF_MONTH)
        val lastDay = sessionManager.getDay()

        if (day == lastDay) {
            updateLog("It's still today\n")
        } else {
            sessionManager.postDay(day)
            sessionManager.postCasesSent("")
            updateLog("It's a different day, cases sent cleared\n")
        }

        // log
        updateLog("Cases sent: ${sessionManager.getCasesSent()}")
        updateLog("Faskes sent: ${sessionManager.getFaskesSent()}")
        updateLog("City sent: ${sessionManager.getCitySent()}\n")

        // debug
        clear_btn.visibility = View.VISIBLE
        clear_btn.setOnClickListener {
            sessionManager.postCitySent("")
            sessionManager.postFaskesSent("")
            sessionManager.postCasesSent("")
            recreate()
        }

    }

    private fun checkPermit() = runWithPermissions(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        options = QuickPermissionsOptions(
            permissionsDeniedMethod = { finish() }
        )
    ) {
        action_btn.text = "Get User"
        action_btn.setOnClickListener {
            updateLog("Showing users...")
            initFun()
        }

        skip_btn.text = "Skip to City"
        skip_btn.setOnClickListener {
            showUser()
        }
    }

    private fun postPath() {
        val path = getExternalFilesDir(Environment.getDataDirectory().path)?.path
        sessionManager.postPath(path!!)
    }

    private fun inProgress(toggle: Boolean) {

        progress_bar.visibility = if (toggle) {
            View.VISIBLE
        } else {
            View.INVISIBLE
        }
        action_btn.isClickable = !toggle
        update_btn.isClickable = !toggle
        skip_btn.isClickable = !toggle
        clear_btn.isClickable = !toggle
    }

    private fun updateLog(text: String) {
        val toWrite = "${log_text.text}\n$text"
        log_text.text = toWrite
    }

    private fun initNotification() {

        val receiver = ComponentName(this, BootReceiver::class.java)
        this.packageManager.setComponentEnabledSetting(
            receiver,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        val alarmIntent = Intent(this, BootReceiver::class.java).let {
            PendingIntent.getBroadcast(this, 0, it, 0)
        }

        mAlarmManager = this.getSystemService(ALARM_SERVICE) as AlarmManager
        mAlarmManager!!.setRepeating(
            AlarmManager.RTC_WAKEUP, System.currentTimeMillis(),
            60000, alarmIntent
        )
    }

    private fun errorLog(e: String) {
        updateLog("$e\n")
        stopLog()
    }

    private fun stopLog() {
        updateLog("\nLog End.")
        progress_bar.visibility = View.INVISIBLE
        action_btn.visibility = View.GONE
        update_btn.visibility = View.GONE
        skip_btn.visibility = View.GONE

        clear_btn.visibility = View.VISIBLE
        clear_btn.text = "Restart"
        clear_btn.setOnClickListener {
            recreate()
        }
    }

    private fun initFun() {
        // post path
        postPath()

        // get user list
        getUser()
    }

    private fun getUser() {
        // init API Service
        val mApiService = sheetsClient.create(ApiService::class.java)

        // send message
        mApiService.getUser().enqueue(object : Callback<ResponseBody> {
            override fun onResponse(
                call: Call<ResponseBody>,
                response: Response<ResponseBody>
            ) {
                if (response.isSuccessful) {
                    try {
                        val responseJson = JSONObject(response.body()!!.string())
                        val feed = responseJson.getJSONObject("feed")
                        val entries = feed.getJSONArray("entry")

                        for (i in 4 until entries.length() step 4) {
                            // get time
                            val entryTime = entries.getJSONObject(i)
                            val contentTime = entryTime.getJSONObject("content")
                            val time = contentTime.getString("\$t")

                            // get phone
                            val entryPhone = entries.getJSONObject(i + 1)
                            val contentPhone = entryPhone.getJSONObject("content")
                            var phone = contentPhone.getString("\$t")
                            if (phone.first().toString() == "8") {
                                phone = "0$phone"
                            }

                            // get lat
                            val entryLat = entries.getJSONObject(i + 2)
                            val contentLat = entryLat.getJSONObject("content")
                            val lat = contentLat.getString("\$t")

                            // get lat
                            val entryLng = entries.getJSONObject(i + 3)
                            val contentLng = entryLng.getJSONObject("content")
                            val lng = contentLng.getString("\$t")

                            // add user to array
                            val user = User(phone, lat, lng)
                            userArray.add(user)
                        }
                        // next fun
                        showUser()

                    } catch (e: JSONException) {
                        e.printStackTrace()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                t.printStackTrace()
            }
        })
    }

    private fun showUser() {

        // write user
        for (i in userArray.indices) {
            val user = userArray[i]
            val text = "${user.phone}, ${user.lat}, ${user.lng}"
            updateLog(text)
        }

        // write total users
        updateLog("Total: ${userArray.size} users\n")

        // next fun
        action_btn.text = "Show City per User"
        action_btn.setOnClickListener {
            updateLog("Get city for user...")
            getCityUser(0)
        }

        update_btn.visibility = View.GONE
        skip_btn.text = "Skip to Faskes"
        skip_btn.setOnClickListener {
            val file = File(sessionManager.getPath(), "faskes.json").exists()
            if (file) {
                findFaskes(0)
            } else {
                getFaskes()
            }
        }

        clear_btn.visibility = View.GONE
    }

    private fun getCityUser(index: Int) {

        if (index < userArray.size) {
            val lat = userArray[index].lat
            val lng = userArray[index].lng
            val loc = "$lat,$lng"
            val key = getString(R.string.maps)
            // init API Service
            val mApiService = geoClient.create(ApiService::class.java)
            // get city
            mApiService.getCity(loc, key).enqueue(object : Callback<ResponseBody> {
                override fun onResponse(
                    call: Call<ResponseBody>,
                    response: Response<ResponseBody>
                ) {
                    if (response.isSuccessful) {
                        try {
                            val responseJson = JSONObject(response.body()!!.string())
                            val results = responseJson.getJSONArray("results")
                            val zero = results.getJSONObject(0)
                            val components = zero.getJSONArray("address_components")

                            for (i in 0 until components.length()) {
                                val address = components.getJSONObject(i)
                                var name = address.getString("long_name")
                                val types = address.getJSONArray("types")
                                val level =
                                    try {
                                        types.getString(0)
                                    } catch (e: JSONException) {
                                        "-"
                                    }

                                if (level == "administrative_area_level_2") {
                                    if (!name.contains("Kota") &&
                                        !name.contains("Kabupaten")
                                    ) {
                                        name = "Kabupaten $name"
                                    }
                                    cityArray.add(name)
                                }
                            }

                            // next fun
                            updateLog("${userArray[index].phone}: ${cityArray[index]}")
                            getCityUser(index + 1)

                        } catch (e: JSONException) {
                            errorLog(e.toString())
                            e.printStackTrace()
                        } catch (e: IOException) {
                            errorLog(e.toString())
                            e.printStackTrace()
                        }
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    errorLog(t.toString())
                    t.printStackTrace()
                }
            })
        } else {

            if (cityArray.size == userArray.size) {
                updateLog("Total city found: ${cityArray.size}. Size same as users\n")
            } else {
                updateLog("Total city found: ${cityArray.size}. SIZE NOT SAME as users\n")
            }

            updateLog("Send city to users...")
            action_btn.text = "Send City Msg"
            action_btn.visibility = View.VISIBLE
            action_btn.setOnClickListener {
                postCity(0)
            }

            skip_btn.text = "Skip to Faskes"
            skip_btn.visibility = View.VISIBLE
            skip_btn.setOnClickListener {
                //next fun
                update_btn.text = "Get Faskes"
                update_btn.visibility = View.VISIBLE
                update_btn.setOnClickListener {
                    getFaskes()
                    //inProgress(true)
                }

                action_btn.text = "Show Faskes"
                action_btn.visibility = View.VISIBLE
                action_btn.setOnClickListener {
                    val exist = File(sessionManager.getPath()!!, "faskes.json").exists()
                    if (exist) {
                        findFaskes(1)
                    } else {
                        updateLog("faskes.json not found!")
                        getFaskes()
                    }
                    //inProgress(true)
                }

                skip_btn.text = "Skip to Jabar"
                skip_btn.visibility = View.VISIBLE
                skip_btn.setOnClickListener {
                    if (userArray.size == 0) {
                        findFaskes(0)
                    } else {
                        findFaskes(userArray.size)
                    }
                }
            }
        }
    }

    private fun getHotline(city: String): String {
        return when (city) {
            "Kabupaten Bandung" -> "0821 1821 9287"
            "Kabupaten Bandung Barat" -> "0895-2243-4611"
            "Kabupaten Bekasi" -> "112, 119, 021-89910039, 0811-113-9927, 0852-8398-0119"
            "Kabupaten Bogor" -> "112, 119"
            "Kabupaten Ciamis" -> "119, 0813-9448-9808, 0853-1499-3901"
            "Kabupaten Cianjur" -> "0853-2116-1119"
            "Kabupaten Cirebon" -> "023-18800119, 0819-9880-0119"
            "Kabupaten Garut" -> "119, 026-22802800, 0811-204-0119"
            "Kabupaten Indramayu" -> "0811-133-3314"
            "Kabupaten Karawang" -> "119, 0899-9700-119, 0852-8253-7355, 0821-2556-9259, 0815-7437-1120"
            "Kabupaten Kuningan" -> "0813-8828-4346"
            "Kabupaten Majalengka" -> "112, 0233-829111, 0813-2484-9727"
            "Kabupaten Pangandaran" -> "119, 0853-2064-3695"
            "Kabupaten Purwakarta" -> "112, 0819-0951-4472"
            "Kabupaten Subang" -> "0813-2291-6001, 0821-1546-7455"
            "Kabupaten Sukabumi" -> "0266-6243816, 0812-1358-3160"
            "Kabupaten Sumedang" -> "119"
            "Kabupaten Tasikmalaya" -> "119, 0821-1962-8957"
            "Kota Bandung" -> "112, 119"
            "Kota Banjar" -> "0852-2334-4119, 0821-2037-0313, 0853-5308-9099"
            "Kota Bekasi" -> "119, 081380027110"
            "Kota Bogor" -> "112, 0251-8363335, 081-1111-6093"
            "Kota Cimahi" -> "0812-212-6257, 0812-2142-3039"
            "Kota Cirebon" -> "112, (0231) 237303"
            "Kota Depok" -> "112, 119"
            "Kota Sukabumi" -> "0800-1000-119"
            "Kota Tasikmalaya" -> "0811-213-3119"
            else -> "Kontak tidak tersedia di sistem. Cek pikobar.jabarprov.go.id/contact untuk lebih lengkap"
        }
    }

    private fun postCity(index: Int) {
        if (index < userArray.size) {
            // init API Service
            val mApiService = waClient.create(ApiService::class.java)

            // get list sent
            val sent = sessionManager.getCitySent()
            val phone = userArray[index].phone

            // get data to sent
            val city = cityArray[index]
            val hotline = getHotline(city)

            // if not sent
            if (!sent!!.contains(phone)) {
                AlertDialog.Builder(this)
                    .setMessage("Yakin kirim city ke ${userArray[index].phone}?")
                    .setPositiveButton("Ya") { _, _ ->
                        val msg = "(NotifCovid)\n\n" +
                                "Kontak yang bisa dihubungi terkait COVID-19 khusus " +
                                "*$city*: *$hotline*\n\n" +
                                "*Call Center Pikobar: 119*\n\n" +
                                "*Hotline Pikobar: 0857-3223-8564*\n\n" +
                                "Pesan ini hanya dikirim sekali. Jangan sampai terhapus ya!"

                        mApiService.postMsg(getString(R.string.wablas), phone, msg)
                            .enqueue(object : Callback<ResponseBody> {
                                override fun onResponse(
                                    call: Call<ResponseBody>,
                                    response: Response<ResponseBody>
                                ) {
                                    if (response.isSuccessful) {
                                        try {
                                            // get contents
                                            val responseJson =
                                                JSONObject(response.body()!!.string())
                                            val status = responseJson.getBoolean("status")
                                            if (status) {
                                                updateLog("City - $phone: $status")
                                                sessionManager.postCitySent("$sent$phone,")
                                                postCity(index + 1)
                                            }
                                        } catch (e: JSONException) {
                                            e.printStackTrace()
                                        } catch (e: IOException) {
                                            e.printStackTrace()
                                        }
                                    }
                                }

                                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                                    t.printStackTrace()
                                }
                            })
                    }
                    .setNegativeButton("Tidak") { _, _ ->
                        postCity(index + 1)
                    }
                    .create().show()
            } else {
                postCity(index + 1)
            }
        } else {
            //next fun
            update_btn.text = "Get Faskes"
            update_btn.visibility = View.VISIBLE
            update_btn.setOnClickListener {
                getFaskes()
                //inProgress(true)
            }

            action_btn.text = "Show Faskes"
            action_btn.visibility = View.VISIBLE
            action_btn.setOnClickListener {
                val exist = File(sessionManager.getPath()!!, "faskes.json").exists()
                if (exist) {
                    findFaskes(1)
                } else {
                    updateLog("faskes.json not found!")
                    getFaskes()
                }
                //inProgress(true)
            }

            skip_btn.text = "Skip to Jabar"
            skip_btn.visibility = View.VISIBLE
            skip_btn.setOnClickListener {
                if (userArray.size == 0) {
                    findFaskes(0)
                } else {
                    findFaskes(userArray.size)
                }
            }
        }
    }

    private fun getFaskes() {
        // update view
        updateLog("Get faskes...")

        // init API Service
        val mApiService = jabarClient.create(ApiService::class.java)

        // send message
        mApiService.getFaskes().enqueue(object : Callback<ResponseBody> {
            override fun onResponse(
                call: Call<ResponseBody>,
                response: Response<ResponseBody>
            ) {
                if (response.isSuccessful) {
                    try {
                        val responseJson = JSONObject(response.body()!!.string())
                        val dataJson = responseJson.getJSONArray("data")

                        for (i in 0 until dataJson.length()) {
                            // json
                            val faskesJson = dataJson.getJSONObject(i)

                            // nama
                            val faskesNama = faskesJson.getString("nama")


                            // tipe
                            val faskesTipe = faskesJson.getInt("tipe_faskes")

                            // rujukan
                            var faskesRujukan = false
                            try {
                                faskesRujukan = faskesJson.getBoolean("rujukan")
                            } catch (e: JSONException) {
                            }

                            // alamat
                            val faskesAlamat = faskesJson.getString("alamat")

                            // telepon
                            val faskesTelepon = faskesJson.getString("telepon")

                            // url
                            val faskesUrl = faskesJson.getString("url")

                            // lat
                            var faskesLat = 0.0
                            try {
                                faskesLat = faskesJson.getDouble("latitude")
                            } catch (e: JSONException) {
                            }

                            // lng
                            var faskesLng = 0.0
                            try {
                                faskesLng = faskesJson.getDouble("longitude")
                            } catch (e: JSONException) {
                            }

                            val faske = Faskes(
                                faskesNama,
                                faskesTipe,
                                faskesRujukan,
                                faskesAlamat,
                                faskesTelepon,
                                faskesUrl,
                                faskesLat,
                                faskesLng
                            )
                            faskesArray.add(faske)
                        }

                        // next action
                        copyFaskes(responseJson.toString())
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                t.printStackTrace()
            }
        })
    }

    private fun copyFaskes(text: String) {
        // start
        updateLog("Copying faskes.json ...")

        // progress
        File(sessionManager.getPath(), "faskes.json").writeText(text)

        // finish
        updateLog("faskes.json copy success!\n")

        // next fun
        findFaskes(1)
    }

    private fun findFaskes(index: Int) {
        if (index < userArray.size) {
            // start
            val file = File(sessionManager.getPath()!!, "faskes.json").readText()
            val json = JSONObject(file)
            val dataJson = json.getJSONArray("data")

            // get list sent
            val sent = sessionManager.getFaskesSent()
            val phone = userArray[index].phone

            // if not sent
            if (!sent!!.contains(phone)) {
                // init var
                var userFaskesNonRujukanArray = arrayListOf<UserFaskes>()
                var userFaskesRujukan = UserFaskes("", "", 999.0, "")

                for (j in 0 until dataJson.length()) {

                    // init val
                    val faskesJson = dataJson.getJSONObject(j)
                    var faskesNama = faskesJson.getString("nama")
                    val faskesRujukan = searchHospital(faskesNama, "rujukan").toBoolean()
                    val faskesAlamat = faskesJson.getString("alamat")

                    // get phone if null
                    var faskesTelepon = faskesJson.getString("telepon")
                    if (faskesTelepon.isNullOrBlank()) {
                        faskesTelepon = searchHospital(faskesNama, "telepon")
                    }

                    // lat
                    var faskesLat = 0.0
                    try {
                        faskesLat = faskesJson.getDouble("latitude")
                    } catch (e: JSONException) {
                    }

                    // lng
                    var faskesLng = 0.0
                    try {
                        faskesLng = faskesJson.getDouble("longitude")
                    } catch (e: JSONException) {
                    }

                    // find radius and direction
                    val searchArray = arrayListOf(
                        userArray[1].lat.toDouble(), userArray[1].lng.toDouble(),
                        faskesLat, faskesLng
                    )
                    val faskesRadius = searchRadius(
                        searchArray[0], searchArray[1], searchArray[2], searchArray[3]
                    )
                    val faskesArah = searchArah(
                        searchArray[0], searchArray[1], searchArray[2], searchArray[3]
                    )

                    // uppercase nama
                    faskesNama = faskesNama.toUpperCase(Locale.ROOT)

                    // set rujukan
                    val inner = if (faskesRujukan) {
                        250.0
                    } else {
                        25.0
                    }
                    if (faskesRadius != 0.0) {
                        if (faskesRujukan) {
                            if (faskesRadius < userFaskesRujukan.faskesRadius) {
                                userFaskesRujukan = UserFaskes(
                                    faskesNama, faskesTelepon, faskesRadius, faskesArah
                                )
                            }
                        } else {
                            if (faskesRadius <= inner) {
                                userFaskesNonRujukanArray.add(
                                    UserFaskes(faskesNama, faskesTelepon, faskesRadius, faskesArah)
                                )
                            }
                        }
                    }
                }

                val limit = 2
                userFaskesNonRujukanArray.sortBy { it.faskesRadius }
                while (userFaskesNonRujukanArray.size > limit) {
                    userFaskesNonRujukanArray.removeAt(limit)
                }
                userFaskesNonRujukanArray.add(0, userFaskesRujukan)

                AlertDialog.Builder(this)
                    .setMessage("Yakin kirim faskes ke $phone?")
                    .setPositiveButton("Ya") { _, _ ->
                        postFaskes(index, phone, userFaskesNonRujukanArray)
                    }
                    .setNegativeButton("Tidak") { _, _ ->
                        findFaskes(index + 1)
                    }
                    .create().show()
            } else {
                findFaskes(index + 1)
            }
        } else {
            //inProgress(false)

            action_btn.text = "Show Jabar"
            action_btn.setOnClickListener {
                val file = File(sessionManager.getPath(), "jabar.json")
                if (!file.exists()) {
                    updateLog("File not found. Copying data...")
                    getJabar()
                } else {
                    showJabar()
                }
                //inProgress(true)
            }

            update_btn.text = "Get Jabar"
            update_btn.setOnClickListener {
                getJabar()
                //inProgress(true)
            }

            skip_btn.text = "Skip to Rawan"
            skip_btn.setOnClickListener {
                showRawanPerUser()
            }
        }
    }

    private fun postFaskes(index: Int, phone: String, list: ArrayList<UserFaskes>) {
        var faskesList = arrayListOf<String>()
        while (list.size > 0) {
            val faskes = list[0]
            faskesList.add(
                "${faskes.faskesNama} (${faskes.faskesRadius} km)\n" +
                        "Kontak: ${faskes.faskesTelepon}\n" +
                        "Lokasi: ${faskes.faskesArah}"
            )
            list.removeAt(0)
        }

        // send message
        var msg = "*Daftar fasilitas kesehatan terdekat :*"
        for (i in faskesList.indices) {
            msg = if (i == 0) {
                "$msg\n\nRujukan - ${faskesList[i]}\n\n" +
                        "Kamu harus mengunjungi fasilitas kesehatan terdekat terlebih dahulu " +
                        "seperti puskesmas/rumah sakit umum di bawah sebelum akhirnya dapat " +
                        "dirujuk ke rumah sakit di atas :"
            } else {
                "$msg\n\nNon Rujukan - ${faskesList[i]}"
            }
        }
        msg = "$msg\n\nPesan ini hanya dikirim sekali, jangan sampai terhapus ya!"

        // init API Service
        val mApiService = waClient.create(ApiService::class.java)
        mApiService.postMsg(getString(R.string.wablas), phone, msg)
            .enqueue(object : Callback<ResponseBody> {
                override fun onResponse(
                    call: Call<ResponseBody>,
                    response: Response<ResponseBody>
                ) {
                    if (response.isSuccessful) {
                        try {
                            // get contents
                            val responseJson = JSONObject(response.body()!!.string())
                            val status = responseJson.getBoolean("status")
                            if (status) {
                                updateLog("Faskes - $phone: $status")
                                sessionManager.postFaskesSent("$phone,")
                                findFaskes(index + 1)
                            }
                        } catch (e: JSONException) {
                            e.printStackTrace()
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    t.printStackTrace()
                }
            })
    }

    private fun searchHospital(place: String, type: String): String {

        // create data class
        data class Hospital(var phone: String, var rujukan: Boolean)

        // input data
        val hospital: Hospital = when (place) {
            "RS Umum Pusat Dr. Hasan Sadikin" ->
                Hospital("+62222034953, +62222034954, +62222034955, +62222551111", true)

            "RS Paru Dr. H. A. Rotinsulu" ->
                Hospital("+62222034446", true)

            "RS Umum Lanud dr. M. Salamun" ->
                Hospital("+62222032090", true)

            "RS Umum Immanuel Bandung" ->
                Hospital("+62225201656, +62225201672", true)

            "RS Umum Santo Borromeus" ->
                Hospital("+62222552001, +622282558000", true)

            "RS Umum Santo Yusup" ->
                Hospital("+62227208172, +62227202420", true)

            "RS Umum Advent Bandung" ->
                Hospital("+62222034386", true)

            "RS Umum Al-Islam Bandung" ->
                Hospital("+62227565588", true)

            "RS Umum Santosa Hospital Bandung Central" ->
                Hospital("+62224348333", true)

            "RS Umum Daerah Kota Bandung" ->
                Hospital("+62227811794, +62227800017", true)


            "RS Umum Hermina Arcamanik" ->
                Hospital("1500488, +622287242525", true)


            "RS Ibu dan Anak Kota Bandung" ->
                Hospital("+622286037777, +62225200505", true)

            "RS Umum Tk II Dustira" ->
                Hospital("+62226652207", true)

            "RS Umum Daerah Cibabat" ->
                Hospital("+62226552025", true)

            "RS Umum Daerah Al Ihsan Provinsi Jawa Barat" ->
                Hospital("+62225940872, +62225941709", true)

            "RS Umum Daerah Majalaya" ->
                Hospital("+62225950035", true)

            "RS Umum Santosa Hospital Bandung Kopo" ->
                Hospital("+622254280333", true)

            "RS Unggul Karsa Medika" ->
                Hospital("+622286011220", true)

            "RS Umum Daerah Cililin" ->
                Hospital("+62226941600", true)

            "RS Umum Daerah Cikalong Wetan" ->
                Hospital("+622286866016", true)

            "RS UMUM DAERAH SUMEDANG" ->
                Hospital("+62261201021", true)

            "RS UMUM MITRA KELUARGA BEKASI TIMUR" ->
                Hospital("+622189999222, +622188342007", true)

            "RS UMUM MITRA KELUARGA BEKASI BARAT" ->
                Hospital("+62218853333, +62218848666", true)

            "RS UMUM DAERAH DR. SLAMET GARUT" ->
                Hospital("+62262237791", true)

            "RS UMUM HERMINA BEKASI" ->
                Hospital("+62218842121", true)

            "RS UMUM HERMINA GALAXY" ->
                Hospital("+62218222525", true)

            "RS UMUM GRAHA JUANDA" ->
                Hospital("+62218811832", true)

            "RS UMUM AWAL BROS" ->
                Hospital("+622188855222, +62218868888", true)

            "RS UMUM BELLA" ->
                Hospital("+62218801778, +62218801775", true)

            "RS SILOAM BEKASI TIMUR" ->
                Hospital("+622180611900, 1500911", true)

            "RS UMUM DAERAH GUNUNG JATI" ->
                Hospital("+62231202441, +62231206330", true)

            "RS UMUM DAERAH R SYAMSUDIN SH" ->
                Hospital("+62266225180", true)

            "RS UMUM DAERAH KAB. INDRAMAYU" ->
                Hospital("+62234272655", true)

            "RS UMUM DAERAH SUBANG" ->
                Hospital("+62260417442, +62260411421", true)

            "RS UMUM DAERAH DR. CHASBULLAH ABDULMADJID" ->
                Hospital("+628788666651", true)

            "RS Umum Sartika Asih" ->
                Hospital("+62225229544", true)

            else -> Hospital("-", false)
        }

        // return data
        return when (type) {
            "telepon" -> hospital.phone
            "rujukan" -> hospital.rujukan.toString()
            else -> "-"
        }
    }

    private fun searchRadius(uLat: Double, uLng: Double, pLat: Double, pLng: Double): Double {
        // init distance
        val distance = FloatArray(1)

        // count distance
        return if (pLat != 0.0 || pLng != 0.0) {

            Location.distanceBetween(
                uLat, uLng,
                pLat, pLng,
                distance
            )
            val distanceKm = distance[0] / 1000
            distanceKm.toBigDecimal()
                .setScale(1, RoundingMode.HALF_UP).toDouble()

        } else {
            0.0
        }
    }

    private fun searchArah(uLat: Double, uLng: Double, pLat: Double, pLng: Double): String {
        return "https://www.google.com/maps/dir/?api=1&origin=$uLat,$uLng&destination=$pLat,$pLng"
    }

    private fun getJabar() {
        // get last update time
        updateLog("\nGetting Jabar...")

        // init API Service
        val mApiService = yasClient.create(ApiService::class.java)

        // send message
        mApiService.getJabarYas().enqueue(object : Callback<ResponseBody> {
            override fun onResponse(
                call: Call<ResponseBody>,
                response: Response<ResponseBody>
            ) {
                if (response.isSuccessful) {
                    try {
                        // get contents
                        val responseJson = JSONObject(response.body()!!.string())
                        val data = responseJson.getJSONObject("data")
                        val metadata = data.getJSONObject("metadata")
                        val lastUpdate = metadata.getString("last_update")
                        val contents = data.getJSONArray("content")

                        // save last update for future reference
                        sessionManager.postLastUpdate(lastUpdate)

                        // copy json
                        updateLog("Copying jabar.json ...")
                        File(sessionManager.getPath(), "jabar.json")
                            .writeText("{\"content\":$contents}")
                        updateLog("jabar.json copy success!\n")

                        // next fun
                        showJabar()

                    } catch (e: JSONException) {
                        errorLog(e.toString())
                        e.printStackTrace()
                    } catch (e: IOException) {
                        errorLog(e.toString())
                        e.printStackTrace()
                    }
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                t.printStackTrace()
            }
        })
    }

    private fun showJabar() {
        // start
        updateLog("Showing jabar...")
        updateLog("update: ${sessionManager.getLastUpdate()}")

        // init val status
        var positif = 0
        var positifSembuh = 0
        var positifProses = 0
        var positifMeninggal = 0
        var positifLainnya = 0

        var pdp = 0
        var pdpSelesai = 0
        var pdpProses = 0
        var pdpMeninggal = 0
        var pdpLainnya = 0

        var odp = 0
        var odpSelesai = 0
        var odpProses = 0
        var odpMeninggal = 0
        var odpLainnya = 0

        var otg = 0
        var otgSelesai = 0
        var otgProses = 0
        var otgLainnya = 0

        var anomali = 0

        // read json
        var file = File(sessionManager.getPath(), "jabar.json")
        val json = JSONObject(file.readText())
        val contents = json.getJSONArray("content")

        // iterate array
        for (i in 0 until contents.length()) {
            val content = contents.getJSONObject(i)
            val status = content.getString("status")
            val stage = content.getString("stage")
            val lat = content.getDouble("latitude")
            val lng = content.getDouble("longitude")

            when (status) {
                "Positif" -> {
                    positif++
                    when (stage) {
                        "Sembuh" -> positifSembuh++
                        "Proses" -> positifProses++
                        "Meninggal" -> positifMeninggal++
                        else -> positifLainnya++
                    }
                }
                "PDP" -> {
                    pdp++
                    when (stage) {
                        "Selesai" -> pdpSelesai++
                        "Proses" -> pdpProses++
                        "Meninggal" -> pdpMeninggal++
                        else -> pdpLainnya++
                    }
                }
                "ODP" -> {
                    odp++
                    when (stage) {
                        "Selesai" -> odpSelesai++
                        "Proses" -> odpProses++
                        "Meninggal" -> odpMeninggal++
                        else -> odpLainnya++
                    }
                }
                "OTG" -> {
                    otg++
                    when (stage) {
                        "Selesai" -> otgSelesai++
                        "Proses" -> otgProses++
                        else -> otgLainnya++
                    }
                }
                else -> {
                    anomali++
                }
            }
        }

        // write cases
        updateLog("Total: ${contents.length()}, Anomali: $anomali")
        updateLog(
            "Positif: $positif, Sembuh: $positifSembuh, Proses: $positifProses, " +
                    "Meninggal: $positifMeninggal, Lainnya: $positifLainnya\n"
        )

        // finish
        skip_btn.text = "Skip to Rawan"
        skip_btn.setOnClickListener {
            action_btn.text = "Find Rawan/User"
            action_btn.setOnClickListener {
                // if file deleted
                file = File(sessionManager.getPath(), "kecamatan_rawan.json")
                if (!file.exists()) {
                    updateLog("File not found!")
                    copyRawan()
                } else {
                    showRawan()
                }
            }

            update_btn.visibility = View.VISIBLE
            update_btn.text = "Update Rawan"
            update_btn.setOnClickListener {
                copyRawan()
            }

            skip_btn.visibility = View.GONE
        }

        action_btn.text = "Show Cases Per User"
        action_btn.setOnClickListener {
            showCasesPerUser()
        }

        update_btn.visibility = View.GONE
    }

    private fun showCasesPerUser() {

        // update view
        updateLog("Counting cases per phone... (radius 2.5 km)")

        // read json
        var file = File(sessionManager.getPath(), "jabar.json")
        val json = JSONObject(file.readText())
        val contents = json.getJSONArray("content")

        // iterate array
        for (i in userArray.indices) {
            // init val
            var positif = 0
            var odp = 0
            var pdp = 0
            var otg = 0

            for (j in 0 until contents.length()) {
                val content = contents.getJSONObject(j)
                val status = content.getString("status")
                val stage = content.getString("stage")
                val lat = content.getDouble("latitude")
                val lng = content.getDouble("longitude")

                // init distance
                val distance = FloatArray(1)
                val userLat = userArray[i].lat.toDouble()
                val userLng = userArray[i].lng.toDouble()


                // count distance
                if (lat != 0.0 || lng != 0.0) {

                    Location.distanceBetween(
                        userLat, userLng,
                        lat, lng,
                        distance
                    )

                    if (distance[0] < 2500) {
                        when (status) {
                            "Positif" -> positif++
                            "PDP" -> pdp++
                            "ODP" -> odp++
                            "OTG" -> otg++
                        }
                    }
                }
            }
            // add case per phone
            val case = Cases(positif, pdp, odp)
            casesArray.add(case)
            updateLog("${userArray[i].phone} Ptf: $positif, PDP: $pdp, ODP: $odp")
        }

        // next fun
        action_btn.text = "Find Rawan/User"
        action_btn.setOnClickListener {
            // if file deleted
            file = File(sessionManager.getPath(), "kecamatan_rawan.json")
            if (!file.exists()) {
                updateLog("File not found!")
                copyRawan()
            } else {
                showRawan()
            }
        }

        update_btn.visibility = View.VISIBLE
        update_btn.text = "Update Rawan"
        update_btn.setOnClickListener {
            copyRawan()
        }

        skip_btn.visibility = View.GONE
    }

    private fun copyRawan() {
        // update log
        updateLog("Copying kecamatan_rawan.json ...")
        //inProgress(true)

        // get rawan
        val mApiService = yasClient.create(ApiService::class.java)
        mApiService.getRawanYas().enqueue(object : Callback<ResponseBody> {
            override fun onResponse(
                call: Call<ResponseBody>,
                response: Response<ResponseBody>
            ) {
                if (response.isSuccessful) {
                    try {
                        val responseJson = JSONArray(response.body()!!.string())
                        File(sessionManager.getPath(), "kecamatan_rawan.json")
                            .writeText("{\"content\":${responseJson}}")
                        updateLog("kecamatan_rawan.json copy success!")
                        showRawan()

                    } catch (e: JSONException) {
                        e.printStackTrace()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                t.printStackTrace()
            }
        })
    }

    private fun showRawan() {
        // start
        updateLog("\nShowing rawan...")

        // init val status
        var high = 0
        var med = 0
        var low = 0

        // progress
        val file = File(sessionManager.getPath(), "kecamatan_rawan.json")
        val json = JSONObject(file.readText())
        val contents = json.getJSONArray("content")

        // save contents to array
        for (i in 0 until contents.length()) {
            val camat = contents.getJSONObject(i)
            val title = camat.getString("title")

            when (camat.getString("kategori")) {
                "high" -> high++
                "medium" -> med++
                "low" -> low++
            }
        }
        updateLog("high: $high, med: $med, low: $low\n")

        //inProgress(false)
        action_btn.text = "Show Rawan Per User"
        action_btn.setOnClickListener {
            showRawanPerUser()
        }

        update_btn.visibility = View.GONE
        skip_btn.visibility = View.GONE
    }

    private fun showRawanPerUser() {
        // start
        updateLog("Showing rawan per user (radius 2.5 km)...")

        // init
        val file = File(sessionManager.getPath(), "kecamatan_rawan.json")
        val json = JSONObject(file.readText())
        val contents = json.getJSONArray("content")

        for (i in userArray.indices) {

            var high = 0
            var med = 0
            var low = 0

            var msg = "Kecamatan dan tingkat kerawanan di sekitar kamu saat ini: "

            for (j in 0 until contents.length()) {
                val camat = contents.getJSONObject(j)
                var title = camat.getString("title")
                var kategori = camat.getString("kategori")

                val lokasi = camat.getJSONObject("lokasi")
                val lat = lokasi.getDouble("lat")
                val lng = lokasi.getDouble("lon")

                kategori = when (kategori) {
                    "high" -> "Tinggi"
                    "medium" -> "Sedang"
                    "low" -> "Rendah"
                    else -> "-"
                }

                // init distance
                val distance = FloatArray(1)
                val userLat = userArray[i].lat.toDouble()
                val userLng = userArray[i].lng.toDouble()

                // count distance
                if (lat != 0.0 || lng != 0.0) {

                    Location.distanceBetween(
                        userLat, userLng,
                        lat, lng,
                        distance
                    )

                    if (distance[0] < 2500) {
                        // modified string
                        title = title.toLowerCase(Locale.ROOT).capitalizeWords()
                        val full = title.split(",")
                        title = full[0]
                        kategori = "*$kategori*"
                        msg = "$msg\n- $title: $kategori"

                        // counting
                        when (kategori) {
                            "*Tinggi*" -> high++
                            "*Sedang*" -> med++
                            "*Rendah*" -> low++
                        }
                    }
                }
            }

            val total = high + med + low
            if (total == 0) {
                msg = "$msg*Tidak ada (Tetap waspada ya!)*"
            }
            rawanArray.add(msg)
            updateLog("${userArray[i].phone} - H: $high, M: $med, L: $low")
        }

        val index = 1
        action_btn.text = "Send message"
        action_btn.setOnClickListener {
            updateLog("\nSending message...")
            postCases(index)
        }
    }

    private fun postCases(index: Int) {
        // init API Service
        val mApiService = waClient.create(ApiService::class.java)

        if (index < userArray.size) {
            // get list sent
            val sent = sessionManager.getCasesSent()
            val phone = userArray[index].phone

            // if not sent
            if (!sent!!.contains(phone)) {
                AlertDialog.Builder(this)
                    .setMessage("Yakin kirim cases ke ${userArray[index].phone}?")
                    .setPositiveButton("Ya") { _, _ ->

                        //val lastUpdate = sessionManager.getLastUpdate()
                        val positif = casesArray[index].positif
                        val pdp = casesArray[index].pdp
                        val odp = casesArray[index].odp
                        val msg = "Selamat pagi,\n" +
                                "Total kasus di sekitar kamu saat ini:\n" +
                                //"Update: $lastUpdate\n" +
                                "*Positif: $positif orang*\n" +
                                "*PDP: $pdp orang*\n" +
                                "*ODP: $odp orang*\n" +
                                "(Sumber: Pikobar - radius 2.5 km)\n\n" +
                                "${rawanArray[index]}\n" +
                                "(Sumber: Gugus Tugas - radius 2.5 km)\n\n" +
                                "*Info: PSBB Jabar sudah dimulai sejak hari ini hingga 19 Mei.* " +
                                "Berita lengkap s.id/psbbjabar"

                        mApiService.postMsg(getString(R.string.wablas), phone, msg)
                            .enqueue(object : Callback<ResponseBody> {
                                override fun onResponse(
                                    call: Call<ResponseBody>,
                                    response: Response<ResponseBody>
                                ) {
                                    if (response.isSuccessful) {
                                        try {
                                            // get contents
                                            val responseJson =
                                                JSONObject(response.body()!!.string())
                                            val status = responseJson.getBoolean("status")
                                            if (status) {
                                                updateLog("Cases - $phone: $status")
                                                sessionManager.postCasesSent("$sent$phone,")
                                                postCases(index + 1)
                                            }
                                        } catch (e: JSONException) {
                                            e.printStackTrace()
                                        } catch (e: IOException) {
                                            e.printStackTrace()
                                        }
                                    }
                                }

                                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                                    t.printStackTrace()
                                }
                            })
                    }
                    .setNegativeButton("Tidak") { _, _ ->
                        postCases(index + 1)
                    }
                    .create().show()
            } else {
                postCases(index + 1)
            }
        } else {
            stopLog()
        }
    }

    private fun String.capitalizeWords(): String =
        split(" ").joinToString(" ") { it.capitalize(Locale.ROOT) }
}
