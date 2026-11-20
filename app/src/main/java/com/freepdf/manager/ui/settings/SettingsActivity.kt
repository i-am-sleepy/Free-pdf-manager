package com.freepdf.manager.ui.settings

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.freepdf.manager.R
import com.freepdf.manager.core.ThemeManager
import com.freepdf.manager.databinding.ActivitySettingsBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Settings — theme (system / light / dark) and an About section.
 * Theme is persisted via [ThemeManager] and applied immediately.
 */
@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private var _binding: ActivitySettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        // Reflect the persisted theme in the radio group.
        when (ThemeManager.current(this)) {
            ThemeManager.THEME_LIGHT -> binding.rgTheme.check(binding.rbLight.id)
            ThemeManager.THEME_DARK -> binding.rgTheme.check(binding.rbDark.id)
            else -> binding.rgTheme.check(binding.rbSystem.id)
        }

        binding.rgTheme.setOnCheckedChangeListener { _, id ->
            val theme = when (id) {
                binding.rbLight.id -> ThemeManager.THEME_LIGHT
                binding.rbDark.id -> ThemeManager.THEME_DARK
                else -> ThemeManager.THEME_SYSTEM
            }
            ThemeManager.apply(this, theme)
            recreate() // apply the new night mode to this activity
        }

        // About — show the installed version.
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: PackageManager.NameNotFoundException) {
            ""
        }
        binding.tvVersion.text = getString(R.string.settings_version) + " " + version
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
