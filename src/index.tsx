import MediaKit from './NativeMediaKit';
import type {
  Album,
  AssetPage,
  CameraResult,
  GetAssetsOptions,
  MediaType,
  PermissionStatus,
} from './types';

export type {
  Album,
  Asset,
  AssetPage,
  CameraPermissionStatus,
  CameraResult,
  GetAssetsOptions,
  MediaType,
  PermissionStatus,
  PhotoPermissionStatus,
} from './types';

const DEFAULT_PAGE_SIZE = 50;

export function requestPermissions(): Promise<PermissionStatus> {
  return MediaKit.requestPermissions();
}

export function getPermissionStatus(): Promise<PermissionStatus> {
  return MediaKit.getPermissionStatus();
}

export function getAlbums(): Promise<Album[]> {
  return MediaKit.getAlbums().then((albums) => [...albums]);
}

export function getAssets(options: GetAssetsOptions): Promise<AssetPage> {
  const pageSize = options.pageSize ?? DEFAULT_PAGE_SIZE;
  const mediaType: MediaType = options.mediaType ?? 'photo';

  return MediaKit.getAssets(
    options.albumId ?? null,
    mediaType,
    options.page,
    pageSize
  ).then((page) => ({
    ...page,
    assets: [...page.assets],
  }));
}

export function openCamera(): Promise<CameraResult> {
  return MediaKit.openCamera();
}
