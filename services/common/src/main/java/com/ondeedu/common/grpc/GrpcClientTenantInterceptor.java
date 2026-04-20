package com.ondeedu.common.grpc;

import com.ondeedu.common.tenant.TenantContext;
import io.grpc.*;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;

@GrpcGlobalClientInterceptor
public class GrpcClientTenantInterceptor implements ClientInterceptor {

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(
                next.newCall(method, callOptions)) {

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                String tenantId = TenantContext.getTenantId();
                String userId = TenantContext.getUserId();
                String branchId = TenantContext.getBranchId();

                if (tenantId != null) {
                    headers.put(GrpcTenantInterceptor.TENANT_ID_KEY, tenantId);
                }
                if (userId != null) {
                    headers.put(GrpcTenantInterceptor.USER_ID_KEY, userId);
                }
                if (branchId != null) {
                    headers.put(GrpcTenantInterceptor.BRANCH_ID_KEY, branchId);
                }

                super.start(responseListener, headers);
            }
        };
    }
}