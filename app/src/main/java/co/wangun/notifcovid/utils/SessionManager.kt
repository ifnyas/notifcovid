package co.wangun.notifcovid.utils

import android.content.Context

class SessionManager(context: Context) {

    private val pref = context.getSharedPreferences("Preferences", 0)
    private val editor = pref.edit()

    fun clearSession() {
        editor.clear()
        editor.apply()
    }

    fun postPath(path: String) {
        editor.putString("Path", path)
        editor.apply()
    }

    fun getPath(): String? {
        return pref.getString("Path", "No Path")
    }

    fun postDay(day: Int) {
        editor.putInt("Day", day)
        editor.apply()
    }

    fun getDay(): Int? {
        return pref.getInt("Day", 0)
    }

    fun postLastUpdate(update: String) {
        editor.putString("Update", update)
        editor.apply()
    }

    fun getLastUpdate(): String? {
        return pref.getString("Update", "No Update")
    }

    fun postTimeJabar(time: String) {
        editor.putString("Time", time)
        editor.apply()
    }

    fun getTimeJabar(): String? {
        return pref.getString("Time", "No Time")
    }


    fun postFaskesSent(faskes: String) {
        editor.putString("Faskes Sent", faskes)
        editor.apply()
    }

    fun getFaskesSent(): String? {
        return pref.getString("Faskes Sent", "")
    }

    fun postCitySent(input: String) {
        editor.putString("City Sent", input)
        editor.apply()
    }

    fun getCitySent(): String? {
        return pref.getString("City Sent", "")
    }

    fun postCasesSent(
        cases: String
    ) {
        editor.putString("Cases Sent", cases)
        editor.apply()
    }

    fun getCasesSent(): String? {
        return pref.getString("Cases Sent", "")
    }

    fun postPhone(phone: String) {
        editor.putString("Phone", phone)
        editor.apply()
    }

    fun getPhone(): String? {
        return pref.getString("Phone", "No Phone")
    }


    fun postLoc(lat: String, lng: String) {
        editor.putString("Lat", lat)
        editor.putString("Lng", lng)
        editor.apply()
    }

    fun getLoc(loc: String): String? {
        val lat = pref.getString("Lat", "No Lat")
        val lng = pref.getString("Lng", "No Lng")

        return when (loc) {
            "lat" -> lat
            "lng" -> lng
            else -> "LAT $lat, LNG $lng"
        }
    }
}


