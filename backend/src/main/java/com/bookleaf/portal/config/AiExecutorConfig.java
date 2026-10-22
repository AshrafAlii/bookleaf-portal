package com.bookleaf.portal.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The executor used for background AI work.
 *
 * <p>Deliberately <b>single-threaded with a bounded queue</b>. A self-hosted model
 * can only serve one request at a time usefully — a second concurrent request
 * needs its own copy of the context in RAM — so queueing the work behind one
 * thread keeps memory flat and turns a burst of tickets into a steady stream
 * rather than a pile-up.</p>
 *
 * <p>If the queue fills, the refinement is dropped rather than run on the
 * caller's thread: the ticket already has a valid deterministic classification,
 * so skipping the AI refinement is a safe outcome and keeps the request fast.</p>
 */
@Configuration
public class AiExecutorConfig {

    private static final Logger log = LoggerFactory.getLogger(AiExecutorConfig.class);

    @Bean(name = "aiTriageExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor aiTriageExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ai-triage-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                log.warn("AI triage queue is full — dropping a refinement; "
                        + "the ticket keeps its deterministic classification");
                super.rejectedExecution(r, executor);
            }
        });
        executor.initialize();
        return executor;
    }
}
