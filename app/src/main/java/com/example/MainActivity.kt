package com.example

import android.app.Application
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.example.ui.*
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.ui.layout.onSizeChanged
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import com.example.ui.coachTarget
import com.example.ui.CoachTarget
import com.example.ui.LocalCoachStep
import com.example.ui.CoachScreen
import com.example.data.WorkCategory
import com.example.data.WorkEntry
import com.example.ui.WorkViewModel
import com.example.ui.WorkViewModelFactory
import java.text.SimpleDateFormat
import java.util.*
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

class MainActivity : ComponentActivity() {
    private fun modelFor(uid: String?): WorkViewModel {
        val owner = com.example.data.WorkAccountScope(uid)
        return androidx.lifecycle.ViewModelProvider(this, WorkViewModelFactory(application, owner))
            .get(owner.storageKey, WorkViewModel::class.java)
    }
    private val viewModel: WorkViewModel
        get() = modelFor(com.example.api.AuthManager.currentUser.value?.uid)

    val intentActionFlow = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.ui.WorkOnboardingStore(this).initialize()
        try {
            com.example.api.FirebaseSafeInitializer.init(applicationContext)
            com.example.api.AuthManager.init(applicationContext)
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "Firebase startup safeguarded: ${e.localizedMessage}")
        }
        enableEdgeToEdge()

        // Only a fresh launch acts on its intent: a recreated Activity (theme, font, process restore) must not
        // start the shortcut's shift again.
        if (savedInstanceState == null) handleIntent(intent)
        // Weekly plans: one daily job records finished plans and sends the reminders the user turned on.
        PlanJobs.schedule(this)

        // Notification permission is requested only when starting a live shift.

        setContent {
            val kisSettings = com.example.ui.kis.rememberKisSettings()
            com.example.ui.kis.KisTheme(kisSettings) {
                val colors = com.example.ui.kis.Kis.colors
                // System bar icons and the window behind the app follow the chosen light/dark theme.
                LaunchedEffect(colors.dark) {
                    val transparent = android.graphics.Color.TRANSPARENT
                    enableEdgeToEdge(
                        statusBarStyle = if (colors.dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                        navigationBarStyle = if (colors.dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                    )
                    window.decorView.setBackgroundColor(colors.bg.toArgbCompat())
                }
                // Hebrew, right to left, everywhere.
                @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
                CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                    androidx.compose.foundation.LocalOverscrollConfiguration provides null
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(colors.bg), contentAlignment = Alignment.TopCenter) {
                        val session by com.example.api.AuthManager.currentUser.collectAsStateWithLifecycle()
                        // All remembered form/selection/AI review state is confined to this owner.
                        androidx.compose.runtime.key(session?.uid) {
                            MainAppContent(
                                viewModel = modelFor(session?.uid),
                                modifier = Modifier.fillMaxSize().widthIn(max = 680.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.performAutoBackup()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        intentActionFlow.value = intent?.action
        if (intent?.action == "com.example.ACTION_START_SHIFT") {
            // Starts the timer with the default workplace and its saved rate.
            viewModel.startDefaultActiveShift()
            intentActionFlow.value = null
        }
    }
}

private fun androidx.compose.ui.graphics.Color.toArgbCompat(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

@Composable
fun MainAppContent(
    viewModel: WorkViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val onboarding = com.example.ui.rememberOnboardingController()
    val onboardingStore = remember { com.example.ui.WorkOnboardingStore(context) }
    // The 2.0 launch comes first (once), then the name, then the short tour from Home.
    var launching by rememberSaveable { mutableStateOf(onboardingStore.launchPending) }

    val googleSignInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        com.example.api.AuthManager.handleGoogleSignInResult(result.data) { success, errorMsg ->
            Toast.makeText(context, if (success) "התחברת בהצלחה למערכת" else errorMsg ?: "ההתחברות בוטלה", Toast.LENGTH_SHORT).show()
        }
    }
    val signInWithGoogle: () -> Unit = {
        if (com.example.api.WebPlatformBridge.isWebTarget) {
            com.example.api.WebPlatformBridge.signInWithWebOAuthPopup(context) { success, errorMsg ->
                Toast.makeText(context, if (success) "התחברת בהצלחה למערכת" else errorMsg ?: "ההתחברות בוטלה", Toast.LENGTH_SHORT).show()
            }
        } else try {
            val client = com.example.api.AuthManager.getGoogleSignInClient(context)
            if (client != null) googleSignInLauncher.launch(client.signInIntent)
            else Toast.makeText(context, "החיבור לחשבון עדיין לא הוגדר. אפשר להמשיך להשתמש באפליקציה.", Toast.LENGTH_LONG).show()
        } catch (t: Throwable) {
            Toast.makeText(context, "לא ניתן להתחבר כעת. אפשר להמשיך להשתמש באפליקציה.", Toast.LENGTH_LONG).show()
        }
    }
    var showAccountDialog by remember { mutableStateOf(false) }
    if (showAccountDialog && BuildConfig.ACCOUNTS_ENABLED) com.example.ui.WorkAccountDialog(
        onDismiss = { showAccountDialog = false }, onGoogle = signInWithGoogle
    )

    val activity = context as MainActivity
    val intentAction by activity.intentActionFlow.collectAsStateWithLifecycle()
    val request = when (intentAction) {
        "com.example.ACTION_IMPORT_EXCEL" -> com.example.ui.kis.ShellRequest.IMPORT
        "com.example.ACTION_OPEN_HISTORY_SEARCH" -> com.example.ui.kis.ShellRequest.HISTORY_SEARCH
        PlanJobs.ACTION_MONTH -> com.example.ui.kis.ShellRequest.HISTORY
        PlanJobs.ACTION_RATES -> com.example.ui.kis.ShellRequest.RATES
        else -> null
    }
    Box(modifier) {
        com.example.ui.kis.KisApp(
            viewModel = viewModel,
            request = request,
            onRequestHandled = { activity.intentActionFlow.value = null },
            onReplayTour = { onboarding.replay() },
            onSignIn = { showAccountDialog = true },
            modifier = Modifier.fillMaxSize().then(if (launching) Modifier.clearAndSetSemantics { } else Modifier)
        )
        if (launching) com.example.ui.kis.KisLaunch(onDone = { onboardingStore.launched(); launching = false; onboarding.replay() })
    }
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    if (!launching) com.example.ui.WorkOnboarding(onboarding, viewModel, categories.distinctBy { it.name.trim() }, entries)
}
