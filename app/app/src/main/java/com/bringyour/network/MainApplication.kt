package com.bringyour.network

import android.app.Application
import com.bringyour.network.goclient.client.BringYourClient
import com.bringyour.network.goclient.client.Client
import com.bringyour.network.goclient.endpoint.Endpoint
import com.bringyour.network.goclient.endpoint.Endpoints

class MainApplication : Application() {

    var byClient : BringYourClient? = null
    var endpoints : Endpoints? = null
    var router : Router? = null


    override fun onCreate() {
        super.onCreate()

        byClient = Client.newBringYourClient()
        endpoints = Endpoint.newEndpoints(byClient)

        router = Router(byClient!!)

    }


}
