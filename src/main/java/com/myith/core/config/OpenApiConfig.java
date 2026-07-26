package com.myith.core.config;

import com.myith.core.common.ErrorResponse;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "MYiTH Core API",
                version = "0.1.0",
                description = """
                        채용공고와 국가직무능력표준(NCS)을 결합한 개인 맞춤형 취업 로드맵 서비스입니다.

                        ## 인증
                        `POST /api/auth/google` 에 Google ID Token을 전달해 `accessToken`과 `refreshToken`을 발급받습니다.
                        이후 모든 인증 필요 요청에는 `Authorization: Bearer {accessToken}` 헤더를 포함해야 합니다.
                        `accessToken` 만료 시 `POST /api/auth/refresh` 로 재발급합니다.

                        ## 공통 성공 응답
                        모든 성공 응답은 아래 구조로 감쌉니다. (`GET /api/health` 제외)
                        ```json
                        { "data": { ... }, "meta": null }
                        ```
                        목록 조회 시 `meta` 에 커서 페이지네이션 정보가 포함됩니다.
                        ```json
                        { "data": [...], "meta": { "nextCursor": "...", "hasNext": true } }
                        ```

                        ## 공통 에러 응답
                        모든 에러는 아래 구조를 사용합니다.
                        ```json
                        { "error": { "code": "QUEST_LOCKED", "message": "선행 퀘스트를 먼저 완료해주세요.", "fieldErrors": null, "requestId": "req_01J3ABC" } }
                        ```
                        - `code`: 에러 식별자 (클라이언트 분기 처리용)
                        - `message`: 사용자에게 그대로 노출 가능한 한국어 메시지
                        - `fieldErrors`: 422 유효성 검사 실패 시 필드별 오류 맵, 그 외 `null`
                        - `requestId`: 로그 추적용 요청 ID

                        ## ID 체계
                        모든 리소스 ID는 접두사가 붙은 문자열입니다. (예: `rdm_01J3ABC`, `qst_01J3DEF`, `chr_01J3GHI`)
                        숫자 타입이 아님에 유의하세요.
                        """
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "로컬"),
                @Server(url = "https://api.myith.store", description = "운영")
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

    /**
     * OperationCustomizer가 수집한 schema를 OpenApiCustomizer가 components에 등록하기 위한 공유 저장소.
     * OperationCustomizer(per-operation) → OpenApiCustomizer(post-processing) 순서로 실행된다.
     */
    private final Map<String, Schema> pendingSchemas = new ConcurrentHashMap<>();

    /**
     * 2xx 응답에 schema가 없으면 메서드 반환 타입에서 추출해 설정한다.
     *
     * 원인: @Content(examples = ...)를 명시적으로 선언하면 springdoc이 return type 기반
     * schema 자동 생성을 건너뛴다. 예시와 schema를 공존시키기 위해 프로그래밍 방식으로 주입.
     *
     * ResponseEntity만 벗기고 ApiResponse<T>는 보존한다.
     * → 실제 응답 { data: T, meta: ... } 구조가 schema에 반영된다.
     */
    @Bean
    public OperationCustomizer successResponseSchemaResolver() {
        return (operation, handlerMethod) -> {
            if (operation.getResponses() == null) return operation;

            Type returnType = handlerMethod.getMethod().getGenericReturnType();
            // ResponseEntity<X> → X (프레임워크 래퍼만 벗김)
            returnType = unwrapResponseEntity(returnType);

            if (returnType == null) return operation;
            if (returnType instanceof Class<?> c && (c == Void.class || c == void.class)) return operation;

            // ApiResponse<T> 전체를 schema로 풀어 래퍼 구조(data, meta)를 보존한다.
            ResolvedSchema resolved;
            try {
                resolved = ModelConverters.getInstance()
                        .resolveAsResolvedSchema(new AnnotatedType(returnType).resolveAsRef(true));
            } catch (Exception e) {
                return operation;
            }
            if (resolved == null || resolved.schema == null) return operation;

            // 참조되는 하위 schema(T, Meta 등)를 수집 → OpenApiCustomizer가 등록
            if (resolved.referencedSchemas != null) {
                resolved.referencedSchemas.forEach(pendingSchemas::putIfAbsent);
            }

            Schema<?> schema = resolved.schema;

            operation.getResponses().forEach((code, response) -> {
                int status;
                try { status = Integer.parseInt(code); } catch (NumberFormatException e) { return; }
                if (status < 200 || status >= 300 || status == 204) return;
                if (response.getContent() == null) return;

                response.getContent().forEach((mediaType, mediaTypeObj) -> {
                    if (mediaType.contains("json") && mediaTypeObj.getSchema() == null) {
                        mediaTypeObj.setSchema(schema);
                    }
                });
            });

            return operation;
        };
    }

    /**
     * components/schemas에 누락된 정의를 등록하고 4xx/5xx 응답에 ErrorResponse $ref를 건다.
     */
    @Bean
    public OpenApiCustomizer schemaRegistrar() {
        return openApi -> {
            // ErrorResponse + ErrorDetail 스키마 등록
            ModelConverters.getInstance().readAll(ErrorResponse.class)
                    .forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));

            // OperationCustomizer가 수집한 스키마 등록
            pendingSchemas.forEach((name, schema) ->
                    openApi.getComponents().addSchemas(name, schema));

            // 4xx/5xx 응답에 ErrorResponse $ref 주입
            if (openApi.getPaths() == null) return;
            openApi.getPaths().forEach((path, pathItem) ->
                    pathItem.readOperations().forEach(op -> {
                        if (op.getResponses() == null) return;
                        op.getResponses().forEach((code, response) -> {
                            if (response.getContent() == null) return;
                            int status;
                            try { status = Integer.parseInt(code); } catch (NumberFormatException e) { return; }
                            if (status < 400) return;
                            response.getContent().forEach((mediaType, mediaTypeObj) -> {
                                if (mediaTypeObj.getSchema() == null) {
                                    mediaTypeObj.setSchema(new Schema<>().$ref("#/components/schemas/ErrorResponse"));
                                }
                            });
                        });
                    }));
        };
    }

    // ── 유틸 ──

    private Type unwrapResponseEntity(Type type) {
        if (type instanceof ParameterizedType pt) {
            Class<?> raw = (Class<?>) pt.getRawType();
            if (ResponseEntity.class.isAssignableFrom(raw) && pt.getActualTypeArguments().length > 0) {
                return pt.getActualTypeArguments()[0];
            }
        }
        return type;
    }
}
