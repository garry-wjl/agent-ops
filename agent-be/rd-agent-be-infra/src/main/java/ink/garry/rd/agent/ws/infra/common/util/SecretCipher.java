package ink.garry.rd.agent.ws.infra.common.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.crypto.symmetric.AES;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 秘钥加解密 / 哈希工具（跨领域，放 infra/common/util）。
 * <p>
 * 统一承载"加密存储（绝不存明文）"能力，供 Agent 对外调用秘钥（{@code AgentApiKey}）与
 * 模型秘钥（{@code ConfigSnapshot.modelApiKey}）共用：
 * <ul>
 *   <li>{@link #encrypt(String)} / {@link #decrypt(String)}：AES 可逆加解密，用于 {@code key_cipher}
 *       与 {@code modelApiKey} 的密文存读（小眼睛 reveal 解密、运行时调 LLM 前解密）；</li>
 *   <li>{@link #sha256(String)}：不可逆 SHA-256，用于秘钥认证比对的 {@code key_hash}；</li>
 *   <li>{@link #randomRawKey()}：生成 {@code ak-} + 32 字节 base62 随机明文（SecureRandom）。</li>
 * </ul>
 * <b>密钥来源</b>：AES 密钥经配置中心 / 环境变量 {@code app.secret.cipher-key} 下发，不硬编码、不入库；
 * 轮换密钥需配套迁移 {@code key_cipher} 与 {@code modelApiKey} 密文。
 */
@Component
public class SecretCipher {

    /** 对外调用秘钥明文前缀（PRD §2.1.3） */
    public static final String RAW_KEY_PREFIX = "ak-";

    /** base62 字符集，用于随机明文生成（无特殊字符，便于复制粘贴） */
    private static final char[] BASE62 =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    /** 随机明文字节数（不含前缀），32 字节 → 足够熵 */
    private static final int RAW_KEY_BYTES = 32;

    /** 线程安全的安全随机源 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** AES 对称加解密器；由配置下发的 cipher-key 派生固定密钥构造 */
    private final AES aes;

    /**
     * 构造加解密器。
     * <p>
     * 将配置下发的 cipher-key（任意长度字符串）经 SHA-256 派生为 32 字节（AES-256）密钥，
     * 避免对 cipher-key 原始长度做强约束。
     *
     * @param cipherKey 配置中心 / 环境变量下发的密钥种子（{@code app.secret.cipher-key}）
     */
    public SecretCipher(@Value("${app.secret.cipher-key:dev-only-secret-cipher-key-please-change}") String cipherKey) {
        // 用 SHA-256 把任意长度的 cipherKey 规整为 32 字节 AES-256 密钥
        byte[] keyBytes = DigestUtil.sha256(cipherKey.getBytes(StandardCharsets.UTF_8));
        this.aes = SecureUtil.aes(keyBytes);
    }

    /**
     * AES 加密为 Base64 密文。
     *
     * @param plain 明文；为空时原样返回（不加密空串，避免无意义密文）
     * @return Base64 密文；plain 为空返回 plain 本身
     */
    public String encrypt(String plain) {
        if (StrUtil.isEmpty(plain)) {
            return plain;
        }
        return aes.encryptBase64(plain);
    }

    /**
     * AES 解密 Base64 密文为明文。
     *
     * @param cipher Base64 密文；为空时原样返回
     * @return 明文；cipher 为空返回 cipher 本身
     */
    public String decrypt(String cipher) {
        if (StrUtil.isEmpty(cipher)) {
            return cipher;
        }
        return aes.decryptStr(cipher);
    }

    /**
     * 计算明文的 SHA-256 十六进制哈希（不可逆），用于秘钥认证比对。
     *
     * @param raw 明文
     * @return 64 位十六进制小写 SHA-256
     */
    public String sha256(String raw) {
        return DigestUtil.sha256Hex(raw);
    }

    /**
     * 生成对外调用秘钥明文：{@code ak-} + 32 字节 base62 随机串（SecureRandom）。
     * <p>
     * 仅在创建当次内存使用，绝不持久化明文。
     *
     * @return 形如 {@code ak-xxxxxxxx...} 的随机明文
     */
    public String randomRawKey() {
        StringBuilder sb = new StringBuilder(RAW_KEY_PREFIX);
        byte[] bytes = new byte[RAW_KEY_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        for (byte b : bytes) {
            sb.append(BASE62[(b & 0xFF) % BASE62.length]);
        }
        return sb.toString();
    }

    /**
     * 计算掩码前缀：取明文前 8 位（不含 {@code ****} 后缀），存入 {@code key_prefix} 列。
     * <p>
     * 列表展示串由上层拼成 {@code key_prefix + ****}（见 AgentApiKeyQueryService）；
     * 本方法只负责截取前缀本身，避免后缀重复。
     *
     * @param rawKey 秘钥明文
     * @return 明文前 8 位（如 {@code ak-7Qb3}）；rawKey 为空原样返回
     */
    public String maskPrefix(String rawKey) {
        if (StrUtil.isEmpty(rawKey)) {
            return rawKey;
        }
        int end = Math.min(8, rawKey.length());
        return rawKey.substring(0, end);
    }

    /** Base64 编码（保留给调用方按需使用，统一编码口径）。 */
    public static String base64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }
}
