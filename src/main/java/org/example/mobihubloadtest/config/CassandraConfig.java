package org.example.mobihubloadtest.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cassandra.CqlSessionBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration;

@Configuration
public class CassandraConfig extends AbstractCassandraConfiguration {

    @Value("${spring.cassandra.keyspace-name}")
    private String keyspaceName;

    @Value("${spring.cassandra.contact-points}")
    private String contactPoints;

    @Value("${spring.cassandra.port}")
    private int port;

    @Override
    protected String getKeyspaceName() {
        return keyspaceName;
    }

    @Override
    protected String getContactPoints() {
        return contactPoints;
    }

    @Bean
    public CqlSessionBuilderCustomizer authCustomizer() {
        return builder -> builder
                .withAuthCredentials("username", "password")
                .withLocalDatacenter("datacenter1");
    }
}
