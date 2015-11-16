package udp;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author Paweł Sikora
 */
public class NioServer {


    public static void main(String[] args) throws Exception {
        InetSocketAddress bindAddr;
        if (args.length == 2) {
            bindAddr = new InetSocketAddress(args[0], Integer.valueOf(args[1]));
        } else {
            bindAddr = new InetSocketAddress(9876);
        }
        DatagramChannel channel = DatagramChannel.open();
        channel.bind(bindAddr);
        channel.configureBlocking(false);
        DatagramSocket serverSocket = channel.socket();
        serverSocket.setSendBufferSize(66000 * 100);
        serverSocket.setReceiveBufferSize(66000 * 100);
        System.out.println("receive buff size: " + serverSocket.getReceiveBufferSize());
        System.out.println("send buff size: " + serverSocket.getSendBufferSize());
        int queueCapacity = 10000;
        LinkedBlockingQueue<SocketAddress> addresses = new LinkedBlockingQueue<>(queueCapacity);

        byte[] sendData = "dupa".getBytes();
        ByteBuffer sendBuffer = ByteBuffer.allocate(sendData.length);
        sendBuffer.put(sendData);
        sendBuffer.flip();

        ByteBuffer receiveBuffer = ByteBuffer.allocate(1024);

        Selector selector = Selector.open();
        channel.register(selector, SelectionKey.OP_READ);


        long testStartPeriod = System.currentTimeMillis();
        long curTime = 0;
        long handledRequests = 0;
        long allReceived = 0;
        Statistics statistics = new Statistics(testStartPeriod, handledRequests, allReceived).invoke();
        while (true) {
//            System.out.println("waiting");
            selector.select();
            SelectionKey key = selector.selectedKeys().iterator().next();
            if (key.isWritable()) {
                SocketAddress address;
                while ((address = addresses.peek()) != null) {
                    int sentBytes = channel.send(sendBuffer, address);
                    sendBuffer.rewind();
                    if (sentBytes == 0) {
                        key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
//                        System.out.println("Waiting for write or read");
                        break;
                    } else {
                        addresses.poll();
                        statistics.invoke();
                    }
                }
//                System.out.printf("Waiting for read");
                key.interestOps(SelectionKey.OP_READ);
            } else if (key.isReadable()) {
                while (true) {
                    SocketAddress receiveAddress = channel.receive(receiveBuffer);
                    receiveBuffer.clear();
                    if (receiveAddress != null) {
                        int sentBytes = channel.send(sendBuffer, receiveAddress);
                        sendBuffer.rewind();
                        if (sentBytes == 0) {
                            System.out.print(" output full ");
                            addresses.offer(receiveAddress);
                            if (addresses.size() == queueCapacity) {
                                System.out.println("queue full");
                                key.interestOps(SelectionKey.OP_WRITE);
//                                System.out.println("waiting for write");
                                break;
                            }
                        } else {
                            statistics.invoke();
                            SocketAddress address;
                            while ((address = addresses.peek()) != null) {
                                int sentBytes2 = channel.send(sendBuffer, address);
                                sendBuffer.rewind();
                                if (sentBytes2 == 0) {
                                    break;
                                } else {
                                    addresses.poll();
                                    statistics.invoke();
                                }
                            }
                        }
                    } else {
                        //not enough to read
                        if (!addresses.isEmpty()) {
                            key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                        }
                        break;
                    }
                }
            }
        }
    }

    private static class Statistics {
        private long testStartPeriod;
        private long handledRequests;
        private long allReceived;

        public Statistics(long testStartPeriod, long handledRequests, long allReceived) {
            this.testStartPeriod = testStartPeriod;
            this.handledRequests = handledRequests;
            this.allReceived = allReceived;
        }

        public long getTestStartPeriod() {
            return testStartPeriod;
        }

        public long getHandledRequests() {
            return handledRequests;
        }

        public long getAllReceived() {
            return allReceived;
        }

        public Statistics invoke() {
            long curTime;
            ++handledRequests;
            ++allReceived;
            curTime = System.currentTimeMillis();
            if (curTime - testStartPeriod >= 1000) {
                System.out.println(String.valueOf(curTime - testStartPeriod) + " " + handledRequests + " all received: " + allReceived);
                handledRequests = 0;
                testStartPeriod = curTime;
            }
            return this;
        }
    }
}