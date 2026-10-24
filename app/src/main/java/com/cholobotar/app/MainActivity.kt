package com.cholobotar.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val vm: StudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            CholobotarTheme(dark = vm.dark) {
                MainScreen(vm)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        vm.onBackground()
    }
}

@Composable
fun MainScreen(vm: StudioViewModel) {
    val ctx = LocalContext.current
    val g = LocalGlass.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var pendingSlot by remember { mutableStateOf("") }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setMic(true)
    }
    val toggleMic: () -> Unit = {
        if (vm.micOn) {
            vm.setMic(false)
        } else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            vm.setMic(true)
        } else {
            permLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && pendingSlot.isNotEmpty()) vm.importSlot(pendingSlot, uri)
    }
    val pickBg = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.addBgFromUri(uri)
    }
    val pickSfx = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.addSfxFromUri(uri)
    }

    // one frame loop for the whole app: bounce, mouth, blink, teleprompter, record timer
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
            last = now
            vm.frame(dt, now)
        }
    }

    Column(Modifier.fillMaxSize().background(g.bg)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> StudioScreen(
                    vm, toggleMic,
                    onAddBg = { pickBg.launch(arrayOf("image/*", "video/*")) },
                    onAddSfx = { pickSfx.launch(arrayOf("audio/*")) }
                )
                1 -> AvatarScreen(vm) { slot ->
                    pendingSlot = slot
                    pickImage.launch("image/*")
                }
                2 -> ScriptScreen(vm)
                else -> SettingsScreen(vm, toggleMic)
            }
        }
        BottomTabs(tab) { tab = it }
    }
}
