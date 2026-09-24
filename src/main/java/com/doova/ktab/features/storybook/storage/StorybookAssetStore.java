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

/** R2 access with caller-chosen keys, so retried steps can find what they already uploaded. */
@Component
public class StorybookAssetStore {

    private final S3Client s3;
    private final String bucket;

    @Autowired
    public StorybookAssetStore(S3Client s3, @Value("${cloudflare.r2.bucketName:${aws.s3.bucketName:ktab-bucket}}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    public void put(String key, byte[] bytes, String contentType) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(bytes));
    }

    public byte[] get(String key) {
        ResponseBytes<GetObjectResponse> bytes = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build());
        return bytes.asByteArray();
    }

    public boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
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
