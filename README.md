# react-native-media-kit

A React Native Turbo Module for accessing the photo library, browsing albums and assets, and capturing photos with the native camera.

## Features

- Request and query photo library + camera permissions
- List albums (user albums and iOS smart albums)
- Paginated asset loading with optional album and media type filters
- Thumbnail URIs suitable for `<Image />` grids
- Open the system camera and receive a captured photo URI

## Requirements

- React Native **0.76+** (New Architecture / Turbo Modules)
- iOS 15.1+
- Android API 24+

## Installation

```sh
npm install react-native-media-kit
# or
yarn add react-native-media-kit
```

### iOS

Add usage descriptions to your app's `Info.plist`:

```xml
<key>NSPhotoLibraryUsageDescription</key>
<string>This app needs access to your photo library.</string>
<key>NSCameraUsageDescription</key>
<string>This app needs access to your camera.</string>
```

Then install pods:

```sh
cd ios && pod install
```

### Android

Photo and camera permissions are declared by the library and merged into your app automatically. No extra setup is required beyond requesting runtime permissions from JS.

## Usage

```tsx
import {
  getAlbums,
  getAssets,
  getPermissionStatus,
  openCamera,
  requestPermissions,
} from 'react-native-media-kit';

// Permissions
const status = await requestPermissions();
// { photos: 'granted' | 'limited' | 'denied' | 'notDetermined', camera: 'granted' | ... }

// Albums
const albums = await getAlbums();

// Assets (paginated)
const page = await getAssets({
  albumId: albums[0]?.id,
  mediaType: 'photo', // 'photo' | 'video' | 'all'
  page: 0,
  pageSize: 30,
});
// { assets, hasNextPage, totalCount? }

// Display thumbnails in a grid
<Image source={{ uri: asset.thumbnailUri ?? asset.uri }} />

// Native camera
const photo = await openCamera();
// { uri, width, height, filename? }
<Image source={{ uri: photo.uri }} />
```

## API

### `requestPermissions(): Promise<PermissionStatus>`

Requests photo library and camera permissions, then returns the current status.

### `getPermissionStatus(): Promise<PermissionStatus>`

Returns the current permission status without prompting.

### `getAlbums(): Promise<Album[]>`

Returns available albums sorted by title.

| Field | Type | Description |
| --- | --- | --- |
| `id` | `string` | Album identifier |
| `title` | `string` | Display name |
| `assetCount` | `number` | Number of assets |
| `coverAssetId` | `string?` | Cover asset id |
| `type` | `'smart' \| 'user'` | Album type |

### `getAssets(options): Promise<AssetPage>`

| Option | Type | Default | Description |
| --- | --- | --- | --- |
| `albumId` | `string?` | — | Filter by album; omit for all photos |
| `mediaType` | `'photo' \| 'video' \| 'all'` | `'photo'` | Media filter |
| `page` | `number` | — | Zero-based page index |
| `pageSize` | `number` | `50` | Items per page (max 200) |

Each `Asset` includes:

| Field | Type | Description |
| --- | --- | --- |
| `id` | `string` | Asset identifier |
| `uri` | `string` | Canonical asset URI |
| `thumbnailUri` | `string?` | Displayable URI for `<Image />` |
| `filename` | `string?` | File name (Android) |
| `width` / `height` | `number` | Dimensions in pixels |
| `mediaType` | `'photo' \| 'video'` | Media type |
| `creationTime` | `number` | Creation time (Unix ms) |
| `albumId` | `string?` | Parent album id |

### `openCamera(): Promise<CameraResult>`

Presents the native camera UI. Resolves with `{ uri, width, height, filename? }` on success.

Common rejection codes:

| Code | Description |
| --- | --- |
| `PERMISSION_DENIED` | Camera permission not granted |
| `CANCELLED` | User closed the camera |
| `NO_CAMERA` | No camera available (e.g. iOS Simulator) |
| `NO_ACTIVITY` | No Android activity to launch the camera |

## Platform notes

### URIs

- **Android** — `uri` and `thumbnailUri` are `content://` URIs. Both work with React Native `<Image />` when photo permissions are granted. Use `uri` for full-resolution previews.
- **iOS** — `uri` uses the `ph://` scheme (asset identifier). React Native `<Image />` cannot load it directly. Use `thumbnailUri` (a cached `file://` JPEG) for display. Full-resolution iOS loading may require a future dedicated API.

### Permissions

- iOS supports **limited** photo library access (`photos: 'limited'`). Treat it the same as `granted` when browsing.
- The iOS Simulator has no camera. Use a physical device to test `openCamera()`.

## Example app

```sh
# Terminal 1
cd example && npm start

# Terminal 2
cd example && npm run ios
# or
adb reverse tcp:8081 tcp:8081
cd example && npm run android
```

The example demonstrates permissions, album browsing, a photo grid with tap-to-preview, and native camera capture.

## Contributing

See the [contributing guide](CONTRIBUTING.md).

## License

MIT
