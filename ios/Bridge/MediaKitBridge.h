#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface MediaKitBridge : NSObject

+ (instancetype)shared;

- (void)requestPermissionsWithCompletion:
    (void (^)(NSDictionary *_Nullable result, NSError *_Nullable error))completion;

- (void)getPermissionStatusWithCompletion:
    (void (^)(NSDictionary *_Nullable result, NSError *_Nullable error))completion;

- (void)getAlbumsWithCompletion:(void (^)(NSArray *_Nullable result, NSError *_Nullable error))completion;

- (void)getAssetsWithAlbumId:(NSString *_Nullable)albumId
                   mediaType:(NSString *)mediaType
                        page:(NSNumber *)page
                    pageSize:(NSNumber *)pageSize
                  completion:(void (^)(NSDictionary *_Nullable result, NSError *_Nullable error))completion;

- (void)openCameraWithCompletion:
    (void (^)(NSDictionary *_Nullable result, NSError *_Nullable error))completion;

@end

NS_ASSUME_NONNULL_END
