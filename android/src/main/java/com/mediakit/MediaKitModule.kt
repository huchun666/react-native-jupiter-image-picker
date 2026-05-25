package com.mediakit

import android.content.Intent
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.mediakit.domain.AlbumService
import com.mediakit.domain.CameraService
import com.mediakit.domain.PermissionManager

class MediaKitModule(reactContext: ReactApplicationContext) :
  NativeMediaKitSpec(reactContext),
  ActivityEventListener {

  private val permissionManager = PermissionManager(reactContext)
  private val albumService = AlbumService(reactContext)
  private val cameraService = CameraService(reactContext)

  init {
    reactContext.addActivityEventListener(this)
  }

  override fun requestPermissions(promise: Promise) {
    permissionManager.requestPermissions(promise)
  }

  override fun getPermissionStatus(promise: Promise) {
    try {
      promise.resolve(
        PermissionManager.toWritableMap(permissionManager.getPermissionStatus()),
      )
    } catch (error: Exception) {
      promise.reject(ERROR_UNKNOWN, error.message, error)
    }
  }

  override fun getAlbums(promise: Promise) {
    try {
      promise.resolve(albumService.getAlbums())
    } catch (error: SecurityException) {
      promise.reject(ERROR_PERMISSION_DENIED, "Photo library permission denied", error)
    } catch (error: Exception) {
      promise.reject(ERROR_UNKNOWN, error.message, error)
    }
  }

  override fun getAssets(
    albumId: String?,
    mediaType: String,
    page: Double,
    pageSize: Double,
    promise: Promise,
  ) {
    try {
      promise.resolve(
        albumService.getAssets(
          albumId = albumId,
          mediaType = mediaType,
          page = page,
          pageSize = pageSize,
        ),
      )
    } catch (error: SecurityException) {
      promise.reject(ERROR_PERMISSION_DENIED, "Photo library permission denied", error)
    } catch (error: Exception) {
      promise.reject(ERROR_UNKNOWN, error.message, error)
    }
  }

  override fun openCamera(promise: Promise) {
    cameraService.openCamera(promise)
  }

  override fun onActivityResult(
    activity: android.app.Activity,
    requestCode: Int,
    resultCode: Int,
    data: Intent?,
  ) {
    cameraService.handleActivityResult(requestCode, resultCode)
  }

  override fun onNewIntent(intent: Intent) {
  }

  companion object {
    const val NAME = NativeMediaKitSpec.NAME
    private const val ERROR_UNKNOWN = "UNKNOWN"
    private const val ERROR_PERMISSION_DENIED = "PERMISSION_DENIED"
  }
}
