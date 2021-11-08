package org.onionshare.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OnionShareApp @Inject constructor() : Application()
