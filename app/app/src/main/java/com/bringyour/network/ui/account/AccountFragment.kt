package com.bringyour.network.ui.account

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.databinding.FragmentAccountBinding

class AccountFragment : Fragment() {

    private var _binding: FragmentAccountBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        val root: View = binding.root



        val logoutButton = root.findViewById<Button>(R.id.account_logout)
        logoutButton.setOnClickListener({
            (activity?.application as MainApplication).logout()

            activity?.startActivity(Intent(activity, LoginActivity::class.java))

            activity?.finish()
        })



//        val textView: TextView = binding.textAccount
//        accountViewModel.text.observe(viewLifecycleOwner) {
//            textView.text = it
//        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}