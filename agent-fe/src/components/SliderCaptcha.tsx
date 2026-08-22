/**
 * 登录页滑块验证码 — 拖动拼图对齐缺口。
 */
import { ReloadOutlined } from '@ant-design/icons';
import { Button, Spin } from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';
import { authCaptchaApi, type SliderCaptchaVO } from '@/services/auth/captcha';

const TRACK_WIDTH = 300;
const SLIDER_SIZE = 44;

export type SliderCaptchaValue = {
  captchaId: string;
  slideX: number;
};

type Props = {
  /** 校验结果变化；未完成时为 null */
  onChange?: (value: SliderCaptchaValue | null) => void;
  /** 外部触发刷新（验证失败后） */
  refreshKey?: number;
};

export default function SliderCaptcha({ onChange, refreshKey = 0 }: Props) {
  const [challenge, setChallenge] = useState<SliderCaptchaVO | null>(null);
  const [loading, setLoading] = useState(false);
  const [offsetX, setOffsetX] = useState(0);
  const [dragging, setDragging] = useState(false);
  const [passed, setPassed] = useState(false);
  const startXRef = useRef(0);
  const originOffsetRef = useRef(0);

  const load = useCallback(async () => {
    setLoading(true);
    setPassed(false);
    setOffsetX(0);
    onChange?.(null);
    try {
      const data = await authCaptchaApi.fetchSlider();
      setChallenge(data);
    } catch {
      setChallenge(null);
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 仅 refreshKey 驱动重载
  }, [refreshKey]);

  useEffect(() => {
    void load();
  }, [load]);

  const maxX = TRACK_WIDTH - SLIDER_SIZE;

  const onPointerDown = (clientX: number) => {
    if (!challenge || passed) return;
    setDragging(true);
    startXRef.current = clientX;
    originOffsetRef.current = offsetX;
  };

  const onPointerMove = (clientX: number) => {
    if (!dragging) return;
    const next = Math.min(
      maxX,
      Math.max(0, originOffsetRef.current + (clientX - startXRef.current)),
    );
    setOffsetX(next);
  };

  const onPointerUp = () => {
    if (!dragging || !challenge) return;
    setDragging(false);
    setPassed(true);
    onChange?.({ captchaId: challenge.captchaId, slideX: Math.round(offsetX) });
  };

  useEffect(() => {
    if (!dragging) return;
    const move = (e: PointerEvent) => onPointerMove(e.clientX);
    const up = () => onPointerUp();
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
    return () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dragging, challenge, offsetX]);

  return (
    <div style={{ width: TRACK_WIDTH }}>
      <div
        style={{
          position: 'relative',
          width: TRACK_WIDTH,
          height: 150,
          borderRadius: 8,
          overflow: 'hidden',
          background: '#E8EEF7',
          marginBottom: 10,
        }}
      >
        {loading || !challenge ? (
          <div
            style={{
              height: '100%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Spin size="small" />
          </div>
        ) : (
          <>
            <img
              src={challenge.backgroundImage}
              alt="captcha-bg"
              width={TRACK_WIDTH}
              height={150}
              draggable={false}
              style={{ display: 'block', userSelect: 'none' }}
            />
            <img
              src={challenge.sliderImage}
              alt="captcha-slider"
              width={SLIDER_SIZE}
              height={SLIDER_SIZE}
              draggable={false}
              style={{
                position: 'absolute',
                left: offsetX,
                top: challenge.sliderY,
                userSelect: 'none',
                pointerEvents: 'none',
                filter: 'drop-shadow(0 1px 2px rgba(0,0,0,.35))',
              }}
            />
          </>
        )}
        <Button
          type="text"
          size="small"
          icon={<ReloadOutlined />}
          onClick={() => void load()}
          style={{ position: 'absolute', right: 4, top: 4, color: '#fff' }}
        />
      </div>
      <div
        style={{
          position: 'relative',
          height: 40,
          borderRadius: 20,
          background: passed ? '#ECFDF5' : '#F1F5F9',
          border: `1px solid ${passed ? '#A7F3D0' : '#E2E8F0'}`,
          userSelect: 'none',
        }}
      >
        <div
          style={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 13,
            color: passed ? '#059669' : '#94A3B8',
            pointerEvents: 'none',
          }}
        >
          {passed ? '验证完成' : '向右拖动滑块完成验证'}
        </div>
        <div
          onPointerDown={(e) => {
            e.currentTarget.setPointerCapture(e.pointerId);
            onPointerDown(e.clientX);
          }}
          style={{
            position: 'absolute',
            left: offsetX,
            top: 2,
            width: 36,
            height: 36,
            borderRadius: 18,
            background: passed ? '#10B981' : '#2B52D9',
            color: '#fff',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: passed ? 'default' : 'grab',
            boxShadow: '0 2px 6px rgba(43,82,217,.35)',
            fontSize: 14,
            fontWeight: 600,
          }}
        >
          ⟫
        </div>
      </div>
    </div>
  );
}
