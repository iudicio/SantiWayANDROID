package com.example.santiway.upload_data;

public class RabbitMQConfig {
    // Эти настройки нужно будет вынести в конфигурацию или получать с сервера
    public static final String RABBITMQ_HOST = "your-rabbitmq-host";
    public static final int RABBITMQ_PORT = 5672;
    public static final String RABBITMQ_USERNAME = "user";
    public static final String RABBITMQ_PASSWORD = "password";
    public static final String RABBITMQ_VHOST = "vhost";

    // Очереди
    public static final String VENDOR_QUEUE = "vendor_queue";
    public static final String ES_WRITER_QUEUE = "es_writer";

    // Имена tasks
    public static final String VENDOR_TASK_NAME = "vendor";
    public static final String ES_WRITER_TASK_NAME = "esWriter";

    public static String getConnectionString() {
        return String.format("amqp://%s:%s@%s:%d/%s",
                RABBITMQ_USERNAME,
                RABBITMQ_PASSWORD,
                RABBITMQ_HOST,
                RABBITMQ_PORT,
                RABBITMQ_VHOST);
    }
}
