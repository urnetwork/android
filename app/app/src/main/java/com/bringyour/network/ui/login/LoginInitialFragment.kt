package com.bringyour.network.ui.login

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.VideoView
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.bringyour.client.AuthLoginArgs
import com.bringyour.network.LoginActivity
import com.bringyour.network.LoginWithPasswordActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.databinding.FragmentLoginInitialBinding
import com.google.android.gms.common.SignInButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class LoginInitialFragment : Fragment() {
    private var _binding: FragmentLoginInitialBinding? = null

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
        _binding = FragmentLoginInitialBinding.inflate(inflater, container, false)
        val root: View = binding.root


        app = activity?.application as MainApplication

        // immutable shadow
        val app = app ?: return root

        loginActivity = activity as LoginActivity







        val videoView = root.findViewById<VideoView>(R.id.video_view)
        videoView.setOnPreparedListener {
            it.isLooping = true
        }

        val path = "android.resource://" + app.packageName + "/" + R.raw.login
        videoView.setVideoURI(Uri.parse(path))
        videoView.start()


        /*
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()

        // Build a GoogleSignInClient with the options specified by gso.
        val mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Check for existing Google Sign In account, if the user is already signed in
// the GoogleSignInAccount will be non-null.
        // Check for existing Google Sign In account, if the user is already signed in
// the GoogleSignInAccount will be non-null.
        val account = GoogleSignIn.getLastSignedInAccount(this)
//        updateUI(account)
*/

        // Set the dimensions of the sign-in button.
        // Set the dimensions of the sign-in button.
        val signInButton = root.findViewById<SignInButton>(R.id.google_sign_in_button)
        signInButton.setSize(SignInButton.SIZE_WIDE)
        signInButton.setColorScheme(SignInButton.COLOR_LIGHT)


        val loginButton = root.findViewById<Button>(R.id.login_user_auth_button)
        loginButton.setOnClickListener {
            val userAuth = root.findViewById<EditText>(R.id.login_user_auth).text.toString()

            var args = AuthLoginArgs()
            args.userAuth = userAuth

            Log.i("LoginActivity", "GOT USER AUTH " + userAuth)

            app.byApi?.authLogin(args, { result, err ->
                Log.i("LoginActivity", "GOT LOGIN RESULT " + result)

                if (err == null) {
                    if (result.authAllowed != null && result.authAllowed.contains("password")) {

                        runBlocking(Dispatchers.Main.immediate) {
                            val args = Bundle()
                            args.putString("userAuth", userAuth)

                            findNavController().navigate(R.id.navigation_password, args)
                        }

//                        var intent = Intent(activity, LoginWithPasswordActivity::class.java)
//                        intent.putExtra("userAuth", userAuth)
//                        startActivity(intent)
                    }
                }
            })

        }

        val createButton = root.findViewById<Button>(R.id.login_create_button)
        createButton.setOnClickListener {
            findNavController().navigate(R.id.navigation_create_network)
        }

        return root
    }

    override fun onStart() {
        super.onStart()


        // immutable shadow
        val loginActivity = loginActivity ?: return

        loginActivity.supportActionBar?.hide()
    }
}