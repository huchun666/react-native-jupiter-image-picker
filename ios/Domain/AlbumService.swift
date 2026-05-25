import Foundation

final class AlbumService {
  private let repository = PhotoLibraryRepository()

  func getAlbums() throws -> [[String: Any]] {
    try repository.getAlbums().map(toDictionary)
  }

  func getAssets(
    albumId: String?,
    mediaType: String,
    page: Int,
    pageSize: Int
  ) throws -> [String: Any] {
    let pageResult = try repository.getAssets(
      albumId: albumId,
      mediaType: mediaType,
      page: page,
      pageSize: pageSize
    )
    return toDictionary(pageResult)
  }

  private func toDictionary(_ album: AlbumDTO) -> [String: Any] {
    var dictionary: [String: Any] = [
      "id": album.id,
      "title": album.title,
      "assetCount": album.assetCount,
      "type": album.type,
    ]

    if let coverAssetId = album.coverAssetId {
      dictionary["coverAssetId"] = coverAssetId
    }

    return dictionary
  }

  private func toDictionary(_ asset: AssetDTO) -> [String: Any] {
    var dictionary: [String: Any] = [
      "id": asset.id,
      "uri": asset.uri,
      "width": asset.width,
      "height": asset.height,
      "mediaType": asset.mediaType,
      "creationTime": asset.creationTime,
    ]

    if let thumbnailUri = asset.thumbnailUri {
      dictionary["thumbnailUri"] = thumbnailUri
    }

    if let filename = asset.filename {
      dictionary["filename"] = filename
    }

    if let albumId = asset.albumId {
      dictionary["albumId"] = albumId
    }

    return dictionary
  }

  private func toDictionary(_ page: AssetPageDTO) -> [String: Any] {
    var dictionary: [String: Any] = [
      "assets": page.assets.map(toDictionary),
      "hasNextPage": page.hasNextPage,
    ]

    if let totalCount = page.totalCount {
      dictionary["totalCount"] = totalCount
    }

    return dictionary
  }
}
