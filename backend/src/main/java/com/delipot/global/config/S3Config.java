package com.delipot.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * EC2에 붙인 IAM 역할에서 자격증명을 자동으로 가져온다(DefaultCredentialsProvider가
 * 인스턴스 메타데이터를 조회). 로컬은 `aws configure`로 등록한 개인 자격증명을 동일한
 * 방식으로 찾는다 — 코드는 두 환경에서 동일하다.
 */
@Configuration
public class S3Config {

	@Bean
	public S3Client s3Client(@Value("${app.aws.s3.region}") String region) {
		return S3Client.builder()
			.region(Region.of(region))
			.credentialsProvider(DefaultCredentialsProvider.create())
			.build();
	}
}
