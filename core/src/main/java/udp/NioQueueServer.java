package udp;

/**
 * .
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
import java.util.concurrent.Exchanger;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 290k - 320k req p second on localhost
 * 390000 req p second with remote clients.
 *
 * with spinning 350k on localhost, 405k with remote clients
 */
class NioQueueServer {


    private static final Statistics stats = new Statistics();

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
        final LinkedBlockingQueue<SocketAddress> addresses = new LinkedBlockingQueue<>(queueCapacity);


        Runnable receiver = () -> {
            try {
                ByteBuffer receiveBuffer = ByteBuffer.allocate(1024);
                Selector receiveSelector = Selector.open();
                channel.register(receiveSelector, SelectionKey.OP_READ);
                while (true) {
                    SocketAddress receiveAddress = null;
                    int retries = 32;
                    while (retries-- > 0 && receiveAddress == null) {
                        receiveAddress = channel.receive(receiveBuffer);
                    }
                    receiveBuffer.clear();
                    if (receiveAddress != null) {
                        addresses.put(receiveAddress);
                    } else {
//                        System.out.printf("Waiting for read");
                        receiveSelector.select();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
        Runnable sender = () -> {
            try {
                byte[] sendData = "dupa".getBytes();
                ByteBuffer sendBuffer = ByteBuffer.allocate(sendData.length);
                sendBuffer.put(sendData);
                sendBuffer.flip();
                Selector sendSelector = Selector.open();
                channel.register(sendSelector, SelectionKey.OP_WRITE);
                while (true) {
                    SocketAddress address = null;
                    address = addresses.take();
                    assert address != null;
                    int sentBytes = channel.send(sendBuffer, address);
                    sendBuffer.rewind();
                    if (sentBytes == 0) {
                        System.out.printf("Waiting on write");
                        sendSelector.select();
                    } else {
//                        System.out.printf(" " + addresses.size());
                        stats.onSent();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
        Thread senderThread = new Thread(sender);
        Thread receiverThread = new Thread(receiver);
        senderThread.start();
        receiverThread.start();
        receiverThread.join();
    }
}