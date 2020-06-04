package co.wangun.notifcovid

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import co.wangun.notifcovid.utils.SessionManager
import com.livinglifetechway.quickpermissions_kotlin.runWithPermissions
import com.livinglifetechway.quickpermissions_kotlin.util.QuickPermissionsOptions
import kotlinx.android.synthetic.main.activity_otp.*

class LocActivity : AppCompatActivity() {

    lateinit var lm: LocationManager
    lateinit var sm: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loc)

        sm = SessionManager(this)

        action_btn.setOnClickListener {
            checkPermit()
        }
    }

    private fun checkPermit() = runWithPermissions(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.RECEIVE_BOOT_COMPLETED,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        options = QuickPermissionsOptions(
            permissionsDeniedMethod = {
                Toast.makeText(
                    this,
                    "Aplikasi terpaksa keluar. " +
                            "Anda tidak mengizinkan aplikasi untuk mencari lokasi.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        )
    ) { reqLoc() }

    private fun reqLoc() {

        lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var gpsEnabled = false
        var networkEnabled = false

        try {
            gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (e: SecurityException) {
        }

        try {
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            if (activeNetworkInfo != null && activeNetworkInfo.isConnected) {
                networkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        } catch (e: SecurityException) {
        }

        when {
            !gpsEnabled -> {
                AlertDialog.Builder(this)
                    .setMessage("Aktifkan GPS")
                    .setPositiveButton("Enable") { _, _ ->
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                    .setNegativeButton("Close") { _, _ -> finish() }
                    .create().show()
            }
            !networkEnabled -> {
                try {
                    AlertDialog.Builder(this)
                        .setMessage("Aktifkan Internet")
                        .setPositiveButton("Enable") { _, _ ->
                            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                        }
                        .setNegativeButton("Close") { _, _ -> finish() }
                        .create().show()
                } catch (e: SecurityException) {
                }
            }
            else -> {
                try {
                    lm.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        0L, 0f, locationListener
                    )
                } catch (e: SecurityException) {
                }
            }
        }
    }

    private val locationListener: LocationListener = object : LocationListener {

        override fun onLocationChanged(l: Location) {
            sm.postLoc(l.latitude.toString(), l.longitude.toString())
            val text = "${sm.getLoc("lat")}, ${sm.getLoc("lng")}}"
            detail_text.text = text
            nextStep()
        }

        override fun onStatusChanged(p: String, s: Int, e: Bundle) {}
        override fun onProviderEnabled(p: String) {}
        override fun onProviderDisabled(p: String) {}
    }

    private fun nextStep() {
        // remove location listener
        lm.removeUpdates(locationListener)
        action_btn.text = "lanjut"
        action_btn.setOnClickListener {
            //startActivity(Intent(this, HomeActivity::class.java))
        }
    }
}
