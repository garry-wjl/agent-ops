package ink.garry.rd.agent.ws.infra.common.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP 请求头读取工具。
 * <p>
 * 从 Spring {@link RequestContextHolder} 当前线程绑定的请求中读取请求头。<b>全程容错</b>：
 * 无 web 请求上下文(异步线程 / 非请求线程 / 容器外调用)或读取过程抛异常时,一律返回空结果 /
 * {@code null},<b>绝不抛错</b>,避免读取上下文这种旁路信息影响主流程。
 * <p>
 * 注意:RequestContextHolder 基于 ThreadLocal,默认不随线程切换传播;在 reactor / 线程池等异步
 * 线程中调用通常取不到请求(返回空),需要的话由调用方在切线程前先取好再传递。
 */
@Slf4j
public final class HttpHeaderUtil {

    private HttpHeaderUtil() {
    }

    /**
     * 读取当前 HTTP 请求的全部请求头,组装为 name → value 的有序 Map(保留原始头名与出现顺序)。
     * <p>无请求上下文或读取异常时返回<b>空 Map</b>,不抛错。
     *
     * @return 请求头 Map(可能为空,但不为 {@code null})
     */
    public static Map<String, String> getHeaderMap() {
        Map<String, String> headers = new LinkedHashMap<>();
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return headers;
        }
        try {
            Enumeration<String> names = request.getHeaderNames();
            if (names == null) {
                return headers;
            }
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                headers.put(name, request.getHeader(name));
            }
        } catch (Exception e) {
            log.debug("读取请求头失败,返回已收集部分", e);
        }
        return headers;
    }

    /**
     * 读取当前 HTTP 请求的指定请求头。
     * <p>无请求上下文、头不存在或读取异常时返回 {@code null},不抛错。
     *
     * @param name 请求头名(大小写不敏感,遵循 servlet 语义)
     * @return 头值;取不到返回 {@code null}
     */
    public static String getHeader(String name) {
        if (name == null) {
            return null;
        }
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        try {
            return request.getHeader(name);
        } catch (Exception e) {
            log.debug("读取请求头 {} 失败", name, e);
            return null;
        }
    }

    /** 取当前线程绑定的 servlet 请求;无 web 上下文返回 {@code null}(吞掉一切异常)。 */
    private static HttpServletRequest currentRequest() {
        try {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletAttributes) {
                return servletAttributes.getRequest();
            }
        } catch (Exception e) {
            log.debug("获取当前请求上下文失败", e);
        }
        return null;
    }
}
