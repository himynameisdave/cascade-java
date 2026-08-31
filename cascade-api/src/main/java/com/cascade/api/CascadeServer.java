package com.cascade.api;

import com.cascade.api.schedule.DigestJob;
import com.cascade.api.web.AuthFilter;
import com.cascade.api.web.AuthServlet;
import com.cascade.api.web.CascadeVersion;
import com.cascade.api.web.CommentServlet;
import com.cascade.api.web.HealthServlet;
import com.cascade.api.web.IntegrationServlet;
import com.cascade.api.web.IssueServlet;
import com.cascade.api.web.ProjectServlet;
import com.cascade.api.web.ReportServlet;
import com.cascade.api.web.SearchServlet;
import com.cascade.api.web.Services;
import com.cascade.core.config.AppConfig;
import jakarta.servlet.DispatcherType;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point: boots embedded Tomcat, mounts the servlets, starts Quartz. */
public final class CascadeServer {

    private static final Logger LOG = LoggerFactory.getLogger(CascadeServer.class);

    private CascadeServer() {
    }

    public static void main(String[] args) throws LifecycleException {
        Path configPath = Paths.get(
                System.getenv().getOrDefault("CASCADE_CONFIG", "config/app.yml"));
        AppConfig config = AppConfig.load(configPath);

        if ("change-me-in-production".equals(config.getJwtSecret())) {
            LOG.warn("JWT_SECRET is unset — using the built-in development secret");
        }

        Services services = new Services(config);
        Runtime.getRuntime().addShutdownHook(new Thread(services::close));

        Tomcat tomcat = new Tomcat();
        tomcat.setPort(config.getPort());
        tomcat.getConnector();

        File docBase = new File(System.getProperty("java.io.tmpdir"));
        Context context = tomcat.addContext("", docBase.getAbsolutePath());
        context.setAttribute(Services.ATTRIBUTE, services);

        mount(tomcat, context, "health", "/api/health", new HealthServlet(services));
        mount(tomcat, context, "auth", "/api/auth/*", new AuthServlet(services));
        mount(tomcat, context, "projects", "/api/projects/*", new ProjectServlet(services));
        mount(tomcat, context, "issues", "/api/issues/*", new IssueServlet(services));
        mount(tomcat, context, "comments", "/api/comments/*", new CommentServlet(services));
        mount(tomcat, context, "search", "/api/search/*", new SearchServlet(services));
        mount(tomcat, context, "reports", "/api/reports/*", new ReportServlet(services));
        mount(tomcat, context, "integrations", "/api/integrations/*",
                new IntegrationServlet(services));

        // One filter authenticates every request and renders thrown ApiExceptions.
        var filter = new org.apache.tomcat.util.descriptor.web.FilterDef();
        filter.setFilterName("auth");
        filter.setFilter(new AuthFilter(services));
        context.addFilterDef(filter);

        var mapping = new org.apache.tomcat.util.descriptor.web.FilterMap();
        mapping.setFilterName("auth");
        mapping.addURLPattern("/api/*");
        EnumSet.of(DispatcherType.REQUEST).forEach(type -> mapping.setDispatcher(type.name()));
        context.addFilterMap(mapping);

        if (config.feature("digests")) {
            startScheduler(services);
        }

        tomcat.start();
        LOG.info("Cascade API v{} listening on http://127.0.0.1:{}",
                CascadeVersion.VERSION, config.getPort());
        tomcat.getServer().await();
    }

    private static void mount(Tomcat tomcat, Context context, String name, String pattern,
                              jakarta.servlet.Servlet servlet) {
        Tomcat.addServlet(context, name, servlet);
        context.addServletMappingDecoded(pattern, name);
    }

    private static void startScheduler(Services services) {
        try {
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
            JobDetail job = JobBuilder.newJob(DigestJob.class)
                    .withIdentity("digest")
                    .build();
            job.getJobDataMap().put(DigestJob.SERVICES_KEY, services);

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity("digest-hourly")
                    .startNow()
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInHours(24)
                            .repeatForever())
                    .build();

            scheduler.scheduleJob(job, trigger);
            scheduler.start();
            LOG.info("digest scheduler started");
        } catch (SchedulerException e) {
            // A scheduler that will not start must not stop the API serving.
            LOG.warn("could not start the digest scheduler: {}", e.getMessage());
        }
    }
}
