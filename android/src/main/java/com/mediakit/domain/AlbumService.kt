package com.mediakit.domain

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.mediakit.data.MediaStoreRepository
import com.mediakit.model.AlbumDto
import com.mediakit.model.AssetDto
import com.mediakit.model.AssetPageDto

class AlbumService(
  reactContext: ReactApplicationContext,
) {
  private val repository = MediaStoreRepository(reactContext.contentResolver)

  fun getAlbums(): WritableArray {
    return toWritableArray(repository.getAlbums().map(::toAlbumMap))
  }

  fun getAssets(
    albumId: String?,
    mediaType: String,
    page: Double,
    pageSize: Double,
  ): WritableMap {
    val pageResult =
      repository.getAssets(
        albumId = albumId,
        mediaType = mediaType,
        page = page.toInt(),
        pageSize = pageSize.toInt(),
      )
    return toAssetPageMap(pageResult)
  }

  private fun toAlbumMap(album: AlbumDto): WritableMap {
    return Arguments.createMap().apply {
      putString("id", album.id)
      putString("title", album.title)
      putInt("assetCount", album.assetCount)
      if (album.coverAssetId != null) {
        putString("coverAssetId", album.coverAssetId)
      }
      putString("type", album.type)
    }
  }

  private fun toAssetMap(asset: AssetDto): WritableMap {
    return Arguments.createMap().apply {
      putString("id", asset.id)
      putString("uri", asset.uri)
      if (asset.thumbnailUri != null) {
        putString("thumbnailUri", asset.thumbnailUri)
      }
      if (asset.filename != null) {
        putString("filename", asset.filename)
      }
      putInt("width", asset.width)
      putInt("height", asset.height)
      putString("mediaType", asset.mediaType)
      putDouble("creationTime", asset.creationTime)
      if (asset.albumId != null) {
        putString("albumId", asset.albumId)
      }
    }
  }

  private fun toAssetPageMap(page: AssetPageDto): WritableMap {
    return Arguments.createMap().apply {
      putArray("assets", toWritableArray(page.assets.map(::toAssetMap)))
      putBoolean("hasNextPage", page.hasNextPage)
      if (page.totalCount != null) {
        putInt("totalCount", page.totalCount)
      }
    }
  }

  private fun toWritableArray(items: List<WritableMap>): WritableArray {
    return Arguments.createArray().apply {
      items.forEach { pushMap(it) }
    }
  }
}
