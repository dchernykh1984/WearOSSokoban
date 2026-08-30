package com.dchernykh.sokoban

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dchernykh.sokoban.store.AssetLevelSource
import com.dchernykh.sokoban.store.DataStoreProgressStore
import com.dchernykh.sokoban.ui.SokobanApp

/**
 * The one and only activity. A watch game is a single full-screen surface with no
 * navigation to speak of, so there is nothing for a second one to do.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val store = remember { DataStoreProgressStore(applicationContext) }
            val levels = remember { AssetLevelSource(applicationContext) }
            SokobanApp(viewModel(factory = SokobanViewModel.factory(store, levels)))
        }
    }
}
