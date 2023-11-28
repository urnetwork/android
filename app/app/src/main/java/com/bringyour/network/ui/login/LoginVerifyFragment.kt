package com.bringyour.network.ui.login

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
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

        verifyDescription.setText("We sent a code to ${userAuthStr}. Please enter it below.")

        verifyResendCodeSpinner.visibility = View.GONE
        verifySpinner.visibility = View.GONE
        verifyError.visibility = View.GONE

        verifyResendCodeButton.setOnClickListener {
            verifyResendCodeButton.isEnabled = false

            verifyResendCodeSpinner.visibility = View.VISIBLE

            // FIXME add send code to api
//            app.byApi.RESEND {
            verifyResendCodeButton.text = "Sent!"
            verifyResendCodeSpinner.visibility = View.GONE
            // allow sending another code after a delay
            Handler(Looper.getMainLooper()).postDelayed({
                verifyResendCodeButton.isEnabled = true
                verifyResendCodeButton.text = "Resend"
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

        verifyButton.setOnClickListener {
            verifyCode.isEnabled = false
            verifyButton.isEnabled = false

            verifySpinner.visibility = View.VISIBLE

            val args = AuthVerifyArgs()
            args.userAuth = userAuthStr
            args.verifyCode = verifyCode.text.toString().trim()

            app.byApi?.authVerify(args) { result, err ->
                if (err != null) {
                    verifyError.visibility = View.VISIBLE
                } else if (result.network != null && 0 < result.network.byJwt.length) {
                    verifyError.visibility = View.GONE

                    app.loginClient(result.network.byJwt)

                    val intent = Intent(loginActivity, MainActivity::class.java)
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)
                    startActivity(intent)
                    loginActivity.finish()
                } else {
                    verifyError.visibility = View.VISIBLE
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