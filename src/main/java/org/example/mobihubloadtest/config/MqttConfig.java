package org.example.mobihubloadtest.config;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import java.util.Arrays;

@Slf4j
@Configuration
public class MqttConfig {
    @Value("${mqtt.client-id}")
    private String clientId;

    @Value("${mqtt.username}")
    private String username;

    @Value("${mqtt.password}")
    private String password;

    @Value("${mqtt.broker-urls}")
    private String[] brokerUrls;

    @Value("${mqtt.topics}")
    private String[] topics;


    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(brokerUrls);
        options.setCleanSession(false);
        options.setAutomaticReconnect(true);

        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(120);

        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttInbound() {
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(clientId, mqttClientFactory(), topics);
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(0);
        adapter.setOutputChannel(mqttInputChannel());
        log.info("Starting MQTT inbound adapter for topics: {}", Arrays.toString(topics));
        return adapter;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new PublishSubscribeChannel();
    }

}
