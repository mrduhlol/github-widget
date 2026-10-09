package com.example.githubwidget

import android.app.Application
import com.example.githubwidget.data.Repository

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Repository.init(this)
    }
}
