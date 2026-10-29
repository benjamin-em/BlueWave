package com.ounben.amaradio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.ounben.amaradio.database.AMARadioDatabase
import com.ounben.amaradio.database.user.AMARadioUserDatabase
import com.ounben.amaradio.ui.AMARadioTheme
import com.ounben.amaradio.utils.LocaleUtils
import com.ounben.amaradio.utils.UiScaler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.preference.PreferenceManager

class SplashActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(newBase)
        val lang = sharedPref.getString("settings_language", "system") ?: "system"
        val localeContext = LocaleUtils.wrapContext(newBase, lang)
        super.attachBaseContext(UiScaler.wrapContext(localeContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Asynchronous DB Unpacking & Initialization on Dispatchers.IO
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // Pre-warm and unpack Asset SQLite databases off the Main Thread
                    val catalogDb = AMARadioDatabase.getDatabase(applicationContext)
                    catalogDb.stationDao().getStationCount()

                    val userDb = AMARadioUserDatabase.getDatabase(applicationContext)
                    userDb.favoriteDao().getAllFavorites()
                } catch (e: Exception) {
                    Log.e("SplashActivity", "Database pre-warm on Dispatchers.IO failed", e)
                }
            }

            // Navigate to ActivityMain once I/O is finished
            val intent = Intent(this@SplashActivity, ActivityMain::class.java)
            getIntent()?.extras?.let { intent.putExtras(it) }
            startActivity(intent)
            finish()
        }

        setContent {
            AMARadioTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(Color(0xFF58BEFF), Color(0xFF0A5CDC))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(R.drawable.ic_cat_face),
                            contentDescription = null,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(24.dp))
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "AMARadio",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "v${stringResource(R.string.version_name)}",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
