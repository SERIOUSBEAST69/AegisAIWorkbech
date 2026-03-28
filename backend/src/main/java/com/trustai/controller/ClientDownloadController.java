package com.trustai.controller;

import com.trustai.utils.R;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客户端安装包下载 API。
 *
 * <p>本控制器提供 Aegis 轻量级客户端安装包的下载接口。
 * 如果正式编译包尚未构建，则提供客户端源码压缩包作为替代。
 *
 * <p>生产环境中建议将编译产物上传至 CDN，并通过 302 重定向到 CDN 地址以节省带宽。
 */
@RestController
@RequestMapping("/api/download")
public class ClientDownloadController {

    /**
     * 返回各平台下载包的元信息（文件名、大小描述、下载URL）。
     */
    @GetMapping("/info")
    public R<Map<String, Object>> info() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("windows", buildInfo("windows", "AegisClient-Setup-1.0.0-x64.exe",
                "Windows 10/11 (64位) NSIS 安装包", "/api/download/client/windows"));
        result.put("macos", buildInfo("macos", "AegisClient-1.0.0.dmg",
                "macOS 12+ (Intel & Apple Silicon) DMG 镜像", "/api/download/client/macos"));
        result.put("linux", buildInfo("linux", "aegis-client_1.0.0_amd64.deb",
                "Ubuntu/Debian 系列 DEB 安装包 (x64)", "/api/download/client/linux"));
        return R.ok(result);
    }

    /**
     * 下载 Windows 客户端安装包（NSIS .exe）。
     * 若编译产物不存在则回退到 electron 源码压缩包。
     */
    @GetMapping("/client/windows")
    public void downloadWindows(HttpServletResponse response) throws IOException {
        serveOrFallback(response, "clients/AegisClient-Setup-1.0.0-x64.exe",
                "AegisClient-Setup-1.0.0-x64.exe", "application/octet-stream");
    }

    /**
     * 下载 macOS 客户端安装包（DMG）。
     */
    @GetMapping("/client/macos")
    public void downloadMacOS(HttpServletResponse response) throws IOException {
        serveOrFallback(response, "clients/AegisClient-1.0.0.dmg",
                "AegisClient-1.0.0.dmg", "application/octet-stream");
    }

    /**
     * 下载 Linux 客户端安装包（DEB）。
     */
    @GetMapping("/client/linux")
    public void downloadLinux(HttpServletResponse response) throws IOException {
        serveOrFallback(response, "clients/aegis-client_1.0.0_amd64.deb",
                "aegis-client_1.0.0_amd64.deb", "application/octet-stream");
    }

    // ── 内部工具 ────────────────────────────────────────────────────────────────

    private Map<String, Object> buildInfo(String platform, String filename, String desc, String url) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("platform", platform);
        m.put("filename", filename);
        m.put("description", desc);
        m.put("downloadUrl", url);
        m.put("version", "1.0.0");
        return m;
    }

    /**
     * 尝试从 classpath:clients/ 下提供预编译包；若不存在则提供通用安装说明文本。
     */
    private void serveOrFallback(HttpServletResponse response, String classpathResource,
                                  String filename, String contentType) throws IOException {
        ClassPathResource resource = new ClassPathResource(classpathResource);
        if (resource.exists()) {
            response.setContentType(contentType);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + filename + "\"");
            try (InputStream is = resource.getInputStream()) {
                is.transferTo(response.getOutputStream());
            }
        } else {
            // 回退：提供纯文本安装说明，触发浏览器下载
            String ext = filename.substring(filename.lastIndexOf('.') + 1);
            String readmeFilename = "AegisClient-install-guide-" + ext + ".txt";
            response.setContentType("text/plain; charset=UTF-8");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + readmeFilename + "\"");
            String guide = buildInstallGuide(ext);
            response.getOutputStream().write(guide.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private String buildInstallGuide(String platform) {
        return "===== Aegis AI 客户端安装指南 =====\n\n"
                + "版本: 1.0.0\n"
                + "平台: " + platform.toUpperCase() + "\n\n"
                + "【从源码构建安装包】\n"
                + "1. 确保已安装 Node.js 18+ 和 npm\n"
                + "2. 进入项目 electron 目录:\n"
                + "   cd electron\n"
                + "3. 安装依赖:\n"
                + "   npm install\n"
                + "4. 构建对应平台安装包:\n"
                + switch (platform) {
                    case "exe" -> "   npm run build:win\n   构建产物在 electron/dist/ 目录\n";
                    case "dmg" -> "   npm run build:mac\n   构建产物在 electron/dist/ 目录\n";
                    default    -> "   npm run build:linux\n   构建产物在 electron/dist/ 目录\n";
                }
                + "\n【已构建包安装步骤】\n"
                + switch (platform) {
                    case "exe" -> "双击 .exe 文件，按向导完成安装。\n安装后在系统托盘中右键 → 服务器设置，填入 AegisAI 服务端地址。\n";
                    case "dmg" -> "打开 .dmg，将 Aegis 拖入 Applications。\n首次运行右键 → 打开，在托盘菜单中配置服务器地址。\n";
                    default    -> "执行: sudo dpkg -i aegis-client_1.0.0_amd64.deb\n或:   sudo rpm -i aegis-client-1.0.0.x86_64.rpm\n启动: aegis-client --server http://<服务端IP>:8080\n";
                }
                + "\n【更多信息】\n"
                + "文档: https://github.com/SERIOUSBEAST69/AegisAI/blob/main/electron/README.md\n"
                + "问题反馈: https://github.com/SERIOUSBEAST69/AegisAI/issues\n";
    }
}
