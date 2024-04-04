package com.bringyour.network.ui.connect

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.VISIBLE
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.bringyour.client.Client.LocationTypeCity
import com.bringyour.client.Client.LocationTypeCountry
import com.bringyour.client.Client.LocationTypeRegion
import com.bringyour.client.ConnectLocation
import com.bringyour.client.ConnectViewController
import com.bringyour.client.LocationListener
import com.bringyour.client.Sub
import com.bringyour.network.MainActivity
import com.bringyour.network.R
import com.bringyour.network.databinding.FragmentConnectBinding
import com.bringyour.network.MainApplication
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.AlignSelf
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.FlexboxLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.w3c.dom.Text
import java.lang.IllegalArgumentException


private const val ViewTypeCountryChips: Int = 0
private const val ViewTypeLocationGroup: Int = 1
private const val ViewTypeLocationCountry: Int = 2
private const val ViewTypeLocationRegion: Int = 3
private const val ViewTypeLocationCity: Int = 4
private const val ViewTypeLocationDevice: Int = 5


class ConnectFragment : Fragment() {

    private var _binding: FragmentConnectBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var app : MainApplication? = null

    private var subs = mutableListOf<Sub>()

    // GetProviderLocations to get the initial list (or when query is empty)
    // on filter type, FindProviderLocations
    // on tap on location, FindProviders and choose one to set as destination
    //   always exclude self

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentConnectBinding.inflate(inflater, container, false)
        val root: View = binding.root

//        val textView: TextView = binding.textConnect
//        connectViewModel.text.observe(viewLifecycleOwner) {
//            textView.text = it
//        }

        // fixme tap gl view to switch between provider cloud and linpath views

        // https://stackoverflow.com/questions/37231560/best-way-to-null-check-in-kotlin

        // fixme move client and endpoints into the application
        app = activity?.application as MainApplication

        // immutable shadow
        val app = app ?: return root

        val connectVc = app.connectVc ?: return root
//
//        val view = root.findViewById(R.id.connect_surface) as GLSurfaceView
//        GLSurfaceViewBinder.bind("connect_surface", view, connectVc!!)
//
        /*
        connectVc.addLocationListListener {
            // locations are ordered by the relevance algo
        }

        connectVc.filter("")

        connectVc.addSelectedLocationListener {
            // if location, query find-providers to find a provider to connect to
            //   set the destination in the local nat
            // if no location, clear the local nat destination

        }

        // struct has a location_id and a location_group_id
        // LocationId
        connectVc.selectLocation()


        // location id, get updates for the location
        // location merge all properties:
        //   type, id, name, city, region, country, country code, number of providers
        connectVc.openLocation()

        connectVc.shuffle()

        connectVc.broaden()


         */


        val connectSearch = root.findViewById<EditText>(R.id.connect_search)
        val locationList = root.findViewById<RecyclerView>(R.id.connect_list)

        val adapter = ConnectAdapter(connectVc, subs)
        locationList.adapter = adapter
        locationList.layoutManager = LinearLayoutManager(context)

        fun setActiveLocation(location: ConnectLocation?) {

            val connectTop = root.findViewById<FrameLayout>(R.id.connect_top)

//            connectTopSub.let {
//                it.Close()
//            }
            connectTop.removeAllViews()

            if (location == null) {
                LayoutInflater.from(connectTop.context)
                    .inflate(R.layout.connect_top_disconnected, connectTop, true)
            } else {

                val view: View
                if (location.isGroup) {
                    view = LayoutInflater.from(connectTop.context)
                        .inflate(R.layout.connect_top_group, connectTop, true)
                    val viewHolder = ConnectTopGroupViewHolder(view)
                    // todo there is no sub currently
                    viewHolder.locationChanged(location)
                } else if (location.isDevice) {
                    view = LayoutInflater.from(connectTop.context)
                        .inflate(R.layout.connect_top_device, connectTop, true)
                    val viewHolder = ConnectTopDeviceViewHolder(view)
                    // todo there is no sub currently
                    viewHolder.locationChanged(location)
                } else {
                    when (location.locationType) {
                        LocationTypeCity -> {
                            view = LayoutInflater.from(connectTop.context)
                                .inflate(R.layout.connect_top_city, connectTop, true)
                            val viewHolder = ConnectTopCityViewHolder(view)
                            // todo there is no sub currently
                            viewHolder.locationChanged(location)
                        }

                        LocationTypeRegion -> {
                            view = LayoutInflater.from(connectTop.context)
                                .inflate(R.layout.connect_top_region, connectTop, true)
                            val viewHolder = ConnectTopRegionViewHolder(view)
                            // todo there is no sub currently
                            viewHolder.locationChanged(location)
                        }

                        else -> {
                            view = LayoutInflater.from(connectTop.context)
                                .inflate(R.layout.connect_top_country, connectTop, true)
                            val viewHolder = ConnectTopCountryViewHolder(view)
                            // todo there is no sub currently
                            viewHolder.locationChanged(location)
                        }
                    }
                }

                // add common listeners


                val disconnectButton = view.findViewById<Button>(R.id.connect_disconnect)
                disconnectButton?.setOnClickListener {
                    connectVc.disconnect()
                }

                val shuffleButton = view.findViewById<ImageButton>(R.id.connect_shuffle)
                shuffleButton?.setOnClickListener {
                    connectVc.shuffle()
                }

                val broadenButton = view.findViewById<ImageButton>(R.id.connect_broaden)
                broadenButton?.setOnClickListener {
                    connectVc.broaden()
                }


                val issueButton = view.findViewById<Button>(R.id.connect_issue)
                issueButton?.setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://support.bringyour.com")))
                }



            }
        }

        subs.add(connectVc.addConnectionListener { location ->

            runBlocking(Dispatchers.Main.immediate) {

                setActiveLocation(location)

                if (app.isVpnRequestStart()) {
                    // user might need to grant permissions
                    (activity as MainActivity).requestPermissionsThenStartVpnServiceWithRestart(true)
                }
            }

        })







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


        connectSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {

            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val filter = connectSearch.text.toString().trim()
                Log.i("ConnectFragment", "SEARCH CHANGED ${filter}")
                connectVc.filterLocations(filter)
//                if (connectSearch.text.toString() == "palo alto") {
//                    root.findViewById<View>(R.id.connect_local_control).visibility = View.GONE
//                    root.findViewById<View>(R.id.connect_remote_control).visibility = View.VISIBLE
//                    root.requestLayout()
//                }
            }
        })




        connectVc.filterLocations(connectSearch.text.toString().trim())


        setActiveLocation(connectVc.activeLocation)

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

        app?.connectVc?.start()
    }

    override fun onStop() {
        super.onStop()

        app?.connectVc?.stop()
    }
}


class ConnectAdapter(val connectVc: ConnectViewController, subs: MutableList<Sub>) : RecyclerView.Adapter<ViewHolder>() {

    private val connectLocations = mutableListOf<ConnectLocation>()
    // map country code -> connect location for the country
    private val connectCountries = mutableMapOf<String, ConnectLocation>()


    init {
        // FIXME view controller to sort the locations before calling callback
        subs.add(connectVc.addFilteredLocationsListener { exportedLocations ->
            runBlocking(Dispatchers.Main.immediate) {
                val locations = mutableListOf<ConnectLocation>()
                val n = exportedLocations.len()
                for (i in 0 until n) {
                    locations.add(exportedLocations.get(i))
                }

                Log.i("CONNECT", "UPDATE ON MAIN GOT NEW LOCATIONS $locations")

                connectLocations.clear()
                connectLocations.addAll(locations)

                connectCountries.clear()
                connectLocations.forEach { location ->
                    if (location.locationType == LocationTypeCountry) {
                        connectCountries[location.countryCode] = location
                    }
                }

                // FIXME do a better merge and support stable ids
                notifyDataSetChanged()
            }
        })
    }


    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        Log.i("CONNECT", "CREATE VIEW HOLDER")

        when (viewType) {
            ViewTypeCountryChips -> {
                val view = LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.connect_location_list_country_chips, viewGroup, false)
                return CountryChipsViewHolder(connectVc, view)
            }
            ViewTypeLocationCity -> {
                val view = LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.connect_location_list_city, viewGroup, false)
                return ConnectCityViewHolder(connectVc, view)
            }
            ViewTypeLocationRegion -> {
                val view = LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.connect_location_list_region, viewGroup, false)
                return ConnectRegionViewHolder(connectVc, view)
            }
            ViewTypeLocationCountry -> {
                val view = LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.connect_location_list_country, viewGroup, false)
                return ConnectCountryViewHolder(connectVc, view)
            }
            ViewTypeLocationGroup -> {
                val view = LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.connect_location_list_group, viewGroup, false)
                return ConnectGroupViewHolder(connectVc, view)
            }
            ViewTypeLocationDevice -> {
                val view = LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.connect_location_list_device, viewGroup, false)
                return ConnectDeviceViewHolder(connectVc, view)
            }
            else -> throw IllegalArgumentException("${viewType}")
        }
    }


    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        Log.i("CONNECT", "BIND $position")

        fun bindViewForLocation(location: ConnectLocation) {
            if (location.isGroup) {
                (viewHolder as ConnectGroupViewHolder).bind(location)
            } else if (location.isDevice) {
                (viewHolder as ConnectDeviceViewHolder).bind(location)
            } else {
                when (location.locationType) {
                    LocationTypeCity -> (viewHolder as ConnectCityViewHolder).bind(location)
                    LocationTypeRegion -> (viewHolder as ConnectRegionViewHolder).bind(location)
                    else -> (viewHolder as ConnectCountryViewHolder).bind(location)
                }
            }
        }

        if (0 < connectCountries.size) {
            if (position == 0) {
                (viewHolder as CountryChipsViewHolder).bind(connectCountries)
            } else {
                bindViewForLocation(connectLocations[position - 1])
            }
        } else {
            bindViewForLocation(connectLocations[position])
        }
    }

    override fun getItemViewType(position: Int): Int {
        fun getViewTypeForLocation(location: ConnectLocation): Int {
            if (location.isGroup) {
                return ViewTypeLocationGroup
            } else if (location.isDevice) {
                return ViewTypeLocationDevice
            } else {
                when (location.locationType) {
                    LocationTypeCity -> return ViewTypeLocationCity
                    LocationTypeRegion -> return ViewTypeLocationRegion
                    else -> return ViewTypeLocationCountry
                }
            }
        }

        if (0 < connectCountries.size) {
            if (position == 0) {
                return ViewTypeCountryChips
            } else {
                return getViewTypeForLocation(connectLocations[position - 1])
            }
        } else {
            return getViewTypeForLocation(connectLocations[position])
        }
    }


    // see https://developer.android.com/develop/ui/views/layout/recyclerview

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount(): Int {
        if (0 < connectCountries.size) {
            // add a country chip filter to the top
            return 1 + connectLocations.size
        } else {
            return connectLocations.size
        }
    }

}



class CountryChipsViewHolder(val connectVc: ConnectViewController, view: View) : RecyclerView.ViewHolder(view) {

    val flexboxRoot: FlexboxLayout

    init {
        flexboxRoot = view.findViewById<FlexboxLayout>(R.id.country_chip_root)
        flexboxRoot.setFlexDirection(FlexDirection.ROW);
    }

    fun bind(connectCountries: Map<String, ConnectLocation>) {
        flexboxRoot.removeAllViews()

        connectCountries.entries.toList().sortedByDescending { it.value.providerCount }.forEach { e ->
            val countryCode = e.key
            val location = e.value

            val view = LayoutInflater.from(flexboxRoot.context)
                .inflate(R.layout.connect_country_chip, flexboxRoot, false)

            val lp = view.layoutParams as FlexboxLayout.LayoutParams
            lp.flexGrow = 0.0f
            lp.alignSelf = AlignItems.FLEX_START
            flexboxRoot.addView(view, lp)

            val countryImageButton = view.findViewById<ImageButton>(R.id.connect_country_image)
            val countryCodeView = view.findViewById<TextView>(R.id.connect_country_code)
            val providerSummaryView = view.findViewById<TextView>(R.id.connect_provider_summary)

            val context = view.context
            val resId = context.resources.getIdentifier("country_${countryCode}_512", "drawable", context.packageName)
            countryImageButton.setImageResource(resId)
            countryCodeView.text = countryCode
            providerSummaryView.text = "${location.providerCount}"

            countryImageButton.setOnClickListener {
                connectVc.connect(location)
            }
        }
    }

}

class ConnectCityViewHolder(val connectVc: ConnectViewController, val view: View) : RecyclerView.ViewHolder(view) {
    val countryImageView: ImageView
    val locationLabelView: TextView
    val providerSummaryView: TextView
    val connectButton: Button

    var location: ConnectLocation? = null

    init {
        countryImageView = view.findViewById<ImageView>(R.id.connect_country_image)
        locationLabelView = view.findViewById<TextView>(R.id.connect_location_label)
        providerSummaryView = view.findViewById<TextView>(R.id.connect_provider_summary)

        connectButton = view.findViewById<Button>(R.id.connect_connect)

        connectButton.setOnClickListener {
            location?.let {
                connectVc.connect(it)
            }
        }
    }

    fun bind(location: ConnectLocation) {
        this.location = location

        val context = view.context
        val resId = context.resources.getIdentifier("country_${location.countryCode}_512_a50", "drawable", context.packageName)

        countryImageView.setImageResource(resId)
        locationLabelView.text = location.name
        val providerSummary: String
        if (location.providerCount == 1) {
            providerSummary = "${location.providerCount} provider"
        } else {
            providerSummary = "${location.providerCount} providers"
        }
        providerSummaryView.text = providerSummary
    }
}

class ConnectRegionViewHolder(val connectVc: ConnectViewController, val view: View) : RecyclerView.ViewHolder(view) {
    val countryImageView: ImageView
    val locationLabelView: TextView
    val providerSummaryView: TextView
    val connectButton: Button

    var location: ConnectLocation? = null

    init {
        countryImageView = view.findViewById<ImageView>(R.id.connect_country_image)
        locationLabelView = view.findViewById<TextView>(R.id.connect_location_label)
        providerSummaryView = view.findViewById<TextView>(R.id.connect_provider_summary)

        connectButton = view.findViewById<Button>(R.id.connect_connect)

        connectButton.setOnClickListener {
            location?.let {
                connectVc.connect(it)
            }
        }
    }

    fun bind(location: ConnectLocation) {
        this.location = location

        val context = view.context
        val resId = context.resources.getIdentifier("country_${location.countryCode}_512_a50", "drawable", context.packageName)

        countryImageView.setImageResource(resId)
        locationLabelView.text = location.name
        val providerSummary: String
        if (location.providerCount == 1) {
            providerSummary = "${location.providerCount} provider"
        } else {
            providerSummary = "${location.providerCount} providers"
        }
        providerSummaryView.text = providerSummary
    }
}

class ConnectCountryViewHolder(val connectVc: ConnectViewController, val view: View) : RecyclerView.ViewHolder(view) {
    val countryImageView: ImageView
    val locationLabelView: TextView
    val providerSummaryView: TextView
    val connectButton: Button

    var location: ConnectLocation? = null

    init {
        countryImageView = view.findViewById<ImageView>(R.id.connect_country_image)
        locationLabelView = view.findViewById<TextView>(R.id.connect_location_label)
        providerSummaryView = view.findViewById<TextView>(R.id.connect_provider_summary)

        connectButton = view.findViewById<Button>(R.id.connect_connect)

        connectButton.setOnClickListener {
            location?.let {
                connectVc.connect(it)
            }
        }
    }

    fun bind(location: ConnectLocation) {
        this.location = location

        val context = view.context
        val resId = context.resources.getIdentifier("country_${location.countryCode}_512", "drawable", context.packageName)
        countryImageView.setImageResource(resId)

        locationLabelView.text = location.name
        val providerSummary: String
        if (location.providerCount == 1) {
            providerSummary = "${location.providerCount} provider"
        } else {
            providerSummary = "${location.providerCount} providers"
        }
        providerSummaryView.text = providerSummary
    }
}


class ConnectGroupViewHolder(connectVc: ConnectViewController, view: View) : RecyclerView.ViewHolder(view) {
    val locationLabelView: TextView
    val promotedImageView: ImageView
    val promotedSummaryView: TextView
    val connectButton: Button

    var location: ConnectLocation? = null

    init {
        locationLabelView = view.findViewById<TextView>(R.id.connect_location_label)
        promotedImageView = view.findViewById<ImageView>(R.id.connect_promoted_image)
        promotedSummaryView = view.findViewById<TextView>(R.id.connect_promoted_summary)

        connectButton = view.findViewById<Button>(R.id.connect_connect)

        connectButton.setOnClickListener {
            location?.let {
                connectVc.connect(it)
            }
        }
    }

    fun bind(location: ConnectLocation) {
        this.location = location

        locationLabelView.text = location.name
        if (location.promoted) {
            promotedImageView.visibility = VISIBLE
            promotedSummaryView.visibility = VISIBLE
        } else {
            promotedImageView.visibility = GONE
            promotedSummaryView.visibility = GONE
        }
    }
}


class ConnectDeviceViewHolder(connectVc: ConnectViewController, view: View) : RecyclerView.ViewHolder(view) {
    val locationLabelView: TextView
    val connectButton: Button

    var location: ConnectLocation? = null

    init {
        locationLabelView = view.findViewById<TextView>(R.id.connect_location_label)

        connectButton = view.findViewById<Button>(R.id.connect_connect)

        connectButton.setOnClickListener {
            location?.let {
                connectVc.connect(it)
            }
        }
    }

    fun bind(location: ConnectLocation) {
        this.location = location

        locationLabelView.text = location.name
    }
}


class ConnectTopGroupViewHolder(val view: View) : LocationListener {
    val groupLabelView: TextView
    val providerSummaryView: TextView

    init {
        groupLabelView = view.findViewById<TextView>(R.id.connect_location_group_label)
        providerSummaryView = view.findViewById<TextView>(R.id.connect_provider_summary)
    }

    override fun locationChanged(location: ConnectLocation) {
        groupLabelView.text = location.name
        val providerSummary: String
        if (location.providerCount == 1) {
            providerSummary = "${location.providerCount} provider"
        } else {
            providerSummary = "${location.providerCount} providers"
        }
        providerSummaryView.text = providerSummary
    }
}


class ConnectTopDeviceViewHolder(val view: View) : LocationListener {
    val deviceLabelView: TextView

    init {
        deviceLabelView = view.findViewById<TextView>(R.id.connect_location_device_label)
    }

    override fun locationChanged(location: ConnectLocation) {
        deviceLabelView.text = location.name
    }
}


class ConnectTopCityViewHolder(val view: View) : LocationListener {
    val countryImageView: ImageView
    val cityLabelView: TextView
    val regionLabelView: TextView
    val countryLabelView: TextView
    val providerSummaryView: TextView

    init {
        countryImageView = view.findViewById<ImageView>(R.id.connect_country_image)
        cityLabelView = view.findViewById<TextView>(R.id.connect_location_city_label)
        regionLabelView = view.findViewById<TextView>(R.id.connect_location_region_label)
        countryLabelView = view.findViewById<TextView>(R.id.connect_location_country_label)
        providerSummaryView = view.findViewById<TextView>(R.id.connect_provider_summary)
    }

    override fun locationChanged(location: ConnectLocation) {
        val context = view.context
        val resId = context.resources.getIdentifier("country_${location.countryCode}_512_a50", "drawable", context.packageName)
        countryImageView.setImageResource(resId)

        cityLabelView.text = location.name
        regionLabelView.text = location.region
        countryLabelView.text = location.country

        val providerSummary: String
        if (location.providerCount == 1) {
            providerSummary = "${location.providerCount} provider"
        } else {
            providerSummary = "${location.providerCount} providers"
        }
        providerSummaryView.text = providerSummary
    }
}

class ConnectTopRegionViewHolder(val view: View) : LocationListener {
    val countryImageView: ImageView
    val regionLabelView: TextView
    val countryLabelView: TextView
    val providerSummaryView: TextView

    init {
        countryImageView = view.findViewById<ImageView>(R.id.connect_country_image)
        regionLabelView = view.findViewById<TextView>(R.id.connect_location_region_label)
        countryLabelView = view.findViewById<TextView>(R.id.connect_location_country_label)
        providerSummaryView = view.findViewById<TextView>(R.id.connect_provider_summary)
    }

    override fun locationChanged(location: ConnectLocation) {
        val context = view.context
        val resId = context.resources.getIdentifier("country_${location.countryCode}_512_a50", "drawable", context.packageName)
        countryImageView.setImageResource(resId)

        regionLabelView.text = location.name
        countryLabelView.text = location.country

        val providerSummary: String
        if (location.providerCount == 1) {
            providerSummary = "${location.providerCount} provider"
        } else {
            providerSummary = "${location.providerCount} providers"
        }
        providerSummaryView.text = providerSummary
    }
}

class ConnectTopCountryViewHolder(val view: View) : LocationListener {
    val countryImageView: ImageView
    val countryLabelView: TextView
    val providerSummaryView: TextView

    init {
        countryImageView = view.findViewById<ImageView>(R.id.connect_country_image)
        countryLabelView = view.findViewById<TextView>(R.id.connect_location_country_label)
        providerSummaryView = view.findViewById<TextView>(R.id.connect_provider_summary)
    }

    override fun locationChanged(location: ConnectLocation) {
        val context = view.context
        val resId = context.resources.getIdentifier("country_${location.countryCode}_512", "drawable", context.packageName)
        countryImageView.setImageResource(resId)

        countryLabelView.text = location.name

        val providerSummary: String
        if (location.providerCount == 1) {
            providerSummary = "${location.providerCount} provider"
        } else {
            providerSummary = "${location.providerCount} providers"
        }
        providerSummaryView.text = providerSummary
    }
}

