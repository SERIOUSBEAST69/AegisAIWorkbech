package com.trustai.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.trustai.entity.User;
import com.trustai.service.CurrentUserService;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class TenantMetaObjectHandler implements MetaObjectHandler {

    private final ObjectProvider<CurrentUserService> currentUserServiceProvider;

    public TenantMetaObjectHandler(ObjectProvider<CurrentUserService> currentUserServiceProvider) {
        this.currentUserServiceProvider = currentUserServiceProvider;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        if (!metaObject.hasSetter("companyId")) {
            return;
        }
        Object companyId = getFieldValByName("companyId", metaObject);
        if (companyId != null) {
            return;
        }
        try {
            CurrentUserService currentUserService = currentUserServiceProvider.getIfAvailable();
            if (currentUserService == null) {
                return;
            }
            User current = currentUserService.requireCurrentUser();
            if (current.getCompanyId() != null) {
                strictInsertFill(metaObject, "companyId", Long.class, current.getCompanyId());
            }
        } catch (Exception ignored) {
            // Ignore non-request contexts like startup jobs.
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // no-op
    }
}
