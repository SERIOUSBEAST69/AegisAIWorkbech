package com.trustai.config;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MybatisSlowSqlInnerInterceptor implements InnerInterceptor {

    private static final Logger log = LoggerFactory.getLogger("slow-sql");
    private static final long SLOW_SQL_MS = 200;

    @Override
    @SuppressWarnings("rawtypes")
    public void beforeQuery(
        org.apache.ibatis.executor.Executor executor,
        org.apache.ibatis.mapping.MappedStatement ms,
        Object parameter,
        org.apache.ibatis.session.RowBounds rowBounds,
        org.apache.ibatis.session.ResultHandler resultHandler,
        org.apache.ibatis.mapping.BoundSql boundSql
    ) {
        StatementTimer.start(ms.getId());
    }

    @Override
    public void beforeUpdate(org.apache.ibatis.executor.Executor executor, org.apache.ibatis.mapping.MappedStatement ms, Object parameter) {
        StatementTimer.start(ms.getId());
    }

    public void afterQuery(
        org.apache.ibatis.executor.Executor executor,
        org.apache.ibatis.mapping.MappedStatement ms,
        Object parameter,
        org.apache.ibatis.session.RowBounds rowBounds,
        @SuppressWarnings("rawtypes")
        org.apache.ibatis.session.ResultHandler resultHandler,
        org.apache.ibatis.mapping.BoundSql boundSql,
        java.util.List<?> resultList
    ) {
        StatementTimer.finishAndLog(ms.getId());
    }

    public void afterUpdate(org.apache.ibatis.executor.Executor executor, org.apache.ibatis.mapping.MappedStatement ms, Object parameter, int result) {
        StatementTimer.finishAndLog(ms.getId());
    }

    private static final class StatementTimer {
        private static final ThreadLocal<Long> START = new ThreadLocal<>();
        private static final ThreadLocal<String> ID = new ThreadLocal<>();

        static void start(String statementId) {
            START.set(System.nanoTime());
            ID.set(statementId);
        }

        static void finishAndLog(String fallbackId) {
            Long start = START.get();
            if (start == null) {
                return;
            }
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            String statementId = ID.get() == null ? fallbackId : ID.get();
            if (durationMs >= SLOW_SQL_MS) {
                log.warn("slow_sql id={} durationMs={}", statementId, durationMs);
            }
            START.remove();
            ID.remove();
        }
    }
}
