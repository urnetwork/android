package com.bringyour.network

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class TvLoginActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish() // Close TvLoginActivity as it's only an entry point
    }
}