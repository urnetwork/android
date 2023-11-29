package com.bringyour.network.ui.login

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.bringyour.client.AuthLoginWithPasswordArgs
import com.bringyour.client.AuthNetworkClientArgs
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.databinding.ActivityLoginWithPasswordBinding
import com.bringyour.network.databinding.FragmentLoginPasswordBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class LoginPasswordFragment : Fragment() {

    private var _binding: FragmentLoginPasswordBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var app : MainApplication? = null

    private var loginActivity: LoginActivity? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentLoginPasswordBinding.inflate(inflater, container, false)
        val root: View = binding.root


        app = activity?.application as MainApplication

        // immutable shadow
        val app = app ?: return root

        loginActivity = activity as LoginActivity

        // immutable shadow
        val loginActivity = loginActivity ?: return root


        loginActivity.supportActionBar?.show()




        val userAuthStr = arguments?.getString("userAuth")


        val forgotPasswordButton = root.findViewById<Button>(R.id.login_forgot_password_button)
        val loginPasswordDescription = root.findViewById<TextView>(R.id.login_description)
        val loginPassword = root.findViewById<EditText>(R.id.login_password)
        val loginButton = root.findViewById<Button>(R.id.login_password_button)
        val loginSpinner = root.findViewById<ProgressBar>(R.id.login_password_spinner)
        val loginError = root.findViewById<TextView>(R.id.login_password_error)

        loginPasswordDescription.text = getString(R.string.login_password_description, userAuthStr)
        loginSpinner.visibility = View.GONE
        loginError.visibility = View.GONE

        forgotPasswordButton.setOnClickListener {
            val navArgs = Bundle()
            navArgs.putString("userAuth", userAuthStr)

            findNavController().navigate(R.id.navigation_password_reset, navArgs)
        }

        loginPassword.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                loginButton.isEnabled = (0 < loginPassword.text.toString().length)
            }
        })

        val inProgress = { busy: Boolean ->
            if (busy) {
                loginPassword.isEnabled = false
                loginButton.isEnabled = false
                loginSpinner.visibility = View.VISIBLE
            } else {
                loginPassword.isEnabled = true
                loginButton.isEnabled = true
                loginSpinner.visibility = View.GONE
            }
        }

        loginButton.setOnClickListener {
            inProgress(true)

            val args = AuthLoginWithPasswordArgs()
            args.userAuth = userAuthStr
            args.password = loginPassword.text.toString()

            app.byApi?.authLoginWithPassword(args) { result, err ->
                runBlocking(Dispatchers.Main.immediate) {
                    inProgress(false)

                    if (err != null) {
                        loginError.visibility = View.VISIBLE
                        loginError.text = err.message
                    } else if (result.error != null) {
                        loginError.visibility = View.VISIBLE
                        loginError.text = result.error.message
                    } else if (result.network != null) {
                        // now create a client id for the network
                        loginError.visibility = View.GONE

                        app.login(result.network.byJwt)

                        inProgress(true)

                        loginActivity.authClientAndFinish { error ->
                            inProgress(false)

                            if (error == null) {
                                loginError.visibility = View.GONE
                            } else {
                                loginError.visibility = View.VISIBLE
                                loginError.text = error
                            }
                        }
                    } else {
                        loginError.visibility = View.VISIBLE
                        loginError.text = getString(R.string.login_error)
                    }
                }
            }
        }

        loginButton.isEnabled = false

        return root
    }


    override fun onStart() {
        super.onStart()


        // immutable shadow
        val loginActivity = loginActivity ?: return

        loginActivity.supportActionBar?.show()
    }
}