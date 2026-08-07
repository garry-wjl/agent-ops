package ink.garry.rd.agent.ws.infra.agent.a2a;

import io.a2a.client.http.A2AHttpClient;
import io.a2a.client.http.A2AHttpResponse;
import io.a2a.common.A2AErrorMessages;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_MULT_CHOICE;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;

/**
 * 强制使用 HTTP/1.1 的 A2A HTTP Client。
 * <p>
 * 该类是 {@link io.a2a.client.http.JdkA2AHttpClient}(a2a-java-sdk 0.3.3.Final) 的拷贝,
 * 唯一区别:把 {@link HttpClient.Version#HTTP_2} 替换为 {@link HttpClient.Version#HTTP_1_1}。
 * <p>
 * <b>为什么需要这个 workaround</b>:
 * <ul>
 *   <li>JDK 25 的 {@link HttpClient} 在 {@code Version.HTTP_2} 模式下,会对明文 HTTP(非 TLS) POST
 *       自动追加 {@code Connection: Upgrade, HTTP2-Settings} + {@code Upgrade: h2c} 头,
 *       触发 RFC 7540 §3.2 定义的 HTTP/2 cleartext upgrade 流程。</li>
 *   <li>本地联调的 A2A 远端是 Python uvicorn(claude-agent-sdk-a2a),其 HTTP/1 parser 对带 Upgrade
 *       头的 POST 处理异常:**响应 200 OK,但 body 被吞**,转而返回
 *       {@code {"error":{"code":-32700,"message":"Expecting value: line 1 column 1 (char 0)"}}}。</li>
 *   <li>这个空响应在 a2a-java-sdk 的 {@code SSEEventListener.onComplete()} 里被翻译成
 *       {@code errorHandler.accept(null)},在 agentscope 的 {@code A2aAgent.doExecute} 里又被
 *       绑定成 {@code MonoSink.error(null)} → worker 线程抛 NPE,Mono sink 永不 resolve,
 *       调用方挂死直到上游超时。</li>
 * </ul>
 * <p>
 * 通过强制 HTTP/1.1,JDK HttpClient 就不再附加任何 Upgrade 头,uvicorn 正常按 HTTP/1.1 处理 body,
 * 链路恢复正常。
 * <p>
 * <b>注入方式</b>:见 application 层 {@code AgentRunnerFactory} 中 A2A 分支构造 {@code A2aAgentConfig} 的代码。
 * <p>
 * <b>移除时机</b>:
 * <ol>
 *   <li>a2a-java-sdk 升级后开放 {@code HttpClient} 注入或暴露 {@code version()} 配置项;或</li>
 *   <li>远端 uvicorn/h11 修复带 Upgrade 头的 POST body 处理;或</li>
 *   <li>切换到 TLS(HTTPS),h2c upgrade 不再发生。</li>
 * </ol>
 */
public class Http1JdkA2AHttpClient implements A2AHttpClient {

    /** 底层 JDK HttpClient,固定 HTTP/1.1,避免 h2c upgrade。 */
    private final HttpClient httpClient;

    /**
     * 构造一个强制 HTTP/1.1 的 A2A HTTP Client,跟 {@code JdkA2AHttpClient} 配置保持一致
     * (除了协议版本)。
     */
    public Http1JdkA2AHttpClient() {
        httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * 创建 GET 请求构造器。
     *
     * @return GET builder
     */
    @Override
    public GetBuilder createGet() {
        return new JdkGetBuilder();
    }

    /**
     * 创建 POST 请求构造器。
     *
     * @return POST builder
     */
    @Override
    public PostBuilder createPost() {
        return new JdkPostBuilder();
    }

    /**
     * 创建 DELETE 请求构造器。
     *
     * @return DELETE builder
     */
    @Override
    public DeleteBuilder createDelete() {
        return new JdkDeleteBuilder();
    }

    /**
     * GET/POST/DELETE 三个 builder 的共同父类,管理 URL 和请求头。
     *
     * @param <T> 子类型(用于 fluent API)
     */
    private abstract class JdkBuilder<T extends Builder<T>> implements Builder<T> {
        /** 目标 URL。 */
        private String url = "";
        /** 自定义请求头。 */
        private Map<String, String> headers = new HashMap<>();

        /**
         * 设置目标 URL。
         *
         * @param url 完整 URL
         * @return 当前 builder
         */
        @Override
        public T url(String url) {
            this.url = url;
            return self();
        }

        /**
         * 追加单个请求头。
         *
         * @param name  header 名
         * @param value header 值
         * @return 当前 builder
         */
        @Override
        public T addHeader(String name, String value) {
            headers.put(name, value);
            return self();
        }

        /**
         * 批量追加请求头。
         *
         * @param headers header 映射(null 或空时跳过)
         * @return 当前 builder
         */
        @Override
        public T addHeaders(Map<String, String> headers) {
            if (headers != null && !headers.isEmpty()) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    addHeader(entry.getKey(), entry.getValue());
                }
            }
            return self();
        }

        /**
         * 返回 this 的子类型引用,用于 fluent API 链式调用。
         *
         * @return this(已强转为 T)
         */
        @SuppressWarnings("unchecked")
        T self() {
            return (T) this;
        }

        /**
         * 拼装 HttpRequest.Builder,带上 URL 和所有自定义 header。
         *
         * @return HttpRequest builder
         * @throws IOException 创建失败
         */
        protected HttpRequest.Builder createRequestBuilder() throws IOException {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url));
            for (Map.Entry<String, String> headerEntry : headers.entrySet()) {
                builder.header(headerEntry.getKey(), headerEntry.getValue());
            }
            return builder;
        }

        /**
         * 异步发起 SSE 请求,逐行处理 server-sent events。
         *
         * @param request          预构造好的 HttpRequest(GET 或 POST)
         * @param messageConsumer  事件消费回调(每行 "data: ..." 调一次)
         * @param errorConsumer    错误回调
         * @param completeRunnable 正常完成回调
         * @return CompletableFuture(完成时整个流处理结束)
         */
        protected CompletableFuture<Void> asyncRequest(
                HttpRequest request,
                Consumer<String> messageConsumer,
                Consumer<Throwable> errorConsumer,
                Runnable completeRunnable
        ) {
            Flow.Subscriber<String> subscriber = new Flow.Subscriber<String>() {
                private Flow.Subscription subscription;
                private volatile boolean errorRaised = false;

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    this.subscription = subscription;
                    this.subscription.request(1);
                }

                @Override
                public void onNext(String item) {
                    // SSE messages sometimes start with "data:". Strip that off
                    if (item != null && item.startsWith("data:")) {
                        item = item.substring(5).trim();
                        if (!item.isEmpty()) {
                            messageConsumer.accept(item);
                        }
                    }
                    if (subscription != null) {
                        subscription.request(1);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    if (!errorRaised) {
                        errorRaised = true;
                        errorConsumer.accept(throwable);
                    }
                    if (subscription != null) {
                        subscription.cancel();
                    }
                }

                @Override
                public void onComplete() {
                    if (!errorRaised) {
                        completeRunnable.run();
                    }
                    if (subscription != null) {
                        subscription.cancel();
                    }
                }
            };

            // Create a custom body handler that checks status before processing body
            BodyHandler<Void> bodyHandler = responseInfo -> {
                // Check for authentication/authorization errors only
                if (responseInfo.statusCode() == HTTP_UNAUTHORIZED || responseInfo.statusCode() == HTTP_FORBIDDEN) {
                    final String errorMessage;
                    if (responseInfo.statusCode() == HTTP_UNAUTHORIZED) {
                        errorMessage = A2AErrorMessages.AUTHENTICATION_FAILED;
                    } else {
                        errorMessage = A2AErrorMessages.AUTHORIZATION_FAILED;
                    }
                    // Return a body subscriber that immediately signals error
                    return BodySubscribers.fromSubscriber(new Flow.Subscriber<List<ByteBuffer>>() {
                        @Override
                        public void onSubscribe(Flow.Subscription subscription) {
                            subscriber.onError(new IOException(errorMessage));
                        }

                        @Override
                        public void onNext(List<ByteBuffer> item) {
                            // Should not be called
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            // Should not be called
                        }

                        @Override
                        public void onComplete() {
                            // Should not be called
                        }
                    });
                } else {
                    // For all other status codes (including other errors), proceed with normal line subscriber
                    return BodyHandlers.fromLineSubscriber(subscriber).apply(responseInfo);
                }
            };

            // Send the response async, and let the subscriber handle the lines.
            return httpClient.sendAsync(request, bodyHandler)
                    .thenAccept(response -> {
                        // Handle non-authentication/non-authorization errors here
                        if (!isSuccessStatus(response.statusCode())
                                && response.statusCode() != HTTP_UNAUTHORIZED
                                && response.statusCode() != HTTP_FORBIDDEN) {
                            subscriber.onError(new IOException(
                                    "Request failed with status " + response.statusCode() + ":" + response.body()));
                        }
                    });
        }
    }

    /**
     * GET builder 实现。
     */
    private class JdkGetBuilder extends JdkBuilder<GetBuilder> implements A2AHttpClient.GetBuilder {

        /**
         * 构造 GET 请求,若 SSE=true 则带上 {@code Accept: text/event-stream}。
         *
         * @param SSE 是否走 SSE 协议
         * @return HttpRequest builder
         * @throws IOException 创建失败
         */
        private HttpRequest.Builder createRequestBuilder(boolean SSE) throws IOException {
            HttpRequest.Builder builder = super.createRequestBuilder().GET();
            if (SSE) {
                builder.header("Accept", "text/event-stream");
            }
            return builder;
        }

        /**
         * 同步 GET。
         *
         * @return A2A HTTP 响应
         * @throws IOException          网络异常
         * @throws InterruptedException 中断
         */
        @Override
        public A2AHttpResponse get() throws IOException, InterruptedException {
            HttpRequest request = createRequestBuilder(false)
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new JdkHttpResponse(response);
        }

        /**
         * 异步 GET + SSE 订阅。
         *
         * @param messageConsumer  事件回调
         * @param errorConsumer    错误回调
         * @param completeRunnable 完成回调
         * @return 异步 future
         * @throws IOException          网络异常
         * @throws InterruptedException 中断
         */
        @Override
        public CompletableFuture<Void> getAsyncSSE(
                Consumer<String> messageConsumer,
                Consumer<Throwable> errorConsumer,
                Runnable completeRunnable) throws IOException, InterruptedException {
            HttpRequest request = createRequestBuilder(true)
                    .build();
            return super.asyncRequest(request, messageConsumer, errorConsumer, completeRunnable);
        }

    }

    /**
     * DELETE builder 实现。
     */
    private class JdkDeleteBuilder extends JdkBuilder<DeleteBuilder> implements A2AHttpClient.DeleteBuilder {

        /**
         * 同步 DELETE。
         *
         * @return A2A HTTP 响应
         * @throws IOException          网络异常
         * @throws InterruptedException 中断
         */
        @Override
        public A2AHttpResponse delete() throws IOException, InterruptedException {
            HttpRequest request = super.createRequestBuilder().DELETE().build();
            HttpResponse<String> response =
                    httpClient.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new JdkHttpResponse(response);
        }

    }

    /**
     * POST builder 实现。
     */
    private class JdkPostBuilder extends JdkBuilder<PostBuilder> implements A2AHttpClient.PostBuilder {
        /** POST body(字符串)。 */
        String body = "";

        /**
         * 设置 POST body。
         *
         * @param body body 字符串
         * @return 当前 builder
         */
        @Override
        public PostBuilder body(String body) {
            this.body = body;
            return self();
        }

        /**
         * 构造 POST 请求,若 SSE=true 则带上 {@code Accept: text/event-stream}。
         *
         * @param SSE 是否走 SSE 协议
         * @return HttpRequest builder
         * @throws IOException 创建失败
         */
        private HttpRequest.Builder createRequestBuilder(boolean SSE) throws IOException {
            HttpRequest.Builder builder = super.createRequestBuilder()
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (SSE) {
                builder.header("Accept", "text/event-stream");
            }
            return builder;
        }

        /**
         * 同步 POST。
         *
         * @return A2A HTTP 响应
         * @throws IOException          401/403 或网络异常
         * @throws InterruptedException 中断
         */
        @Override
        public A2AHttpResponse post() throws IOException, InterruptedException {
            HttpRequest request = createRequestBuilder(false)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() == HTTP_UNAUTHORIZED) {
                throw new IOException(A2AErrorMessages.AUTHENTICATION_FAILED);
            } else if (response.statusCode() == HTTP_FORBIDDEN) {
                throw new IOException(A2AErrorMessages.AUTHORIZATION_FAILED);
            }

            return new JdkHttpResponse(response);
        }

        /**
         * 异步 POST + SSE 订阅(A2A {@code message/stream} 走这条)。
         *
         * @param messageConsumer  事件回调
         * @param errorConsumer    错误回调
         * @param completeRunnable 完成回调
         * @return 异步 future
         * @throws IOException          网络异常
         * @throws InterruptedException 中断
         */
        @Override
        public CompletableFuture<Void> postAsyncSSE(
                Consumer<String> messageConsumer,
                Consumer<Throwable> errorConsumer,
                Runnable completeRunnable) throws IOException, InterruptedException {
            HttpRequest request = createRequestBuilder(true)
                    .build();
            return super.asyncRequest(request, messageConsumer, errorConsumer, completeRunnable);
        }
    }

    /**
     * JDK HttpResponse 到 A2AHttpResponse 的适配器。
     *
     * @param response 底层 JDK 响应
     */
    private record JdkHttpResponse(HttpResponse<String> response) implements A2AHttpResponse {

        /**
         * HTTP 状态码。
         *
         * @return status code
         */
        @Override
        public int status() {
            return response.statusCode();
        }

        /**
         * 是否 2xx。
         *
         * @return true 表示成功
         */
        @Override
        public boolean success() {
            return success(response);
        }

        /**
         * 静态工具:判断 HttpResponse 是否 2xx。
         *
         * @param response HttpResponse
         * @return true 表示 2xx
         */
        static boolean success(HttpResponse<?> response) {
            return response.statusCode() >= HTTP_OK && response.statusCode() < HTTP_MULT_CHOICE;
        }

        /**
         * 响应 body。
         *
         * @return body 字符串
         */
        @Override
        public String body() {
            return response.body();
        }
    }

    /**
     * 判断状态码是否 2xx。
     *
     * @param statusCode HTTP 状态码
     * @return true 表示 2xx
     */
    private static boolean isSuccessStatus(int statusCode) {
        return statusCode >= HTTP_OK && statusCode < HTTP_MULT_CHOICE;
    }
}
