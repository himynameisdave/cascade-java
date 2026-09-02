package com.cascade.api.cache;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cross-node cache for values that are expensive to recompute and tolerable to
 * serve slightly stale — report summaries and the per-request user index.
 *
 * <p>Entries expire rather than being invalidated, so a node that misses an
 * update is wrong for at most the TTL.
 */
public class ClusterCache implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ClusterCache.class);

    private static final String REPORTS = "cascade.reports";
    private static final int TTL_SECONDS = 120;

    private final HazelcastInstance hazelcast;

    public ClusterCache(String clusterName) {
        Config config = new Config();
        config.setClusterName(clusterName == null ? "cascade" : clusterName);
        config.setProperty("hazelcast.logging.type", "slf4j");

        MapConfig reports = new MapConfig(REPORTS);
        reports.setTimeToLiveSeconds(TTL_SECONDS);
        config.addMapConfig(reports);

        this.hazelcast = Hazelcast.newHazelcastInstance(config);
        LOG.info("cache joined cluster '{}'", config.getClusterName());
    }

    private IMap<String, String> reports() {
        return hazelcast.getMap(REPORTS);
    }

    public String get(String key) {
        return reports().get(key);
    }

    public void put(String key, String value) {
        reports().put(key, value, TTL_SECONDS, TimeUnit.SECONDS);
    }

    /** Drops every cached report for a project after its issues change. */
    public void evictProject(String projectId) {
        reports().keySet().removeIf(key -> key.startsWith(projectId + ':'));
    }

    @Override
    public void close() {
        hazelcast.shutdown();
    }
}
