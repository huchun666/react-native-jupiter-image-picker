package com.mediakit.model

data class PermissionStatusDto(
  val photos: String,
  val camera: String,
)

data class AlbumDto(
  val id: String,
  val title: String,
  val assetCount: Int,
  val coverAssetId: String?,
  val type: String,
)

data class AssetDto(
  val id: String,
  val uri: String,
  val thumbnailUri: String?,
  val filename: String?,
  val width: Int,
  val height: Int,
  val mediaType: String,
  val creationTime: Double,
  val albumId: String?,
)

data class AssetPageDto(
  val assets: List<AssetDto>,
  val hasNextPage: Boolean,
  val totalCount: Int?,
)
