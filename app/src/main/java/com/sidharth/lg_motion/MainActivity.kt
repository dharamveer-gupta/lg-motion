package com.sidharth.lg_motion

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.sidharth.lg_motion.databinding.ActivityMainBinding
import com.sidharth.lg_motion.util.LiquidGalaxyController
import com.sidharth.lg_motion.util.NetworkUtils
import com.sidharth.lg_motion.util.ToastUtil
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val activityMainBinding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }
    private var navHostFragment: Fragment? = null
    private val preferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(this)
    }
    private var scheduledExecutorService: ScheduledExecutorService? = null
    override fun onDestroy() {
        super.onDestroy()
        NetworkUtils.stopNetworkCallback(this)
        scheduledExecutorService?.shutdown()
        scheduledExecutorService = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(activityMainBinding.root)
        navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
        setupBottomNavigation()
        setupRailViewNavigation()
        setupLiquidGalaxyConnection().also {
            addNetworkCallbackAndStartSchedule()
        }
    }

    private fun addNetworkCallbackAndStartSchedule() {
        NetworkUtils.startNetworkCallback(this, onConnectionAvailable = {
            setupLiquidGalaxyConnection()
        }, onConnectionLost = {
            lifecycleScope.launch {
                LiquidGalaxyController.getInstance()?.disconnect()
                preferences.edit().putBoolean("connection_status", false).apply()
            }
        })

        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
        scheduledExecutorService?.scheduleAtFixedRate({
            preferences.edit().putBoolean(
                "connection_status", LiquidGalaxyController.getInstance()?.connected == true
            ).apply()
        }, 0, 1, TimeUnit.SECONDS)
    }

    private fun setupBottomNavigation() {
        navHostFragment?.findNavController()?.apply {
            activityMainBinding.bottomNavigationView?.setupWithNavController(this)
            this.addOnDestinationChangedListener { _, destination, _ ->
                checkBottomNavigationViewMenuItem(destination.id)
            }
        }
    }

    private fun setupRailViewNavigation() {
        navHostFragment?.findNavController()?.apply {
            activityMainBinding.navigationRailView?.setupWithNavController(this)
            this.addOnDestinationChangedListener { _, destination, _ ->
                checkBottomNavigationViewMenuItem(destination.id)
            }
        }
    }

    private fun checkBottomNavigationViewMenuItem(id: Int) {
        when (id) {
            R.id.cameraFragment, R.id.audioFragment -> {
                activityMainBinding.bottomNavigationView?.menu?.findItem(R.id.homeFragment)?.isChecked =
                    true
                activityMainBinding.navigationRailView?.menu?.findItem(R.id.homeFragment)?.isChecked =
                    true
            }

            R.id.aboutFragment, R.id.openSourceLicenseFragment, R.id.privacyPolicyFragment -> {
                activityMainBinding.bottomNavigationView?.menu?.findItem(R.id.settingsFragment)?.isChecked =
                    true
                activityMainBinding.navigationRailView?.menu?.findItem(R.id.settingsFragment)?.isChecked =
                    true
            }
        }
    }

    private fun setupLiquidGalaxyConnection() {
        val editor = preferences.edit()

        val autoConnect = preferences.getBoolean("auto_connect", false)
        val username = preferences.getString("username", "") ?: ""
        val password = preferences.getString("password", "") ?: ""
        val ip = preferences.getString("ip", "") ?: ""
        val port = preferences.getString("port", "-1")?.toIntOrNull() ?: -1
        val screens = preferences.getInt("screens", 3)

        if (autoConnect && username.isNotBlank() && password.isNotBlank() && ip.isNotBlank() && port >= 0) {
            LiquidGalaxyController.newInstance(
                username = username,
                password = password,
                host = ip,
                port = port,
                screens = screens,
            )

            if (NetworkUtils.isNetworkConnected(this)) {
                lifecycleScope.launch {
                    when (LiquidGalaxyController.getInstance()?.connect()) {
                        true -> {
                            showToast("Connection Successful")
                            editor.putBoolean("connection_status", true).apply()
                        }

                        else -> {
                            showToast("Connection Failed")
                            editor.putBoolean("connection_status", false).apply()
                        }
                    }
                }
            } else {
                showToast("No Internet Connection")
                editor.putBoolean("connection_status", false).apply()
            }
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            ToastUtil.showToast(this, message)
        }
    }
}