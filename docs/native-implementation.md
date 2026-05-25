# react-native-media-kit 原生实现说明

本文档详细讲解 `react-native-media-kit` 的原生层实现，涵盖 JavaScript 层、Android（Kotlin）与 iOS（Swift/Objective-C++）两端，并对**每个源码文件中的关键代码**逐段说明其作用。

---

## 总体架构

原生层采用 **分层架构**，职责划分如下：

```
JavaScript (TurboModule Spec)
        │
        ▼
┌───────────────────────────────────────────────┐
│  Bridge 层                                     │
│  Android: MediaKitModule                      │
│  iOS:     MediaKit.mm → MediaKitBridge        │
└───────────────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────────────┐
│  Domain 层（业务逻辑）                          │
│  PermissionManager / AlbumService / CameraService│
└───────────────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────────────┐
│  Data 层（系统 API 访问）                       │
│  Android: MediaStoreRepository                │
│  iOS:     PhotoLibraryRepository              │
└───────────────────────────────────────────────┘
        │
        ▼
   系统相册 / 相机 API
```

**设计原则：**

| 层级 | 职责 | 不应做的事 |
|------|------|-----------|
| **Bridge 层** | RN 桥接：Promise 回调、类型转换、错误码映射 | 不包含业务逻辑 |
| **Domain 层** | 权限、相册、相机等业务能力；DTO → JS 字典 | 不直接调用系统 API |
| **Data 层** | 对接 `MediaStore` / `Photos` 框架 | 不关心 RN 类型 |

TypeScript 侧通过 Codegen 生成 `NativeMediaKitSpec`（Android）和 `NativeMediaKitSpecJSI`（iOS），保证两端 API 签名一致。Spec 定义在 `src/NativeMediaKit.ts`。

---

## JavaScript 层

### `src/NativeMediaKit.ts` — TurboModule Spec 定义

这是 Codegen 的输入文件，定义了 JS 与原生层之间的**契约**（类型 + 方法签名）。

```typescript
export interface Spec extends TurboModule {
  requestPermissions(): Promise<PermissionStatus>;
  getPermissionStatus(): Promise<PermissionStatus>;
  getAlbums(): Promise<ReadonlyArray<Album>>;
  getAssets(
    albumId: string | null,
    mediaType: string,
    page: number,
    pageSize: number
  ): Promise<AssetPage>;
  openCamera(): Promise<CameraResult>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('MediaKit');
```

**逐段说明：**

- `Spec extends TurboModule`：声明这是一个 Turbo Module，Codegen 会根据此接口生成 Android 的 `NativeMediaKitSpec` 抽象类和 iOS 的 `NativeMediaKitSpec` 协议。
- 所有方法返回 `Promise`：原生层通过 `Promise.resolve/reject` 异步回调 JS。
- `getAssets` 的参数设计：`albumId` 可为 `null`（查全部）、`mediaType` 为字符串（`'photo' | 'video' | 'all'`）、`page`/`pageSize` 为数字分页。
- `TurboModuleRegistry.getEnforcing<Spec>('MediaKit')`：按模块名 `"MediaKit"` 获取原生模块实例；若未注册则抛错，便于开发期快速发现问题。

### `src/index.tsx` — 对外公开 API

对 TurboModule 做薄封装，提供更友好的 JS API。

```typescript
const DEFAULT_PAGE_SIZE = 50;

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
```

**逐段说明：**

- `DEFAULT_PAGE_SIZE = 50`：JS 层默认每页 50 条，原生层本身也支持 1–200 的范围限制。
- `[...albums]` / `[...page.assets]`：将原生返回的 `ReadonlyArray` 拷贝为可变数组，避免调用方意外修改原生侧数据。
- `options.albumId ?? null`：未传 albumId 时传 `null` 给原生，表示查询全部资源。
- `options.mediaType ?? 'photo'`：默认只查照片，与原生 Repository 的默认行为一致。

---

## 数据模型

两端使用结构一致的 DTO，字段与 TypeScript 类型对齐。

### PermissionStatus

```typescript
{
  photos: 'granted' | 'limited' | 'denied' | 'notDetermined',
  camera: 'granted' | 'denied' | 'notDetermined'
}
```

- `photos` 的 `limited` 仅 iOS 支持（用户选择了部分照片访问）。
- Android 只有 `granted` / `denied`，无 `limited` / `notDetermined`。

### Album / Asset / AssetPage / CameraResult

详见 `src/NativeMediaKit.ts` 中的 interface 定义。原生 DTO（`MediaModels.kt` / `MediaModels.swift`）字段一一对应。

---

## Android 实现

### 目录结构

```
android/src/main/java/com/mediakit/
├── MediaKitModule.kt          # Turbo Module 入口
├── MediaKitPackage.kt         # RN 包注册
├── domain/
│   ├── PermissionManager.kt   # 权限管理
│   ├── AlbumService.kt        # 相册业务
│   └── CameraService.kt       # 相机业务
├── data/
│   └── MediaStoreRepository.kt # MediaStore 数据访问
└── model/
    └── MediaModels.kt         # DTO 定义
```

---

### `MediaModels.kt` — 数据传输对象

```kotlin
data class PermissionStatusDto(val photos: String, val camera: String)
data class AlbumDto(val id: String, val title: String, val assetCount: Int,
                    val coverAssetId: String?, val type: String)
data class AssetDto(val id: String, val uri: String, val thumbnailUri: String?,
                    val filename: String?, val width: Int, val height: Int,
                    val mediaType: String, val creationTime: Double, val albumId: String?)
data class AssetPageDto(val assets: List<AssetDto>, val hasNextPage: Boolean, val totalCount: Int?)
```

**说明：**

- 使用 Kotlin `data class`，自动生成 `copy()`、`equals()` 等，便于在 Repository 中更新 `assetCount`。
- 所有字符串枚举（如 `mediaType`、`type`）用 `String` 而非 enum，与 JS 侧 JSON 序列化直接对应。
- `creationTime` 用 `Double` 存储毫秒时间戳，对应 JS 的 `number` 类型。

---

### `MediaKitPackage.kt` — 模块注册

```kotlin
class MediaKitPackage : BaseReactPackage() {
  override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? {
    return if (name == MediaKitModule.NAME) {
      MediaKitModule(reactContext)
    } else {
      null
    }
  }

  override fun getReactModuleInfoProvider() = ReactModuleInfoProvider {
    mapOf(
      MediaKitModule.NAME to ReactModuleInfo(
        name = MediaKitModule.NAME,
        className = MediaKitModule.NAME,
        canOverrideExistingModule = false,
        needsEagerInit = false,
        isCxxModule = false,
        isTurboModule = true   // 标记为 Turbo Module
      )
    )
  }
}
```

**逐段说明：**

- `getModule`：RN 按名称 `"MediaKit"` 请求模块时，创建并返回 `MediaKitModule` 实例。
- `isTurboModule = true`：告诉 RN 新架构使用 JSI 直接调用，而非旧的 Bridge 异步消息队列。
- `needsEagerInit = false`：模块懒加载，App 启动时不预先初始化。

---

### `MediaKitModule.kt` — Bridge 入口

```kotlin
class MediaKitModule(reactContext: ReactApplicationContext) :
  NativeMediaKitSpec(reactContext),    // Codegen 生成的基类
  ActivityEventListener {              // 监听 Activity 回调（相机拍照结果）

  private val permissionManager = PermissionManager(reactContext)
  private val albumService = AlbumService(reactContext)
  private val cameraService = CameraService(reactContext)

  init {
    reactContext.addActivityEventListener(this)  // 注册 Activity 事件监听
  }
```

**类声明说明：**

- `NativeMediaKitSpec`：Codegen 根据 TS Spec 自动生成，包含 `requestPermissions`、`getAlbums` 等抽象方法。
- `ActivityEventListener`：Android 相机通过 `startActivityForResult` 启动，结果通过 `onActivityResult` 回调，Module 需实现此接口并转发给 `CameraService`。

#### `requestPermissions` / `getPermissionStatus`

```kotlin
override fun requestPermissions(promise: Promise) {
  permissionManager.requestPermissions(promise)  // 直接委托，权限逻辑在 Domain 层
}

override fun getPermissionStatus(promise: Promise) {
  try {
    promise.resolve(
      PermissionManager.toWritableMap(permissionManager.getPermissionStatus()),
    )
  } catch (error: Exception) {
    promise.reject(ERROR_UNKNOWN, error.message, error)
  }
}
```

- `requestPermissions`：完全委托给 `PermissionManager`，因为权限请求涉及 Activity 交互，逻辑较复杂。
- `getPermissionStatus`：同步查询当前状态，将 DTO 转为 `WritableMap` 后 resolve。
- `toWritableMap` 是 `PermissionManager` 的 companion 静态方法，供 Module 和其他地方复用。

#### `getAlbums` / `getAssets`

```kotlin
override fun getAlbums(promise: Promise) {
  try {
    promise.resolve(albumService.getAlbums())
  } catch (error: SecurityException) {
    promise.reject(ERROR_PERMISSION_DENIED, "Photo library permission denied", error)
  } catch (error: Exception) {
    promise.reject(ERROR_UNKNOWN, error.message, error)
  }
}
```

- `SecurityException`：MediaStore 查询时若缺少存储权限会抛出，Module 将其映射为 `PERMISSION_DENIED` 错误码。
- 其他异常统一映射为 `UNKNOWN`。
- `getAssets` 的错误处理逻辑相同，参数直接透传给 `AlbumService`。

#### `openCamera` / `onActivityResult`

```kotlin
override fun openCamera(promise: Promise) {
  cameraService.openCamera(promise)  // promise 会被 CameraService 暂存，等 Activity 回调
}

override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
  cameraService.handleActivityResult(requestCode, resultCode)
}
```

- `openCamera` 启动系统相机后立即返回（异步），`Promise` 由 `CameraService` 持有。
- `onActivityResult` 在用户拍照或取消后被 RN 框架调用，Module 只做转发，具体逻辑在 `CameraService.handleActivityResult`。
- `onNewIntent` 空实现：相机流程不需要处理新 Intent。

---

### `PermissionManager.kt` — 权限管理

#### 查询权限状态

```kotlin
fun getPermissionStatus(): PermissionStatusDto {
  return PermissionStatusDto(
    photos = mapPhotosPermission(getPhotosPermissionState()),
    camera = mapCameraPermission(getCameraPermissionState()),
  )
}
```

- 分别查询相册和相机权限，映射为 JS 可识别的字符串。

#### 版本适配的权限列表

```kotlin
private fun getRequiredPermissions(): List<String> {
  val permissions = mutableListOf<String>()
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    permissions.add(Manifest.permission.READ_MEDIA_IMAGES)  // Android 13+
  } else {
    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)  // Android 12 及以下
  }
  permissions.add(Manifest.permission.CAMERA)
  return permissions
}
```

- Android 13（API 33）起，`READ_EXTERNAL_STORAGE` 被细分为 `READ_MEDIA_IMAGES`、`READ_MEDIA_VIDEO` 等。
- 本库目前只请求 `READ_MEDIA_IMAGES`（读图片相册），视频读取在 `mediaType: 'video'` 时可能需要额外权限（后续扩展点）。

#### 请求权限流程

```kotlin
fun requestPermissions(promise: Promise) {
  val activity = reactContext.currentActivity
  if (activity == null) {
    promise.reject(ERROR_NO_ACTIVITY, "No activity available to request permissions")
    return
  }

  val missingPermissions = getRequiredPermissions().filter { permission ->
    ContextCompat.checkSelfPermission(reactContext, permission) != PackageManager.PERMISSION_GRANTED
  }

  if (missingPermissions.isEmpty()) {
    promise.resolve(toWritableMap(getPermissionStatus()))  // 已全部授权，直接返回
    return
  }

  val permissionAwareActivity = activity as? PermissionAwareActivity
  // ...
  val listener = PermissionListener { _, _, _ ->
    promise.resolve(toWritableMap(getPermissionStatus()))  // 无论用户允许或拒绝，都返回最新状态
    true  // 返回 true 表示已处理，不再传递给其他 listener
  }

  permissionAwareActivity.requestPermissions(missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE, listener)
}
```

**关键点：**

- 必须先拿到 `currentActivity`，RN 的权限 API 需要 Activity 上下文。
- `PermissionAwareActivity` 是 RN 提供的接口，封装了 Android 运行时权限请求。
- 权限回调中**不 reject**，而是 resolve 最新状态——这样 JS 层可以根据 `photos: 'denied'` 自行决定 UI 提示，与 iOS 行为一致。
- `PERMISSION_REQUEST_CODE = 1001`：用于区分不同权限请求（相机权限用另一个 code `2002`）。

#### 权限状态映射

```kotlin
private fun getPhotosPermissionState(): PhotosPermissionState {
  val permission = if (Build.VERSION.SDK_INT >= TIRAMISU) READ_MEDIA_IMAGES else READ_EXTERNAL_STORAGE
  return when (ContextCompat.checkSelfPermission(reactContext, permission)) {
    PERMISSION_GRANTED -> GRANTED
    else -> DENIED  // Android 无 limited/notDetermined，未授权统一为 denied
  }
}
```

#### `toWritableMap` — DTO 转 RN 类型

```kotlin
fun toWritableMap(status: PermissionStatusDto): WritableMap {
  return Arguments.createMap().apply {
    putString("photos", status.photos)
    putString("camera", status.camera)
  }
}
```

- `Arguments.createMap()` 是 RN 提供的工厂方法，创建可被 JS 消费的 Map。
- 字段名必须与 TS Spec 中的 `PermissionStatus` 一致。

---

### `AlbumService.kt` — 相册业务层

```kotlin
class AlbumService(reactContext: ReactApplicationContext) {
  private val repository = MediaStoreRepository(reactContext.contentResolver)

  fun getAlbums(): WritableArray {
    return toWritableArray(repository.getAlbums().map(::toAlbumMap))
  }
```

- 注入 `ContentResolver`（而非整个 Context），Data 层只依赖 Android 内容提供者 API。
- `getAlbums()` 返回 `WritableArray`（相册列表），每个元素是 `WritableMap`。

#### DTO → WritableMap 转换

```kotlin
private fun toAlbumMap(album: AlbumDto): WritableMap {
  return Arguments.createMap().apply {
    putString("id", album.id)
    putString("title", album.title)
    putInt("assetCount", album.assetCount)
    if (album.coverAssetId != null) {
      putString("coverAssetId", album.coverAssetId)  // 可选字段：仅非 null 时写入
    }
    putString("type", album.type)
  }
}
```

- 可选字段（`coverAssetId`、`thumbnailUri`、`filename`、`albumId`）用 `if (x != null)` 判断，避免 JS 收到 `"coverAssetId": null`（RN 中 null 和 undefined 行为不同）。
- `creationTime` 用 `putDouble`，因为 JS 数字均为 double。

#### 分页资源转换

```kotlin
private fun toAssetPageMap(page: AssetPageDto): WritableMap {
  return Arguments.createMap().apply {
    putArray("assets", toWritableArray(page.assets.map(::toAssetMap)))
    putBoolean("hasNextPage", page.hasNextPage)
    if (page.totalCount != null) {
      putInt("totalCount", page.totalCount)
    }
  }
}
```

- `hasNextPage` 帮助 JS 实现无限滚动：`true` 表示还有下一页。
- `totalCount` 可选，Android 通过 Cursor.count 获取，可能为 null（查询失败时）。

---

### `MediaStoreRepository.kt` — MediaStore 数据访问

#### `getAlbums()` — 按 BUCKET 聚合相册

```kotlin
fun getAlbums(): List<AlbumDto> {
  val albums = linkedMapOf<String, AlbumDto>()  // 保持插入顺序，key = BUCKET_ID
  val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
  val projection = arrayOf(BUCKET_ID, BUCKET_DISPLAY_NAME, _ID, DATE_ADDED)
  val sortOrder = "$BUCKET_ID ASC, $DATE_ADDED DESC"  // 同 BUCKET 内按时间降序

  query(uri, projection, null, null, sortOrder).use { cursor ->
    while (cursor.moveToNext()) {
      val bucketId = cursor.getString(bucketIdColumn) ?: continue
      val existing = albums[bucketId]
      if (existing != null) {
        albums[bucketId] = existing.copy(assetCount = existing.assetCount + 1)
        continue  // 该 BUCKET 已记录，只需累加计数
      }
      // 首次遇到该 BUCKET：当前行就是最新图片（因 DATE_ADDED DESC 排序）
      albums[bucketId] = AlbumDto(
        id = bucketId,
        title = cursor.getString(bucketNameColumn)?.ifBlank { "Untitled" } ?: "Untitled",
        assetCount = 1,
        coverAssetId = cursor.getLong(idColumn).toString(),
        type = "user",
      )
    }
  }
  return albums.values.sortedBy { it.title.lowercase() }
}
```

**算法说明：**

1. 查询外部存储所有图片，按 `BUCKET_ID`（相册桶 ID）+ `DATE_ADDED DESC` 排序。
2. 遍历时，同一 `BUCKET_ID` 第一次出现即为最新照片，作为封面 `coverAssetId`。
3. 后续相同 BUCKET 的行只增加 `assetCount`。
4. Android MediaStore 没有 iOS 的 "smart album" 概念，统一标记 `type = "user"`。
5. 最终按标题字母序排序返回。

#### `getAssets()` — 分页查询媒体资源

```kotlin
fun getAssets(albumId: String?, mediaType: String, page: Int, pageSize: Int): AssetPageDto {
  val safePage = page.coerceAtLeast(0)
  val safePageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)  // 限制 1–200
  val uri = resolveMediaUri(mediaType)       // 根据类型选择不同的 MediaStore URI
  val bucketColumn = resolveBucketColumn(mediaType)
```

**URI 选择逻辑：**

```kotlin
private fun resolveMediaUri(mediaType: String): Uri = when (mediaType) {
  "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
  "all"   -> MediaStore.Files.getContentUri("external")  // 图片+视频联合表
  else    -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI  // 默认 photo
}
```

**筛选条件构建：**

```kotlin
if (!albumId.isNullOrBlank()) {
  selectionBuilder.append("$bucketColumn = ?")
  selectionArgs.add(albumId)
}
if (mediaType == "all") {
  selectionBuilder.append("(${MEDIA_TYPE} = ? OR ${MEDIA_TYPE} = ?)")
  selectionArgs.add(MEDIA_TYPE_IMAGE.toString())
  selectionArgs.add(MEDIA_TYPE_VIDEO.toString())
}
```

- `albumId` 对应 MediaStore 的 `BUCKET_ID`，限定在某个相册内查询。
- `mediaType: 'all'` 时额外过滤 MIME 类型为图片或视频。

**Cursor 行 → AssetDto：**

```kotlin
val id = cursor.getLong(idColumn)
val itemUri = ContentUris.withAppendedId(uri, id)  // 构建 content:// URI
assets.add(AssetDto(
  id = id.toString(),
  uri = itemUri.toString(),
  thumbnailUri = itemUri.toString(),  // 暂与主 URI 相同，后续可接入 loadThumbnail
  filename = cursor.getString(nameColumn),
  width = cursor.getInt(widthColumn).coerceAtLeast(0),
  height = cursor.getInt(heightColumn).coerceAtLeast(0),
  mediaType = mapMediaType(cursor.getString(mimeColumn)),  // video/* → "video"，其余 → "photo"
  creationTime = cursor.getLong(dateColumn) * 1000.0,      // DATE_ADDED 是秒，转毫秒
  albumId = cursor.getString(bucketIndex),
))
```

**分页实现（API 版本差异）：**

```kotlin
// Android R (API 30+)：使用 Bundle 参数
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
  val queryArgs = Bundle().apply {
    putInt(QUERY_ARG_LIMIT, pageSize)
    putInt(QUERY_ARG_OFFSET, page * pageSize)
    putString(QUERY_ARG_SQL_SELECTION, selection)
    // ...
  }
  contentResolver.query(uri, projection, queryArgs, null)
} else {
  // 低版本：SQL LIMIT/OFFSET 拼在 sortOrder 末尾
  contentResolver.query(uri, projection, selection, selectionArgs,
    "$sortOrder LIMIT $pageSize OFFSET ${page * pageSize}")
}
```

- API 30 以下直接在 `sortOrder` 末尾拼接 `LIMIT/OFFSET`（部分 OEM 可能不支持，但主流设备可用）。
- API 30+ 使用官方 `QUERY_ARG_LIMIT/OFFSET`，更安全规范。

**hasNextPage 计算：**

```kotlin
val startIndex = safePage * safePageSize
val hasNextPage = totalCount?.let { startIndex + assets.size < it } ?: assets.size == safePageSize
```

- 有 `totalCount` 时精确判断；无 totalCount 时，若返回条数等于 pageSize 则推测还有下一页。

---

### `CameraService.kt` — 相机业务

#### 状态变量

```kotlin
private var pendingPromise: Promise? = null  // 暂存 JS 侧的 Promise
private var outputUri: Uri? = null           // FileProvider 生成的 content:// URI
private var outputFile: File? = null         // 实际写入的临时 JPEG 文件
```

- 相机是异步 Activity 流程：启动相机时保存 Promise，拍照完成后在 `handleActivityResult` 中 resolve/reject。

#### `openCamera()` 入口检查

```kotlin
fun openCamera(promise: Promise) {
  val activity = reactContext.currentActivity ?: run {
    promise.reject(ERROR_NO_ACTIVITY, "..."); return
  }
  if (!activity.packageManager.hasSystemFeature(FEATURE_CAMERA_ANY)) {
    promise.reject(ERROR_NO_CAMERA, "..."); return
  }
  if (checkSelfPermission(CAMERA) != GRANTED) {
    requestCameraPermission(activity, promise); return  // 先请求权限再启动
  }
  launchCamera(activity, promise)
}
```

- 三层检查：Activity 存在 → 设备有相机 → 相机权限已授予。

#### `launchCamera()` — 启动系统相机

```kotlin
private fun launchCamera(activity: Activity, promise: Promise) {
  if (pendingPromise != null) {
    promise.reject(ERROR_UNKNOWN, "Camera is already open"); return  // 防止重复打开
  }

  val cacheDir = File(reactContext.cacheDir, "camera").apply { mkdirs() }
  val filename = "photo_${UUID.randomUUID()}.jpg"
  val file = File(cacheDir, filename)
  val authority = "${reactContext.packageName}.mediakit.fileprovider"
  val uri = FileProvider.getUriForFile(reactContext, authority, file)

  val intent = Intent(ACTION_IMAGE_CAPTURE).apply {
    putExtra(EXTRA_OUTPUT, uri)  // 告诉相机 App 将照片写入此 URI
    addFlags(FLAG_GRANT_WRITE_URI_PERMISSION or FLAG_GRANT_READ_URI_PERMISSION)
  }

  pendingPromise = promise
  outputFile = file
  outputUri = uri
  activity.startActivityForResult(Intent.createChooser(intent, null), CAMERA_REQUEST_CODE)
}
```

**关键点：**

- 使用 `FileProvider` 而非 `file://` URI：Android 7.0+ 禁止向其他 App 暴露 `file://` 路径。
- `authority` 必须与 `AndroidManifest.xml` 中声明的一致：`${applicationId}.mediakit.fileprovider`。
- `EXTRA_OUTPUT`：指定输出文件，相机 App 直接写入，而非通过 Intent data 返回缩略图。
- `createChooser`：若设备有多个相机 App，弹出选择器。

#### `handleActivityResult()` — 处理拍照结果

```kotlin
fun handleActivityResult(requestCode: Int, resultCode: Int): Boolean {
  if (requestCode != CAMERA_REQUEST_CODE) return false

  val promise = pendingPromise ?: return true
  pendingPromise = null

  if (resultCode != RESULT_OK) {
    cleanupOutputFile()  // 用户取消，删除临时文件
    promise.reject(ERROR_CANCELLED, "Camera cancelled")
    return true
  }

  val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeFile(file.absolutePath, options)  // 只读尺寸，不加载完整 Bitmap

  promise.resolve(toResultMap(
    uri = uri.toString(),
    width = options.outWidth.coerceAtLeast(0),
    height = options.outHeight.coerceAtLeast(0),
    filename = file.name,
  ))
}
```

- `inJustDecodeBounds = true`：只解析图片头信息获取宽高，避免 OOM。
- 取消时 reject `CANCELLED` 并清理临时文件。
- 返回的 `uri` 是 `content://` 格式，JS 侧可用 `<Image source={{ uri }} />` 直接显示。

#### 相机权限单独请求

```kotlin
private fun requestCameraPermission(activity: Activity, promise: Promise) {
  val listener = PermissionListener { _, _, grantResults ->
    val granted = grantResults.isNotEmpty() && grantResults[0] == PERMISSION_GRANTED
    if (granted) launchCamera(currentActivity, promise)
    else promise.reject(ERROR_PERMISSION_DENIED, "Camera permission denied")
    true
  }
  permissionAwareActivity.requestPermissions(arrayOf(CAMERA), CAMERA_PERMISSION_REQUEST_CODE, listener)
}
```

- 与 `PermissionManager` 的全量权限请求分开，使用不同的 `requestCode`（2002 vs 1001）。

---

### `AndroidManifest.xml` — 权限与 FileProvider 声明

```xml
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.CAMERA" />

<uses-feature android:name="android.hardware.camera" android:required="false" />

<application>
  <provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.mediakit.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
               android:resource="@xml/mediakit_file_paths" />
  </provider>
</application>
```

**说明：**

- `READ_EXTERNAL_STORAGE` 加 `maxSdkVersion="32"`：只在 Android 12 及以下生效，13+ 用 `READ_MEDIA_IMAGES`。
- `camera required="false"`：无相机设备（如平板）也可安装，运行时再检查。
- `grantUriPermissions="true"`：允许临时授权相机 App 读写 FileProvider URI。
- 路径配置在 `res/xml/mediakit_file_paths.xml`：

```xml
<paths>
  <cache-path name="camera" path="camera/" />
</paths>
```

- `cache-path` 映射到 `context.cacheDir/camera/`，与 `CameraService` 中创建临时文件的目录一致。

---

## iOS 实现

### 目录结构

```
ios/
├── MediaKit.mm                  # Turbo Module ObjC++ 入口
├── MediaKit.h
├── Bridge/
│   ├── MediaKitBridge.swift   # Swift 桥接层
│   └── MediaKitBridge.h       # ObjC 头文件（供 .mm 引用）
├── Domain/
│   ├── PermissionManager.swift
│   ├── AlbumService.swift
│   └── CameraService.swift
├── Data/
│   └── PhotoLibraryRepository.swift
└── Models/
    └── MediaModels.swift
```

---

### `MediaModels.swift` — 数据传输对象

```swift
struct PermissionStatusDTO { let photos: String; let camera: String }
struct AlbumDTO { let id, title, type: String; let assetCount: Int; let coverAssetId: String? }
struct AssetDTO { let id, uri, mediaType: String; let thumbnailUri, filename, albumId: String?;
                  let width, height: Int; let creationTime: Double }
struct AssetPageDTO { let assets: [AssetDTO]; let hasNextPage: Bool; let totalCount: Int? }

enum MediaKitErrorCode {
  static let permissionDenied = "PERMISSION_DENIED"
  static let cancelled = "CANCELLED"
  static let noCamera = "NO_CAMERA"
  static let unknown = "UNKNOWN"
}
```

- 与 Android DTO 结构一致，额外定义 `MediaKitErrorCode` 常量，保证错误码跨平台统一。
- iOS 错误通过 `NSError.userInfo["code"]` 传递字符串错误码。

---

### `MediaKit.mm` — ObjC++ Turbo Module 入口

```objc
static NSString *MediaKitErrorCodeFromError(NSError *error) {
  NSString *code = error.userInfo[@"code"];
  return code.length > 0 ? code : @"UNKNOWN";
}
```

- 从 Swift 抛出的 `NSError` 中提取 `"code"` 字段，映射为 JS 可识别的错误码字符串。

#### 方法转发模式（以 getAlbums 为例）

```objc
- (void)getAlbums:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
  [[MediaKitBridge shared] getAlbumsWithCompletion:^(NSArray *result, NSError *error) {
    if (error != nil) {
      reject(MediaKitErrorCodeFromError(error), error.localizedDescription, error);
      return;
    }
    resolve(result);
  }];
}
```

**说明：**

- `.mm` 文件是 ObjC++ 桥接层：实现 Codegen 生成的 `NativeMediaKitSpec` 协议。
- 所有业务逻辑在 Swift 的 `MediaKitBridge` 中，`.mm` 只负责 Promise 回调转换。
- 每个方法都是相同的模式：调用 Bridge → 检查 error → resolve/reject。

#### Turbo Module 注册

```objc
- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params {
  return std::make_shared<facebook::react::NativeMediaKitSpecJSI>(params);
}

+ (NSString *)moduleName { return @"MediaKit"; }
```

- `NativeMediaKitSpecJSI`：Codegen 生成的 C++ JSI 绑定，使 JS 可以直接调用原生方法。
- `moduleName` 必须与 TS 中 `TurboModuleRegistry.getEnforcing('MediaKit')` 的名称一致。

---

### `MediaKitBridge.swift` — Swift 桥接层

```swift
@objc(MediaKitBridge)
final class MediaKitBridge: NSObject {
  @objc static let shared = MediaKitBridge()  // 单例

  private let permissionManager = PermissionManager()
  private let albumService = AlbumService()
  private let cameraService = CameraService()
```

- `@objc` 标记使 Swift 类可被 ObjC（`.mm`）调用。
- 单例模式：Module 生命周期内共享同一组 Domain 服务。

#### 线程调度策略

```swift
// 相册读取：后台线程执行，主线程回调
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
        completion(nil, Self.makeError(code: .permissionDenied, message: error.localizedDescription))
      }
    }
  }
}

// 相机：主线程执行（UI 操作必须在主线程）
func openCamera(completion: @escaping (NSDictionary?, NSError?) -> Void) {
  DispatchQueue.main.async {
    self.cameraService.openCamera { result in ... }
  }
}
```

- 相册查询可能耗时（尤其 iOS 缩略图同步生成），放后台线程避免阻塞 UI。
- `UIImagePickerController.present` 必须在主线程。
- RN 的 Promise resolve/reject 也应在主线程调用。

#### 错误构造

```swift
private static func makeError(code: String, message: String) -> NSError {
  NSError(domain: "MediaKit", code: 1, userInfo: [
    NSLocalizedDescriptionKey: message,
    "code": code,  // 自定义字段，供 MediaKit.mm 提取
  ])
}
```

---

### `PermissionManager.swift` — 权限管理

#### 查询权限状态

```swift
func getPermissionStatus() -> PermissionStatusDTO {
  PermissionStatusDTO(
    photos: mapPhotoStatus(PHPhotoLibrary.authorizationStatus(for: .readWrite)),
    camera: mapCameraStatus(AVCaptureDevice.authorizationStatus(for: .video))
  )
}
```

- 相册用 `Photos` 框架的 `PHPhotoLibrary`，相机用 `AVFoundation` 的 `AVCaptureDevice`。
- `.readWrite`：请求读写权限（本库需要读取相册内容）。

#### iOS 相册权限映射（比 Android 更细）

```swift
private func mapPhotoStatus(_ status: PHAuthorizationStatus) -> String {
  switch status {
  case .authorized:  return "granted"
  case .limited:     return "limited"    // iOS 14+ 用户选择了部分照片
  case .denied, .restricted: return "denied"
  case .notDetermined: return "notDetermined"
  @unknown default:  return "denied"
  }
}
```

#### 请求权限（串行）

```swift
func requestPermissions(completion: @escaping (Result<PermissionStatusDTO, Error>) -> Void) {
  PHPhotoLibrary.requestAuthorization(for: .readWrite) { _ in
    AVCaptureDevice.requestAccess(for: .video) { _ in
      DispatchQueue.main.async {
        completion(.success(self.getPermissionStatus()))  // 两个都请求完后返回最新状态
      }
    }
  }
}
```

- 先请求相册，再请求相机，串行执行（系统弹窗不能同时出现）。
- 无论用户如何选择，都返回当前最新状态（与 Android 行为一致）。

#### 读取前权限校验

```swift
func ensurePhotoAccess() throws {
  let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
  switch status {
  case .authorized, .limited: return  // limited 也允许读取已授权的照片
  default: throw NSError(..., "Photo library permission denied")
  }
}
```

- `getAlbums` / `getAssets` 调用前先 `ensurePhotoAccess()`，无权限则 throw，Bridge 层捕获后 reject `PERMISSION_DENIED`。

#### 相机权限（供 CameraService 使用）

```swift
func requestCameraAccess(completion: @escaping (Bool) -> Void) {
  switch AVCaptureDevice.authorizationStatus(for: .video) {
  case .authorized: completion(true)
  case .notDetermined: AVCaptureDevice.requestAccess(for: .video, completionHandler: completion)
  default: completion(false)
  }
}
```

---

### `AlbumService.swift` — 相册业务层

```swift
final class AlbumService {
  private let repository = PhotoLibraryRepository()

  func getAlbums() throws -> [[String: Any]] {
    try repository.getAlbums().map(toDictionary)
  }

  func getAssets(...) throws -> [String: Any] {
    let pageResult = try repository.getAssets(...)
    return toDictionary(pageResult)
  }
}
```

- 职责与 Android `AlbumService` 完全相同：调用 Repository → DTO 转 `[String: Any]` 字典。
- iOS 不需要 `WritableMap`，Swift 字典可以直接桥接为 ObjC `NSDictionary`。

#### 字典转换（以 Asset 为例）

```swift
private func toDictionary(_ asset: AssetDTO) -> [String: Any] {
  var dictionary: [String: Any] = [
    "id": asset.id, "uri": asset.uri,
    "width": asset.width, "height": asset.height,
    "mediaType": asset.mediaType, "creationTime": asset.creationTime,
  ]
  if let thumbnailUri = asset.thumbnailUri { dictionary["thumbnailUri"] = thumbnailUri }
  // ... 其他可选字段
  return dictionary
}
```

---

### `PhotoLibraryRepository.swift` — Photos 框架数据访问

#### `getAlbums()` — 用户相册 + 智能相册

```swift
func getAlbums() throws -> [AlbumDTO] {
  var albums: [AlbumDTO] = []

  let userAlbums = PHAssetCollection.fetchAssetCollections(with: .album, subtype: .any, options: nil)
  albums.append(contentsOf: mapCollections(userAlbums, type: "user"))

  let smartAlbums = PHAssetCollection.fetchAssetCollections(with: .smartAlbum, subtype: .any, options: nil)
  albums.append(contentsOf: mapCollections(smartAlbums, type: "smart"))

  return albums.sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
}
```

- `.album`：用户创建的相册（对应 Android 的 BUCKET）。
- `.smartAlbum`：系统智能相册（最近项目、收藏、自拍等），Android 无对应概念。
- 两者合并后按标题排序。

#### `mapCollections()` — 相册集合映射

```swift
private func mapCollections(_ collections: PHFetchResult<PHAssetCollection>, type: String) -> [AlbumDTO] {
  collections.enumerateObjects { collection, _, _ in
    let assets = PHAsset.fetchAssets(in: collection, options: nil)
    guard assets.count > 0 else { return }  // 过滤空相册

    albums.append(AlbumDTO(
      id: collection.localIdentifier,       // iOS 唯一标识
      title: collection.localizedTitle ?? "Untitled",
      assetCount: assets.count,
      coverAssetId: assets.object(at: 0).localIdentifier,  // 第一张作为封面
      type: type
    ))
  }
}
```

#### `getAssets()` — 内存分页

```swift
func getAssets(albumId: String?, mediaType: String, page: Int, pageSize: Int) throws -> AssetPageDTO {
  let safePage = max(page, 0)
  let safePageSize = min(max(pageSize, 1), 200)
  let fetchResult = try fetchAssets(albumId: albumId, mediaType: mediaType)
  let totalCount = fetchResult.count
  let startIndex = safePage * safePageSize

  guard startIndex < totalCount else {
    return AssetPageDTO(assets: [], hasNextPage: false, totalCount: totalCount)
  }

  let endIndex = min(startIndex + safePageSize, totalCount)
  for index in startIndex..<endIndex {
    assets.append(mapAsset(fetchResult.object(at: index), albumId: albumId))
  }

  return AssetPageDTO(assets: assets, hasNextPage: endIndex < totalCount, totalCount: totalCount)
}
```

- `PHFetchResult` 是惰性加载的，`.count` 获取总数，`object(at:)` 按索引取元素。
- 分页通过索引切片实现（`startIndex..<endIndex`），而非 SQL LIMIT。
- 超出范围时返回空数组 + `hasNextPage: false`。

#### `fetchAssets()` — 构建查询

```swift
private func fetchAssets(albumId: String?, mediaType: String) throws -> PHFetchResult<PHAsset> {
  let options = PHFetchOptions()
  options.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
  options.predicate = buildPredicate(for: mediaType)

  if let albumId, !albumId.isEmpty {
    let collections = PHAssetCollection.fetchAssetCollections(withLocalIdentifiers: [albumId], options: nil)
    guard collections.count > 0 else { return PHAsset.fetchAssets(with: options) }
    return PHAsset.fetchAssets(in: collections.object(at: 0), options: options)
  }
  return PHAsset.fetchAssets(with: options)
}
```

- 有 `albumId` 时先找到对应的 `PHAssetCollection`，再在其中查询资源。
- 找不到相册时 fallback 到全局查询。

#### `buildPredicate()` — 媒体类型过滤

```swift
private func buildPredicate(for mediaType: String) -> NSPredicate? {
  switch mediaType {
  case "video": return NSPredicate(format: "mediaType == %d", PHAssetMediaType.video.rawValue)
  case "all":   return NSPredicate(format: "mediaType == %d OR mediaType == %d",
                                   PHAssetMediaType.image.rawValue, PHAssetMediaType.video.rawValue)
  default:      return NSPredicate(format: "mediaType == %d", PHAssetMediaType.image.rawValue)
  }
}
```

#### `mapAsset()` — 资源映射

```swift
private func mapAsset(_ asset: PHAsset, albumId: String?) -> AssetDTO {
  AssetDTO(
    id: asset.localIdentifier,
    uri: "ph://\(asset.localIdentifier)",  // 自定义 scheme，需配合 PHImageManager 使用
    thumbnailUri: thumbnailUri(for: asset),
    filename: nil,  // PHAsset 不直接暴露文件名
    width: asset.pixelWidth,
    height: asset.pixelHeight,
    mediaType: asset.mediaType == .video ? "video" : "photo",
    creationTime: (asset.creationDate?.timeIntervalSince1970 ?? 0) * 1000,
    albumId: albumId
  )
}
```

#### `thumbnailUri()` — 缩略图生成与缓存

```swift
private func thumbnailUri(for asset: PHAsset) -> String? {
  let cacheDir = .../MediaKitThumbnails/
  let safeId = asset.localIdentifier.replacingOccurrences(of: "/", with: "_")...
  let fileURL = cacheDir.appendingPathComponent("\(safeId).jpg")

  if FileManager.default.fileExists(atPath: fileURL.path) {
    return fileURL.absoluteString  // 命中缓存，直接返回
  }

  let options = PHImageRequestOptions()
  options.isSynchronous = true       // 同步等待，简化调用方逻辑
  options.deliveryMode = .opportunistic
  options.resizeMode = .fast
  options.isNetworkAccessAllowed = true  // 允许从 iCloud 下载

  PHImageManager.default().requestImage(for: asset, targetSize: CGSize(width: 400, height: 400),
                                        contentMode: .aspectFill, options: options) { image, _ in
    thumbnail = image
  }

  // 将 UIImage 以 JPEG 0.85 质量写入缓存目录
  try data.write(to: fileURL)
  return fileURL.absoluteString  // 返回 file:// URI
}
```

**说明：**

- iOS 的 `ph://` URI 不能直接在 `<Image>` 中使用，所以同步生成 400×400 缩略图并缓存为 `file://` URI。
- `localIdentifier` 中的 `/` 和 `:` 替换为 `_`，避免文件名非法。
- 同步生成可能阻塞后台线程，大量资源时有性能风险（见后续扩展建议）。

---

### `CameraService.swift` — 相机业务

```swift
final class CameraService: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
  private var completion: ((Result<[String: Any], Error>) -> Void)?
```

- 实现 `UIImagePickerControllerDelegate` 接收拍照/取消回调。
- `completion` 闭包暂存 JS 侧的 Promise 回调（类似 Android 的 `pendingPromise`）。

#### `openCamera()` 流程

```swift
func openCamera(completion: @escaping (Result<[String: Any], Error>) -> Void) {
  guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
    completion(.failure(makeError(code: .noCamera, ...))); return
  }

  permissionManager.requestCameraAccess { [weak self] granted in
    guard granted else { completion(.failure(makeError(code: .permissionDenied, ...))); return }
    guard let presenter = Self.topViewController() else { ... }

    self?.completion = completion
    let picker = UIImagePickerController()
    picker.sourceType = .camera
    picker.mediaTypes = ["public.image"]  // 只拍照片
    picker.delegate = self
    picker.modalPresentationStyle = .fullScreen
    presenter.present(picker, animated: true)
  }
}
```

#### 拍照完成回调

```swift
func imagePickerController(_ picker: UIImagePickerController,
                           didFinishPickingMediaWithInfo info: [...]) {
  picker.dismiss(animated: true)
  guard let image = info[.originalImage] as? UIImage else { ... }

  let result = try Self.saveCapturedImage(image)
  finish(with: .success(result))
}

func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
  picker.dismiss(animated: true)
  finish(with: .failure(makeError(code: .cancelled, ...)))
}
```

#### 保存照片到缓存

```swift
private static func saveCapturedImage(_ image: UIImage) throws -> [String: Any] {
  let cacheDir = .../MediaKitCamera/
  let filename = "photo_\(UUID().uuidString).jpg"
  let fileURL = cacheDir.appendingPathComponent(filename)

  guard let data = image.jpegData(compressionQuality: 0.92) else { throw ... }
  try data.write(to: fileURL)

  return [
    "uri": fileURL.absoluteString,
    "width": Int(image.size.width * image.scale),   // 乘 scale 得到真实像素尺寸
    "height": Int(image.size.height * image.scale),
    "filename": filename,
  ]
}
```

- `image.size` 是点（point）尺寸，`scale` 是屏幕缩放因子（@2x/@3x），相乘得真实像素。

#### 查找顶层 ViewController

```swift
private static func topViewController() -> UIViewController? {
  let scenes = UIApplication.shared.connectedScenes
    .compactMap { $0 as? UIWindowScene }
    .filter { $0.activationState == .foregroundActive || .foregroundInactive }

  guard let window = scenes.compactMap({ $0.windows.first(where: \.isKeyWindow) }).first
      ?? scenes.first?.windows.first,
      var top = window.rootViewController else { return nil }

  while let presented = top.presentedViewController {
    top = presented  // 递归找到最顶层（可能有 modal 叠加）
  }
  return top
}
```

- iOS 13+ 多 Scene 架构：遍历 `connectedScenes` 找到前台 Window。
- 递归 `presentedViewController` 确保在 RN Modal 之上 present 相机。

---

## 错误码

| 错误码 | 场景 | Android 触发位置 | iOS 触发位置 |
|--------|------|-----------------|-------------|
| `PERMISSION_DENIED` | 相册或相机权限被拒绝 | `MediaKitModule` catch SecurityException；`CameraService` 权限回调 | `MediaKitBridge` catch ensurePhotoAccess；`CameraService` 权限回调 |
| `CANCELLED` | 用户取消拍照 | `CameraService.handleActivityResult` resultCode != OK | `CameraService.imagePickerControllerDidCancel` |
| `NO_CAMERA` | 设备无相机 | `CameraService.openCamera` hasSystemFeature 检查 | `CameraService.openCamera` isSourceTypeAvailable 检查 |
| `NO_ACTIVITY` | 无可用 Activity | `PermissionManager` / `CameraService` currentActivity == null | — |
| `UNKNOWN` | 其他未知错误 | 各 Module 方法的 catch (Exception) | `MediaKitBridge.makeError(code: .unknown)` |

---

## 平台差异

| 特性 | Android | iOS |
|------|---------|-----|
| 相册 API | MediaStore ContentResolver | Photos (PHAsset / PHAssetCollection) |
| 资源 URI | `content://` | `ph://`（相册资源）/ `file://`（拍照、缩略图缓存） |
| 相册类型 | 仅 `user`（按 BUCKET 聚合） | `user` + `smart`（系统智能相册） |
| 权限粒度 | `granted` / `denied` | 相册支持 `limited`；均有 `notDetermined` |
| 分页实现 | SQL LIMIT/OFFSET 或 QueryArgs | PHFetchResult 索引切片 |
| 相机实现 | Intent + FileProvider | UIImagePickerController |
| 缩略图 | 与主 URI 相同 | 同步生成 400×400 并缓存到本地 |
| 线程模型 | 主线程（MediaStore 查询较快） | 相册读后台线程，相机/UI 主线程 |

---

## 调用流程示例

### 获取相册列表

```
JS: getAlbums()
  → TurboModule: getAlbums()
    → [Android] MediaKitModule.getAlbums()
        → AlbumService.getAlbums()
            → MediaStoreRepository.getAlbums()  // ContentResolver 查询
            → toAlbumMap() × N → WritableArray
    → [iOS] MediaKit.mm.getAlbums()
        → MediaKitBridge.getAlbums()  // DispatchQueue.global
            → PermissionManager.ensurePhotoAccess()
            → AlbumService.getAlbums()
                → PhotoLibraryRepository.getAlbums()  // PHAssetCollection 查询
                → toDictionary() × N → NSArray
  ← Promise resolve → JS 收到 Album[]
```

### 分页加载照片

```
JS: getAssets({ albumId: '123', mediaType: 'photo', page: 0, pageSize: 50 })
  → index.tsx: 补全默认值 albumId=null, pageSize=50
  → TurboModule: getAssets('123', 'photo', 0, 50)
    → AlbumService.getAssets()
        → Repository.getAssets()
            → 构建 selection / predicate
            → 分页查询（Android: LIMIT/OFFSET, iOS: index slice）
            → 映射为 AssetDto / AssetDTO
        → toAssetMap / toDictionary → WritableMap / NSDictionary
  ← { assets: [...], hasNextPage: true, totalCount: 238 }
```

### 打开相机

```
JS: openCamera()
  → TurboModule: openCamera()
    → CameraService.openCamera(promise/completion)
        → 检查 Activity/ViewController 可用
        → 检查设备有相机
        → 检查/请求 CAMERA 权限
        → 创建临时文件 + FileProvider URI (Android) / UIImagePickerController (iOS)
        → 启动系统相机
        → [用户拍照]
            → Android: onActivityResult → handleActivityResult → 读 Bitmap 尺寸 → resolve
            → iOS: imagePickerController:didFinishPicking → saveCapturedImage → resolve
        → [用户取消]
            → reject(CANCELLED)
  ← { uri, width, height, filename }
```

---

## 后续扩展建议

- **Android 相册**：目前仅基于图片 BUCKET 聚合，可扩展支持视频相册和 smart album 概念。
- **Android 缩略图**：可接入 `ContentResolver.loadThumbnail`（API 29+）生成真实缩略图，而非复用主 URI。
- **iOS 缩略图**：目前同步生成，大量资源时可能影响性能，可改为异步 + LRU 缓存策略。
- **Android 视频权限**：`mediaType: 'video'` 时在 Android 13+ 可能需要额外请求 `READ_MEDIA_VIDEO`。
- **两端 `mediaType: 'all'`**：Android 使用 `MediaStore.Files`，iOS 使用 `PHAsset` 联合查询，行为已基本对齐。
