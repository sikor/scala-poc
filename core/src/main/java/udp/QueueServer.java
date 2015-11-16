package udp;

/**
 * Created by Paweł Sikora.
 */

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 320000 - 380000 req p second on localhost.
 * 550000 req p second with clients on separate nodes.
 */
class QueueServer {
    public static void main(String args[]) throws Exception {
        InetSocketAddress bindAddr;
        if (args.length == 2) {
            bindAddr = new InetSocketAddress(args[0], Integer.valueOf(args[1]));
        } else {
            bindAddr = new InetSocketAddress(9876);
        }
        final DatagramSocket serverSocket = new DatagramSocket(bindAddr);
        serverSocket.setSendBufferSize(66000 * 100);
        serverSocket.setReceiveBufferSize(66000 * 100);
        System.out.println("receive buff size: " + serverSocket.getReceiveBufferSize());
        System.out.println("send buff size: " + serverSocket.getSendBufferSize());
        LinkedBlockingQueue<InetSocketAddress> addresses = new LinkedBlockingQueue<>(10000);
        Statistics stats = new Statistics();
        Runnable receiver = () -> {
            byte[] receiveData = new byte[1024];
            try {
                while (true) {
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    serverSocket.receive(receivePacket);
                    addresses.add(new InetSocketAddress(receivePacket.getAddress(), receivePacket.getPort()));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        };
        Runnable sender = () -> {
            byte[] sendData = new byte[1024];
            String capitalizedSentence = "dupa";
            sendData = capitalizedSentence.getBytes();
            try {
                while (true) {
                    InetSocketAddress address = null;
                    address = addresses.poll(100, TimeUnit.DAYS);
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length);
                    sendPacket.setAddress(address.getAddress());
                    sendPacket.setPort(address.getPort());
                    serverSocket.send(sendPacket);
                    stats.onSent();
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