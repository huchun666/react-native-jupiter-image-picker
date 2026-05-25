package com.mediakit

import com.facebook.react.bridge.ReactApplicationContext

class MediaKitModule(reactContext: ReactApplicationContext) :
  NativeMediaKitSpec(reactContext) {

  override fun multiply(a: Double, b: Double): Double {
    return a * b
  }

  companion object {
    const val NAME = NativeMediaKitSpec.NAME
  }
}
