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
 * 채팅 이미지를 S3에 올린다. 버킷은 프론트 정적 자산과 같은 버킷이라 CloudFront OAC 뒤에서만
 * 읽히고 S3 도메인으로 직접 접근하면 403이 난다(사적인 그룹 채팅용이라 presigned URL까지는
 * 과함, 파일명을 UUID로 둬서 추측 불가능성으로 보호). 그래서 반환 URL은 S3 도메인이 아니라
 * {@code publicBaseUrl}(CloudFront 도메인)을 우선 쓴다 — 객체 ACL을 따로 지정하지 않는 이유도
 * 같다(버킷 Object Ownership이 "Bucket owner enforced"면 ACL 자체가 막혀 있다).
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

	/** CloudFront 등 버킷 프록시 도메인. 비어 있으면(로컬/h2) S3 버킷 도메인으로 직접 URL을 만든다. */
	@Value("${app.aws.s3.public-base-url:}")
	private String publicBaseUrl;

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

		return buildPublicUrl(key);
	}

	private String buildPublicUrl(String key) {
		if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
			return "https://%s.s3.%s.amazonaws.com/%s".formatted(bucket, region, key);
		}
		String base = publicBaseUrl.endsWith("/")
			? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
			: publicBaseUrl;
		return "%s/%s".formatted(base, key);
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
