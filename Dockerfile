# =========================
# 第一阶段：编译 Spring Boot
# =========================

FROM eclipse-temurin:21-jdk AS build

WORKDIR /app


# 复制项目文件
COPY . .


# 使用 Maven 编译
RUN ./mvnw clean package -DskipTests



# =========================
# 第二阶段：运行程序
# =========================

FROM eclipse-temurin:21-jre

WORKDIR /app


# 复制编译后的 jar
COPY --from=build /app/target/*.jar app.jar


# Spring Boot端口
EXPOSE 8080


# 启动
ENTRYPOINT ["java","-jar","app.jar"]