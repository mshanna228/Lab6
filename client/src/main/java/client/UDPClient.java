package client;

import common.interaction.Request;
import common.interaction.Response;
import common.interaction.ResponseCode;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

/**
 * UDP-клиент, использующий неблокирующий DatagramChannel для обмена данными с сервером.
 * Корректно обрабатывает временную недоступность сервера с помощью таймаутов и повторных попыток.
 */
public class UDPClient {
    private final InetSocketAddress serverAddress;
    private DatagramChannel channel;
    private final int MAX_ATTEMPTS = 5;
    private final int TIMEOUT = 2000; // 2 секунды ожидания
    private final int BUFFER_SIZE = 65535; // Максимальный размер UDP-пакета

    public UDPClient(String host, int port) {
        this.serverAddress = new InetSocketAddress(host, port);
    }

    /**
     * Открывает неблокирующий канал.
     */
    public void start() throws IOException {
        channel = DatagramChannel.open();
        channel.configureBlocking(false);
    }

    /**
     * Закрывает канал.
     */
    public void stop() {
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
            } catch (IOException e) {
                System.err.println("Ошибка при закрытии канала: " + e.getMessage());
            }
        }
    }

    /**
     * Отправляет запрос и ожидает ответ в неблокирующем режиме.
     * При отсутствии ответа повторяет попытку до MAX_ATTEMPTS раз.
     */
    public Response sendAndReceive(Request request) {
        try {
            // 1. Сериализация запроса
            byte[] requestBytes = serialize(request);
            if (requestBytes.length > BUFFER_SIZE) {
                return new Response(ResponseCode.ERROR, "Ошибка: Размер запроса превышает лимит UDP пакета!");
            }

            int attempt = 0;
            ByteBuffer responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);

            while (attempt < MAX_ATTEMPTS) {
                attempt++;
                responseBuffer.clear();
                
                // Отправляем пакет
                ByteBuffer sendBuffer = ByteBuffer.wrap(requestBytes);
                channel.send(sendBuffer, serverAddress);

                // Ожидаем ответ в неблокирующем режиме с таймаутом
                long startTime = System.currentTimeMillis();
                SocketAddress sender = null;

                while (System.currentTimeMillis() - startTime < TIMEOUT) {
                    sender = channel.receive(responseBuffer);
                    if (sender != null) {
                        break;
                    }
                    Thread.sleep(10); // Небольшая пауза, чтобы не нагружать процессор
                }

                if (sender != null) {
                    // Ответ получен! Десериализуем его
                    responseBuffer.flip();
                    byte[] responseBytes = new byte[responseBuffer.remaining()];
                    responseBuffer.get(responseBytes);
                    return deserialize(responseBytes);
                }

                System.out.println("Превышено время ожидания ответа от сервера. Попытка " + attempt + " из " + MAX_ATTEMPTS + "...");
            }

            return new Response(ResponseCode.ERROR, "Сервер временно недоступен. Пожалуйста, попробуйте позже.");

        } catch (Exception e) {
            return new Response(ResponseCode.ERROR, "Произошла ошибка при обмене данными по сети: " + e.getMessage());
        }
    }

    private byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
            oos.flush();
            return baos.toByteArray();
        }
    }

    private Response deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (Response) ois.readObject();
        }
    }
}
