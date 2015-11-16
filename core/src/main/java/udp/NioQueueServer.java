package udp;

/**
 * Created by Paweł Sikora.
 */

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

class NioQueueServer {


    public static void main(String args[]) throws Exception {
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


        Runnable receiver = () -> {
            try {
                ByteBuffer receiveBuffer = ByteBuffer.allocate(1024);
                SocketAddress receiveAddress;
                Selector receiveSelector = Selector.open();
                channel.register(receiveSelector, SelectionKey.OP_READ);
                while (true) {
                    receiveAddress = channel.receive(receiveBuffer);
                    receiveBuffer.clear();
                    if (receiveAddress != null) {
                        addresses.offer(receiveAddress);
                    } else {
                        receiveSelector.select();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        };
        Runnable sender = () -> {
            try {
                byte[] sendData = "dupa".getBytes();
                ByteBuffer sendBuffer = ByteBuffer.allocate(sendData.length);
                sendBuffer.put(sendData);
                sendBuffer.flip();
                long testStartPeriod = System.currentTimeMillis();
                long curTime = 0;
                long handledRequests = 0;
                long allReceived = 0;
                Selector sendSelector = Selector.open();
                channel.register(sendSelector, SelectionKey.OP_WRITE);
                while (true) {
                    SocketAddress address = null;
                    address = addresses.poll(100, TimeUnit.DAYS);
                    int sentBytes = channel.send(sendBuffer, address);
                    sendBuffer.rewind();
                    if (sentBytes == 0) {
                        System.out.printf("Waiting on write");
                        sendSelector.select();
                    }
                    ++handledRequests;
                    ++allReceived;
                    curTime = System.currentTimeMillis();
                    if (curTime - testStartPeriod >= 1000) {
                        System.out.println(String.valueOf(curTime - testStartPeriod) + " " + handledRequests + " all received: " + allReceived);
                        handledRequests = 0;
                        testStartPeriod = curTime;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
        Thread senderThread = new Thread(receiver);
        Thread receiverThread = new Thread(sender);
        senderThread.start();
        receiverThread.start();

        receiverThread.join();
    }
}