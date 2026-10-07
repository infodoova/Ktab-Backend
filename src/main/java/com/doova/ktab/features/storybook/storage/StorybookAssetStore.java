package com.doova.ktab.features.storybook.storage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** R2 access with caller-chosen keys, so retried steps can find what they already uploaded. */
@Component
@Slf4j
public class StorybookAssetStore {

    private final S3Client s3;
    private final String bucket;
    private final Map<String, byte[]> localCache = new ConcurrentHashMap<>();

    @Autowired
    public StorybookAssetStore(S3Client s3, @Value("${cloudflare.r2.bucketName:${aws.s3.bucketName:ktab-bucket}}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    public void put(String key, byte[] bytes, String contentType) {
        if (bytes != null) {
            localCache.put(key, bytes);
        }
        try {
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                    RequestBody.fromBytes(bytes));
        } catch (Exception e) {
            log.warn("S3 putObject failed for key={}, cached locally: {}", key, e.getMessage());
        }
    }

    public byte[] get(String key) {
        if (localCache.containsKey(key)) {
            return localCache.get(key);
        }
        try {
            ResponseBytes<GetObjectResponse> bytes = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build());
            byte[] arr = bytes.asByteArray();
            localCache.put(key, arr);
            return arr;
        } catch (Exception e) {
            if (localCache.containsKey(key)) {
                return localCache.get(key);
            }
            throw e;
        }
    }

    public boolean exists(String key) {
        if (localCache.containsKey(key)) {
            return true;
        }
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return localCache.containsKey(key);
            }
            return localCache.containsKey(key);
        } catch (Exception e) {
            return localCache.containsKey(key);
        }
    }

    public void delete(String key) {
        localCache.remove(key);
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (Exception e) {
            log.warn("S3 deleteObject failed for key={}: {}", key, e.getMessage());
        }
    }

    public Set<Long> listBookIds() {
        Set<Long> ids = new HashSet<>();
        String token = null;
        do {
            ListObjectsV2Response page = s3.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucket).prefix("storybook/").delimiter("/").continuationToken(token).build());
            for (CommonPrefix p : page.commonPrefixes()) {
                String id = p.prefix().substring("storybook/".length(), p.prefix().length() - 1);
                if (id.chars().allMatch(Character::isDigit) && !id.isEmpty()) {
                    ids.add(Long.parseLong(id));
                }
            }
            token = page.isTruncated() ? page.nextContinuationToken() : null;
        } while (token != null);
        return ids;
    }

    public void deletePrefix(String prefix) {
        localCache.keySet().removeIf(k -> k.startsWith(prefix));
        String token = null;
        do {
            ListObjectsV2Response page = s3.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucket).prefix(prefix).continuationToken(token).build());
            List<ObjectIdentifier> keys = page.contents().stream()
                    .map(o -> ObjectIdentifier.builder().key(o.key()).build()).toList();
            if (!keys.isEmpty()) {
                s3.deleteObjects(DeleteObjectsRequest.builder().bucket(bucket)
                        .delete(Delete.builder().objects(keys).build()).build());
            }
            token = page.isTruncated() ? page.nextContinuationToken() : null;
        } while (token != null);
    }
}
