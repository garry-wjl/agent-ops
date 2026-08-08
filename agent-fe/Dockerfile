# 前端静态站：阿里云 ACR nginx
FROM registry.cn-shenzhen.aliyuncs.com/ys-common/nginx:latest

COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY dist/ /usr/share/nginx/html/

EXPOSE 80
