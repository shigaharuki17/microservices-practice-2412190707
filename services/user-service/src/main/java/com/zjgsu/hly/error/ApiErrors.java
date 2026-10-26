package com.zjgsu.hly.error;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
@RestControllerAdvice
public class ApiErrors {
    public record Error(int status, String message) {}
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<Error> business(BusinessException e) {
        return ResponseEntity.status(e.status).body(new Error(e.status, e.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<Error> invalid(Exception e) {
        return ResponseEntity.badRequest().body(new Error(400, "请求参数不合法"));
    }
    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class})
    ResponseEntity<Error> conflict(Exception e) {
        return ResponseEntity.status(409).body(new Error(409, "数据重复或已被其他请求修改"));
    }
}
