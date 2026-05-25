import Foundation
import UIKit

final class CameraService: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
  private let permissionManager = PermissionManager()
  private var completion: ((Result<[String: Any], Error>) -> Void)?

  func openCamera(completion: @escaping (Result<[String: Any], Error>) -> Void) {
    guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
      completion(.failure(Self.makeError(code: MediaKitErrorCode.noCamera, message: "Camera is not available")))
      return
    }

    permissionManager.requestCameraAccess { [weak self] granted in
      guard let self else {
        return
      }

      guard granted else {
        completion(.failure(Self.makeError(code: MediaKitErrorCode.permissionDenied, message: "Camera permission denied")))
        return
      }

      guard let presenter = Self.topViewController() else {
        completion(.failure(Self.makeError(code: MediaKitErrorCode.unknown, message: "Unable to find a view controller to present the camera")))
        return
      }

      self.completion = completion

      let picker = UIImagePickerController()
      picker.sourceType = .camera
      picker.mediaTypes = ["public.image"]
      picker.delegate = self
      picker.modalPresentationStyle = .fullScreen
      presenter.present(picker, animated: true)
    }
  }

  func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
    picker.dismiss(animated: true)
    finish(with: .failure(Self.makeError(code: MediaKitErrorCode.cancelled, message: "Camera cancelled")))
  }

  func imagePickerController(
    _ picker: UIImagePickerController,
    didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
  ) {
    picker.dismiss(animated: true)

    guard let image = info[.originalImage] as? UIImage else {
      finish(with: .failure(Self.makeError(code: MediaKitErrorCode.unknown, message: "Failed to capture photo")))
      return
    }

    do {
      let result = try Self.saveCapturedImage(image)
      finish(with: .success(result))
    } catch {
      finish(with: .failure(error))
    }
  }

  private func finish(with result: Result<[String: Any], Error>) {
    completion?(result)
    completion = nil
  }

  private static func saveCapturedImage(_ image: UIImage) throws -> [String: Any] {
    let cacheDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
      .appendingPathComponent("MediaKitCamera", isDirectory: true)
    try FileManager.default.createDirectory(at: cacheDir, withIntermediateDirectories: true)

    let filename = "photo_\(UUID().uuidString).jpg"
    let fileURL = cacheDir.appendingPathComponent(filename)

    guard let data = image.jpegData(compressionQuality: 0.92) else {
      throw makeError(code: MediaKitErrorCode.unknown, message: "Failed to encode captured photo")
    }

    try data.write(to: fileURL)

    return [
      "uri": fileURL.absoluteString,
      "width": Int(image.size.width * image.scale),
      "height": Int(image.size.height * image.scale),
      "filename": filename,
    ]
  }

  private static func topViewController() -> UIViewController? {
    let scenes = UIApplication.shared.connectedScenes
      .compactMap { $0 as? UIWindowScene }
      .filter { $0.activationState == .foregroundActive || $0.activationState == .foregroundInactive }

    guard let window = scenes.compactMap({ $0.windows.first(where: \.isKeyWindow) }).first
      ?? scenes.first?.windows.first,
      var top = window.rootViewController else {
      return nil
    }

    while let presented = top.presentedViewController {
      top = presented
    }

    return top
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
