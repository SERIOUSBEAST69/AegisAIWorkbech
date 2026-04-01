package com.trustai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.config.jwt.JwtPrincipal;
import com.trustai.utils.R;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CompanyConsistencyFilter extends OncePerRequestFilter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth == null ? null : auth.getPrincipal();
        if (!(principal instanceof JwtPrincipal jwtPrincipal) || jwtPrincipal.companyId() == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Long companyIdFromToken = jwtPrincipal.companyId();
        Long headerCompany = parseLong(request.getHeader("X-Company-Id"));
        Long queryCompany = parseLong(request.getParameter("companyId"));

        if ((headerCompany != null && !headerCompany.equals(companyIdFromToken))
            || (queryCompany != null && !queryCompany.equals(companyIdFromToken))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(MAPPER.writeValueAsString(R.error(40300, "租户标识不一致，已拒绝访问")));
            return;
        }

        // Downstream components can read a normalized company context from request attribute.
        request.setAttribute("resolvedCompanyId", companyIdFromToken);
        filterChain.doFilter(request, response);
    }

    private Long parseLong(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (Exception ex) {
            return null;
        }
    }
}
