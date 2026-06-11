package com.mediakit.data

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import com.mediakit.model.AlbumDto
import com.mediakit.model.AssetDto
import com.mediakit.model.AssetPageDto
import android.util.Log

class MediaStoreRepository(
  private val contentResolver: ContentResolver,
) {
  fun getAlbums(): List<AlbumDto> {
    val albums = linkedMapOf<String, AlbumDto>()
    // 查 所有图片
    val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection =
      arrayOf(
        MediaStore.Images.Media.BUCKET_ID, // 相册ID
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME, // 相册名称
        MediaStore.Images.Media._ID, // 图片ID
        MediaStore.Images.Media.DATE_ADDED,// 图片创建时间
      )
    // 按相册ID升序，图片创建时间降序
    val sortOrder =
      "${MediaStore.Images.Media.BUCKET_ID} ASC, ${MediaStore.Images.Media.DATE_ADDED} DESC"

    // use 自动关闭 cursor（避免内存泄漏）
    query(uri, projection, null, null, sortOrder).use { cursor ->
      if (cursor == null) {
        return emptyList()
      }

      // 提前缓存列 index，提高性能
      val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
      val bucketNameColumn =
        cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
      val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)

      // 遍历每一行图片
      while (cursor.moveToNext()) {
        val bucketId = cursor.getString(bucketIdColumn) ?: continue
        val existing = albums[bucketId]
        // 如果相册已存在，则累加计数
        if (existing != null) {
          albums[bucketId] = existing.copy(assetCount = existing.assetCount + 1)
          continue
        }

        val coverId = cursor.getLong(idColumn)
        // 构造一个相册对象（AlbumDto）
        albums[bucketId] =
          AlbumDto(
            id = bucketId,
            title = cursor.getString(bucketNameColumn)?.ifBlank { DEFAULT_ALBUM_TITLE }
              ?: DEFAULT_ALBUM_TITLE,
            assetCount = 1,
            coverAssetId = coverId.toString(),
            type = "user",
          )
      }
    }
    Log.d("MediaStoreRepository", "getAssets: albumId: $albums.values")

    return albums.values.sortedBy { it.title.lowercase() }
  }

  fun getAssets(
    albumId: String?,
    mediaType: String,
    page: Int,
    pageSize: Int,
  ): AssetPageDto {
    Log.d("MediaStoreRepository", "getAssets: albumId: $albumId, mediaType: $mediaType, page: $page, pageSize: $pageSize")
    val safePage = page.coerceAtLeast(0)
    val safePageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
    // 根据类型选表
    val uri = resolveMediaUri(mediaType)
    val bucketColumn = resolveBucketColumn(mediaType)
    val projection =
      arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.WIDTH,
        MediaStore.MediaColumns.HEIGHT,
        MediaStore.MediaColumns.DATE_ADDED,
        MediaStore.MediaColumns.MIME_TYPE,
        bucketColumn,
      )

    val selectionBuilder = StringBuilder()
    val selectionArgs = mutableListOf<String>()

    if (!albumId.isNullOrBlank()) {
      selectionBuilder.append("$bucketColumn = ?")
      selectionArgs.add(albumId)
    }

    if (mediaType == "all") {
      val mediaTypeSelection =
        "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)"
      if (selectionBuilder.isNotEmpty()) {
        selectionBuilder.append(" AND ")
      }
      selectionBuilder.append(mediaTypeSelection)
      selectionArgs.add(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
      selectionArgs.add(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
    }

    val selection = selectionBuilder.takeIf { it.isNotEmpty() }?.toString()
    val selectionArgsArray = selectionArgs.takeIf { it.isNotEmpty() }?.toTypedArray()

    val totalCount = countAssets(uri, selection, selectionArgsArray)
    val assets = mutableListOf<AssetDto>()

    queryPaginated(
      uri = uri,
      projection = projection,
      selection = selection,
      selectionArgs = selectionArgsArray,
      page = safePage,
      pageSize = safePageSize,
    ).use { cursor ->
      if (cursor == null) {
        return AssetPageDto(
          assets = emptyList(),
          hasNextPage = false,
          totalCount = totalCount,
        )
      }

      val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
      val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
      val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
      val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
      val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
      val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
      val bucketIndex = cursor.getColumnIndexOrThrow(bucketColumn)

      while (cursor.moveToNext()) {
        val id = cursor.getLong(idColumn)
        val itemUri = ContentUris.withAppendedId(uri, id)
        assets.add(
          AssetDto(
            id = id.toString(),
            uri = itemUri.toString(),
            thumbnailUri = itemUri.toString(),
            filename = cursor.getString(nameColumn),
            width = cursor.getInt(widthColumn).coerceAtLeast(0),
            height = cursor.getInt(heightColumn).coerceAtLeast(0),
            mediaType = mapMediaType(cursor.getString(mimeColumn)),
            creationTime = cursor.getLong(dateColumn) * 1000.0,
            albumId = cursor.getString(bucketIndex),
          ),
        )
      }
    }

    val startIndex = safePage * safePageSize
    val hasNextPage = totalCount?.let { startIndex + assets.size < it } ?: assets.size == safePageSize

    return AssetPageDto(
      assets = assets,
      hasNextPage = hasNextPage,
      totalCount = totalCount,
    )
  }

  private fun resolveBucketColumn(mediaType: String): String {
    return when (mediaType) {
      "video" -> MediaStore.Video.Media.BUCKET_ID
      "all" -> MediaStore.Files.FileColumns.BUCKET_ID
      else -> MediaStore.Images.Media.BUCKET_ID
    }
  }

  private fun resolveMediaUri(mediaType: String): Uri {
    return when (mediaType) {
      "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
      "all" -> MediaStore.Files.getContentUri("external")
      else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
  }

  private fun countAssets(
    uri: Uri,
    selection: String?,
    selectionArgs: Array<String>?,
  ): Int? {
    val projection = arrayOf(MediaStore.MediaColumns._ID)
    query(uri, projection, selection, selectionArgs, null).use { cursor ->
      return cursor?.count
    }
  }

  private fun queryPaginated(
    uri: Uri,
    projection: Array<String>,
    selection: String?,
    selectionArgs: Array<String>?,
    page: Int,
    pageSize: Int,
  ): Cursor? {
    val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val queryArgs =
        Bundle().apply {
          putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.MediaColumns.DATE_ADDED))
          putInt(
            ContentResolver.QUERY_ARG_SORT_DIRECTION,
            ContentResolver.QUERY_SORT_DIRECTION_DESCENDING,
          )
          putInt(ContentResolver.QUERY_ARG_LIMIT, pageSize)
          putInt(ContentResolver.QUERY_ARG_OFFSET, page * pageSize)
          if (!selection.isNullOrBlank()) {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
          }
        }
      return contentResolver.query(uri, projection, queryArgs, null)
    }

    return query(
      uri,
      projection,
      selection,
      selectionArgs,
      "$sortOrder LIMIT $pageSize OFFSET ${page * pageSize}",
    )
  }

  private fun query(
    uri: Uri, // 查哪张表
    projection: Array<String>, // 查哪些字段
    selection: String?, // 查哪些条件
    selectionArgs: Array<String>?, // 查哪些条件值
    sortOrder: String?, // 排序方式
  ): Cursor? {
    return contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
  }

  private fun mapMediaType(mimeType: String?): String {
    return if (mimeType?.startsWith("video/") == true) {
      "video"
    } else {
      "photo"
    }
  }

  companion object {
    private const val DEFAULT_ALBUM_TITLE = "Untitled"
    private const val MAX_PAGE_SIZE = 200
  }
}
