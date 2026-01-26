package com.csc301.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import java.io.FileReader;
import java.io.IOException;

@Component
public class ConfigLoader {
    private static ISCSConfig iscsConfig;

    public static Config loadConfig(String configPath, String serviceName) throws IOException {
        FileReader reader = new FileReader(configPath);
        JsonObject jsonConfig = JsonParser.parseReader(reader).getAsJsonObject();
        reader.close();

        JsonObject serviceConfig = jsonConfig.getAsJsonObject(serviceName);
        int port = serviceConfig.get("port").getAsInt();
        String ip = serviceConfig.get("ip").getAsString();

        JsonObject iscsJson = jsonConfig.getAsJsonObject("InterServiceCommunication");
        iscsConfig = new ISCSConfig(iscsJson.get("ip").getAsString(), iscsJson.get("port").getAsInt());

        return new Config(ip, port);
    }

    public static String getISCSIp(String configPath) throws IOException {
        FileReader reader = new FileReader(configPath);
        JsonObject jsonConfig = JsonParser.parseReader(reader).getAsJsonObject();
        reader.close();
        JsonObject iscsJson = jsonConfig.getAsJsonObject("InterServiceCommunication");
        return iscsJson.get("ip").getAsString();
    }

    public static int getISCSPort(String configPath) throws IOException {
        FileReader reader = new FileReader(configPath);
        JsonObject jsonConfig = JsonParser.parseReader(reader).getAsJsonObject();
        reader.close();
        JsonObject iscsJson = jsonConfig.getAsJsonObject("InterServiceCommunication");
        return iscsJson.get("port").getAsInt();
    }

    @Bean
    public ISCSConfig getISCSConfig() {
        return iscsConfig;
    }

    public static class Config {
        public String ip;
        public int port;

        public Config(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }
    }

    public static class ISCSConfig {
        public String ip;
        public int port;

        public ISCSConfig(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }
    }
}
