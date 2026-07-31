package com.starsoft.voint.question;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Zəng təhlili üçün ayrıca, HƏDDİ OLAN icraçı.
 *
 * <p>Spring-in defolt {@code SimpleAsyncTaskExecutor}-u hər tapşırığa yeni thread açır — həddsiz.
 * Bir neçə zəng eyni anda bitəndə bu, Gemini-yə paralel onlarla sorğu deməkdir; bu server
 * beş başqa layihə ilə paylaşılır və 7.8 GB yaddaşı var.
 *
 * <p>Növbə dolarsa {@code CallerRunsPolicy}: tapşırıq sükutla atılmır, çağıran thread-də işlənir.
 * Bu, təhlili yavaşladır, amma itirmir — arxa fon işində sükutla itmək ən pis nəticədir.
 */
@Configuration
@EnableAsync
public class CallAnalysisAsyncConfig {

    @Bean("callAnalysisExecutor")
    public Executor callAnalysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("call-analysis-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
