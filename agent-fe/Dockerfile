# 前端静态站：阿里云 ACR nginx
FROM registry.cn-shenzhen.aliyuncs.com/ys-common/nginx:latest

# 默认反代目标（K8s 可用环境变量覆盖）
ENV BACKEND_HOST=rd-agent-be \
    BACKEND_PORT=8080

# 官方入口 20-envsubst-on-templates.sh：*.template → /etc/nginx/conf.d/
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY dist/ /usr/share/nginx/html/

EXPOSE 80
