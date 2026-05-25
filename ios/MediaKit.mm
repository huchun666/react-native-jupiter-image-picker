#import "MediaKit.h"
#import "Bridge/MediaKitBridge.h"

static NSString *MediaKitErrorCodeFromError(NSError *error)
{
  NSString *code = error.userInfo[@"code"];
  return code.length > 0 ? code : @"UNKNOWN";
}

@implementation MediaKit

- (void)requestPermissions:(RCTPromiseResolveBlock)resolve
                    reject:(RCTPromiseRejectBlock)reject
{
  [[MediaKitBridge shared] requestPermissionsWithCompletion:^(NSDictionary *result, NSError *error) {
    if (error != nil) {
      reject(MediaKitErrorCodeFromError(error), error.localizedDescription, error);
      return;
    }

    resolve(result);
  }];
}

- (void)getPermissionStatus:(RCTPromiseResolveBlock)resolve
                     reject:(RCTPromiseRejectBlock)reject
{
  [[MediaKitBridge shared] getPermissionStatusWithCompletion:^(NSDictionary *result, NSError *error) {
    if (error != nil) {
      reject(MediaKitErrorCodeFromError(error), error.localizedDescription, error);
      return;
    }

    resolve(result);
  }];
}

- (void)getAlbums:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  [[MediaKitBridge shared] getAlbumsWithCompletion:^(NSArray *result, NSError *error) {
    if (error != nil) {
      reject(MediaKitErrorCodeFromError(error), error.localizedDescription, error);
      return;
    }

    resolve(result);
  }];
}

- (void)getAssets:(NSString *)albumId
        mediaType:(NSString *)mediaType
             page:(double)page
         pageSize:(double)pageSize
          resolve:(RCTPromiseResolveBlock)resolve
           reject:(RCTPromiseRejectBlock)reject
{
  [[MediaKitBridge shared] getAssetsWithAlbumId:albumId
                                      mediaType:mediaType
                                           page:@(page)
                                       pageSize:@(pageSize)
                                     completion:^(NSDictionary *result, NSError *error) {
    if (error != nil) {
      reject(MediaKitErrorCodeFromError(error), error.localizedDescription, error);
      return;
    }

    resolve(result);
  }];
}

- (void)openCamera:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  [[MediaKitBridge shared] openCameraWithCompletion:^(NSDictionary *result, NSError *error) {
    if (error != nil) {
      reject(MediaKitErrorCodeFromError(error), error.localizedDescription, error);
      return;
    }

    resolve(result);
  }];
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
  return std::make_shared<facebook::react::NativeMediaKitSpecJSI>(params);
}

+ (NSString *)moduleName
{
  return @"MediaKit";
}

@end
