package com.mediakit.domain

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.PermissionAwareActivity
import com.facebook.react.modules.core.PermissionListener
import java.io.File
import java.util.UUID

class CameraService(
  private val reactContext: ReactApplicationContext,
) {
  private var pendingPromise: Promise? = null
  private var outputUri: Uri? = null
  private var outputFile: File? = null

  fun openCamera(promise: Promise) {
    val activity = reactContext.currentActivity
    if (activity == null) {
      promise.reject(ERROR_NO_ACTIVITY, "No activity available to open the camera")
      return
    }

    if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
      promise.reject(ERROR_NO_CAMERA, "Camera is not available")
      return
    }

    if (ContextCompat.checkSelfPermission(reactContext, Manifest.permission.CAMERA) !=
      PackageManager.PERMISSION_GRANTED
    ) {
      requestCameraPermission(activity, promise)
      return
    }

    launchCamera(activity, promise)
  }

  fun handleActivityResult(requestCode: Int, resultCode: Int): Boolean {
    if (requestCode != CAMERA_REQUEST_CODE) {
      return false
    }

    val promise = pendingPromise ?: return true
    pendingPromise = null

    if (resultCode != Activity.RESULT_OK) {
      cleanupOutputFile()
      promise.reject(ERROR_CANCELLED, "Camera cancelled")
      return true
    }

    val file = outputFile
    val uri = outputUri
    outputFile = null
    outputUri = null

    if (file == null || uri == null || !file.exists()) {
      promise.reject(ERROR_UNKNOWN, "Failed to capture photo")
      return true
    }

    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)

    promise.resolve(
      toResultMap(
        uri = uri.toString(),
        width = options.outWidth.coerceAtLeast(0),
        height = options.outHeight.coerceAtLeast(0),
        filename = file.name,
      ),
    )
    return true
  }

  private fun requestCameraPermission(activity: Activity, promise: Promise) {
    val permissionAwareActivity = activity as? PermissionAwareActivity
    if (permissionAwareActivity == null) {
      promise.reject(ERROR_NO_ACTIVITY, "Current activity does not support permission requests")
      return
    }

    val listener =
      PermissionListener { _, _, grantResults ->
        val granted =
          grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED

        if (granted) {
          val currentActivity = reactContext.currentActivity
          if (currentActivity == null) {
            promise.reject(ERROR_NO_ACTIVITY, "No activity available to open the camera")
          } else {
            launchCamera(currentActivity, promise)
          }
        } else {
          promise.reject(ERROR_PERMISSION_DENIED, "Camera permission denied")
        }
        true
      }

    permissionAwareActivity.requestPermissions(
      arrayOf(Manifest.permission.CAMERA),
      CAMERA_PERMISSION_REQUEST_CODE,
      listener,
    )
  }

  private fun launchCamera(activity: Activity, promise: Promise) {
    if (pendingPromise != null) {
      promise.reject(ERROR_UNKNOWN, "Camera is already open")
      return
    }

    try {
      val cacheDir = File(reactContext.cacheDir, "camera").apply { mkdirs() }
      val filename = "photo_${UUID.randomUUID()}.jpg"
      val file = File(cacheDir, filename)
      val authority = "${reactContext.packageName}.mediakit.fileprovider"
      val uri = FileProvider.getUriForFile(reactContext, authority, file)

      val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
        putExtra(MediaStore.EXTRA_OUTPUT, uri)
        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }

      val chooser = Intent.createChooser(intent, null)
      pendingPromise = promise
      outputFile = file
      outputUri = uri
      activity.startActivityForResult(chooser, CAMERA_REQUEST_CODE)
    } catch (error: Exception) {
      cleanupOutputFile()
      pendingPromise = null
      promise.reject(ERROR_UNKNOWN, error.message, error)
    }
  }

  private fun cleanupOutputFile() {
    outputFile?.delete()
    outputFile = null
    outputUri = null
  }

  private fun toResultMap(
    uri: String,
    width: Int,
    height: Int,
    filename: String,
  ): WritableMap {
    return Arguments.createMap().apply {
      putString("uri", uri)
      putInt("width", width)
      putInt("height", height)
      putString("filename", filename)
    }
  }

  companion object {
    const val CAMERA_REQUEST_CODE = 2001
    private const val CAMERA_PERMISSION_REQUEST_CODE = 2002
    const val ERROR_NO_ACTIVITY = "NO_ACTIVITY"
    const val ERROR_NO_CAMERA = "NO_CAMERA"
    const val ERROR_PERMISSION_DENIED = "PERMISSION_DENIED"
    const val ERROR_CANCELLED = "CANCELLED"
    const val ERROR_UNKNOWN = "UNKNOWN"
  }
}
