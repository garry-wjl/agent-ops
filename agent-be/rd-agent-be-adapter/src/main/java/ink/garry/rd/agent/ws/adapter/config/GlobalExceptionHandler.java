package ink.garry.rd.agent.ws.adapter.config;

import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.facade.common.Result;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.util.TraceContext;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：所有异常转换为 Result，HTTP 200 + body.code 区分。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> business(BusinessException ex) {
        log.warn("business exception code={} msg={}", ex.getCode(), ex.getMessage());
        return ResponseEntity.ok(Result.<Void>fail(ex.getCode(), ex.getMessage()).withTraceId(TraceContext.get()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, ConstraintViolationException.class,
            IllegalArgumentException.class})
    public ResponseEntity<Result<Void>> validation(Exception ex) {
        log.warn("validation failed: {}", ex.getMessage());
        return ResponseEntity.ok(Result.<Void>fail(BizCode.INVALID_PARAM.getCode(), ex.getMessage())
                .withTraceId(TraceContext.get()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> unknown(Exception ex) {
        log.error("uncaught exception", ex);
        return ResponseEntity.status(HttpStatus.OK)
                .body(Result.<Void>fail(BizCode.SYSTEM_BUSY.getCode(), ex.getMessage())
                        .withTraceId(TraceContext.get()));
    }
}
