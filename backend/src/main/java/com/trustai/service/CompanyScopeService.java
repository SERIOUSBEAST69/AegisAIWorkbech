package com.trustai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trustai.entity.User;
import com.trustai.exception.BizException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CompanyScopeService {

    private final CurrentUserService currentUserService;
    private final UserService userService;

    public Long requireCompanyId() {
        User user = currentUserService.requireCurrentUser();
        if (user.getCompanyId() == null) {
            throw new BizException(40300, "当前账号未绑定公司，无法访问数据");
        }
        return user.getCompanyId();
    }

    public <T> QueryWrapper<T> withCompany(QueryWrapper<T> wrapper) {
        return withCompany(wrapper, "company_id");
    }

    public <T> QueryWrapper<T> withCompany(QueryWrapper<T> wrapper, String columnName) {
        QueryWrapper<T> target = wrapper == null ? new QueryWrapper<>() : wrapper;
        target.eq(columnName, requireCompanyId());
        return target;
    }

    public List<Long> companyUserIds() {
        Long companyId = requireCompanyId();
        return userService.lambdaQuery()
            .eq(User::getCompanyId, companyId)
            .list()
            .stream()
            .map(User::getId)
            .filter(id -> id != null)
            .collect(Collectors.toList());
    }

    public List<String> companyUsernames() {
        Long companyId = requireCompanyId();
        return userService.lambdaQuery()
            .eq(User::getCompanyId, companyId)
            .list()
            .stream()
            .map(User::getUsername)
            .filter(name -> name != null && !name.isBlank())
            .collect(Collectors.toList());
    }
}
