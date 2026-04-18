package com.trustai.controller;

import com.trustai.utils.R;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");
    private static final PathMatchingResourcePatternResolver RESOURCE_RESOLVER = new PathMatchingResourcePatternResolver();

    /**
     * 返回各平台下载包的元信息（文件名、大小描述、下载URL）。
     */
    @GetMapping("/info")
    public R<Map<String, Object>> info() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("windows", buildInfo("windows", latestArtifact("windows",
            List.of("Aegis Workbench Setup *.exe", "AegisClient-Setup-*.exe"),
            "Aegis Workbench Setup 1.0.0.exe", "exe"), "Windows 10/11 (64位) NSIS 安装包", "/api/download/client/windows"));
        result.put("macos", buildInfo("macos", latestArtifact("macos", "AegisClient-*.dmg",
            "AegisClient-1.0.0.dmg", "dmg"), "macOS 安装包", "/api/download/client/macos"));
        result.put("linux", buildInfo("linux", latestArtifact("linux", "aegis-client_*.deb",
            "aegis-client_1.0.0_amd64.deb", "deb"), "Linux 安装包", "/api/download/client/linux"));
        return R.ok(result);
    }

    /**
     * 下载 Windows 客户端安装包（NSIS .exe）。
     * 若编译产物不存在则回退到 electron 源码压缩包。
     */
    @GetMapping("/client/windows")
    public void downloadWindows(HttpServletResponse response) throws IOException {
        PackageArtifact artifact = latestArtifact("windows",
            List.of("Aegis Workbench Setup *.exe", "AegisClient-Setup-*.exe"),
            "Aegis Workbench Setup 1.0.0.exe", "exe");
        serveOrFallback(response, artifact, "application/octet-stream");
    }

    /**
     * 下载 macOS 客户端安装包（DMG）。
     */
    @GetMapping("/client/macos")
    public void downloadMacOS(HttpServletResponse response) throws IOException {
        PackageArtifact artifact = latestArtifact("macos", "AegisClient-*.dmg",
            "AegisClient-1.0.0.dmg", "dmg");
        serveOrFallback(response, artifact, "application/octet-stream");
    }

    /**
     * 下载 Linux 客户端安装包（DEB）。
     */
    @GetMapping("/client/linux")
    public void downloadLinux(HttpServletResponse response) throws IOException {
        PackageArtifact artifact = latestArtifact("linux", "aegis-client_*.deb",
            "aegis-client_1.0.0_amd64.deb", "deb");
        serveOrFallback(response, artifact, "application/octet-stream");
    }

    // ── 内部工具 ────────────────────────────────────────────────────────────────

    private Map<String, Object> buildInfo(String platform, PackageArtifact artifact, String desc, String url) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("platform", platform);
        m.put("filename", artifact.filename);
        m.put("description", desc);
        m.put("downloadUrl", url);
        m.put("version", artifact.version);
        return m;
    }

    private PackageArtifact latestArtifact(String platform, String pattern, String fallbackFilename, String extension) {
        return latestArtifact(platform, List.of(pattern), fallbackFilename, extension);
    }

    private PackageArtifact latestArtifact(String platform, List<String> patterns, String fallbackFilename, String extension) {
        try {
            List<Resource> classpathResources = patterns.stream()
                    .flatMap(pattern -> {
                        try {
                            return Arrays.stream(RESOURCE_RESOLVER.getResources("classpath*:clients/" + pattern));
                        } catch (IOException ex) {
                            return java.util.stream.Stream.empty();
                        }
                    })
                    .toList();

            List<Resource> fileSystemResources = patterns.stream()
                    .flatMap(pattern -> listFileSystemArtifacts(pattern).stream())
                    .toList();

            return java.util.stream.Stream.concat(classpathResources.stream(), fileSystemResources.stream())
                    .filter(Resource::exists)
                    .map(resource -> toArtifact(resource, platform, extension))
                    .filter(Objects::nonNull)
                    .max((a, b) -> {
                        int versionDiff = compareVersion(a.version, b.version);
                        if (versionDiff != 0) {
                            return versionDiff;
                        }
                        return Integer.compare(a.sourceRank, b.sourceRank);
                    })
                    .orElseGet(() -> new PackageArtifact(fallbackFilename, "1.0.0", new ClassPathResource("clients/" + fallbackFilename), platform));
        } catch (Exception ex) {
            return new PackageArtifact(fallbackFilename, "1.0.0", new ClassPathResource("clients/" + fallbackFilename), platform);
        }
    }

    private List<Resource> listFileSystemArtifacts(String globPattern) {
        List<Path> roots = List.of(
                Paths.get(System.getProperty("user.dir"), "electron", "dist"),
                Paths.get(System.getProperty("user.dir"), "..", "electron", "dist")
        );

        List<Resource> resources = new java.util.ArrayList<>();
        for (Path root : roots) {
            try {
                if (!Files.isDirectory(root)) {
                    continue;
                }
                try (var stream = Files.newDirectoryStream(root, globPattern)) {
                    for (Path file : stream) {
                        if (Files.isRegularFile(file)) {
                            resources.add(new FileSystemResource(file));
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return resources;
    }

    private PackageArtifact toArtifact(Resource resource, String platform, String extension) {
        String filename = extractFilename(resource);
        if (filename == null) {
            return null;
        }
        String version = extractVersion(filename);
        PackageArtifact artifact = new PackageArtifact(filename, version, resource, platform);
        return isUsableArtifact(artifact) ? artifact : null;
    }

    private boolean isUsableArtifact(PackageArtifact artifact) {
        if (artifact == null || artifact.resource == null || !artifact.resource.exists()) {
            return false;
        }
        // Guard against accidentally committed placeholder binaries (e.g. a few hundred bytes).
        // Real installers are usually measured in MB.
        long minBytes = switch (artifact.extension) {
            case "exe", "dmg", "deb", "rpm" -> 64 * 1024L;
            default -> 1L;
        };
        try {
            long size = artifact.resource.contentLength();
            return size >= minBytes;
        } catch (IOException ex) {
            return false;
        }
    }

    private int compareVersion(String left, String right) {
        int[] a = parseVersion(left);
        int[] b = parseVersion(right);
        for (int i = 0; i < 3; i++) {
            int diff = Integer.compare(a[i], b[i]);
            if (diff != 0) return diff;
        }
        return 0;
    }

    private int[] parseVersion(String version) {
        Matcher matcher = VERSION_PATTERN.matcher(String.valueOf(version));
        if (!matcher.find()) {
            return new int[] {1, 0, 0};
        }
        return new int[] {
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)),
        };
    }

    private String extractVersion(String filename) {
        Matcher matcher = VERSION_PATTERN.matcher(filename);
        if (!matcher.find()) {
            return "1.0.0";
        }
        return matcher.group(0);
    }

    private String extractFilename(Resource resource) {
        try {
            String path = resource.getURL().getPath();
            int slash = path.lastIndexOf('/');
            return slash >= 0 ? path.substring(slash + 1) : path;
        } catch (IOException ex) {
            return null;
        }
    }

    /**
     * 尝试从 classpath:clients/ 下提供预编译包；若不存在则提供通用安装说明文本。
     */
    private void serveOrFallback(HttpServletResponse response, PackageArtifact artifact,
                                  String contentType) throws IOException {
        ClassPathResource resource = new ClassPathResource("clients/" + artifact.filename);
        if (resource.exists()) {
            setDownloadHeaders(response, artifact.filename, contentType);
            response.setContentType(contentType);
            try (InputStream is = resource.getInputStream()) {
                is.transferTo(response.getOutputStream());
            }
        } else if (artifact.resource != null && artifact.resource.exists()) {
            setDownloadHeaders(response, artifact.filename, contentType);
            response.setContentType(contentType);
            try (InputStream is = artifact.resource.getInputStream()) {
                is.transferTo(response.getOutputStream());
            }
        } else {
            // 回退：提供纯文本安装说明，触发浏览器下载
            String ext = artifact.extension;
            String readmeFilename = "AegisClient-install-guide-" + ext + ".txt";
            response.setContentType("text/plain; charset=UTF-8");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + readmeFilename + "\"; filename*=UTF-8''" + URLEncoder.encode(readmeFilename, StandardCharsets.UTF_8));
            String guide = buildInstallGuide(artifact.platform, artifact.version, ext);
            response.getOutputStream().write(guide.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private void setDownloadHeaders(HttpServletResponse response, String filename, String contentType) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, max-age=0, must-revalidate");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setHeader(HttpHeaders.EXPIRES, "0");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8));
        response.setContentType(contentType);
    }

    private String buildInstallGuide(String platform, String version, String extension) {
        return "===== Aegis AI 客户端安装指南 =====\n\n"
                + "版本: " + version + "\n"
                + "平台: " + switch (platform) {
                    case "windows" -> "WINDOWS";
                    case "macos" -> "MACOS";
                    case "linux" -> "LINUX";
                    default -> platform.toUpperCase();
                } + "\n\n"
                + "【从源码构建安装包】\n"
                + "1. 确保已安装 Node.js 18+ 和 npm\n"
                + "2. 进入项目 electron 目录:\n"
                + "   cd electron\n"
                + "3. 安装依赖:\n"
                + "   npm install\n"
                + "4. 构建对应平台安装包:\n"
                + switch (platform) {
                    case "windows" -> "   npm run build:win\n   构建产物在 electron/dist/ 目录\n";
                    case "macos" -> "   npm run build:mac\n   构建产物在 electron/dist/ 目录\n";
                    default    -> "   npm run build:linux\n   构建产物在 electron/dist/ 目录\n";
                }
                + "\n【已构建包安装步骤】\n"
                + switch (extension) {
                    case "exe" -> "双击 .exe 文件，按向导完成安装。\n安装后在系统托盘中右键 → 服务器设置，填入 AegisAI 服务端地址。\n";
                    case "dmg" -> "打开 .dmg，将 Aegis 拖入 Applications。\n首次运行右键 → 打开，在托盘菜单中配置服务器地址。\n";
                    default    -> "执行: sudo dpkg -i aegis-client_" + version + "_amd64." + extension + "\n或:   sudo rpm -i aegis-client-" + version + ".x86_64.rpm\n启动: aegis-client --server http://<服务端IP>:8080\n";
                }
                + "\n【更多信息】\n"
                + "文档: https://github.com/SERIOUSBEAST69/AegisAI/blob/main/electron/README.md\n"
                + "问题反馈: https://github.com/SERIOUSBEAST69/AegisAI/issues\n";
    }

    private static final class PackageArtifact {
        private final String filename;
        private final String version;
        private final Resource resource;
        private final String platform;
        private final String extension;
        private final int sourceRank;

        private PackageArtifact(String filename, String version, Resource resource, String platform) {
            this.filename = filename;
            this.version = version;
            this.resource = resource;
            this.platform = platform;
            int dot = filename.lastIndexOf('.');
            this.extension = dot >= 0 ? filename.substring(dot + 1) : "bin";
            this.sourceRank = resource instanceof FileSystemResource ? 2 : 1;
        }
    }
}
