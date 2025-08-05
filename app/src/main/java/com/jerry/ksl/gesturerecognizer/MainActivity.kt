package com.jerry.ksl.gesturerecognizer

import android.os.Bundle
import androidx.activity.viewModels // Import for by viewModels()
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.firebase.FirebaseApp // Import FirebaseApp
import com.jerry.ksl.gesturerecognizer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var activityMainBinding: ActivityMainBinding
    private lateinit var navController: NavController
    private val mainViewModel: MainViewModel by viewModels() // Initialize ViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase (consider moving to Application class if not already done)
        FirebaseApp.initializeApp(this)

        activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)

        // Set up the toolbar
        setSupportActionBar(activityMainBinding.toolbar)

        // Set up navigation
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.fragment_container) as NavHostFragment
        navController = navHostFragment.navController

        // Connect bottom navigation with the navigation controller
        activityMainBinding.navigation.setupWithNavController(navController)

        // Ignore reselection
        activityMainBinding.navigation.setOnNavigationItemReselectedListener {
            // ignore the reselection
        }

        // Set up destination change listener to update toolbar title
        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateToolbarTitle(destination)
        }

        // Load settings from Firestore via ViewModel
        mainViewModel.refreshSettingsFromFirestore()
    }

    private fun updateToolbarTitle(destination: NavDestination) {
        // Set the toolbar title based on the destination ID
        val title = when (destination.id) {
            R.id.camera_fragment -> getString(R.string.menu_camera)
            R.id.gallery_fragment -> getString(R.string.menu_gallery)
            R.id.model_validation_fragment -> getString(R.string.menu_validate) // Updated from learn_ksl_fragment to model_validation_fragment
            R.id.submit_training_data_fragment -> getString(R.string.menu_submit)
            R.id.settings_fragment -> getString(R.string.menu_settings)
            R.id.permissions_fragment -> getString(R.string.menu_permissions)
            else -> getString(R.string.app_name)
        }

        // Update the toolbar title
        supportActionBar?.title = title
    }
}
