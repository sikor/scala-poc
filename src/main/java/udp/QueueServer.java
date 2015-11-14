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

class QueueServer {
    public static void main(String args[]) throws Exception {
        DatagramSocket serverSocket = new DatagramSocket(9876);
        serverSocket.setSendBufferSize(66000 * 100);
        serverSocket.setReceiveBufferSize(66000 * 100);
        System.out.println("receive buff size: " + serverSocket.getReceiveBufferSize());
        System.out.println("send buff size: " + serverSocket.getSendBufferSize());
        LinkedBlockingQueue<InetSocketAddress> addresses = new LinkedBlockingQueue<>();
        Runnable receiver = () -> {
            byte[] receiveData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            try {
                while (true) {
                    serverSocket.receive(receivePacket);
                    addresses.offer(new InetSocketAddress(receivePacket.getAddress(), receivePacket.getPort()));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        };
        Runnable sender = () -> {
            byte[] sendData = new byte[1024];
            String capitalizedSentence = "dupa";
            sendData = capitalizedSentence.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length);
            long testStartPeriod = System.currentTimeMillis();
            long curTime = 0;
            long handledRequests = 0;
            long allReceived = 0;
            try {
                while (true) {
                    InetSocketAddress address = null;
                    address = addresses.poll(100, TimeUnit.DAYS);
                    sendPacket.setAddress(address.getAddress());
                    sendPacket.setPort(address.getPort());
                    serverSocket.send(sendPacket);
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