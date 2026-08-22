package ink.garry.rd.agent.ws.application.auth.command;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.application.auth.LoginSecurityProperties;
import ink.garry.rd.agent.ws.client.auth.captcha.vo.SliderCaptchaVO;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.constant.RedisKeyConstant;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 自研滑块验证码：JDK 绘背景与拼图缺口，正确 X 存 Redis，登录时校验。
 */
@Service
public class SliderCaptchaService {

    private static final int BG_WIDTH = 300;
    private static final int BG_HEIGHT = 150;
    private static final int SLIDER_SIZE = 44;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private LoginSecurityProperties loginSecurityProperties;

    /**
     * 生成滑块挑战并写入 Redis。
     *
     * @return 前端展示 VO
     */
    public SliderCaptchaVO createChallenge() {
        Random rnd = ThreadLocalRandom.current();
        int sliderY = 12 + rnd.nextInt(BG_HEIGHT - SLIDER_SIZE - 24);
        int correctX = 40 + rnd.nextInt(BG_WIDTH - SLIDER_SIZE - 50);

        BufferedImage background = new BufferedImage(BG_WIDTH, BG_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = background.createGraphics();
        try {
            paintBackground(g, rnd);
        } finally {
            g.dispose();
        }

        Area puzzle = puzzleShape(0, 0);
        BufferedImage slider = new BufferedImage(SLIDER_SIZE, SLIDER_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = slider.createGraphics();
        try {
            sg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // 从背景抠出拼图
            for (int x = 0; x < SLIDER_SIZE; x++) {
                for (int y = 0; y < SLIDER_SIZE; y++) {
                    if (puzzle.contains(x, y)) {
                        int rgb = background.getRGB(correctX + x, sliderY + y);
                        slider.setRGB(x, y, rgb);
                    }
                }
            }
            sg.setStroke(new BasicStroke(1.5f));
            sg.setColor(new Color(255, 255, 255, 220));
            sg.draw(puzzle);
        } finally {
            sg.dispose();
        }

        // 背景挖空
        Graphics2D bg = background.createGraphics();
        try {
            bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Area hole = puzzleShape(correctX, sliderY);
            bg.setColor(new Color(0, 0, 0, 90));
            bg.fill(hole);
            bg.setStroke(new BasicStroke(1.2f));
            bg.setColor(new Color(255, 255, 255, 160));
            bg.draw(hole);
        } finally {
            bg.dispose();
        }

        String captchaId = IdUtil.fastSimpleUUID();
        stringRedisTemplate.opsForValue().set(
                RedisKeyConstant.AUTH_CAPTCHA_PREFIX + captchaId,
                Integer.toString(correctX),
                Math.max(30L, loginSecurityProperties.getCaptchaTtlSeconds()),
                java.util.concurrent.TimeUnit.SECONDS);

        SliderCaptchaVO vo = new SliderCaptchaVO();
        vo.setCaptchaId(captchaId);
        vo.setBackgroundImage(toDataUrl(background));
        vo.setSliderImage(toDataUrl(slider));
        vo.setSliderY(sliderY);
        return vo;
    }

    /**
     * 校验滑块 X，成功后销毁会话（一次性）。
     *
     * @param captchaId 挑战 ID
     * @param slideX    用户拖动到的 X
     */
    public void verifyOrThrow(String captchaId, Integer slideX) {
        if (StrUtil.isBlank(captchaId) || slideX == null) {
            throw new BusinessException(BizCode.LOGIN_CAPTCHA_REQUIRED.getCode(),
                    BizCode.LOGIN_CAPTCHA_REQUIRED.getMessage());
        }
        String key = RedisKeyConstant.AUTH_CAPTCHA_PREFIX + captchaId.trim();
        String raw = stringRedisTemplate.opsForValue().get(key);
        stringRedisTemplate.delete(key);
        if (StrUtil.isBlank(raw)) {
            throw new BusinessException(BizCode.LOGIN_CAPTCHA_INVALID.getCode(),
                    BizCode.LOGIN_CAPTCHA_INVALID.getMessage());
        }
        int correctX;
        try {
            correctX = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(BizCode.LOGIN_CAPTCHA_INVALID.getCode(),
                    BizCode.LOGIN_CAPTCHA_INVALID.getMessage());
        }
        int tolerance = Math.max(1, loginSecurityProperties.getCaptchaTolerancePx());
        if (Math.abs(slideX - correctX) > tolerance) {
            throw new BusinessException(BizCode.LOGIN_CAPTCHA_INVALID.getCode(),
                    BizCode.LOGIN_CAPTCHA_INVALID.getMessage());
        }
    }

    private static void paintBackground(Graphics2D g, Random rnd) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color c1 = new Color(30 + rnd.nextInt(40), 70 + rnd.nextInt(50), 160 + rnd.nextInt(60));
        Color c2 = new Color(20 + rnd.nextInt(30), 40 + rnd.nextInt(40), 100 + rnd.nextInt(50));
        for (int y = 0; y < BG_HEIGHT; y++) {
            float t = y / (float) BG_HEIGHT;
            int r = (int) (c1.getRed() * (1 - t) + c2.getRed() * t);
            int gg = (int) (c1.getGreen() * (1 - t) + c2.getGreen() * t);
            int b = (int) (c1.getBlue() * (1 - t) + c2.getBlue() * t);
            g.setColor(new Color(r, gg, b));
            g.drawLine(0, y, BG_WIDTH, y);
        }
        for (int i = 0; i < 28; i++) {
            g.setColor(new Color(255, 255, 255, 20 + rnd.nextInt(50)));
            int x = rnd.nextInt(BG_WIDTH);
            int y = rnd.nextInt(BG_HEIGHT);
            int w = 20 + rnd.nextInt(80);
            g.fillOval(x, y, w, w / 2);
        }
        for (int i = 0; i < 120; i++) {
            g.setColor(new Color(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256), 40));
            g.fillRect(rnd.nextInt(BG_WIDTH), rnd.nextInt(BG_HEIGHT), 2, 2);
        }
    }

    private static Area puzzleShape(int offsetX, int offsetY) {
        Area area = new Area(new RoundRectangle2D.Float(offsetX + 2, offsetY + 8, 30, 34, 6, 6));
        area.add(new Area(new Ellipse2D.Float(offsetX + 26, offsetY + 16, 16, 16)));
        area.subtract(new Area(new Ellipse2D.Float(offsetX + 8, offsetY - 2, 14, 14)));
        return area;
    }

    private static String toDataUrl(BufferedImage image) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            return "data:image/png;base64," + b64;
        } catch (IOException e) {
            throw new BusinessException(BizCode.SYSTEM_BUSY.getCode(), "生成验证码失败");
        }
    }
}
