package com.bringyour.network.ui.connect

import android.graphics.Color
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bringyour.network.R
import com.bringyour.network.databinding.FragmentConnectBinding
import com.bringyour.network.goclient.client.BringYourClient
import com.bringyour.network.goclient.client.Client
import com.bringyour.network.goclient.endpoint.Endpoint
import com.bringyour.network.goclient.endpoint.Endpoints
import com.bringyour.network.goclient.support.GLSurfaceViewBinder
import com.bringyour.network.goclient.vc.Vc

class ConnectFragment : Fragment() {

    private var _binding: FragmentConnectBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val connectViewModel =
            ViewModelProvider(this).get(ConnectViewModel::class.java)

        _binding = FragmentConnectBinding.inflate(inflater, container, false)
        val root: View = binding.root

//        val textView: TextView = binding.textConnect
//        connectViewModel.text.observe(viewLifecycleOwner) {
//            textView.text = it
//        }

        // fixme tap gl view to switch between provider cloud and linpath views

        // fixme move client and endpoints into the application
        val client: BringYourClient = Client.newBringYourClient()
        val endpoints: Endpoints = Endpoint.newEndpoints(client)
        val connectVc = Vc.newConnectViewController()

        val view = root.findViewById(R.id.connect_surface) as GLSurfaceView
        GLSurfaceViewBinder.bind("connect_surface", view, connectVc)


        /*
        // match the action bar background
        val colorBackgroundTypedValue = TypedValue()
        context?.theme?.resolveAttribute(android.R.attr.colorBackground, colorBackgroundTypedValue, true)
        @ColorInt val colorBackground = colorBackgroundTypedValue.data
        connectVc.setBackgroundColor(
            Color.red(colorBackground) / 255f,
            Color.green(colorBackground) / 255f,
            Color.blue(colorBackground) / 255f
        )
        */



        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}