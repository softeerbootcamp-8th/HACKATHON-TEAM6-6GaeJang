package com.delipot.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.delipot.global.error.BusinessException;
import com.delipot.global.error.ErrorCode;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ExtendWith(MockitoExtension.class)
class ChatImageUploaderTest {

	@Mock
	private S3Client s3Client;

	private ChatImageUploader uploader;

	private ChatImageUploader newUploader() {
		ChatImageUploader u = new ChatImageUploader(s3Client);
		ReflectionTestUtils.setField(u, "bucket", "delipot-s3-523224780149-ap-northeast-2-an");
		ReflectionTestUtils.setField(u, "region", "ap-northeast-2");
		return u;
	}

	@Test
	@DisplayName("public-base-url이 없으면 S3 버킷 도메인으로 직접 URL을 만든다(로컬/h2)")
	void upload_success_withoutPublicBaseUrl() {
		uploader = newUploader();
		given(s3Client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
			.willReturn(PutObjectResponse.builder().build());
		MockMultipartFile file = new MockMultipartFile("file", "cat.png", "image/png", new byte[] {1, 2, 3});

		String url = uploader.upload(10L, file);

		assertThat(url).startsWith("https://delipot-s3-523224780149-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/chat-images/10/");
		assertThat(url).endsWith(".png");
	}

	@Test
	@DisplayName("public-base-url이 있으면(prod) 그 도메인으로 URL을 만든다 — 버킷은 CloudFront OAC 뒤에 있어 직접 접근이 403이라서다")
	void upload_success_withPublicBaseUrl() {
		uploader = newUploader();
		ReflectionTestUtils.setField(uploader, "publicBaseUrl", "https://delipot.cloud/");
		given(s3Client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
			.willReturn(PutObjectResponse.builder().build());
		MockMultipartFile file = new MockMultipartFile("file", "cat.png", "image/png", new byte[] {1, 2, 3});

		String url = uploader.upload(10L, file);

		assertThat(url).startsWith("https://delipot.cloud/chat-images/10/");
		assertThat(url).doesNotContain("s3.ap-northeast-2.amazonaws.com");
		assertThat(url).endsWith(".png");
	}

	@Test
	@DisplayName("5MB를 넘는 파일은 업로드를 시도하지 않고 거부한다")
	void upload_tooLarge() {
		uploader = newUploader();
		MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", new byte[6 * 1024 * 1024]);

		assertThatThrownBy(() -> uploader.upload(10L, file))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(ErrorCode.INVALID_INPUT);

		verifyNoInteractions(s3Client);
	}

	@Test
	@DisplayName("이미지가 아닌 파일은 거부한다")
	void upload_notAnImage() {
		uploader = newUploader();
		MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[] {1});

		assertThatThrownBy(() -> uploader.upload(10L, file))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(ErrorCode.INVALID_INPUT);

		verifyNoInteractions(s3Client);
	}
}
