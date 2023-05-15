package com.bringyour.network.ui.provide

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bringyour.network.R
import com.bringyour.network.databinding.FragmentProvideBinding
import com.bringyour.network.goclient.client.BringYourClient
import com.bringyour.network.goclient.client.Client
import com.bringyour.network.goclient.endpoint.Endpoint
import com.bringyour.network.goclient.endpoint.Endpoints
import com.bringyour.network.goclient.support.GLSurfaceViewBinder
import com.bringyour.network.goclient.vc.Vc

class ProvideFragment : Fragment() {

    private var _binding: FragmentProvideBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val provideViewModel =
            ViewModelProvider(this).get(ProvideViewModel::class.java)

        _binding = FragmentProvideBinding.inflate(inflater, container, false)
        val root: View = binding.root


        val textView: TextView = binding.textProvide
        provideViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }

        // fixme move client and endpoints into the application
        val client: BringYourClient = Client.newBringYourClient()
        val endpoints: Endpoints = Endpoint.newEndpoints(client)
        val provideVc = Vc.newProvideViewController()

        val view = root.findViewById(R.id.provide_surface) as GLSurfaceView
        GLSurfaceViewBinder.bind("provide_surface", view, provideVc, endpoints)

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}