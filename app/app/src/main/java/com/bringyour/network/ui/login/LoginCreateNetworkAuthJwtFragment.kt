package com.bringyour.network.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.databinding.FragmentLoginCreateNetworkAuthJwtBinding

class LoginCreateNetworkAuthJwtFragment : Fragment() {
    private var _binding: FragmentLoginCreateNetworkAuthJwtBinding? = null

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
        _binding = FragmentLoginCreateNetworkAuthJwtBinding.inflate(inflater, container, false)
        val root: View = binding.root


        app = activity?.application as MainApplication

        // immutable shadow
        val app = app ?: return root

        loginActivity = activity as LoginActivity

        // immutable shadow
        val loginActivity = loginActivity ?: return root


        return root
    }



    override fun onStart() {
        super.onStart()


        // immutable shadow
        val loginActivity = loginActivity ?: return

        loginActivity.supportActionBar?.show()
    }
}