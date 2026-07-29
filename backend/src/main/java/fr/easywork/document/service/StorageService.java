package fr.easywork.document.service;

import fr.easywork.document.config.StorageProperties;
import fr.easywork.document.exception.StorageException;
import io.minio.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final MinioClient minioClient;
    private final StorageProperties props;

    public String store(MultipartFile file, UUID documentId) {
        String key = documentId + "/" + file.getOriginalFilename();
        try {
            ensureBucketExists();
            minioClient.putObject(PutObjectArgs.builder()
                .bucket(props.bucket())
                .object(key)
                .stream(file.getInputStream(), file.getSize(), -1L)
                .contentType(file.getContentType())
                .build());
        } catch (Exception e) {
            throw new StorageException("Failed to store file: " + key, e);
        }
        return key;
    }

    public InputStream download(String key) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                .bucket(props.bucket())
                .object(key)
                .build());
        } catch (Exception e) {
            throw new StorageException("Failed to download file: " + key, e);
        }
    }

    public void delete(String key) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(props.bucket())
                .object(key)
                .build());
        } catch (Exception e) {
            throw new StorageException("Failed to delete file: " + key, e);
        }
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
            .bucket(props.bucket()).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(props.bucket()).build());
        }
    }
}
