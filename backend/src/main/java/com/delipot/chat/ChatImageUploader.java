package com.delipot.chat;

import java.io.IOException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.delipot.global.error.BusinessException;
import com.delipot.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * 채팅 이미지를 S3에 올린다. 버킷은 public-read 정책이 걸려 있어(사적인 그룹 채팅용이라
 * presigned URL까지는 과함, 파일명을 UUID로 둬서 추측 불가능성으로 보호) 객체 ACL을 따로
 * 지정하지 않는다 — 버킷 Object Ownership이 "Bucket owner enforced"면 ACL 자체가 막혀 있다.
 */
@Component
@RequiredArgsConstructor
public class ChatImageUploader {

	private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

	private final S3Client s3Client;

	@Value("${app.aws.s3.bucket}")
	private String bucket;

	@Value("${app.aws.s3.region}")
	private String region;

	public String upload(Long roomId, MultipartFile file) {
		validate(file);

		String key = "chat-images/%d/%s%s".formatted(roomId, UUID.randomUUID(), extensionOf(file.getOriginalFilename()));
		try {
			s3Client.putObject(
				PutObjectRequest.builder()
					.bucket(bucket)
					.key(key)
					.contentType(file.getContentType())
					.build(),
				RequestBody.fromInputStream(file.getInputStream(), file.getSize())
			);
		} catch (IOException e) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "이미지를 읽을 수 없습니다.");
		}

		return "https://%s.s3.%s.amazonaws.com/%s".formatted(bucket, region, key);
	}

	private void validate(MultipartFile file) {
		if (file.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "이미지 파일이 비어 있습니다.");
		}
		if (file.getSize() > MAX_SIZE_BYTES) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "이미지는 5MB 이하만 업로드할 수 있습니다.");
		}
		String contentType = file.getContentType();
		if (contentType == null || !contentType.startsWith("image/")) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "이미지 파일만 업로드할 수 있습니다.");
		}
	}

	private String extensionOf(String originalFilename) {
		if (originalFilename == null || !originalFilename.contains(".")) {
			return "";
		}
		return originalFilename.substring(originalFilename.lastIndexOf('.'));
	}
}
