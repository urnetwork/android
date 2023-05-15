package com.bringyour.network

import android.graphics.Color
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.bringyour.network.databinding.ActivityMainBinding
import com.bringyour.network.goclient.client.Client
import com.bringyour.network.goclient.endpoint.Endpoint
import com.bringyour.network.goclient.support.GLSurfaceViewBinder
import com.bringyour.network.goclient.vc.Vc
import com.google.android.material.bottomnavigation.BottomNavigationView


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView
        // the tint occludes the drawable icons
        navView.itemIconTintList = null

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_connect,
                R.id.navigation_provide,
                R.id.navigation_devices,
                R.id.navigation_account
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setLogo(R.drawable.logo_by)
        supportActionBar?.setDisplayUseLogoEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)


        // fixme use a custom view to show up/down statistics and hot linpath spark
        // setCustomView


//        com.bringyour.network.goclient.client.

        val client = Client.newBringYourClient()
        val endpoints = Endpoint.newEndpoints(client)
        val statusVc = Vc.newStatusViewController()


        // match the action bar background
        val colorPrimaryTypedValue = TypedValue()
        theme.resolveAttribute(R.attr.colorPrimary, colorPrimaryTypedValue, true)
        @ColorInt val colorPrimary = colorPrimaryTypedValue.data
        statusVc.setBackgroundColor(
            Color.red(colorPrimary) / 255f,
            Color.green(colorPrimary) / 255f,
            Color.blue(colorPrimary) / 255f
        )


        supportActionBar?.setCustomView(R.layout.view_status)
        supportActionBar?.setDisplayShowCustomEnabled(true)
//
        val view = supportActionBar?.customView?.findViewById(R.id.status_surface) as GLSurfaceView
        GLSurfaceViewBinder.bind("status_surface", view, statusVc, endpoints)
    }
}


