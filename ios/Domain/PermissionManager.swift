import Foundation
import Photos
import AVFoundation

final class PermissionManager {
  func getPermissionStatus() -> PermissionStatusDTO {
    PermissionStatusDTO(
      photos: mapPhotoStatus(PHPhotoLibrary.authorizationStatus(for: .readWrite)),
      camera: mapCameraStatus(AVCaptureDevice.authorizationStatus(for: .video))
    )
  }

  func requestPermissions(completion: @escaping (Result<PermissionStatusDTO, Error>) -> Void) {
    PHPhotoLibrary.requestAuthorization(for: .readWrite) { _ in
      AVCaptureDevice.requestAccess(for: .video) { _ in
        DispatchQueue.main.async {
          completion(.success(self.getPermissionStatus()))
        }
      }
    }
  }

  func ensurePhotoAccess() throws {
    let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
    switch status {
    case .authorized, .limited:
      return
    default:
      throw NSError(
        domain: "MediaKit",
        code: 1,
        userInfo: [NSLocalizedDescriptionKey: "Photo library permission denied"]
      )
    }
  }

  func ensureCameraAccess() throws {
    let status = AVCaptureDevice.authorizationStatus(for: .video)
    switch status {
    case .authorized:
      return
    default:
      throw NSError(
        domain: "MediaKit",
        code: 1,
        userInfo: [NSLocalizedDescriptionKey: "Camera permission denied"]
      )
    }
  }

  func requestCameraAccess(completion: @escaping (Bool) -> Void) {
    switch AVCaptureDevice.authorizationStatus(for: .video) {
    case .authorized:
      completion(true)
    case .notDetermined:
      AVCaptureDevice.requestAccess(for: .video, completionHandler: completion)
    default:
      completion(false)
    }
  }

  private func mapPhotoStatus(_ status: PHAuthorizationStatus) -> String {
    switch status {
    case .authorized:
      return "granted"
    case .limited:
      return "limited"
    case .denied, .restricted:
      return "denied"
    case .notDetermined:
      return "notDetermined"
    @unknown default:
      return "denied"
    }
  }

  private func mapCameraStatus(_ status: AVAuthorizationStatus) -> String {
    switch status {
    case .authorized:
      return "granted"
    case .denied, .restricted:
      return "denied"
    case .notDetermined:
      return "notDetermined"
    @unknown default:
      return "denied"
    }
  }
}
