package com.bringyour.network.ui.devices

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bringyour.client.DevicesViewController
import com.bringyour.client.NetworkClientInfo
import com.bringyour.client.Sub
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.databinding.FragmentDevicesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

private const val ViewTypeAndroidDevice: Int = 0

class DevicesFragment : Fragment() {

    private var _binding: FragmentDevicesBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var app : MainApplication? = null

    private var subs = mutableListOf<Sub>()


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentDevicesBinding.inflate(inflater, container, false)
        val root: View = binding.root

        app = activity?.application as MainApplication

        // immutable shadow
        val app = app ?: return root

        val devicesVc = app.devicesVc ?: return root

        val adapter = DevicesAdapter(devicesVc, subs)

        val deviceList = root.findViewById<RecyclerView>(R.id.device_list)
        deviceList.adapter = adapter
        deviceList.layoutManager = LinearLayoutManager(context)

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        subs.forEach { sub ->
            sub.close()
        }
        subs.clear()
    }


    override fun onStart() {
        super.onStart()

        app?.devicesVc?.start()
    }

    override fun onStop() {
        super.onStop()

        app?.devicesVc?.stop()
    }
}



class DevicesAdapter(val devicesVc: DevicesViewController, subs: MutableList<Sub>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    val networkClients = mutableListOf<NetworkClientInfo>()

    init {
        // FIXME view controller to sort the locations before calling callback
        subs.add(devicesVc.addNetworkClientsListener { exportedNetworkClients ->
            val clients = mutableListOf<NetworkClientInfo>()
            val n = exportedNetworkClients.len()
            for (i in 0 until n) {
                clients.add(exportedNetworkClients.get(i))
            }

            runBlocking(Dispatchers.Main.immediate) {
                networkClients.clear()
                networkClients.addAll(clients)

                // FIXME do a better merge and support stable ids
                notifyDataSetChanged()
            }
        })
    }


    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.devices_device_list_android, viewGroup, false)
        return AndroidDeviceViewHolder(devicesVc, view)
    }


    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        (viewHolder as AndroidDeviceViewHolder).bind(networkClients[position])
    }

    override fun getItemViewType(position: Int): Int {
        return ViewTypeAndroidDevice
    }


    // see https://developer.android.com/develop/ui/views/layout/recyclerview

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount(): Int {
        return networkClients.size
    }

}


class AndroidDeviceViewHolder(val devicesVc: DevicesViewController, view: View) : RecyclerView.ViewHolder(view) {

    val clientIdView: TextView
    val clientIdCopyButton: ImageButton
    val connectedIconView: ImageView
    val connectedSummaryView: TextView
    val thisDeviceLabelView: TextView



    init {
        clientIdView = view.findViewById<TextView>(R.id.device_client_id)
        clientIdCopyButton = view.findViewById<ImageButton>(R.id.device_client_id_copy)
        connectedIconView = view.findViewById<ImageView>(R.id.device_connected_icon)
        connectedSummaryView = view.findViewById<TextView>(R.id.device_connected_summary)
        thisDeviceLabelView = view.findViewById<TextView>(R.id.device_this_device_label)

    }

    fun bind(networkClient: NetworkClientInfo) {
        clientIdView.text = networkClient.clientId.string()
        if (networkClient.connections != null && 0 < networkClient.connections.len()) {
            connectedIconView.setImageResource(R.drawable.device_connected_connected)
            connectedSummaryView.text = "Connected"
        } else {
            connectedIconView.setImageResource(R.drawable.device_connected_disconnected)
            connectedSummaryView.text = "Disconnected"
        }
        if (devicesVc.clientId().idEquals(networkClient.clientId)) {
            thisDeviceLabelView.visibility = VISIBLE
        } else {
            thisDeviceLabelView.visibility = GONE
        }
    }

}
