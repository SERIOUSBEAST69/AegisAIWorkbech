# 单阶段构建，使用稳定的 Maven 镜像版本
FROM docker.m.daocloud.io/library/maven:3.9.11-eclipse-temurin-17
WORKDIR /app

# 先复制 pom.xml 预热依赖层，减少后续改代码时的重复下载
COPY backend/pom.xml ./backend/pom.xml
RUN mvn -f backend/pom.xml dependency:go-offline -DskipTests \
    -Dmaven.wagon.http.ssl.insecure=true \
    -Dmaven.wagon.http.ssl.allowall=true \
    -Dmaven.resolver.transport=wagon

# 再复制全部后端源代码
COPY backend ./backend

# 打包（跳过测试，禁用 SSL 验证）
RUN mvn -f backend/pom.xml clean package -DskipTests \
    -Dmaven.wagon.http.ssl.insecure=true \
    -Dmaven.wagon.http.ssl.allowall=true \
    -Dmaven.resolver.transport=wagon

EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/backend/target/*.jar"]
