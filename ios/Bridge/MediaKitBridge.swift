import Foundation

@objc(MediaKitBridge)
final class MediaKitBridge: NSObject {
  @objc static let shared = MediaKitBridge()

  private let permissionManager = PermissionManager()
  private let albumService = AlbumService()
  private let cameraService = CameraService()

  @objc(requestPermissionsWithCompletion:)
  func requestPermissions(completion: @escaping (NSDictionary?, NSError?) -> Void) {
    permissionManager.requestPermissions { result in
      switch result {
      case let .success(status):
        completion(Self.toDictionary(status), nil)
      case let .failure(error):
        completion(nil, Self.makeError(code: MediaKitErrorCode.unknown, message: error.localizedDescription))
      }
    }
  }

  @objc(getPermissionStatusWithCompletion:)
  func getPermissionStatus(completion: @escaping (NSDictionary?, NSError?) -> Void) {
    completion(Self.toDictionary(permissionManager.getPermissionStatus()), nil)
  }

  @objc(getAlbumsWithCompletion:)
  func getAlbums(completion: @escaping (NSArray?, NSError?) -> Void) {
    DispatchQueue.global(qos: .userInitiated).async {
      do {
        try self.permissionManager.ensurePhotoAccess()
        let albums = try self.albumService.getAlbums()
        DispatchQueue.main.async {
          completion(albums as NSArray, nil)
        }
      } catch {
        DispatchQueue.main.async {
          completion(nil, Self.makeError(code: MediaKitErrorCode.permissionDenied, message: error.localizedDescription))
        }
      }
    }
  }

  @objc(getAssetsWithAlbumId:mediaType:page:pageSize:completion:)
  func getAssets(
    albumId: String?,
    mediaType: String,
    page: NSNumber,
    pageSize: NSNumber,
    completion: @escaping (NSDictionary?, NSError?) -> Void
  ) {
    DispatchQueue.global(qos: .userInitiated).async {
      do {
        try self.permissionManager.ensurePhotoAccess()
        let assets = try self.albumService.getAssets(
          albumId: albumId,
          mediaType: mediaType,
          page: page.intValue,
          pageSize: pageSize.intValue
        )
        DispatchQueue.main.async {
          completion(assets as NSDictionary, nil)
        }
      } catch {
        DispatchQueue.main.async {
          completion(nil, Self.makeError(code: MediaKitErrorCode.permissionDenied, message: error.localizedDescription))
        }
      }
    }
  }

  @objc(openCameraWithCompletion:)
  func openCamera(completion: @escaping (NSDictionary?, NSError?) -> Void) {
    DispatchQueue.main.async {
      self.cameraService.openCamera { result in
        switch result {
        case let .success(payload):
          completion(payload as NSDictionary, nil)
        case let .failure(error):
          completion(nil, error as NSError)
        }
      }
    }
  }

  private static func toDictionary(_ status: PermissionStatusDTO) -> NSDictionary {
    [
      "photos": status.photos,
      "camera": status.camera,
    ] as NSDictionary
  }

  private static func makeError(code: String, message: String) -> NSError {
    NSError(
      domain: "MediaKit",
      code: 1,
      userInfo: [
        NSLocalizedDescriptionKey: message,
        "code": code,
      ]
    )
  }
}
