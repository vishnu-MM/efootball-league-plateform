package com.leagueapp.files;

import java.io.InputStream;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** The bucket screenshots live in: S3, R2, B2 or MinIO, all through the S3 API (path-style, so MinIO works). */
@Component
class ObjectStore {

	private static final Logger log = LoggerFactory.getLogger(ObjectStore.class);

	private final S3Client s3;
	private final String bucket;

	ObjectStore(@Value("${app.storage.endpoint}") String endpoint, @Value("${app.storage.region}") String region,
			@Value("${app.storage.bucket}") String bucket, @Value("${app.storage.access-key}") String accessKey,
			@Value("${app.storage.secret-key}") String secretKey) {
		this.bucket = bucket;
		this.s3 = S3Client.builder()
			.endpointOverride(URI.create(endpoint))
			.region(Region.of(region))
			.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
			.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
			.build();
	}

	/** Creates the bucket if it's missing. If storage is down the app still starts; uploads fail until it's back. */
	@EventListener(ApplicationReadyEvent.class)
	void ensureBucket() {
		try {
			s3.headBucket(b -> b.bucket(bucket));
		}
		catch (NoSuchBucketException e) {
			createBucket();
		}
		catch (S3Exception e) {
			// HEAD responses have no body, so a missing bucket can come back as a plain 404.
			if (e.statusCode() != 404) {
				log.warn("Storage answered {} for bucket {}; screenshot uploads may not work", e.statusCode(), bucket);
				return;
			}
			createBucket();
		}
		catch (RuntimeException e) {
			log.warn("Storage isn't reachable ({}); screenshot uploads won't work until it is", e.getMessage());
		}
	}

	private void createBucket() {
		s3.createBucket(b -> b.bucket(bucket));
		log.info("Created storage bucket {}", bucket);
	}

	void put(String key, byte[] bytes, String contentType) {
		s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType), RequestBody.fromBytes(bytes));
	}

	InputStream get(String key) {
		return s3.getObject(b -> b.bucket(bucket).key(key));
	}

	void delete(String key) {
		try {
			s3.deleteObject(b -> b.bucket(bucket).key(key));
		}
		catch (S3Exception e) {
			log.warn("Couldn't delete {} from storage: {}", key, e.getMessage());
		}
	}

}
