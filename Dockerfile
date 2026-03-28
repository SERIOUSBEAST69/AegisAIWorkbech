# 单阶段构建，使用公开 Maven 镜像
FROM maven:3.9-eclipse-temurin-17
WORKDIR /app

# 复制全部源代码
COPY backend ./backend

# 打包（跳过测试，禁用 SSL 验证）
RUN mvn -f backend/pom.xml clean package -DskipTests \
    -Dmaven.wagon.http.ssl.insecure=true \
    -Dmaven.wagon.http.ssl.allowall=true \
    -Dmaven.resolver.transport=wagon

EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/backend/target/*.jar"]
