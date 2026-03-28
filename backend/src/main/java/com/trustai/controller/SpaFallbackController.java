package com.trustai.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * SPA（单页应用）回退控制器。
 *
 * <p>当 Spring Boot 同时承载 Vue 前端静态资源时，所有非 API、非静态资源的路由
 * 都应返回 {@code index.html}，由 Vue Router 在前端处理路由。
 *
 * <p>此控制器仅在 {@code classpath:static/index.html} 存在时生效
 * （即打包模式：执行 {@code npm run build} 后将 {@code dist/} 内容复制到
 * {@code backend/src/main/resources/static/}）。
 *
 * <p>开发模式下，前端运行在独立的 Vite 开发服务器（通常为 {@code http://localhost:5173}），
 * 本控制器不会被触发。
 *
 * <p><strong>实现说明</strong>：每个路由模式中第一段路径变量使用了负向先行断言
 * ({@code (?!...)})，从匹配阶段就排除 {@code /api/}、{@code /uploads/} 等保留前缀。
 * 这样 REST 控制器始终能在 Spring MVC 解析阶段前拿到请求，避免回退到静态资源处理器
 * 产生 "No static resource" 误报日志（旧实现在方法体内 {@code return null} 会触发
 * Spring MVC 的视图名称推断，最终走到静态资源处理器）。
 */
@Controller
@ConditionalOnResource(resources = "classpath:static/index.html")
public class SpaFallbackController {

    /**
     * 第一路径段的排除模式：不匹配 api、uploads、h2-console、swagger-ui、v3 这几个保留前缀。
     * 负向先行断言 {@code (?!word(?:/|$))} 排除该词本身以及以该词开头再跟 {@code /} 的情况，
     * 不影响以相同字母开头但含更多字符的正常前端路由（如 {@code /apidocs}）。
     * 后半部分 {@code [^.]+} 要求至少一个字符且不含点号（排除静态资源文件名如 {@code favicon.ico}）。
     */
    private static final String SAFE_P1 =
            "(?!(?:api|uploads|h2-console|swagger-ui|v3)(?:/|$))[^.]+";

    /**
     * 将所有非保留路径转发至 {@code index.html}。
     *
     * <p>匹配规则：
     * <ul>
     *   <li>第一路径段为 {@code api / uploads / h2-console / swagger-ui / v3}（或以其为前缀路径）
     *       → 不匹配此控制器，直接由对应 RestController / 静态资源处理器处理。</li>
     *   <li>其他所有路径（最多 5 级，不含扩展名）→ 转发至 {@code /index.html}。</li>
     * </ul>
     */
    @RequestMapping(value = {
            "/{p1:" + SAFE_P1 + "}",
            "/{p1:" + SAFE_P1 + "}/{p2:[^.]*}",
            "/{p1:" + SAFE_P1 + "}/{p2:[^.]*}/{p3:[^.]*}",
            "/{p1:" + SAFE_P1 + "}/{p2:[^.]*}/{p3:[^.]*}/{p4:[^.]*}",
            "/{p1:" + SAFE_P1 + "}/{p2:[^.]*}/{p3:[^.]*}/{p4:[^.]*}/{p5:[^.]*}"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
