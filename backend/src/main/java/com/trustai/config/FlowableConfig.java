package com.trustai.config;

import org.flowable.engine.HistoryService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.spring.ProcessEngineFactoryBean;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.IOException;

/**
 * Explicit Flowable Process Engine configuration.
 *
 * <p>Defining these beans explicitly guarantees that the ProcessEngine and its
 * service beans (RuntimeService, TaskService, …) are always created, even when
 * MyBatis-Plus or JPA auto-configuration would otherwise interfere with
 * Flowable's own Spring-Boot auto-configuration.
 *
 * <p>All beans are annotated with {@code @Bean} without
 * {@code @ConditionalOnMissingBean} so they always take precedence; Flowable's
 * own auto-configuration uses {@code @ConditionalOnMissingBean} everywhere and
 * will therefore skip re-creating these beans.
 */
@Configuration
public class FlowableConfig {

    @Value("${flowable.database-schema-update:true}")
    private String databaseSchemaUpdate;

    @Value("${flowable.async-executor-activate:false}")
    private boolean asyncExecutorActivate;

    @Bean
    public SpringProcessEngineConfiguration processEngineConfiguration(
            DataSource dataSource,
            PlatformTransactionManager transactionManager,
            ResourcePatternResolver resourcePatternResolver) throws IOException {
        Resource[] processDefinitions;
        try {
            processDefinitions = resourcePatternResolver.getResources("classpath*:/processes/*.bpmn20.xml");
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load BPMN process definitions from classpath:/processes/", e);
        }
        SpringProcessEngineConfiguration config = new SpringProcessEngineConfiguration();
        config.setDataSource(dataSource);
        config.setTransactionManager(transactionManager);
        config.setDatabaseSchemaUpdate(databaseSchemaUpdate);
        config.setAsyncExecutorActivate(asyncExecutorActivate);
        config.setDeploymentResources(processDefinitions);
        return config;
    }

    @Bean
    public ProcessEngineFactoryBean processEngineFactoryBean(
            SpringProcessEngineConfiguration processEngineConfiguration) {
        ProcessEngineFactoryBean factoryBean = new ProcessEngineFactoryBean();
        factoryBean.setProcessEngineConfiguration(processEngineConfiguration);
        return factoryBean;
    }

    @Bean
    public RuntimeService runtimeService(ProcessEngine processEngine) {
        return processEngine.getRuntimeService();
    }

    @Bean
    public TaskService taskService(ProcessEngine processEngine) {
        return processEngine.getTaskService();
    }

    @Bean
    public RepositoryService repositoryService(ProcessEngine processEngine) {
        return processEngine.getRepositoryService();
    }

    @Bean
    public HistoryService historyService(ProcessEngine processEngine) {
        return processEngine.getHistoryService();
    }

    @Bean
    public ManagementService managementService(ProcessEngine processEngine) {
        return processEngine.getManagementService();
    }
}
