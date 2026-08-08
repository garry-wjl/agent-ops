# 运行镜像：阿里云 ACR 同步的 eclipse-temurin JRE 17
FROM registry.cn-shenzhen.aliyuncs.com/ys-common/eclipse-temurin-17-jre-jammy:17-jre-jammy

ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 多模块项目：fat jar 在 adapter 子模块的 target 下
# 默认 JAR_FILE = rd-agent-be-adapter/target/app.jar（与 adapter pom <finalName>app</finalName> 一致）
ARG JAR_FILE=rd-agent-be-adapter/target/app.jar
COPY ${JAR_FILE} /app/app.jar

WORKDIR /app
RUN mkdir -p /app/heapdump /app/logs \
    && chmod a+r /app/app.jar

VOLUME /app/heapdump

# 配置来自 jar 内 application*.yml；profile 由 SPRING_PROFILES_ACTIVE 注入。
# 运行时可由 JAVA_TOOL_OPTIONS 追加 JVM 参数。
ENTRYPOINT ["java", \
    "--add-opens=java.base/java.lang=ALL-UNNAMED", \
    "-Duser.timezone=Asia/Shanghai", \
    "-Dfile.encoding=UTF-8", \
    "-Dclient.encoding.override=UTF-8", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-XX:InitialRAMPercentage=40.0", \
    "-XX:MinRAMPercentage=40.0", \
    "-XX:MaxRAMPercentage=80.0", \
    "-XX:MetaspaceSize=128m", \
    "-XX:MaxMetaspaceSize=256m", \
    "-XX:+UseG1GC", \
    "-XX:MaxGCPauseMillis=200", \
    "-XX:+ExplicitGCInvokesConcurrent", \
    "-XX:-OmitStackTraceInFastThrow", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/app/heapdump/heapdump.hprof", \
    "-Xlog:gc*:file=/app/logs/gc.log:time,uptime,level,tags", \
    "-jar", "/app/app.jar"]
