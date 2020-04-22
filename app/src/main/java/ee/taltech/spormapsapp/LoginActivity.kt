package ee.taltech.spormapsapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import ee.taltech.spormapsapp.api.API
import ee.taltech.spormapsapp.api.API.AUTH_LOGIN
import ee.taltech.spormapsapp.api.API.AUTH_REGISTER
import ee.taltech.spormapsapp.api.RequestAPI
import ee.taltech.spormapsapp.api.ResponseAPI
import ee.taltech.spormapsapp.helper.Utils


class LoginActivity : AppCompatActivity() {
    companion object {
        private val TAG = this::class.java.declaringClass!!.simpleName
        private var gson = Gson()
    }

    private var isLogin = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (LocationService.isServiceCreated()) {
            val intent = Intent(this, GameActivity::class.java)
            startActivity(intent)
        }

        setContentView(R.layout.activity_login)

        val mainButton = findViewById<Button>(R.id.main_button)
        val secondaryButton = findViewById<Button>(R.id.secondary_button)

        mainButton.setOnClickListener { mainButtonClicked() }
        secondaryButton.setOnClickListener { secondaryButtonClicked() }
    }

    private fun mainButtonClicked() {

        val email = findViewById<EditText>(R.id.email).text.toString()
        val password = findViewById<EditText>(R.id.password).text.toString()

        if (!Utils.isValidEmail(email)) {
            val snackbar = Snackbar.make(
                findViewById(R.id.email),
                "E-mail is invalid!",
                Snackbar.LENGTH_SHORT
            )
            return displaySnackbackTopLeft(snackbar)
        }

        if (!Utils.isValidPasswordBasic(password) && !Utils.isValidPassword(password)) {
            val snackbar = Snackbar.make(
                findViewById<CoordinatorLayout>(R.id.password),
                "Password must be 6 characters, one letter and one number!",
                Snackbar.LENGTH_LONG
            )
            return displaySnackbackTopLeft(snackbar)
        }

        if (!Utils.isValidPasswordMedium(password) && !Utils.isValidPassword(password)) {
            val snackbar = Snackbar.make(
                findViewById<CoordinatorLayout>(R.id.password),
                "Password must contain at least one uppercase letter, one lowercase letter!",
                Snackbar.LENGTH_LONG
            )
            return displaySnackbackTopLeft(snackbar)
        }

        if (!Utils.isValidPassword(password)) {
            val snackbar = Snackbar.make(
                findViewById<CoordinatorLayout>(R.id.password),
                "Password must contain at least one special character!",
                Snackbar.LENGTH_LONG
            )
            return displaySnackbackTopLeft(snackbar)
        }

        if (isLogin) {
            API.postBodyToUrl(
                gson.toJson(RequestAPI.AuthRequestLogin(email, password)),
                AUTH_LOGIN,
                this::handleAuthCallback
            )

        } else {

            val firstName = findViewById<EditText>(R.id.first_name).text.toString()
            val lastName = findViewById<EditText>(R.id.last_name).text.toString()

            if (!Utils.isValidName(firstName)) {
                val snackbar = Snackbar.make(
                    findViewById<CoordinatorLayout>(R.id.password),
                    "First name must be at least 1 character!",
                    Snackbar.LENGTH_LONG
                )
                return displaySnackbackTopLeft(snackbar)
            }

            if (!Utils.isValidName(lastName)) {
                val snackbar = Snackbar.make(
                    findViewById<CoordinatorLayout>(R.id.password),
                    "Last name must be at least 1 character!",
                    Snackbar.LENGTH_LONG
                )
                return displaySnackbackTopLeft(snackbar)
            }

            API.postBodyToUrl(
                gson.toJson(RequestAPI.AuthRequestRegister(email, password, firstName, lastName)),
                AUTH_REGISTER,
                this::handleAuthCallback
            )
        }

    }

    private fun displaySnackbackTopLeft(snackbar: Snackbar) {
        val snackbarLayout = snackbar.view
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(0, 0, 0, 0)
        snackbarLayout.layoutParams = lp
        snackbar.show()
    }

    private fun handleAuthCallback(response: String, successful: Boolean) {
        Snackbar.make(
            findViewById<CoordinatorLayout>(R.id.password),
            response,
            Snackbar.LENGTH_LONG
        ).show()

        if (successful) {
            val authResponse = gson.fromJson(response, ResponseAPI.AuthResponse::class.java)
            API.token = authResponse.token
            if (API.token != null) {
                val intent = Intent(this, GameActivity::class.java)
                startActivity(intent)
            }

        } else {
            val snackbar = Snackbar.make(
                findViewById(android.R.id.content),
                response.replace("{\"message\":\"", "").replace("\"}", ""),
                Snackbar.LENGTH_SHORT
            )

            return displaySnackbackTopLeft(snackbar)
        }
    }

    private fun secondaryButtonClicked() {
        isLogin = !isLogin

        drawTexts()
    }

    private fun drawTexts() {
        val mainButton = findViewById<Button>(R.id.main_button)
        val secondaryButton = findViewById<Button>(R.id.secondary_button)
        val firstName = findViewById<EditText>(R.id.first_name)
        val lastName = findViewById<EditText>(R.id.last_name)

        if (isLogin) {
            mainButton.text = "login"
            secondaryButton.text = "register instead"
            firstName.visibility = View.GONE
            lastName.visibility = View.GONE
        } else {
            mainButton.text = "register"
            secondaryButton.text = "login instead"
            firstName.visibility = View.VISIBLE
            lastName.visibility = View.VISIBLE
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean("isLogin", isLogin)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        isLogin = savedInstanceState.getBoolean("isLogin")
        drawTexts()
    }

}