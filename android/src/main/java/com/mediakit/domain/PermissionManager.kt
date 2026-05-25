package com.mediakit.domain

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.PermissionAwareActivity
import com.facebook.react.modules.core.PermissionListener
import com.mediakit.model.PermissionStatusDto

class PermissionManager(
  private val reactContext: ReactApplicationContext,
) {
  fun getPermissionStatus(): PermissionStatusDto {
    return PermissionStatusDto(
      photos = mapPhotosPermission(getPhotosPermissionState()),
      camera = mapCameraPermission(getCameraPermissionState()),
    )
  }

  fun requestPermissions(promise: Promise) {
    val activity = reactContext.currentActivity
    if (activity == null) {
      promise.reject(ERROR_NO_ACTIVITY, "No activity available to request permissions")
      return
    }

    val missingPermissions = getRequiredPermissions().filter { permission ->
      ContextCompat.checkSelfPermission(reactContext, permission) !=
        PackageManager.PERMISSION_GRANTED
    }

    if (missingPermissions.isEmpty()) {
      promise.resolve(toWritableMap(getPermissionStatus()))
      return
    }

    val permissionAwareActivity = activity as? PermissionAwareActivity
    if (permissionAwareActivity == null) {
      promise.reject(ERROR_NO_ACTIVITY, "Current activity does not support permission requests")
      return
    }

    val listener = PermissionListener { _, _, _ ->
      promise.resolve(toWritableMap(getPermissionStatus()))
      true
    }

    permissionAwareActivity.requestPermissions(
      missingPermissions.toTypedArray(),
      PERMISSION_REQUEST_CODE,
      listener,
    )
  }

  private fun getRequiredPermissions(): List<String> {
    val permissions = mutableListOf<String>()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
      permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    permissions.add(Manifest.permission.CAMERA)
    return permissions
  }

  private fun getPhotosPermissionState(): PhotosPermissionState {
    val permission =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
      } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
      }

    return when (ContextCompat.checkSelfPermission(reactContext, permission)) {
      PackageManager.PERMISSION_GRANTED -> PhotosPermissionState.GRANTED
      else -> PhotosPermissionState.DENIED
    }
  }

  private fun getCameraPermissionState(): CameraPermissionState {
    return when (
      ContextCompat.checkSelfPermission(reactContext, Manifest.permission.CAMERA)
    ) {
      PackageManager.PERMISSION_GRANTED -> CameraPermissionState.GRANTED
      else -> CameraPermissionState.DENIED
    }
  }

  private fun mapPhotosPermission(state: PhotosPermissionState): String {
    return when (state) {
      PhotosPermissionState.GRANTED -> "granted"
      PhotosPermissionState.DENIED -> "denied"
    }
  }

  private fun mapCameraPermission(state: CameraPermissionState): String {
    return when (state) {
      CameraPermissionState.GRANTED -> "granted"
      CameraPermissionState.DENIED -> "denied"
    }
  }

  private enum class PhotosPermissionState {
    GRANTED,
    DENIED,
  }

  private enum class CameraPermissionState {
    GRANTED,
    DENIED,
  }

  companion object {
    const val ERROR_NO_ACTIVITY = "NO_ACTIVITY"
    private const val PERMISSION_REQUEST_CODE = 1001

    fun toWritableMap(status: PermissionStatusDto): com.facebook.react.bridge.WritableMap {
      return com.facebook.react.bridge.Arguments.createMap().apply {
        putString("photos", status.photos)
        putString("camera", status.camera)
      }
    }
  }
}
