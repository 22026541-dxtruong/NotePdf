package ie.app.notepdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.animation.AnticipateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import ie.app.notepdf.di.IntentManager
import ie.app.notepdf.ui.screens.NotePdfNav
import ie.app.notepdf.ui.theme.NotePdfTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var intentManager: IntentManager

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        splashScreen.setOnExitAnimationListener { splashScreenView ->
            splashScreenView.iconView.animate()
                .scaleX(0f)
                .scaleY(0f)
                .alpha(0f)
                .setDuration(400L)
                .setInterpolator(AnticipateInterpolator())
                .withEndAction { splashScreenView.remove() }
                .start()
        }

        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            NotePdfTheme {
                NotePdfNav(modifier = Modifier.fillMaxSize())
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let {
            if (it.type == "application/pdf") {
                when (it.action) {
                    Intent.ACTION_SEND -> {
                        val uri = IntentCompat.getParcelableExtra(
                            intent,
                            Intent.EXTRA_STREAM,
                            Uri::class.java
                        )
                        uri?.let { intentManager.setUri(it) }
                    }

                    Intent.ACTION_VIEW -> {
                        it.data?.let { intentManager.setUri(it) }
                    }
                }
            }
        }
    }

}
