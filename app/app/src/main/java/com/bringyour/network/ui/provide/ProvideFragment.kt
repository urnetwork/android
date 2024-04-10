package com.bringyour.network.ui.provide

import android.content.Intent
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bringyour.client.Client.ProvideModeNone
import com.bringyour.client.Client.ProvideModePublic
import com.bringyour.client.ProvideViewController
import com.bringyour.network.R
import com.bringyour.network.databinding.FragmentProvideBinding
import com.bringyour.client.support.GLSurfaceViewBinder
import com.bringyour.network.MainActivity
import com.bringyour.network.MainApplication

class ProvideFragment : Fragment() {

    private var _binding: FragmentProvideBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var provideVc : ProvideViewController? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentProvideBinding.inflate(inflater, container, false)
        val root: View = binding.root

//
//        val textView: TextView = binding.textProvide
//        provideViewModel.text.observe(viewLifecycleOwner) {
//            textView.text = it
//        }

        val app = activity?.application as MainApplication
        provideVc = app.byDevice?.openProvideViewController()

//        val view = root.findViewById(R.id.provide_surface) as GLSurfaceView
//        GLSurfaceViewBinder.bind("provide_surface", view, provideVc!!)

        val provideSwitch = root.findViewById<CompoundButton>(R.id.provide_switch)
        provideSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {


                app.setProvideMode(ProvideModePublic)
                if (app.isVpnRequestStart()) {
                    // user might need to grant permissions
                    (activity as MainActivity).requestPermissionsThenStartVpnServiceWithRestart()
                }
            } else {
                app.setProvideMode(ProvideModeNone)
            }
        }

        binding.provideHelpButton.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://support.bringyour.com")))
        }

        provideSwitch.isChecked = (ProvideModeNone < app.getProvideMode())

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        val app = activity?.application as MainApplication
        app.byDevice?.closeViewController(provideVc)
        provideVc = null
    }
}