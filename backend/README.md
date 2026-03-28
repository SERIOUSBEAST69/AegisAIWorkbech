# TrustAI 后端项目

基于 Spring Boot 2.7.x + Java 11 + MyBatis-Plus + Spring Security + JWT + Redis + Elasticsearch

## 启动

1. 配置数据库（MySQL 8.0）和 Redis
2. 修改 `src/main/resources/application.yml` 数据库连接信息
3. 执行数据库建表 SQL（见 backend/src/main/resources/db.sql）
4. 启动后若表为空，会自动创建默认账号：用户名 `admin`，密码 `admin`
5. 编译并启动：

```bash
# Windows（PowerShell）示例，避免 -D 参数被误解析
mvn "-DskipTests=true" "-Denforcer.skip=true" clean package
java -jar target/AegisAI-backend-0.1.0.jar

# 若需直接运行
mvn "-DskipTests=true" "-Denforcer.skip=true" spring-boot:run
```

## 目录结构
- controller/  控制器
- service/     业务逻辑
- mapper/      MyBatis-Plus 持久层
- entity/      实体类
- dto/         数据传输对象
- config/      配置类
- utils/       工具类
- aspect/      切面
- exception/   全局异常

## 说明
- Swagger 文档：/swagger-ui.html
- 默认端口：8080
