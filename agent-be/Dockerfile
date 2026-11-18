# 运行镜像：阿里云 ACR 同步的 eclipse-temurin JRE 17
FROM registry.cn-shenzhen.aliyuncs.com/ys-common/eclipse-temurin-17-jre-jammy:17-jre-jammy

ENV TZ=Asia/Shanghai

# 多模块项目：fat jar 在 adapter 子模块的 target 下
# 默认 JAR_FILE = rd-agent-be-adapter/target/app.jar（与 adapter pom <finalName>app</finalName> 一致）
ARG JAR_FILE=rd-agent-be-adapter/target/app.jar
COPY ${JAR_FILE} /app/app.jar

COPY docker/jvm-entrypoint.sh /app/jvm-entrypoint.sh

WORKDIR /app
RUN mkdir -p /app/heapdump /app/logs \
    && chmod a+r /app/app.jar \
    && chmod a+rx /app/jvm-entrypoint.sh

VOLUME /app/heapdump

# 配置来自 jar 内 application*.yml；profile 由 SPRING_PROFILES_ACTIVE 注入。
# 堆 / 元空间 / 直接内存按容器内存限额的百分比设置，见 docker/jvm-entrypoint.sh。
# 运行时可由 JAVA_TOOL_OPTIONS 追加 JVM 参数。
ENTRYPOINT ["/app/jvm-entrypoint.sh"]
