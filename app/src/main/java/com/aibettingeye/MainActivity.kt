package com.aibettingeye

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.aibettingeye.observer.MatchParser
import com.aibettingeye.observer.ObserverState
import com.aibettingeye.observer.Store
import com.aibettingeye.ui.AppRoot
import com.aibettingeye.ui.BettingEyeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            BettingEyeTheme {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        ObserverState.resumeTick.value = ObserverState.resumeTick.value + 1
    }

    private fun handleIntent(i: Intent?) {
        if (i?.action != Intent.ACTION_SEND) return
        val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val res = MatchParser.parse(text.lines(), "Paylaşım")
        val m = res.match
        if (m != null) {
            ObserverState.shareNote.value = ""
            ObserverState.onDetected(m)
            ObserverState.selected.value = m
        } else {
            ObserverState.shareNote.value =
                "Paylaşılan metinde maç bulunamadı: \"" + text.take(80) + "\". Maçı elle girebilirsin."
        }
    }
}
