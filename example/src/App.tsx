import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Dimensions,
  FlatList,
  Image,
  Modal,
  Pressable,
  SafeAreaView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import {
  getAlbums,
  getAssets,
  getPermissionStatus,
  openCamera,
  requestPermissions,
  type Album,
  type Asset,
  type CameraResult,
  type PermissionStatus,
} from 'react-native-media-kit';

const PAGE_SIZE = 30;
const COLUMN_COUNT = 3;
const TILE_GAP = 4;
const HORIZONTAL_PADDING = 16;
const tileSize =
  (Dimensions.get('window').width -
    HORIZONTAL_PADDING * 2 -
    TILE_GAP * COLUMN_COUNT) /
  COLUMN_COUNT;

function getAssetImageUri(asset: Asset, preferFullSize = false) {
  if (preferFullSize && !asset.uri.startsWith('ph://')) {
    return asset.uri;
  }

  return asset.thumbnailUri ?? asset.uri;
}

export default function App() {
  const [permissionStatus, setPermissionStatus] =
    useState<PermissionStatus | null>(null);
  const [albums, setAlbums] = useState<Album[]>([]);
  const [selectedAlbum, setSelectedAlbum] = useState<Album | null>(null);
  const [assets, setAssets] = useState<Asset[]>([]);
  const [page, setPage] = useState(0);
  const [hasNextPage, setHasNextPage] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [capturedPhoto, setCapturedPhoto] = useState<CameraResult | null>(null);
  const [previewAsset, setPreviewAsset] = useState<Asset | null>(null);

  const canBrowsePhotos = useMemo(() => {
    return (
      permissionStatus?.photos === 'granted' ||
      permissionStatus?.photos === 'limited'
    );
  }, [permissionStatus]);

  const refreshPermissionStatus = useCallback(async () => {
    const status = await getPermissionStatus();
    setPermissionStatus(status);
    return status;
  }, []);

  const loadAlbums = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const result = await getAlbums();
      setAlbums(result);
    } catch (loadError) {
      setError(
        loadError instanceof Error ? loadError.message : 'Failed to load albums'
      );
    } finally {
      setLoading(false);
    }
  }, []);

  const loadAssets = useCallback(
    async (album: Album | null, nextPage: number, append: boolean) => {
      setLoading(true);
      setError(null);

      try {
        const result = await getAssets({
          albumId: album?.id,
          mediaType: 'photo',
          page: nextPage,
          pageSize: PAGE_SIZE,
        });

        setAssets((current) =>
          append ? [...current, ...result.assets] : [...result.assets]
        );
        setHasNextPage(result.hasNextPage);
        setPage(nextPage);
      } catch (loadError) {
        setError(
          loadError instanceof Error
            ? loadError.message
            : 'Failed to load assets'
        );
      } finally {
        setLoading(false);
      }
    },
    []
  );

  useEffect(() => {
    refreshPermissionStatus();
  }, [refreshPermissionStatus]);

  useEffect(() => {
    if (!canBrowsePhotos) {
      return;
    }

    loadAlbums();
    loadAssets(null, 0, false);
  }, [canBrowsePhotos, loadAlbums, loadAssets]);

  const handleRequestPermissions = async () => {
    setLoading(true);
    setError(null);

    try {
      const status = await requestPermissions();
      setPermissionStatus(status);
    } catch (permissionError) {
      setError(
        permissionError instanceof Error
          ? permissionError.message
          : 'Failed to request permissions'
      );
    } finally {
      setLoading(false);
    }
  };

  const handleSelectAlbum = async (album: Album) => {
    setSelectedAlbum(album);
    await loadAssets(album, 0, false);
  };

  const handleShowAllPhotos = async () => {
    setSelectedAlbum(null);
    await loadAssets(null, 0, false);
  };

  const handleLoadMore = async () => {
    if (!hasNextPage || loading) {
      return;
    }

    await loadAssets(selectedAlbum, page + 1, true);
  };

  const handleOpenCamera = async () => {
    setError(null);

    try {
      const photo = await openCamera();
      setCapturedPhoto(photo);
      await refreshPermissionStatus();
    } catch (cameraError) {
      const message =
        cameraError instanceof Error
          ? cameraError.message
          : 'Failed to open camera';
      if (message !== 'Camera cancelled') {
        setError(message);
      }
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>MediaKit Example</Text>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Permissions</Text>
        <Text style={styles.meta}>
          Photos: {permissionStatus?.photos ?? 'checking...'}
        </Text>
        <Text style={styles.meta}>
          Camera: {permissionStatus?.camera ?? 'checking...'}
        </Text>
        <Pressable style={styles.button} onPress={handleRequestPermissions}>
          <Text style={styles.buttonText}>Request Permissions</Text>
        </Pressable>
        <Pressable
          style={[styles.button, styles.cameraButton]}
          onPress={handleOpenCamera}
        >
          <Text style={styles.buttonText}>Open Camera</Text>
        </Pressable>
      </View>

      {capturedPhoto ? (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Last Capture</Text>
          <Image
            source={{ uri: capturedPhoto.uri }}
            style={styles.capturedPhoto}
            resizeMode="cover"
          />
          <Text style={styles.meta}>
            {capturedPhoto.width}x{capturedPhoto.height}
            {capturedPhoto.filename ? ` · ${capturedPhoto.filename}` : ''}
          </Text>
        </View>
      ) : null}

      {error ? <Text style={styles.error}>{error}</Text> : null}

      {!canBrowsePhotos ? (
        <Text style={styles.hint}>
          Grant photo library access to browse albums and photos.
        </Text>
      ) : (
        <>
          <View style={styles.section}>
            <View style={styles.sectionHeader}>
              <Text style={styles.sectionTitle}>Albums ({albums.length})</Text>
              <Pressable onPress={handleShowAllPhotos}>
                <Text style={styles.link}>All Photos</Text>
              </Pressable>
            </View>
            <FlatList
              horizontal
              data={albums}
              keyExtractor={(item) => item.id}
              showsHorizontalScrollIndicator={false}
              renderItem={({ item }) => {
                const selected = selectedAlbum?.id === item.id;
                return (
                  <Pressable
                    style={[
                      styles.albumChip,
                      selected && styles.albumChipSelected,
                    ]}
                    onPress={() => handleSelectAlbum(item)}
                  >
                    <Text
                      style={[
                        styles.albumChipText,
                        selected && styles.albumChipTextSelected,
                      ]}
                    >
                      {item.title} ({item.assetCount})
                    </Text>
                  </Pressable>
                );
              }}
            />
          </View>

          <View style={styles.assetsSection}>
            <Text style={styles.sectionTitle}>
              {selectedAlbum ? selectedAlbum.title : 'All Photos'} ·{' '}
              {assets.length}
              {hasNextPage ? '+' : ''}
            </Text>
            <FlatList
              data={assets}
              keyExtractor={(item) => item.id}
              numColumns={3}
              onEndReached={handleLoadMore}
              onEndReachedThreshold={0.4}
              ListFooterComponent={
                loading ? (
                  <ActivityIndicator style={styles.loader} />
                ) : undefined
              }
              renderItem={({ item }) => (
                <Pressable
                  style={[
                    styles.assetCard,
                    { width: tileSize, height: tileSize },
                  ]}
                  onPress={() => setPreviewAsset(item)}
                >
                  <Image
                    source={{ uri: getAssetImageUri(item) }}
                    style={styles.assetImage}
                    resizeMode="cover"
                  />
                </Pressable>
              )}
            />
          </View>
        </>
      )}

      <Modal
        visible={previewAsset != null}
        transparent
        animationType="fade"
        onRequestClose={() => setPreviewAsset(null)}
      >
        <View style={styles.previewOverlay}>
          <SafeAreaView style={styles.previewContainer}>
            <View style={styles.previewHeader}>
              <Text style={styles.previewTitle} numberOfLines={1}>
                {previewAsset?.filename ?? previewAsset?.id ?? 'Photo'}
              </Text>
              <Pressable
                style={styles.previewCloseButton}
                onPress={() => setPreviewAsset(null)}
              >
                <Text style={styles.previewCloseText}>Close</Text>
              </Pressable>
            </View>

            {previewAsset ? (
              <Image
                source={{ uri: getAssetImageUri(previewAsset, true) }}
                style={styles.previewImage}
                resizeMode="contain"
              />
            ) : null}

            {previewAsset ? (
              <Text style={styles.previewMeta}>
                {previewAsset.width}x{previewAsset.height}
              </Text>
            ) : null}
          </SafeAreaView>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0f172a',
    paddingHorizontal: 16,
  },
  title: {
    color: '#f8fafc',
    fontSize: 24,
    fontWeight: '700',
    marginTop: 8,
    marginBottom: 16,
  },
  section: {
    marginBottom: 16,
  },
  sectionHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  sectionTitle: {
    color: '#e2e8f0',
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 8,
  },
  meta: {
    color: '#cbd5e1',
    fontSize: 14,
    marginBottom: 4,
  },
  button: {
    alignSelf: 'flex-start',
    backgroundColor: '#2563eb',
    borderRadius: 8,
    marginTop: 8,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  cameraButton: {
    backgroundColor: '#0f766e',
  },
  capturedPhoto: {
    borderRadius: 12,
    height: 220,
    marginBottom: 8,
    width: '100%',
  },
  buttonText: {
    color: '#ffffff',
    fontSize: 14,
    fontWeight: '600',
  },
  hint: {
    color: '#94a3b8',
    fontSize: 14,
    marginTop: 8,
  },
  error: {
    color: '#f87171',
    fontSize: 14,
    marginBottom: 12,
  },
  link: {
    color: '#60a5fa',
    fontSize: 14,
    fontWeight: '600',
  },
  albumChip: {
    backgroundColor: '#1e293b',
    borderColor: '#334155',
    borderRadius: 999,
    borderWidth: 1,
    marginRight: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  albumChipSelected: {
    backgroundColor: '#1d4ed8',
    borderColor: '#1d4ed8',
  },
  albumChipText: {
    color: '#cbd5e1',
    fontSize: 13,
  },
  albumChipTextSelected: {
    color: '#ffffff',
  },
  assetsSection: {
    flex: 1,
  },
  assetCard: {
    backgroundColor: '#1e293b',
    borderRadius: 8,
    margin: TILE_GAP / 2,
    overflow: 'hidden',
  },
  assetImage: {
    height: '100%',
    width: '100%',
  },
  loader: {
    marginVertical: 16,
  },
  previewOverlay: {
    backgroundColor: 'rgba(15, 23, 42, 0.96)',
    flex: 1,
  },
  previewContainer: {
    flex: 1,
    paddingHorizontal: 16,
  },
  previewHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 12,
    marginTop: 8,
  },
  previewTitle: {
    color: '#f8fafc',
    flex: 1,
    fontSize: 16,
    fontWeight: '600',
    marginRight: 12,
  },
  previewCloseButton: {
    backgroundColor: '#334155',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  previewCloseText: {
    color: '#f8fafc',
    fontSize: 14,
    fontWeight: '600',
  },
  previewImage: {
    flex: 1,
    width: '100%',
  },
  previewMeta: {
    color: '#94a3b8',
    fontSize: 14,
    marginBottom: 16,
    marginTop: 12,
    textAlign: 'center',
  },
});
