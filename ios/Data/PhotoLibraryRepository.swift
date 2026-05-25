import Foundation
import Photos
import UIKit

final class PhotoLibraryRepository {
  func getAlbums() throws -> [AlbumDTO] {
    var albums: [AlbumDTO] = []

    let userAlbums = PHAssetCollection.fetchAssetCollections(
      with: .album,
      subtype: .any,
      options: nil
    )
    albums.append(contentsOf: mapCollections(userAlbums, type: "user"))

    let smartAlbums = PHAssetCollection.fetchAssetCollections(
      with: .smartAlbum,
      subtype: .any,
      options: nil
    )
    albums.append(contentsOf: mapCollections(smartAlbums, type: "smart"))

    return albums.sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
  }

  func getAssets(
    albumId: String?,
    mediaType: String,
    page: Int,
    pageSize: Int
  ) throws -> AssetPageDTO {
    let safePage = max(page, 0)
    let safePageSize = min(max(pageSize, 1), 200)
    let fetchResult = try fetchAssets(albumId: albumId, mediaType: mediaType)
    let totalCount = fetchResult.count
    let startIndex = safePage * safePageSize

    guard startIndex < totalCount else {
      return AssetPageDTO(assets: [], hasNextPage: false, totalCount: totalCount)
    }

    let endIndex = min(startIndex + safePageSize, totalCount)
    var assets: [AssetDTO] = []
    assets.reserveCapacity(endIndex - startIndex)

    for index in startIndex..<endIndex {
      let asset = fetchResult.object(at: index)
      assets.append(mapAsset(asset, albumId: albumId))
    }

    return AssetPageDTO(
      assets: assets,
      hasNextPage: endIndex < totalCount,
      totalCount: totalCount
    )
  }

  private func fetchAssets(albumId: String?, mediaType: String) throws -> PHFetchResult<PHAsset> {
    let options = PHFetchOptions()
    options.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
    options.predicate = buildPredicate(for: mediaType)

    if let albumId, !albumId.isEmpty {
      let collections = PHAssetCollection.fetchAssetCollections(
        withLocalIdentifiers: [albumId],
        options: nil
      )
      guard collections.count > 0 else {
        return PHAsset.fetchAssets(with: options)
      }
      return PHAsset.fetchAssets(in: collections.object(at: 0), options: options)
    }

    return PHAsset.fetchAssets(with: options)
  }

  private func buildPredicate(for mediaType: String) -> NSPredicate? {
    switch mediaType {
    case "video":
      return NSPredicate(format: "mediaType == %d", PHAssetMediaType.video.rawValue)
    case "all":
      return NSPredicate(
        format: "mediaType == %d OR mediaType == %d",
        PHAssetMediaType.image.rawValue,
        PHAssetMediaType.video.rawValue
      )
    default:
      return NSPredicate(format: "mediaType == %d", PHAssetMediaType.image.rawValue)
    }
  }

  private func mapCollections(
    _ collections: PHFetchResult<PHAssetCollection>,
    type: String
  ) -> [AlbumDTO] {
    var albums: [AlbumDTO] = []

    collections.enumerateObjects { collection, _, _ in
      let assets = PHAsset.fetchAssets(in: collection, options: nil)
      guard assets.count > 0 else {
        return
      }

      let coverAsset = assets.object(at: 0)
      albums.append(
        AlbumDTO(
          id: collection.localIdentifier,
          title: collection.localizedTitle ?? "Untitled",
          assetCount: assets.count,
          coverAssetId: coverAsset.localIdentifier,
          type: type
        )
      )
    }

    return albums
  }

  private func mapAsset(_ asset: PHAsset, albumId: String?) -> AssetDTO {
    AssetDTO(
      id: asset.localIdentifier,
      uri: "ph://\(asset.localIdentifier)",
      thumbnailUri: thumbnailUri(for: asset),
      filename: nil,
      width: asset.pixelWidth,
      height: asset.pixelHeight,
      mediaType: asset.mediaType == .video ? "video" : "photo",
      creationTime: (asset.creationDate?.timeIntervalSince1970 ?? 0) * 1000,
      albumId: albumId
    )
  }

  private func thumbnailUri(for asset: PHAsset) -> String? {
    let cacheDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
      .appendingPathComponent("MediaKitThumbnails", isDirectory: true)
    try? FileManager.default.createDirectory(at: cacheDir, withIntermediateDirectories: true)

    let safeId = asset.localIdentifier
      .replacingOccurrences(of: "/", with: "_")
      .replacingOccurrences(of: ":", with: "_")
    let fileURL = cacheDir.appendingPathComponent("\(safeId).jpg")

    if FileManager.default.fileExists(atPath: fileURL.path) {
      return fileURL.absoluteString
    }

    let options = PHImageRequestOptions()
    options.isSynchronous = true
    options.deliveryMode = .opportunistic
    options.resizeMode = .fast
    options.isNetworkAccessAllowed = true

    var thumbnail: UIImage?
    PHImageManager.default().requestImage(
      for: asset,
      targetSize: CGSize(width: 400, height: 400),
      contentMode: .aspectFill,
      options: options
    ) { image, _ in
      thumbnail = image
    }

    guard let thumbnail, let data = thumbnail.jpegData(compressionQuality: 0.85) else {
      return nil
    }

    do {
      try data.write(to: fileURL)
      return fileURL.absoluteString
    } catch {
      return nil
    }
  }
}
