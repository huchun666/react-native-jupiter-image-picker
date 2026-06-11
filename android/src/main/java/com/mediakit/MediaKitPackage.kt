package com.mediakit

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider
import java.util.HashMap

// RN 模块注册器，告诉 RN 如何找到这个模块
class MediaKitPackage : BaseReactPackage() {
  // RN 在 JS 调用模块时，会走这里
  override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? {
    return if (name == MediaKitModule.NAME) {
      MediaKitModule(reactContext)
    } else {
      null
    }
  }

  // 提供“模块的配置和能力信息”，给 RN 新架构用
  override fun getReactModuleInfoProvider() = ReactModuleInfoProvider {
    mapOf(
      MediaKitModule.NAME to ReactModuleInfo(
        name = MediaKitModule.NAME, // JS 模块名
        className = MediaKitModule.NAME, // Native 类名
        canOverrideExistingModule = false, // 是否可以覆盖已有的模块
        needsEagerInit = false, // 是否需要提前初始化
        isCxxModule = false, // 是否是 C++ 模块(JSI)
        isTurboModule = true // 是否是 Turbo 模块
      )
    )
  }
}
