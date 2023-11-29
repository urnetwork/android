package com.bringyour.network.ui.login

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bringyour.client.AuthVerifyArgs
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.databinding.FragmentLoginVerifyBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class LoginVerifyFragment : Fragment() {

    private var _binding: FragmentLoginVerifyBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var app: MainApplication? = null

    private var loginActivity: LoginActivity? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentLoginVerifyBinding.inflate(inflater, container, false)
        val root: View = binding.root


        app = activity?.application as MainApplication

        // immutable shadow
        val app = app ?: return root

        loginActivity = activity as LoginActivity

        // immutable shadow
        val loginActivity = loginActivity ?: return root

        val userAuthStr = arguments?.getString("userAuth")

        val verifyDescription = root.findViewById<TextView>(R.id.verify_description)
        val verifyResendCodeButton = root.findViewById<Button>(R.id.verify_resend_code_button)
        val verifyResendCodeSpinner = root.findViewById<ProgressBar>(R.id.verify_resend_code_spinner)
        val verifyCode = root.findViewById<EditText>(R.id.verify_code)
        val verifyButton = root.findViewById<Button>(R.id.verify_button)
        val verifySpinner = root.findViewById<ProgressBar>(R.id.verify_spinner)
        val verifyError = root.findViewById<TextView>(R.id.verify_error)

        verifyDescription.text = getString(R.string.verify_description, userAuthStr)

        verifyResendCodeSpinner.visibility = View.GONE
        verifySpinner.visibility = View.GONE
        verifyError.visibility = View.GONE

        verifyResendCodeButton.setOnClickListener {
            verifyResendCodeButton.isEnabled = false

            verifyResendCodeSpinner.visibility = View.VISIBLE

            // FIXME add send code to api
//            app.byApi.RESEND {
            verifyResendCodeButton.text = getString(R.string.verify_sent)
            verifyResendCodeSpinner.visibility = View.GONE
            // allow sending another code after a delay
            Handler(Looper.getMainLooper()).postDelayed({
                verifyResendCodeButton.isEnabled = true
                verifyResendCodeButton.text = getString(R.string.verify_resend)
            }, 15 * 1000)
            // }
        }

        // validate code
        verifyCode.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val verifyCodeStr = verifyCode.text.toString().trim()
                verifyButton.isEnabled = (0 < verifyCodeStr.length)
            }
        })

        val inProgress = { busy: Boolean ->
            if (busy) {
                verifyCode.isEnabled = false
                verifyButton.isEnabled = false
                verifySpinner.visibility = View.VISIBLE
            } else {
                verifyCode.isEnabled = true
                verifyButton.isEnabled = true
                verifySpinner.visibility = View.GONE
            }
        }

        verifyButton.setOnClickListener {
            inProgress(true)

            val args = AuthVerifyArgs()
            args.userAuth = userAuthStr
            args.verifyCode = verifyCode.text.toString().trim()

            app.byApi?.authVerify(args) { result, err ->
                runBlocking(Dispatchers.Main.immediate) {
                    inProgress(false)

                    if (err != null) {
                        verifyError.visibility = View.VISIBLE
                        verifyError.text = err.message
                    } else if (result.error != null) {
                        verifyError.visibility = View.VISIBLE
                        verifyError.text = result.error.message
                    } else if (result.network != null && 0 < result.network.byJwt.length) {
                        verifyError.visibility = View.GONE

                        app.login(result.network.byJwt)

                        inProgress(true)

                        loginActivity.authClientAndFinish { error ->
                            inProgress(false)

                            if (error == null) {
                                verifyError.visibility = View.GONE
                            } else {
                                verifyError.visibility = View.VISIBLE
                                verifyError.text = error
                            }
                        }
                    } else {
                        verifyError.visibility = View.VISIBLE
                        verifyError.text = getString(R.string.verify_error)
                    }
                }
            }
        }

        verifyButton.isEnabled = false

        return root
    }

    override fun onStart() {
        super.onStart()

        // immutable shadow
        val loginActivity = loginActivity ?: return
        loginActivity.supportActionBar?.show()
    }
}