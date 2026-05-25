import Foundation
import Photos
import AVFoundation

struct PermissionStatusDTO {
  let photos: String
  let camera: String
}

struct AlbumDTO {
  let id: String
  let title: String
  let assetCount: Int
  let coverAssetId: String?
  let type: String
}

struct AssetDTO {
  let id: String
  let uri: String
  let thumbnailUri: String?
  let filename: String?
  let width: Int
  let height: Int
  let mediaType: String
  let creationTime: Double
  let albumId: String?
}

struct AssetPageDTO {
  let assets: [AssetDTO]
  let hasNextPage: Bool
  let totalCount: Int?
}

enum MediaKitErrorCode {
  static let permissionDenied = "PERMISSION_DENIED"
  static let cancelled = "CANCELLED"
  static let noCamera = "NO_CAMERA"
  static let noActivity = "NO_ACTIVITY"
  static let unknown = "UNKNOWN"
}
