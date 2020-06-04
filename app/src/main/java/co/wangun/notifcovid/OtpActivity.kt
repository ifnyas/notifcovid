package co.wangun.notifcovid

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import co.wangun.notifcovid.api.ApiClient.waClient
import co.wangun.notifcovid.api.ApiService
import co.wangun.notifcovid.utils.SessionManager
import kotlinx.android.synthetic.main.activity_otp.*
import okhttp3.ResponseBody
import org.json.JSONException
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException

class OtpActivity : AppCompatActivity() {

    lateinit var otp: String
    lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp)

        sessionManager = SessionManager(this)

        action_btn.setOnClickListener {
            if (otp_edit.text.isNullOrBlank()) {
                otp_edit.visibility = View.VISIBLE
                hp_edit.visibility = View.INVISIBLE
                action_btn.text = "Verifikasi"

                otp = (0..999999).random().toString()
                sessionManager.postPhone(hp_edit.text.toString())
                requestOtp()
            } else {
                if (otp_edit.text.toString() == otp) {
                    //startActivity(Intent(this, LocActivity::class.java))
                }
            }
        }

        debug.setOnClickListener {
            sessionManager.postPhone("081394234752")
            sessionManager.postLoc("-6.892594", "107.680470")
            //startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }
    }

    private fun requestOtp() {
        // init API Service
        val mApiService = waClient.create(ApiService::class.java)

        // init values
        val auth = getString(R.string.wablas)
        val phone = sessionManager.getPhone()
        val msg = "Kode OTP Anda: $otp"

        // send message
        mApiService.postMsg(auth, phone!!, msg)
            .enqueue(object : Callback<ResponseBody> {

                override fun onResponse(
                    call: Call<ResponseBody>,
                    response: Response<ResponseBody>
                ) {
                    if (response.isSuccessful) {
                        try {
                            Toast.makeText(
                                applicationContext,
                                "Permintaan kode OTP berhasil",
                                Toast.LENGTH_SHORT
                            ).show()
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
}
