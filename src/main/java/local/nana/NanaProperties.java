package local.nana;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nana")
public class NanaProperties {
    private final Telegram telegram = new Telegram();
    private final Ollama ollama = new Ollama();
    private final Context context = new Context();
    private final RateLimit rateLimit = new RateLimit();
    private int responseMaxChars = 1800;

    public Telegram getTelegram() { return telegram; }
    public Ollama getOllama() { return ollama; }
    public Context getContext() { return context; }
    public RateLimit getRateLimit() { return rateLimit; }
    public int getResponseMaxChars() { return responseMaxChars; }
    public void setResponseMaxChars(int value) { responseMaxChars = value; }

    public static class Telegram {
        private String apiBase = "https://api.telegram.org";
        private int pollTimeoutSeconds = 30;
        private int connectTimeoutSeconds = 10;
        public String getApiBase() { return apiBase; }
        public void setApiBase(String value) { apiBase = value; }
        public int getPollTimeoutSeconds() { return pollTimeoutSeconds; }
        public void setPollTimeoutSeconds(int value) { pollTimeoutSeconds = value; }
        public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
        public void setConnectTimeoutSeconds(int value) { connectTimeoutSeconds = value; }
    }
    public static class Ollama {
        private String baseUrl = "http://localhost:11434";
        private String model = "qwen2.5:7b";
        private int timeoutSeconds = 180;
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String value) { baseUrl = value; }
        public String getModel() { return model; }
        public void setModel(String value) { model = value; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int value) { timeoutSeconds = value; }
    }
    public static class Context {
        private int maxMessages = 16;
        private int maxAgeMinutes = 120;
        public int getMaxMessages() { return maxMessages; }
        public void setMaxMessages(int value) { maxMessages = value; }
        public int getMaxAgeMinutes() { return maxAgeMinutes; }
        public void setMaxAgeMinutes(int value) { maxAgeMinutes = value; }
    }
    public static class RateLimit {
        private int groupSeconds = 3;
        public int getGroupSeconds() { return groupSeconds; }
        public void setGroupSeconds(int value) { groupSeconds = value; }
    }
}
