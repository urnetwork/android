package com.bringyour.network.ui.connect

import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
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
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.databinding.FragmentConnectBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.math.MathUtils.lerp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.lang.Math.pow
import java.util.Timer
import kotlin.concurrent.timer
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin


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

    private var updateTimer: Timer? = null

    private var activeLocation: ConnectLocation? = null

    private var animateJob: Job? = null


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

        val adapter = ConnectAdapter(this, connectVc, subs)
        locationList.adapter = adapter
        locationList.layoutManager = LinearLayoutManager(context)

        fun setActiveLocation(location: ConnectLocation?) {

            activeLocation = location

            val connectTop = root.findViewById<FrameLayout>(R.id.connect_top)

//            connectTopSub.let {
//                it.Close()
//            }
            connectTop.removeAllViews()

            updateTimer?.cancel()
            updateTimer = null

            if (location == null) {
                LayoutInflater.from(connectTop.context)
                    .inflate(R.layout.connect_top_disconnected, connectTop, true)

                animateJob?.cancel()
                animateJob = null
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
                            val viewHolder = ConnectTopCityViewHolder(this, view)
                            // todo there is no sub currently
                            viewHolder.locationChanged(location)
                        }

                        LocationTypeRegion -> {
                            view = LayoutInflater.from(connectTop.context)
                                .inflate(R.layout.connect_top_region, connectTop, true)
                            val viewHolder = ConnectTopRegionViewHolder(this, view)
                            // todo there is no sub currently
                            viewHolder.locationChanged(location)
                        }

                        else -> {
                            view = LayoutInflater.from(connectTop.context)
                                .inflate(R.layout.connect_top_country, connectTop, true)
                            val viewHolder = ConnectTopCountryViewHolder(this, view)
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

                view.findViewById<TextView>(R.id.connect_header)?.let { connectHeader ->
                    updateWindowStats(connectHeader)
                }
            }
        }

        subs.add(connectVc.addConnectionListener { location ->

            runBlocking(Dispatchers.Main.immediate) {

                setActiveLocation(location)

                if (app.isVpnRequestStart()) {
                    // user might need to grant permissions
                    (activity as MainActivity).requestPermissionsThenStartVpnServiceWithRestart()
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

    override fun onResume() {
        super.onResume()

        view?.findViewById<TextView>(R.id.connect_header)?.let { connectHeader ->
            updateWindowStats(connectHeader)
        }
    }

    override fun onStop() {
        super.onStop()

        app?.connectVc?.stop()

        animateJob?.cancel()
        animateJob = null

        updateTimer?.cancel()
        updateTimer = null
    }


    fun updateWindowStats(connectHeader: TextView) {
        val app = app ?: return

        updateTimer?.cancel()

        var iconState = -1

        // start window stats update
        updateTimer = timer(period = 1000) {
            app.byDevice?.windowEvents()?.let { windowEvents ->
                val currentSize = windowEvents.currentSize().toInt()
                val targetSize = windowEvents.targetSize().toInt()
//                            val addedClientCount = windowEvents.addedClientCount().toInt()
                val notAddedClientCount = windowEvents.notAddedClientCount().toInt() + windowEvents.evaluationFailedClientCount().toInt()
                val inEvaluationClientCount = windowEvents.inEvaluationClientCount().toInt()

                runBlocking(Dispatchers.Main.immediate) {

                    context?.let { context ->

                        if (0 < currentSize) {
                            if (0 < inEvaluationClientCount) {
                                connectHeader.text =
                                    "Connected (${currentSize}/${targetSize}) scanning +${inEvaluationClientCount} rejected ${notAddedClientCount}"
                            } else {
                                connectHeader.text =
                                    "Connected (${currentSize}/${targetSize})"
                            }
                        } else {
                            if (0 < inEvaluationClientCount) {
                                connectHeader.text =
                                    "Connecting (${currentSize}/${targetSize}) scanning +${inEvaluationClientCount} rejected ${notAddedClientCount}"
                            } else {
                                connectHeader.text =
                                    "Not Connected"
                            }
                        }

                        var nextIconState: Int
                        if (currentSize == 0) {
                            if (0 < inEvaluationClientCount) {
                                nextIconState = 0
                            } else {
                                nextIconState = 1

                            }
                        } else {
                            if (0 < inEvaluationClientCount) {
                                nextIconState = 2
                            } else {
                                nextIconState = 3
                            }
                        }

                        if (iconState != nextIconState) {
                            iconState = nextIconState
                            when (iconState) {
                                0 -> {
                                    val d = ContextCompat.getDrawable(
                                        requireContext(),
                                        R.drawable.connect_not_connected_in_progress
                                    ) as AnimatedVectorDrawable
                                    connectHeader.setCompoundDrawablesWithIntrinsicBounds(
                                        d,
                                        null,
                                        null,
                                        null
                                    )
                                    d.registerAnimationCallback(object :
                                        Animatable2.AnimationCallback() {
                                        override fun onAnimationEnd(drawable: Drawable?) {
                                            d.start()
                                        }

                                    })
                                    d.start()
                                }

                                1 -> {
                                    val d = ContextCompat.getDrawable(
                                        context,
                                        R.drawable.connect_not_connected
                                    ) as AnimatedVectorDrawable
                                    connectHeader.setCompoundDrawablesWithIntrinsicBounds(
                                        d,
                                        null,
                                        null,
                                        null
                                    )
                                }

                                2 -> {
                                    val d = ContextCompat.getDrawable(
                                        context,
                                        R.drawable.connect_connected_in_progress
                                    ) as AnimatedVectorDrawable
                                    connectHeader.setCompoundDrawablesWithIntrinsicBounds(
                                        d,
                                        null,
                                        null,
                                        null
                                    )
                                    d.registerAnimationCallback(object :
                                        Animatable2.AnimationCallback() {
                                        override fun onAnimationEnd(drawable: Drawable?) {
                                            d.start()
                                        }

                                    })
                                    d.start()
                                }

                                3 -> {
                                    val d = ContextCompat.getDrawable(
                                        context,
                                        R.drawable.connect_connected
                                    ) as AnimatedVectorDrawable
                                    connectHeader.setCompoundDrawablesWithIntrinsicBounds(
                                        d,
                                        null,
                                        null,
                                        null
                                    )

                                }
                            }
                        }
                    }

                }
            }
        }
    }

    fun animateConnect(startView: View, location: ConnectLocation) {

        // startView must be a descendant of this fragment view
        animateJob?.cancel()
        animateJob = lifecycleScope.launch(Dispatchers.Main) {
            context?.let { context ->
                val view = requireView() as ViewGroup

                val transitionRoot = view.findViewById<ViewGroup>(R.id.transition_root)!!
                val transitionContainer = view.findViewById<View>(R.id.transition_container)!!
                val transitionImage = view.findViewById<ImageView>(R.id.transition_image)!!

                try {

                    val startViewBounds = Rect()
                    startView.getDrawingRect(startViewBounds)
                    view.offsetDescendantRectToMyCoords(startView, startViewBounds)

                    val endViewBounds = Rect()

                    val connectTop = view.findViewById<FrameLayout>(R.id.connect_top)
                    val connectTopTemp = view.findViewById<FrameLayout>(R.id.connect_top_temp)
                    val connectTopDisconnected = view.findViewById<View>(R.id.connect_top_disconnected)

                    val h0: Int
                    val h1: Int
                    if (connectTopDisconnected != null) {
                        // inflate a temp target view to use for target bounds
                        val temp = LayoutInflater.from(context)
                            .inflate(R.layout.connect_top_country, connectTopTemp, true)

                        delay(1)

                        val connectTopImage = connectTopTemp.findViewById<View>(R.id.connect_top_image)
                        connectTopImage.getDrawingRect(endViewBounds)
                        view.offsetDescendantRectToMyCoords(connectTopImage, endViewBounds)

                        val tlp = connectTopDisconnected.layoutParams as FrameLayout.LayoutParams
                        h0 = tlp.bottomMargin
                        h1 = temp.height - connectTopDisconnected.height
                    } else {
                        h0 = 0
                        h1 = 0

                        val connectTopImage = connectTop.findViewById<View>(R.id.connect_top_image)
                        connectTopImage.getDrawingRect(endViewBounds)
                        view.offsetDescendantRectToMyCoords(connectTopImage, endViewBounds)
                    }


                    var dx = (endViewBounds.left - startViewBounds.left).toFloat()
                    var dy = (endViewBounds.top - startViewBounds.top).toFloat()
                    val d = (dx * dx + dy * dy).pow(0.5f)
                    dx /= d
                    dy /= d



                    if (location.isGroup) {
                        Glide.with(this@ConnectFragment)
                            .load(R.drawable.ic_location_group_large)
                            .override(512)
                            .priority(Priority.LOW)
                            .into(transitionImage)
                    } else if (location.isDevice) {
                        Glide.with(this@ConnectFragment)
                            .load(R.drawable.device_android)
                            .override(512)
                            .priority(Priority.LOW)
                            .into(transitionImage)
                    } else {
                        val resId = context.resources.getIdentifier(
                            "country_${location.countryCode}_512",
                            "drawable",
                            context.packageName
                        )
                        Glide.with(this@ConnectFragment)
                            .load(resId)
                            .override(512)
                            .priority(Priority.LOW)
                            .into(transitionImage)
                    }


                    val lp = transitionContainer.layoutParams as FrameLayout.LayoutParams

                    lp.width = startViewBounds.right - startViewBounds.left
                    lp.height = startViewBounds.bottom - startViewBounds.top
                    lp.leftMargin = startViewBounds.left
                    lp.topMargin = startViewBounds.top
                    transitionContainer.layoutParams = lp


                    transitionContainer.alpha = 1.0f
                    transitionRoot.visibility = View.VISIBLE


                    var startTime = System.currentTimeMillis()
                    var endTime = startTime + 300

                    // animate to place
                    while (true) {
                        val now = System.currentTimeMillis()

                        var u: Float
                        if (endTime <= now) {
                            u = 1.0f
                        } else {
                            u = (now - startTime).toFloat() / (endTime - startTime).toFloat()
                            u = u.pow(0.5f)
                        }

//                        if (connectTopImage == null) {
//                            connectTopImage = view.findViewById<View>(R.id.connect_top_image)
//                            if (connectTopImage != null) {
//                                connectTopImage.getDrawingRect(endViewBounds)
//                                view.offsetDescendantRectToMyCoords(connectTopImage, endViewBounds)
//                            }
//                        }


                        lp.width = lerp(
                            (startViewBounds.right - startViewBounds.left).toFloat(),
                            (endViewBounds.right - endViewBounds.left).toFloat(),
                            u
                        ).toInt()
                        lp.height = lerp(
                            (startViewBounds.bottom - startViewBounds.top).toFloat(),
                            (endViewBounds.bottom - endViewBounds.top).toFloat(),
                            u
                        ).toInt()
                        var x =
                            lerp(startViewBounds.left.toFloat(), endViewBounds.left.toFloat(), u)
                        var y = lerp(startViewBounds.top.toFloat(), endViewBounds.top.toFloat(), u)

                        // slightly arc the path
                        val s = 48 * sin(PI * u).toFloat()
                        x += -dy * s
                        y += dx * s

                        lp.leftMargin = x.toInt()
                        lp.topMargin = y.toInt()
                        transitionContainer.layoutParams = lp


                        if (connectTopDisconnected != null) {
                            val tlp = connectTopDisconnected.layoutParams as FrameLayout.LayoutParams
                            connectTopDisconnected.alpha = lerp(1f, 0f, u)
                            tlp.bottomMargin = lerp(h0.toFloat(), h1.toFloat(), u).toInt()
                            connectTopDisconnected.layoutParams = tlp
                        }

                        if (endTime <= now) {
                            break
                        }

                        if (activeLocation?.connectLocationId == location.connectLocationId) {
                            break
                        }

                        delay(1000 / 24)
                    }

                    // wait until the connection is active
                    while (true) {
                        connectTop.findViewById<View>(R.id.connect_top_image)?.let { connectTopImage ->
                            connectTopImage.getDrawingRect(endViewBounds)
                            view.offsetDescendantRectToMyCoords(connectTopImage, endViewBounds)

                            lp.width = endViewBounds.width()
                            lp.height = endViewBounds.height()
                            lp.leftMargin = endViewBounds.left
                            lp.topMargin = endViewBounds.top
                            transitionContainer.layoutParams = lp
                        }
                        if (activeLocation?.connectLocationId == location.connectLocationId) {
                            break
                        }
                        delay(200)
                    }

                    connectTopTemp.removeAllViews()

                    delay(2000)

                    startTime = System.currentTimeMillis()
                    endTime = startTime + 1000
                    while (true) {
                        val now = System.currentTimeMillis()

                        var u: Float
                        if (endTime <= now) {
                            u = 1.0f
                        } else {
                            u = (now - startTime).toFloat() / (endTime - startTime).toFloat()
                        }

                        transitionContainer.alpha = 1.0f - u

                        if (endTime <= now) {
                            break
                        }

                        delay(1000 / 24)
                    }

                } finally {
                    transitionRoot.visibility = View.GONE
                }

            }
        }
    }
}


class ConnectAdapter(val connectFragment: ConnectFragment, val connectVc: ConnectViewController, subs: MutableList<Sub>) : RecyclerView.Adapter<ViewHolder>() {

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

//                Log.i("CONNECT", "UPDATE ON MAIN GOT NEW LOCATIONS $locations")

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
//        Log.i("CONNECT", "CREATE VIEW HOLDER")

        when (viewType) {
            ViewTypeCountryChips -> {
                val view = LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.connect_location_list_country_chips, viewGroup, false)
                return CountryChipsViewHolder(connectFragment, connectVc, view)
            }
            ViewTypeLocationCity -> {
                val view = LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.connect_location_list_city, viewGroup, false)
                return ConnectCityViewHolder(connectFragment, connectVc, view)
            }
            ViewTypeLocationRegion -> {
                val view = LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.connect_location_list_region, viewGroup, false)
                return ConnectRegionViewHolder(connectFragment, connectVc, view)
            }
            ViewTypeLocationCountry -> {
                val view = LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.connect_location_list_country, viewGroup, false)
                return ConnectCountryViewHolder(connectFragment, connectVc, view)
            }
            ViewTypeLocationGroup -> {
                val view = LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.connect_location_list_group, viewGroup, false)
                return ConnectGroupViewHolder(connectFragment, connectVc, view)
            }
            ViewTypeLocationDevice -> {
                val view = LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.connect_location_list_device, viewGroup, false)
                return ConnectDeviceViewHolder(connectFragment, connectVc, view)
            }
            else -> throw IllegalArgumentException("${viewType}")
        }
    }


    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
//        Log.i("CONNECT", "BIND $position")

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



class CountryChipsViewHolder(val connectFragment: ConnectFragment, val connectVc: ConnectViewController, view: View) : RecyclerView.ViewHolder(view) {

    val flexboxRoot: FlexboxLayout

    init {
        flexboxRoot = view.findViewById<FlexboxLayout>(R.id.country_chip_root)
//        flexboxRoot.setFlexDirection(FlexDirection.ROW);
    }

    fun bind(connectCountries: Map<String, ConnectLocation>) {
        flexboxRoot.removeAllViews()

        connectCountries.entries.toList().sortedByDescending { it.value.providerCount }.forEach { e ->
            val countryCode = e.key
            val location = e.value

            val view = LayoutInflater.from(flexboxRoot.context)
                .inflate(R.layout.connect_country_chip, flexboxRoot, false)

            val lp = view.layoutParams as FlexboxLayout.LayoutParams
            lp.flexGrow = 1.0f
            lp.alignSelf = AlignItems.FLEX_START
            flexboxRoot.addView(view, lp)

            val countryImageButton = view.findViewById<ImageButton>(R.id.connect_country_image)
            val countryCodeView = view.findViewById<TextView>(R.id.connect_country_code)
            val providerSummaryView = view.findViewById<TextView>(R.id.connect_provider_summary)

            val context = view.context
            val resId = context.resources.getIdentifier("country_${countryCode}_192", "drawable", context.packageName)
            Glide.with(connectFragment)
                .load(resId)
                .override(192)
                .priority(Priority.LOW)
                .into(countryImageButton)

            countryCodeView.text = countryCode
            providerSummaryView.text = "${location.providerCount}"

            countryImageButton.setOnClickListener {
                connectFragment.animateConnect(countryImageButton, location)
                connectVc.connect(location)
            }
        }
    }

}

class ConnectCityViewHolder(val connectFragment: ConnectFragment, val connectVc: ConnectViewController, val view: View) : RecyclerView.ViewHolder(view) {
    val countryImageView: ImageView
    val iconImageView: ImageView
    val locationLabelView: TextView
    val providerSummaryView: TextView
    val connectButton: Button

    var location: ConnectLocation? = null

    init {
        countryImageView = view.findViewById<ImageView>(R.id.connect_country_image)

        iconImageView = view.findViewById<ImageView>(R.id.connect_location_icon_large)
        Glide.with(connectFragment)
            .load(R.drawable.ic_location_city_large)
            .priority(Priority.LOW)
            .into(iconImageView)

        locationLabelView = view.findViewById<TextView>(R.id.connect_location_label)
        providerSummaryView = view.findViewById<TextView>(R.id.connect_provider_summary)

        connectButton = view.findViewById<Button>(R.id.connect_connect)

        connectButton.setOnClickListener {
            location?.let {
                connectFragment.animateConnect(countryImageView, it)
                connectVc.connect(it)
            }
        }
    }

    fun bind(location: ConnectLocation) {
        this.location = location

        val context = view.context
        val resId = context.resources.getIdentifier("country_${location.countryCode}_192", "drawable", context.packageName)
        Glide.with(connectFragment)
            .load(resId)
            .override(192)
            .priority(Priority.LOW)
            .into(countryImageView)

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

class ConnectRegionViewHolder(val connectFragment: ConnectFragment, val connectVc: ConnectViewController, val view: View) : RecyclerView.ViewHolder(view) {
    val countryImageView: ImageView
    val iconImageView: ImageView
    val locationLabelView: TextView
    val providerSummaryView: TextView
    val connectButton: Button

    var location: ConnectLocation? = null

    init {
        countryImageView = view.findViewById<ImageView>(R.id.connect_country_image)

        iconImageView = view.findViewById<ImageView>(R.id.connect_location_icon_large)
        Glide.with(connectFragment)
            .load(R.drawable.ic_location_region_large)
            .priority(Priority.LOW)
            .into(iconImageView)

        locationLabelView = view.findViewById<TextView>(R.id.connect_location_label)
        providerSummaryView = view.findViewById<TextView>(R.id.connect_provider_summary)

        connectButton = view.findViewById<Button>(R.id.connect_connect)

        connectButton.setOnClickListener {
            location?.let {
                connectFragment.animateConnect(countryImageView, it)
                connectVc.connect(it)
            }
        }
    }

    fun bind(location: ConnectLocation) {
        this.location = location

        val context = view.context
        val resId = context.resources.getIdentifier("country_${location.countryCode}_192", "drawable", context.packageName)
        Glide.with(connectFragment)
            .load(resId)
            .override(192)
            .priority(Priority.LOW)
            .into(countryImageView)

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

class ConnectCountryViewHolder(val connectFragment: ConnectFragment, val connectVc: ConnectViewController, val view: View) : RecyclerView.ViewHolder(view) {
    val countryImageView: ImageView
    val iconImageView: ImageView
    val locationLabelView: TextView
    val providerSummaryView: TextView
    val connectButton: Button

    var location: ConnectLocation? = null

    init {
        countryImageView = view.findViewById<ImageView>(R.id.connect_country_image)

        val context = view.context
        iconImageView = view.findViewById<ImageView>(R.id.connect_location_type_icon)
        Glide.with(connectFragment)
            .load(R.drawable.ic_location_country_small)
            .priority(Priority.LOW)
            .into(iconImageView)

        locationLabelView = view.findViewById<TextView>(R.id.connect_location_label)
        providerSummaryView = view.findViewById<TextView>(R.id.connect_provider_summary)

        connectButton = view.findViewById<Button>(R.id.connect_connect)

        connectButton.setOnClickListener {
            location?.let {
                connectFragment.animateConnect(countryImageView, it)
                connectVc.connect(it)
            }
        }
    }

    fun bind(location: ConnectLocation) {
        this.location = location

        val context = view.context
        val resId = context.resources.getIdentifier("country_${location.countryCode}_192", "drawable", context.packageName)
        Glide.with(connectFragment)
            .load(resId)
            .override(192)
            .priority(Priority.LOW)
            .into(countryImageView)

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


class ConnectGroupViewHolder(val connectFragment: ConnectFragment, connectVc: ConnectViewController, view: View) : RecyclerView.ViewHolder(view) {
    val groupImageView: ImageView
    val locationLabelView: TextView
    val promotedImageView: ImageView
    val promotedSummaryView: TextView
    val connectButton: Button

    var location: ConnectLocation? = null

    init {
        groupImageView = view.findViewById<ImageView>(R.id.connect_group_image)
        Glide.with(connectFragment)
            .load(R.drawable.ic_location_group_large)
            .priority(Priority.LOW)
            .into(groupImageView)

        locationLabelView = view.findViewById<TextView>(R.id.connect_location_label)

        promotedImageView = view.findViewById<ImageView>(R.id.connect_promoted_image)
        Glide.with(connectFragment)
            .load(R.drawable.ic_connect_promoted)
            .priority(Priority.LOW)
            .into(promotedImageView)

        promotedSummaryView = view.findViewById<TextView>(R.id.connect_promoted_summary)

        connectButton = view.findViewById<Button>(R.id.connect_connect)

        connectButton.setOnClickListener {
            location?.let {
                connectFragment.animateConnect(groupImageView, it)
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


class ConnectDeviceViewHolder(val connectFragment: ConnectFragment, connectVc: ConnectViewController, view: View) : RecyclerView.ViewHolder(view) {
    val deviceImageView: ImageView
    val locationLabelView: TextView
    val connectButton: Button

    var location: ConnectLocation? = null

    init {
        deviceImageView = view.findViewById<ImageView>(R.id.connect_device_image)
        Glide.with(connectFragment)
            .load(R.drawable.device_android)
            .priority(Priority.LOW)
            .into(deviceImageView)

        locationLabelView = view.findViewById<TextView>(R.id.connect_location_label)

        connectButton = view.findViewById<Button>(R.id.connect_connect)

        connectButton.setOnClickListener {
            location?.let {
                connectFragment.animateConnect(deviceImageView, it)
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


class ConnectTopCityViewHolder(val connectFragment: ConnectFragment, val view: View) : LocationListener {
    val countryImageView: ImageView
    val cityLabelView: TextView
    val regionLabelView: TextView
    val countryLabelView: TextView
    val providerSummaryView: TextView

    init {
        countryImageView = view.findViewById<ImageView>(R.id.connect_top_image)
        cityLabelView = view.findViewById<TextView>(R.id.connect_location_city_label)
        regionLabelView = view.findViewById<TextView>(R.id.connect_location_region_label)
        countryLabelView = view.findViewById<TextView>(R.id.connect_location_country_label)
        providerSummaryView = view.findViewById<TextView>(R.id.connect_provider_summary)
    }

    override fun locationChanged(location: ConnectLocation) {
        val context = view.context
        val resId = context.resources.getIdentifier("country_${location.countryCode}_512", "drawable", context.packageName)
        Glide.with(connectFragment)
            .load(resId)
            .override(512)
            .priority(Priority.LOW)
            .into(countryImageView)

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

class ConnectTopRegionViewHolder(val connectFragment: ConnectFragment, val view: View) : LocationListener {
    val countryImageView: ImageView
    val regionLabelView: TextView
    val countryLabelView: TextView
    val providerSummaryView: TextView

    init {
        countryImageView = view.findViewById<ImageView>(R.id.connect_top_image)
        regionLabelView = view.findViewById<TextView>(R.id.connect_location_region_label)
        countryLabelView = view.findViewById<TextView>(R.id.connect_location_country_label)
        providerSummaryView = view.findViewById<TextView>(R.id.connect_provider_summary)
    }

    override fun locationChanged(location: ConnectLocation) {
        val context = view.context
        val resId = context.resources.getIdentifier("country_${location.countryCode}_512", "drawable", context.packageName)
        Glide.with(connectFragment)
            .load(resId)
            .override(512)
            .priority(Priority.LOW)
            .into(countryImageView)

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

class ConnectTopCountryViewHolder(val connectFragment: ConnectFragment, val view: View) : LocationListener {
    val countryImageView: ImageView
    val countryLabelView: TextView
    val providerSummaryView: TextView

    init {
        countryImageView = view.findViewById<ImageView>(R.id.connect_top_image)
        countryLabelView = view.findViewById<TextView>(R.id.connect_location_country_label)
        providerSummaryView = view.findViewById<TextView>(R.id.connect_provider_summary)
    }

    override fun locationChanged(location: ConnectLocation) {
        val context = view.context
        val resId = context.resources.getIdentifier("country_${location.countryCode}_512", "drawable", context.packageName)
        Glide.with(connectFragment)
            .load(resId)
            .override(512)
            .priority(Priority.LOW)
            .into(countryImageView)

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

