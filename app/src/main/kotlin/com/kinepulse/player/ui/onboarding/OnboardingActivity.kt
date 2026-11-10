package com.kinepulse.player.ui.onboarding

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.kinepulse.player.KinePulseApp
import com.kinepulse.player.databinding.ActivityOnboardingBinding
import com.kinepulse.player.util.ThemeHelper

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val app by lazy { application as KinePulseApp }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        finishOnboarding()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rgTheme.setOnCheckedChangeListener { _, checkedId ->
            val theme = when (checkedId) {
                binding.rbLight.id -> "light"
                binding.rbDark.id -> "dark"
                binding.rbBlack.id -> "black"
                else -> "system"
            }
            app.prefs.appTheme = theme
            ThemeHelper.applyNightMode(theme)
        }

        binding.btnStreamsOnly.setOnClickListener {
            finishOnboarding()
        }

        binding.btnGrantStandardMedia.setOnClickListener {
            val perms = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.READ_MEDIA_VIDEO)
                perms.add(Manifest.permission.READ_MEDIA_AUDIO)
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    private fun finishOnboarding() {
        app.prefs.onboardingCompleted = true
        setResult(RESULT_OK)
        finish()
    }
}
