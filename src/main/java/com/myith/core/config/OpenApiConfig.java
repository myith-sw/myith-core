package com.myith.core.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "MYiTH Core API",
                version = "0.1.0",
                description = """
                        채용공고와 국가직무능력표준(NCS)을 결합한 개인 맞춤형 취업 로드맵 서비스.

                        인증: Google ID Token으로 로그인 후 발급받은 accessToken을
                        Authorization: Bearer {token} 헤더에 실어 보낸다.

                        공통 응답: 모든 성공 응답은 data로 감싼다. (GET /api/health 제외)
                        공통 오류: { "error": { code, message, fieldErrors, requestId } }
                        """
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "로컬"),
                @Server(url = "https://api.myith.example", description = "운영")
        },
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}
