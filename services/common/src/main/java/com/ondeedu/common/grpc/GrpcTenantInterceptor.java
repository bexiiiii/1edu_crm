package com.ondeedu.common.grpc;

import com.ondeedu.common.tenant.TenantContext;
import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

@Slf4j
@GrpcGlobalServerInterceptor
public class GrpcTenantInterceptor implements ServerInterceptor {

    public static final Metadata.Key<String> TENANT_ID_KEY =
        Metadata.Key.of("X-Tenant-ID", Metadata.ASCII_STRING_MARSHALLER);

    public static final Metadata.Key<String> USER_ID_KEY =
        Metadata.Key.of("X-User-ID", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String tenantId = headers.get(TENANT_ID_KEY);
        String userId = headers.get(USER_ID_KEY);

        if (tenantId != null) {
            TenantContext.setTenantId(tenantId);
            TenantContext.setSchemaName("tenant_" + tenantId);
        }

        if (userId != null) {
            TenantContext.setUserId(userId);
        }

        log.debug("gRPC call - Tenant: {}, User: {}", tenantId, userId);

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(
                next.startCall(call, headers)) {

            @Override
            public void onComplete() {
                try {
                    super.onComplete();
                } finally {
                    TenantContext.clear();
                }
            }

            @Override
            public void onCancel() {
                try {
                    super.onCancel();
                } finally {
                    TenantContext.clear();
                }
            }
        };
    }
}