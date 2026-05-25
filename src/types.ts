export type PhotoPermissionStatus =
  | 'granted'
  | 'limited'
  | 'denied'
  | 'notDetermined';

export type CameraPermissionStatus = 'granted' | 'denied' | 'notDetermined';

export interface PermissionStatus {
  photos: PhotoPermissionStatus;
  camera: CameraPermissionStatus;
}

export interface Album {
  id: string;
  title: string;
  assetCount: number;
  coverAssetId?: string;
  type: 'smart' | 'user';
}

export type MediaType = 'photo' | 'video' | 'all';

export interface Asset {
  id: string;
  uri: string;
  thumbnailUri?: string;
  filename?: string;
  width: number;
  height: number;
  mediaType: 'photo' | 'video';
  creationTime: number;
  albumId?: string;
}

export interface AssetPage {
  assets: Asset[];
  hasNextPage: boolean;
  totalCount?: number;
}

export interface GetAssetsOptions {
  albumId?: string;
  mediaType?: MediaType;
  page: number;
  pageSize?: number;
}

export interface CameraResult {
  uri: string;
  width: number;
  height: number;
  filename?: string;
}
