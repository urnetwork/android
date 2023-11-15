package com.bringyour.network

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.bringyour.client.AuthLoginWithPasswordArgs
import com.bringyour.client.AuthNetworkClientArgs
import com.bringyour.network.databinding.ActivityLoginWithPasswordBinding

import com.google.android.gms.common.SignInButton
import kotlin.math.sign


class LoginWithPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginWithPasswordBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userAuth = intent.getStringExtra("userAuth")

        binding = ActivityLoginWithPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)


        findViewById<TextView>(R.id.login_description).text = "Log in using " + userAuth

        val loginButton = findViewById<Button>(R.id.login_with_password_button)
        loginButton.setOnClickListener {

            val password = findViewById<EditText>(R.id.login_password).text.toString()

            var args = AuthLoginWithPasswordArgs()
            args.userAuth = userAuth
            args.password = password

            Log.i("LoginWithPasswordActivity", "LOGIN WITH PASSWORD " + userAuth + " " + password)

            (application as MainApplication).byApi?.authLoginWithPassword(args, { result, err ->
                if (err == null) {
                    Log.i("LoginWithPasswordActivity", "GOT RESULT " + it)

                    if (result.network != null) {
                        // now create a client id for the network

                        (application as MainApplication).login(result.network.byJwt)

                        var authArgs = AuthNetworkClientArgs()
                        authArgs.description = "test"
                        authArgs.description = "test"

                        (application as MainApplication).byApi?.authNetworkClient(authArgs, { result, err ->
                            Log.i("LoginWithPasswordActivity", "GOT CREATE CLIENT RESULT " + result)

                            if (result != null) {

                                (application as MainApplication).loginClient(result.byJwt)

                                startActivity(Intent(this, MainActivity::class.java))

                                finish()
                            }
                        })

                    }
                }
            })


        }
    }
}